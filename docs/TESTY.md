# Testovací výsledky

## Automatizované testy (prošly v CI prostředí)

| Sada | Počet | Výsledek |
|---|---|---|
| Android JVM unit testy (`:app:testDebugUnitTest`) | 15 | ✅ vše prochází |
| Backend testy (`node --test`, mockované upstream API) | 19 | ✅ vše prochází |
| Android Lint (`:app:lintDebug`) | – | ✅ 0 chyb (42 varování – převážně „je k dispozici novější verze knihovny“) |
| Build (`:app:assembleDebug`) | – | ✅ APK ~104 MB (vč. Vosk modelu a nativních knihoven pro 4 ABI) |

Prostředí bez Android emulátoru – instrumentované testy a testy s reálným
mikrofonem/API klíči nebylo možné spustit; níže je uvedeno, čím je každý
povinný scénář pokryt.

## Pokrytí povinných scénářů ze zadání

| # | Scénář | Pokrytí |
|---|---|---|
| 1 | Krátká běžná česká poznámka | Návrh: Whisper `language=cs`; E2E tok pokryt backend testem „úspěšné zpracování“. **Vyžaduje ověření na zařízení s API klíči.** |
| 2 | Poznámka s dlouhými pauzami | Mechanismus: žádné auto-ukončení podle ticha neexistuje (smyčka `NoteRecorder` běží, dokud ji neukončí povel/tlačítko). Pauza jen finalizuje promluvu ve Vosku. |
| 3 | Technický název | Prompt Claude výslovně zakazuje hádat technické názvy; Whisper je přepisuje foneticky. **Ověřit na zařízení.** |
| 4 | České jméno | Totéž – prompt: „Nehádej nejasná jména… ponech je tak, jak jsou přepsané.“ |
| 5 | Ukončení hlasovým povelem | ✅ unit testy `EndCommandDetectorTest` (samostatný povel, velké písmeno, mezery). |
| 6 | Ukončení tlačítkem | Mechanismus: tlačítko „Hotovo“ volá stejný `finish(save=true)` tok; tlačítko je vždy viditelné. |
| 7 | Povel nerozpoznán | Mechanismus: nerozpoznání = žádná akce, nahrávání běží, tlačítko je záloha. Pokud Vosk model chybí/nenačte se, `endpointer` zůstane null a povel se tiše vypne. |
| 8 | Podobné slovo uprostřed poznámky | ✅ unit testy: „[unk] konec [unk]“ ani „[unk] [unk] konec“ neukončí; backend testy: `stripEndCommand` nesahá na „nakonec“ ani „konec“ uvnitř věty. |
| 9 | Prázdné nahrávání | ✅ dvě vrstvy: konzervativní lokální kontrola (< 0,7 s nebo peak < 250/32767 → zahodit s hláškou) + backend test „prázdný přepis se vrací explicitně“ → poznámka FAILED, nezmizí. |
| 10 | Velmi tichý hlas | Mechanismus: práh 250/32767 je hluboko pod úrovní tiché řeči – tichá řeč se neukládá jako prázdná. **Kalibraci ověřit na zařízení.** |
| 11 | Hluk v pozadí | Návrh: gramatikou omezené rozpoznávání (`["konec","[unk]"]`) mapuje hluk na `[unk]`; povel vyžaduje samostatnou promluvu. **Ověřit na zařízení.** |
| 12 | Výpadek sítě po uložení | ✅ mechanismus: poznámka je v DB před sítí; worker má `NetworkType.CONNECTED` constraint + `Result.retry()` s backoffem. Chyba spojení → `BackendError.Transient`. |
| 13 | Výpadek backendu | ✅ backend test „selhání přepisu vrací 5xx“; klient: 5xx/429 → Transient → retry, po 6 pokusech FAILED s audio zachovaným. |
| 14 | Pád aplikace během zpracování | ✅ mechanismus `recoverOnStartup`: PROCESSING → PENDING + re-enqueue; osiřelé WAV se uklidí; zavření capture obrazovky poznámku uloží (`onCleared`). |
| 15 | Opakovaný request po ztracené odpovědi | ✅ backend test „idempotence: opakovaný request nevolá modely podruhé“. |
| 16 | Chyba přepisu | ✅ backend test „selhání přepisu … poznámka jde opakovat“; audio se nemaže, stav FAILED viditelný. |
| 17 | Chyba AI | ✅ backend testy „selhání AI úpravy vrací přepis + polishFailed“ a „retry přeskočí STT“; klient uloží přepis jako dočasný text. |
| 18 | Ruční editace | Mechanismus: editace jde výhradně do `polishedText`; `rawTranscript` je po zápisu immutable (viz `NoteRepository.updatePolishedText`). |
| 19 | Smazání poznámky | Mechanismus: `NoteRepository.delete` maže záznam i audio soubor; UI má potvrzovací dialog. |
| 20 | Otevření aplikace s PENDING/FAILED poznámkou | ✅ mechanismus: seznam zobrazuje stavové chipy („Čeká na zpracování“, „Zpracování se nezdařilo“); start aplikace re-enqueuuje PENDING poznámky. |

## Chyby nalezené validací a jejich opravy

1. **`stripEndCommand` žral „nakonec“** – regex bez hranice slova odstraňoval
   „konec“ i uvnitř slova. Oprava: povel musí být samostatné slovo
   (`(^|\s)konec…$`). Odhaleno backend testem.
2. **`stripEndCommand` mazal interpunkci předchozí věty** – „Zavolat
   sklenáři. Konec.“ → „Zavolat sklenáři“ místo „Zavolat sklenáři.“.
   Oprava: úvodní třída znaků povelu nekonzumuje tečku předchozí věty.
3. **Lint error `StartActivityAndCollapseDeprecated`** – deprecated větev je
   na API < 34 jediná možná; doplněna cílená suprese s komentářem.
4. **`@anthropic-ai/sdk@0.116` neexistuje na npm** – opraveno na `^0.110.0`
   (aktuální vydání).

## Jak testy spustit

```bash
# Android
./gradlew :app:testDebugUnitTest :app:lintDebug

# Backend
cd backend && npm install && node --test
```

## Doporučený manuální test na zařízení (první spuštění)

1. Nastav backend URL + token, spusť backend s reálnými klíči.
2. Dlaždice → nadiktuj „Hele, máma má rozbitý okno, potřebuju zavolat
   sklenáři, jestli je dostupnej“ → pauza → „konec“.
3. Ověř: okamžité „Uloženo, zpracovávám.“ → v seznamu chip „Zpracovávám…“ →
   do pár sekund výsledný text typu „Zavolat sklenáři kvůli rozbitému oknu
   u mámy a ověřit jeho dostupnost.“ + původní přepis v detailu.
4. Zapni letadlový režim, nadiktuj poznámku → zůstává „Čeká na zpracování“;
   po vypnutí režimu se sama dozpracuje.
