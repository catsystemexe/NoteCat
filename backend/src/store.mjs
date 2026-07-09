import { mkdirSync, readFileSync, writeFileSync, renameSync, existsSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Perzistentní úložiště výsledků pro idempotenci.
 * Klíč = X-Idempotency-Key (ID poznámky). Ukládá i částečné výsledky
 * (hotový přepis bez AI úpravy), aby retry po selhání AI nevolal
 * Whisper podruhé.
 */
export class ResultStore {
  constructor(dataDir) {
    this.file = join(dataDir, 'results.json');
    mkdirSync(dataDir, { recursive: true });
    this.data = existsSync(this.file)
      ? JSON.parse(readFileSync(this.file, 'utf8'))
      : {};
  }

  get(key) {
    return this.data[key] ?? null;
  }

  set(key, value) {
    this.data[key] = { ...value, updatedAt: new Date().toISOString() };
    this.#persist();
  }

  #persist() {
    const tmp = this.file + '.tmp';
    writeFileSync(tmp, JSON.stringify(this.data, null, 2));
    renameSync(tmp, this.file); // atomický zápis
  }
}

/** In-memory varianta pro testy. */
export class MemoryStore {
  constructor() {
    this.data = new Map();
  }
  get(key) {
    return this.data.get(key) ?? null;
  }
  set(key, value) {
    this.data.set(key, value);
  }
}
