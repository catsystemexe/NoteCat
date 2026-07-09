# NoteCat 🐈

Osobní Android aplikace pro bleskové zachycení hlasové poznámky. Nadiktuješ
myšlenku, řekneš **„konec“** (nebo klepneš na tlačítko), poznámka se okamžitě
bezpečně uloží a na pozadí se přepíše (Whisper) a přeformuluje do stručné,
čitelné podoby (Claude).

```
aktivace → nahrávání → diktát → „konec“ → „Uloženo, zpracovávám.“
```

---

## Obsah repozitáře

| Cesta | Obsah |
|---|---|
| `app/` | Android aplikace (Kotlin, Jetpack Compose) |
| `backend/` | Node.js backend (Whisper STT + Claude úprava textu) |
| `docs/` | Architektura, testovací výsledky, ukázky UI |

## Použité technologie a proč

| Volba | Důvod |
|---|---|
| **Kotlin + Jetpack Compose, nativní Android** | Plný přístup k mikrofonu (`AudioRecord`), Quick Settings dlaždici, WorkManageru a bezpečnému úložišti; žádný webview kompromis. |
| **`AudioRecord` → WAV (16 kHz mono PCM)** | Jediný vlastník mikrofonu. Stejný PCM stream jde do souboru i do lokálního rozpoznávače povelu – `MediaRecorder` + systémový `SpeechRecognizer` současně o mikrofon soupeří a na Androidu 10+ jeden z nich mlčí. WAV navíc odpadá riziko poškozené AAC hlavičky při pádu. |
| **Vosk (offline, český model, ~46 MB)** | Rozpoznání povelu „konec“ **lokálně** – funguje bez sítě, nic neposílá ven. Běží s gramatikou omezenou na `["konec", "[unk]"]`, což je v hluku výrazně robustnější než plný přepis. |
| **Room + WorkManager** | Trvalé lokální uložení a persistentní fronta zpracování: přežije zavření aplikace, výpadek sítě (exponenciální retry) i zabití procesu. |
| **OpenAI Whisper (`whisper-1`)** | Prakticky nejlepší dostupná čeština pro krátké diktáty vč. jmen a technických názvů; jednoduché API (pošle se celý WAV). |
| **Claude (`claude-opus-4-8`, konfigurovatelné)** | Úprava přepisu se **strukturovaným výstupem** (JSON schema) – odpověď je technicky validovaná, model nemůže „odpovídat na poznámku“. Levnější varianta: `POLISH_MODEL=claude-haiku-4-5`. |
| **Node.js backend bez frameworku** | Jediná externí závislost (`@anthropic-ai/sdk`). Audio se posílá jako raw tělo requestu – žádný multipart, žádné další knihovny. |

## Rychlé spuštění

### 1. Backend

```bash
cd backend
npm install
cp .env.example .env        # vyplň NOTECAT_TOKEN, OPENAI_API_KEY, ANTHROPIC_API_KEY
node server.mjs             # poslouchá na :8787
```

- `NOTECAT_TOKEN` – sdílený tajný token (min. 16 znaků, např. `openssl rand -hex 24`).
- Pro dosažitelnost z telefonu spusť za HTTPS reverse proxy (Caddy, Cloudflare
  Tunnel, Tailscale…), nebo v lokální síti přes `http://<ip>:8787` (jen pro vývoj).

Testy: `cd backend && node --test` (19 testů, bez volání skutečných API).

### 2. Android aplikace

Požadavky: JDK 17+, Android SDK (platform 35). Vosk model se stáhne
automaticky při prvním buildu (~46 MB, necommituje se).

```bash
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM unit testy
adb install app/build/outputs/apk/debug/app-debug.apk
```

Volitelné dev defaulty do `local.properties`:

```properties
notecat.backendUrl=https://muj-server.example.com
notecat.backendToken=…
```

### 3. Konfigurace v aplikaci

1. Otevři NoteCat → ozubené kolo → **Nastavení**.
2. Vyplň **Adresu backendu** a **Přístupový token** (ukládají se do šifrovaných
   preferencí přes Android Keystore).
3. Přidej si dlaždici **Hlasová poznámka** do Quick Settings (tužka v panelu
   rychlého nastavení), případně zkratku dlouhým podržením ikony aplikace.

## Jak to funguje (datový tok)

```
┌────────────┐  PCM 16 kHz   ┌───────────────┐
│ AudioRecord ├──────┬──────▶│ WavWriter     │→ files/audio/<id>.wav
└────────────┘       │       └───────────────┘
                     │       ┌───────────────┐
                     └──────▶│ Vosk („konec“)│→ ukončení diktátu
                             └───────────────┘
   „konec“ / tlačítko
        │
        ▼
 1. WAV se korektně dokončí (hlavička, fsync)
 2. Room: INSERT Note(status=PENDING, audioPath)     ← save-first
 3. UI: „Uloženo, zpracovávám.“ + vibrace            ← žádné čekání na síť
 4. WorkManager: ProcessNoteWorker (unikátní na id poznámky, síťová constraint)
        │
        ▼  POST /v1/notes/process  (raw WAV, Bearer token, X-Idempotency-Key=id)
 ┌──────────────────────────── backend ────────────────────────────┐
 │ cache výsledků (idempotence) → Whisper (cs) → strip „konec“     │
 │ → Claude (JSON schema {polished}) → uložení výsledku            │
 └──────────────────────────────────────────────────────────────────┘
        │
        ▼
 5. Room: rawTranscript + polishedText, status=DONE
 6. Audio soubor se smaže (audio je dočasný zdroj, ne archiv)
```

Chybové větve:

- **Výpadek sítě / 5xx / 429** → worker vrátí `retry()` s exponenciálním
  backoffem (max 6 pokusů), poznámka zůstává PENDING a audio zachované.
- **Prázdný přepis** → poznámka se označí FAILED (nezmizí), audio zůstává.
- **Chyba AI úpravy** → backend vrátí přepis + `polishFailed`; aplikace uloží
  přepis jako dočasný text, poznámka je FAILED s tlačítkem „Zkusit znovu“.
  Retry díky serverové cache **znovu nevolá Whisper**, jen AI krok.
- **Pád aplikace** → při dalším startu se PROCESSING poznámky vrátí do PENDING
  a znovu zařadí, osiřelé WAV soubory se uklidí. Zavření nahrávací obrazovky
  uprostřed diktátu poznámku uloží (ne zahodí).

## Hlasový povel „konec“

- Vosk běží nad stejným PCM streamem jako zápis do WAV (žádný konflikt mikrofonu).
- Gramatika `["konec", "[unk]"]` – všechna ostatní slova se mapují na `[unk]`.
- Diktát se ukončí **jen** když je „konec“ celou samostatnou promluvou
  (krátká pauza → „konec“). „…a nakonec zavolat mámě“ nahrávání neukončí.
- Prvních 1,5 s se povel ignoruje (ochrana proti šumu při startu).
- Povel se odstraňuje z přepisu na backendu (`stripEndCommand`) a AI prompt ho
  má explicitně ignorovat.
- Když povel nezachytí (hluk, šeptání), nic se neděje – výrazné tlačítko
  **Hotovo** je vždy dole na dosah palce.

## Bezpečnost a soukromí

- **Žádná tajemství v APK** – adresa backendu a token se zadávají v aplikaci
  a ukládají v `EncryptedSharedPreferences` (Android Keystore).
- Backend vyžaduje Bearer token (timing-safe porovnání), limituje velikost
  requestu (25 MB) i frekvenci (30/min) proti zneužití a nákladům.
- Audio žije jen v interním úložišti aplikace a maže se po úspěšném zpracování.
- Mikrofon je aktivní výhradně na nahrávací obrazovce; rozpoznání povelu je
  offline (Vosk). Data odcházejí pouze na tvůj backend → Whisper/Claude.
- `allowBackup=false` – poznámky se nezálohují mimo zařízení.

## Známá omezení

- Diktát běží na obrazovce (displej zapnutý); dlouhé diktování se zhasnutým
  displejem není podporováno (mimo scénář „krátká poznámka“).
- „Konec“ je rezervované slovo na konci promluvy – když má být poslední slovo
  poznámky, ukonči diktát tlačítkem a slovo případně doplň editací.
- Rate limit a idempotence backendu jsou v paměti/JSON souboru – přesně podle
  rozsahu osobního MVP, ne pro víc uživatelů.
- APK je velké (~100 MB) kvůli přibalenému českému Vosk modelu – daň za
  offline rozpoznání povelu bez stahování za běhu.
- Instrumentované testy (emulátor) nebyly v CI prostředí k dispozici; pokryto
  JVM unit testy + testy backendu, viz `docs/TESTY.md`.

## Možná další rozšíření (mimo MVP)

- Ukončení povelu i uprostřed promluvy s potvrzovacím oknem.
- Sdílení poznámky do jiných aplikací (Share sheet).
- Automatický re-run FAILED poznámek při obnovení konektivity.
- Widget na plochu, Wear OS klient.

Podrobná architektura: [docs/ARCHITEKTURA.md](docs/ARCHITEKTURA.md) ·
Testovací výsledky: [docs/TESTY.md](docs/TESTY.md) ·
Ukázky UI: [docs/ui-preview.html](docs/ui-preview.html)
