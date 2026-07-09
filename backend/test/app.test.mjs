import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from '../src/app.mjs';
import { MemoryStore } from '../src/store.mjs';

const TOKEN = 'test-token-1234567890abcdef';

function startApp(overrides = {}) {
  const calls = { transcribe: 0, polish: 0 };
  const app = createApp({
    authToken: TOKEN,
    transcribe: async () => {
      calls.transcribe += 1;
      return 'ehm zavolat sklenáři konec';
    },
    polish: async (t) => {
      calls.polish += 1;
      return `UPRAVENO: ${t}`;
    },
    store: new MemoryStore(),
    ...overrides,
  });
  return new Promise((resolve) => {
    app.listen(0, () => {
      const { port } = app.address();
      resolve({
        calls,
        close: () => new Promise((r) => app.close(r)),
        request: (path, options = {}) =>
          fetch(`http://127.0.0.1:${port}${path}`, options),
      });
    });
  });
}

const authHeaders = (key = 'note-1') => ({
  authorization: `Bearer ${TOKEN}`,
  'x-idempotency-key': key,
  'content-type': 'audio/wav',
});

const fakeAudio = () => Buffer.alloc(2000, 7);

test('health endpoint funguje bez auth', async () => {
  const srv = await startApp();
  const res = await srv.request('/health');
  assert.equal(res.status, 200);
  await srv.close();
});

test('bez tokenu vrací 401', async () => {
  const srv = await startApp();
  const res = await srv.request('/v1/notes/process', {
    method: 'POST',
    headers: { 'x-idempotency-key': 'a' },
    body: fakeAudio(),
  });
  assert.equal(res.status, 401);
  assert.equal(srv.calls.transcribe, 0);
  await srv.close();
});

test('špatný token vrací 401', async () => {
  const srv = await startApp();
  const res = await srv.request('/v1/notes/process', {
    method: 'POST',
    headers: { ...authHeaders(), authorization: 'Bearer spatny-token-123456' },
    body: fakeAudio(),
  });
  assert.equal(res.status, 401);
  await srv.close();
});

test('chybějící idempotency key vrací 400', async () => {
  const srv = await startApp();
  const res = await srv.request('/v1/notes/process', {
    method: 'POST',
    headers: { authorization: `Bearer ${TOKEN}` },
    body: fakeAudio(),
  });
  assert.equal(res.status, 400);
  await srv.close();
});

test('úspěšné zpracování: přepis bez povelu + AI úprava', async () => {
  const srv = await startApp();
  const res = await srv.request('/v1/notes/process', {
    method: 'POST',
    headers: authHeaders(),
    body: fakeAudio(),
  });
  assert.equal(res.status, 200);
  const body = await res.json();
  // povel „konec" je odstraněn PŘED AI úpravou
  assert.equal(body.transcript, 'ehm zavolat sklenáři');
  assert.equal(body.polished, 'UPRAVENO: ehm zavolat sklenáři');
  await srv.close();
});

test('idempotence: opakovaný request nevolá modely podruhé (scénář 15)', async () => {
  const srv = await startApp();
  await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders('same-key'), body: fakeAudio(),
  });
  const res2 = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders('same-key'), body: fakeAudio(),
  });
  assert.equal(res2.status, 200);
  const body = await res2.json();
  assert.equal(body.polished, 'UPRAVENO: ehm zavolat sklenáři');
  assert.equal(srv.calls.transcribe, 1);
  assert.equal(srv.calls.polish, 1);
  await srv.close();
});

test('prázdný přepis se vrací explicitně, ne jako chyba (scénář 9)', async () => {
  const srv = await startApp({ transcribe: async () => '   ' });
  const res = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders(), body: fakeAudio(),
  });
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.empty, true);
  assert.equal(body.transcript, '');
  await srv.close();
});

test('selhání AI úpravy vrací přepis + polishFailed (scénář 17)', async () => {
  const srv = await startApp({
    polish: async () => { throw new Error('AI down'); },
  });
  const res = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders(), body: fakeAudio(),
  });
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.polishFailed, true);
  assert.equal(body.transcript, 'ehm zavolat sklenáři');
  await srv.close();
});

test('retry po selhání AI přeskočí STT a zopakuje jen úpravu', async () => {
  let polishAttempts = 0;
  const srv = await startApp({
    polish: async (t) => {
      polishAttempts += 1;
      if (polishAttempts === 1) throw new Error('AI down');
      return `OK: ${t}`;
    },
  });
  await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders('retry-key'), body: fakeAudio(),
  });
  const res2 = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders('retry-key'), body: fakeAudio(),
  });
  const body = await res2.json();
  assert.equal(body.polished, 'OK: ehm zavolat sklenáři');
  assert.equal(srv.calls.transcribe, 1, 'Whisper se nesmí volat podruhé');
  await srv.close();
});

test('selhání přepisu vrací 5xx a poznámka jde opakovat (scénář 16)', async () => {
  let attempts = 0;
  const srv = await startApp({
    transcribe: async () => {
      attempts += 1;
      if (attempts === 1) {
        throw Object.assign(new Error('Whisper down'), { transient: true });
      }
      return 'druhý pokus konec';
    },
  });
  const res1 = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders('stt-retry'), body: fakeAudio(),
  });
  assert.equal(res1.status, 503);
  const res2 = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders('stt-retry'), body: fakeAudio(),
  });
  assert.equal(res2.status, 200);
  const body = await res2.json();
  assert.equal(body.transcript, 'druhý pokus');
  await srv.close();
});

test('prázdné tělo vrací 400', async () => {
  const srv = await startApp();
  const res = await srv.request('/v1/notes/process', {
    method: 'POST', headers: authHeaders(),
  });
  assert.equal(res.status, 400);
  await srv.close();
});

test('neznámá cesta vrací 404', async () => {
  const srv = await startApp();
  const res = await srv.request('/nic');
  assert.equal(res.status, 404);
  await srv.close();
});
