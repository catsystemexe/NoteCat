import { createApp } from './src/app.mjs';
import { ResultStore } from './src/store.mjs';
import {
  createWhisperTranscriber,
  createOpenAiPolisher,
} from './src/providers.mjs';

const {
  NOTECAT_TOKEN,
  OPENAI_API_KEY,
  POLISH_MODEL = 'gpt-4.1-mini',
  WHISPER_MODEL = 'whisper-1',
  PORT = '8787',
  DATA_DIR = './data',
} = process.env;

if (!NOTECAT_TOKEN) {
  console.error('Chybí NOTECAT_TOKEN (sdílený tajný token pro aplikaci).');
  process.exit(1);
}
if (!OPENAI_API_KEY) {
  console.error('Chybí OPENAI_API_KEY (přepis přes Whisper).');
  process.exit(1);
}

const app = createApp({
  authToken: NOTECAT_TOKEN,
  transcribe: createWhisperTranscriber({ apiKey: OPENAI_API_KEY, model: WHISPER_MODEL }),
  polish: createOpenAiPolisher({
    apiKey: OPENAI_API_KEY,
    model: POLISH_MODEL,
  }),
  store: new ResultStore(DATA_DIR),
  log: (msg) => console.log(new Date().toISOString(), msg),
});

app.listen(Number(PORT), () => {
  console.log(`NoteCat backend poslouchá na portu ${PORT} (model úprav: ${POLISH_MODEL})`);
});
