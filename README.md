# NoteCat

NoteCat is an MVP native Android voice-note app with a small personal backend. The Android app saves audio first, creates a local pending note, confirms immediately, and only then processes the note in the background.

## Android app

- Kotlin + Jetpack Compose UI.
- Room stores `Note` records with `id`, timestamps, transcript, edited note text, processing status, audio path, error message, and idempotency key.
- WorkManager uploads audio and retries failures.
- Temporary audio lives under the app's internal `filesDir/audio` directory.
- Android `SpeechRecognizer` is only a best-effort helper for explicit Czech end commands such as “Konec”; silence never auto-stops capture.
- Quick Settings tile launches capture mode directly.

### Run

```bash
gradle :app:assembleDebug
```

Configure the app backend URL/token for local development by replacing the defaults in `ProcessNoteWorker` or injecting them through your app configuration strategy. Do not hardcode production secrets into the APK.

## Backend

The backend is a Fastify service in `backend/`.

### Configuration

```bash
export NOTECAT_PERSONAL_TOKEN='choose-a-long-random-personal-token'
export OPENAI_API_KEY='sk-...'
export OPENAI_MODEL='gpt-4.1-mini'
```

For contract tests without OpenAI calls:

```bash
export NOTECAT_FAKE_TRANSCRIPT='Test přepisu'
export NOTECAT_FAKE_NOTE='# Poznámka\n- Test'
```

### Run

```bash
cd backend
npm install
npm run build
npm run dev
```

### API

`POST /v1/notes/process`

- Auth: `Authorization: Bearer $NOTECAT_PERSONAL_TOKEN`.
- Multipart fields: `idempotencyKey`, `audio`.
- Returns `{ transcriptOriginal, noteText, cached }`.
- Repeated idempotency keys return cached output to prevent duplicate AI/transcription cost.

## Architecture and data flow

1. Capture screen starts `MediaRecorder` immediately.
2. User stops via button or explicit voice command.
3. Recorder finalizes audio safely in internal storage.
4. Room note is inserted as `pending` with an idempotency key.
5. UI confirms “Uloženo, zpracovávám.”.
6. WorkManager uploads audio to the backend.
7. Backend authenticates, checks idempotency cache, transcribes Czech audio, asks AI for structured Czech note text, validates output, and caches the result.
8. App stores transcript/note text as `done` and deletes audio.
9. Failures are marked `failed`; audio remains for retry.

## Security notes

- The backend requires a personal bearer token configured by environment variable.
- No production secret should be shipped in the APK.
- Idempotency protects users from duplicate processing costs on retries.
- Local audio is removed after successful processing and bounded by cleanup policy.

## Known limitations

- The MVP uses an in-memory idempotency cache; production should use persistent storage.
- Android backend configuration is intentionally minimal and should be moved to a secure configuration flow before release.
- Speech command detection is best-effort and Czech-focused.

## Testing

```bash
gradle test
cd backend && npm install && npm run build && npm test
```

Manual validation scenarios are in [`docs/manual-checklist.md`](docs/manual-checklist.md).

## Glass Night UI demo

The Android app now opens a self-contained Jetpack Compose demo of the NoteCat main screen on the `list` route. It is intentionally isolated from the production Room database, WorkManager processing, backend contract, and real audio capture flow. The existing capture route and repository code remain available for production capture entry points.

### Visual style

Glass Night uses a dark blue-black background, translucent dark cards, subtle blue/light borders, muted blue accents, and high-contrast grey/white text. Design tokens for colors, spacing, radius, typography, and animation durations live in `app/src/main/java/cz/notecat/ui/GlassNightTheme.kt`.

### Interactions

- Tap a note card to open an overlay detail with the full edited text, optional timestamp, and `Původní přepis` section.
- Double tap a card to edit its text with `Uložit` and `Zrušit` actions.
- Swipe a card left to reveal `Smazat`; deletion only happens after tapping that action.
- Deleted notes show a Snackbar with `Vrátit zpět`, restoring the note to its original position.
- Failed demo notes show `Opakovat`, transitioning through processing and then done.

### Demo recording flow

The centered bottom `+` button starts simulated recording, pulses gently, changes to a stop symbol, and shows elapsed seconds. Tapping it again stops the simulation, inserts a processing note at the top, and completes it after a short delay.

### Known limitations

This is a UI demo. It does not write demo notes into Room, upload audio, call the backend, or enqueue WorkManager jobs. Real capture remains separate in the existing `CaptureScreen`.
