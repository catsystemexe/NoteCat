# Architektura NoteCat

## Přehled komponent

```
┌───────────────────────────── Android aplikace ─────────────────────────────┐
│                                                                            │
│  Aktivace                     Capture flow                Zpracování       │
│  ─────────                    ────────────                ──────────       │
│  QS dlaždice ─┐                                                            │
│  App shortcut ┼─▶ CaptureActivity ─▶ CaptureViewModel                      │
│  Ikona/FAB  ──┘        │                  │                                │
│                        ▼                  ▼                                │
│                  CaptureScreen      NoteRecorder ── AudioRecord (16 kHz)   │
│                  (Compose)            │      │                             │
│                                       ▼      ▼                             │
│                                 WavWriter  VoskEndpointer                  │
│                                 (soubor)   (povel „konec“)                 │
│                                       │                                    │
│                        save-first ────▼──────────────────────────┐         │
│                                 NoteRepository ── Room (notes)   │         │
│                                       │                          ▼         │
│                                       └────────▶ ProcessNoteWorker         │
│                                                  (WorkManager, retry)      │
│                                                        │                   │
│  MainActivity ─ NotesListScreen / NoteDetailScreen /   │ BackendClient     │
│                 SettingsScreen (Compose + Navigation)  │ (OkHttp, raw WAV) │
└────────────────────────────────────────────────────────┼───────────────────┘
                                                         ▼  HTTPS + Bearer
┌──────────────────────────────── backend (Node.js) ─────────────────────────┐
│ app.mjs: auth → rate-limit → idempotency store → processNote               │
│   ├─ providers.mjs: Whisper API (STT, language=cs)                         │
│   │                 Claude API (structured output {polished})              │
│   ├─ text.mjs: stripEndCommand, isEmptyTranscript                          │
│   └─ store.mjs: JSON soubor s výsledky (idempotence, partial results)      │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Klíčová rozhodnutí

### 1. Jeden zdroj audia (AudioRecord → WAV + Vosk)

Zadání vyžaduje současně (a) spolehlivý záznam pro přepis a (b) rozpoznání
hlasového povelu. `MediaRecorder` + systémový `SpeechRecognizer` nemohou
sdílet mikrofon (na Androidu 10+ tichne ten s nižší prioritou). Proto vlastní
smyčka nad `AudioRecord`: každý PCM buffer jde zároveň do `WavWriter`
a do `VoskEndpointer`. Vosk pracuje offline s malým českým modelem
(`vosk-model-small-cs-0.4-rhasspy`), který se stahuje při buildu do assets
a při prvním spuštění rozbaluje do interního úložiště.

### 2. Detekce povelu „konec“

- Rozpoznávač běží s gramatikou `["konec", "[unk]"]` – binární úloha
  „zaznělo cílové slovo?“ je řádově robustnější než plný přepis.
- Ukončuje se **jen na finální výsledek promluvy**, jejíž text je přesně
  `konec`. Vosk finalizuje promluvu po přirozené pauze v řeči; přirozené
  použití je tedy: dokončit myšlenku → pauza → „konec“.
- Slova obsahující „konec“ („nakonec“) nebo „konec“ uvnitř věty neukončují –
  ve finální promluvě jsou obklopena dalšími tokeny.
- Prvních 1 500 ms se povel ignoruje.
- Libovolně dlouhé pauzy v diktátu nič neukončí – ticho pouze finalizuje
  promluvu ve Vosku, nahrávání běží dál.

### 3. Save-first a životní cyklus poznámky

```
      nahrávání
          │ stop (povel/tlačítko/zavření obrazovky)
          ▼
   WAV dokončen (hlavička + fsync)
          ▼
   PENDING ──── síť OK ────▶ PROCESSING ──── úspěch ────▶ DONE (audio smazáno)
      ▲  │                      │
      │  │ výpadek sítě/5xx     │ prázdný přepis / 4xx / AI chyba
      │  ▼                      ▼
      retry (backoff, max 6) FAILED (audio zůstává, tlačítko „Zkusit znovu“)
```

- Poznámka existuje v Room **před** jakoukoli síťovou aktivitou;
  potvrzení „Uloženo, zpracovávám.“ nečeká na síť.
- `ProcessNoteWorker` běží jako unikátní práce `process-note-<id>`
  (`KEEP` při enqueue, `REPLACE` při ručním retry) – nikdy dvě zpracování
  téže poznámky souběžně.
- Po pádu procesu `NoteCatApp.onCreate` → `recoverOnStartup`:
  PROCESSING → PENDING + re-enqueue, poznámky bez audio souboru → FAILED,
  osiřelé WAV bez záznamu v DB se smažou.
- Zavření nahrávací obrazovky uprostřed diktátu (`onCleared`) poznámku
  **uloží**, nezahodí – ochrana myšlenky má přednost.

### 4. Idempotence (klient i server)

- Klient posílá `X-Idempotency-Key = id poznámky` (stabilní napříč retry).
- Server si drží výsledky v perzistentním JSON store:
  - hotový výsledek → vrací se z cache, žádné volání modelů, žádné dvojí náklady;
  - hotový přepis + selhaná AI úprava → retry přeskočí Whisper a opakuje jen
    Claude krok;
  - souběžné requesty se stejným klíčem sdílejí jednu rozpracovanou úlohu
    (`inFlight` mapa).
- Klientská pojistka: worker před zpracováním kontroluje `status == DONE`.

### 5. AI úprava s validovaným výstupem

Claude dostává systémový prompt s explicitními zákazy (nepřidávat fakta,
nehádat jména, nevytvářet připomínky/kategorie) a vrací odpověď přes
structured output (`output_config.format`, JSON schema
`{polished: string}`) – tvar odpovědi je vynucený API, ne parsováním prózy.
Původní přepis se ukládá odděleně (`rawTranscript`) a editace uživatele jde
vždy jen do `polishedText`.

### 6. Uchování audia

Audio = dočasný technický zdroj: žije v `files/audio/` (interní úložiště),
maže se ihned po úspěšném zpracování, při smazání poznámky a při úklidu
osiřelých souborů na startu. U FAILED poznámek zůstává pro retry. Neomezený
růst brání (a) mazání po úspěchu, (b) úklid sirotků, (c) FAILED poznámky
jsou viditelné v UI a jdou smazat.

## Datový model (Room, tabulka `notes`)

| Sloupec | Typ | Význam |
|---|---|---|
| `id` | TEXT PK | UUID poznámky |
| `createdAt` / `updatedAt` | INTEGER | epocha ms |
| `rawTranscript` | TEXT? | původní přepis (immutable po zápisu) |
| `polishedText` | TEXT? | AI-upravený / ručně editovaný text |
| `status` | TEXT | PENDING / PROCESSING / DONE / FAILED |
| `audioPath` | TEXT? | cesta k WAV; null po úspěchu |
| `errorMessage` | TEXT? | poslední chyba pro UI |
| `idempotencyKey` | TEXT | klíč pro backend (== id) |
| `durationMs` | INTEGER | délka záznamu |

## Backend API

`POST /v1/notes/process`

| | |
|---|---|
| Auth | `Authorization: Bearer <NOTECAT_TOKEN>` (timing-safe) |
| Hlavičky | `X-Idempotency-Key` (povinná), `Content-Type: audio/wav` |
| Tělo | raw WAV (max 25 MB) |
| 200 | `{transcript, polished}` · `{transcript:"", polished:"", empty:true}` · `{transcript, polished:"", polishFailed:true}` |
| 4xx | 400 chybějící klíč/audio · 401 token · 413 velikost · 429 rate-limit |
| 5xx | 502 trvalá chyba STT · 503 dočasná chyba upstreamu (klient opakuje) |

`GET /health` → `{ok:true}` (bez auth, pro monitoring).
