import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { stripEndCommand, isEmptyTranscript } from './text.mjs';

const MAX_BODY_BYTES = 25 * 1024 * 1024; // limit Whisper API
const RATE_LIMIT_PER_MINUTE = 30;        // ochrana proti nadměrným nákladům

/**
 * Sestaví HTTP server. Závislosti (STT, AI, úložiště) se injektují,
 * aby šla logika testovat bez skutečných API.
 *
 * @param {object} deps
 * @param {string} deps.authToken  sdílený tajný token (Bearer)
 * @param {(audio: Buffer) => Promise<string>} deps.transcribe
 * @param {(transcript: string) => Promise<string>} deps.polish
 * @param {{get(k:string):any, set(k:string,v:any):void}} deps.store
 * @param {(msg: string) => void} [deps.log]
 */
export function createApp({ authToken, transcribe, polish, store, log = () => {} }) {
  if (!authToken || authToken.length < 16) {
    throw new Error('NOTECAT_TOKEN musí mít alespoň 16 znaků');
  }

  // Jednoduchý rate limit klouzavým oknem
  let windowStart = Date.now();
  let windowCount = 0;
  const rateLimited = () => {
    const now = Date.now();
    if (now - windowStart > 60_000) {
      windowStart = now;
      windowCount = 0;
    }
    windowCount += 1;
    return windowCount > RATE_LIMIT_PER_MINUTE;
  };

  // Souběžné requesty se stejným klíčem sdílejí jednu rozpracovanou úlohu
  const inFlight = new Map();

  const checkAuth = (req) => {
    const header = req.headers['authorization'] ?? '';
    const match = /^Bearer\s+(.+)$/.exec(header);
    if (!match) return false;
    const provided = Buffer.from(match[1]);
    const expected = Buffer.from(authToken);
    return (
      provided.length === expected.length && timingSafeEqual(provided, expected)
    );
  };

  const json = (res, status, body) => {
    const payload = JSON.stringify(body);
    res.writeHead(status, {
      'content-type': 'application/json; charset=utf-8',
      'content-length': Buffer.byteLength(payload),
    });
    res.end(payload);
  };

  const readBody = (req) =>
    new Promise((resolve, reject) => {
      const chunks = [];
      let size = 0;
      req.on('data', (chunk) => {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) {
          reject(Object.assign(new Error('payload too large'), { status: 413 }));
          req.destroy();
          return;
        }
        chunks.push(chunk);
      });
      req.on('end', () => resolve(Buffer.concat(chunks)));
      req.on('error', reject);
    });

  /**
   * Jádro zpracování jedné poznámky. Idempotentní:
   *  - hotový výsledek se vrací z cache bez volání modelů,
   *  - hotový přepis bez AI úpravy přeskočí STT a zopakuje jen AI krok.
   */
  const processNote = async (key, audio) => {
    const cached = store.get(key);
    if (cached?.done) {
      log(`[${key}] výsledek z cache`);
      return { status: 200, body: { transcript: cached.transcript, polished: cached.polished } };
    }

    let transcript = cached?.transcript;
    if (transcript === undefined || transcript === null) {
      if (!audio || audio.length < 100) {
        return { status: 400, body: { error: 'missing_audio' } };
      }
      log(`[${key}] přepisuji ${audio.length} B audia`);
      transcript = stripEndCommand(await transcribe(audio));
      store.set(key, { transcript, done: false });
    } else {
      log(`[${key}] přepis z cache, opakuji jen AI úpravu`);
    }

    if (isEmptyTranscript(transcript)) {
      store.set(key, { transcript: '', polished: '', done: true });
      return { status: 200, body: { transcript: '', polished: '', empty: true } };
    }

    let polished;
    try {
      polished = await polish(transcript);
    } catch (err) {
      log(`[${key}] AI úprava selhala: ${err.message}`);
      // Přepis se vrací, ať má klient dočasný text; retry zopakuje jen AI krok.
      return {
        status: 200,
        body: { transcript, polished: '', polishFailed: true },
      };
    }

    if (!polished || !polished.trim()) polished = transcript;
    store.set(key, { transcript, polished, done: true });
    return { status: 200, body: { transcript, polished } };
  };

  return createServer(async (req, res) => {
    try {
      
      if (req.method === 'GET' && req.url === '/') {
        return json(res, 200, {
          ok: true,
          service: 'NoteCat backend',
        });
      }
      
      if (req.method === 'GET' && req.url === '/health') {
        return json(res, 200, { ok: true });
      }

      if (req.method === 'POST' && req.url === '/v1/notes/process') {
        if (!checkAuth(req)) return json(res, 401, { error: 'unauthorized' });
        if (rateLimited()) return json(res, 429, { error: 'rate_limited' });

        const key = req.headers['x-idempotency-key'];
        if (!key || typeof key !== 'string' || key.length > 128) {
          return json(res, 400, { error: 'missing_idempotency_key' });
        }

        const audio = await readBody(req);

        // Deduplikace souběžných requestů se stejným klíčem
        let task = inFlight.get(key);
        if (!task) {
          task = processNote(key, audio).finally(() => inFlight.delete(key));
          inFlight.set(key, task);
        }
        const result = await task;
        return json(res, result.status, result.body);
      }

      json(res, 404, { error: 'not_found' });
    } catch (err) {
      const status = err.status ?? (err.transient ? 503 : 500);
      log(`chyba: ${err.stack ?? err.message}`);
      json(res, status, { error: err.publicMessage ?? 'internal_error' });
    }
  });
}
