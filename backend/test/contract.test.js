import test from 'node:test';
import assert from 'node:assert/strict';
import { buildServer } from '../dist/server.js';

test('process endpoint authenticates and is idempotent', async () => {
  process.env.NOTECAT_PERSONAL_TOKEN = 'test-token'; process.env.NOTECAT_FAKE_TRANSCRIPT = 'Koupit mléko'; process.env.NOTECAT_FAKE_NOTE = '# Úkol\n- Koupit mléko';
  const app = buildServer(); await app.ready();
  const form = new FormData(); form.set('idempotencyKey', 'abc'); form.set('audio', new Blob(['audio'], { type: 'audio/mp4' }), 'a.m4a');
  const first = await app.inject({ method: 'POST', url: '/v1/notes/process', headers: { authorization: 'Bearer test-token' }, payload: form });
  assert.equal(first.statusCode, 200); assert.equal(JSON.parse(first.body).cached, false);
  const form2 = new FormData(); form2.set('idempotencyKey', 'abc'); form2.set('audio', new Blob(['other'], { type: 'audio/mp4' }), 'b.m4a');
  const second = await app.inject({ method: 'POST', url: '/v1/notes/process', headers: { authorization: 'Bearer test-token' }, payload: form2 });
  assert.equal(JSON.parse(second.body).cached, true); await app.close();
});
