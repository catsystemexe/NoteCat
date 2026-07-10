import Fastify from 'fastify';
import multipart from '@fastify/multipart';
import OpenAI from 'openai';
import { z } from 'zod';

const Output = z.object({ transcriptOriginal: z.string().min(1), noteText: z.string().min(1) });
type Cached = z.infer<typeof Output>;
const cache = new Map<string, Cached>();

export function buildServer() {
  const app = Fastify({ logger: true });
  app.register(multipart, { limits: { fileSize: 25 * 1024 * 1024, files: 1 } });
  app.addHook('preHandler', async (req, reply) => {
    const token = process.env.NOTECAT_PERSONAL_TOKEN;
    if (!token) return reply.code(500).send({ error: 'Server token is not configured' });
    if (req.headers.authorization !== `Bearer ${token}`) return reply.code(401).send({ error: 'Unauthorized' });
  });
  app.post('/v1/notes/process', async (req, reply) => {
    const parts = req.parts(); let key = ''; let audio: Buffer | undefined;
    for await (const part of parts) {
      if (part.type === 'field' && part.fieldname === 'idempotencyKey') key = String(part.value);
      if (part.type === 'file' && part.fieldname === 'audio') audio = await part.toBuffer();
    }
    if (!key || !audio) return reply.code(400).send({ error: 'idempotencyKey and audio are required' });
    const cached = cache.get(key); if (cached) return { ...cached, cached: true };
    const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
    const transcript = process.env.NOTECAT_FAKE_TRANSCRIPT || await transcribeCzech(client, audio);
    const noteText = process.env.NOTECAT_FAKE_NOTE || await structureNote(client, transcript);
    const parsed = Output.parse({ transcriptOriginal: transcript, noteText });
    cache.set(key, parsed);
    return { ...parsed, cached: false };
  });
  return app;
}

async function transcribeCzech(client: OpenAI, audio: Buffer) {
  const file = new File([audio], 'note.m4a', { type: 'audio/mp4' });
  const response = await client.audio.transcriptions.create({ model: 'gpt-4o-mini-transcribe', file, language: 'cs' });
  return response.text;
}
async function structureNote(client: OpenAI, transcript: string) {
  const response = await client.responses.create({ model: process.env.OPENAI_MODEL || 'gpt-4.1-mini', input: `Přeformuluj český diktát do stručné strukturované poznámky s nadpisem a odrážkami. Zachovej fakta.\n\n${transcript}` });
  return response.output_text;
}
if (import.meta.url === `file://${process.argv[1]}`) buildServer().listen({ port: Number(process.env.PORT || 3000), host: '0.0.0.0' });
