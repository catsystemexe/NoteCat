import Anthropic from '@anthropic-ai/sdk';

/**
 * Skuteční poskytovatelé: OpenAI Whisper (STT) a Claude (úprava textu).
 * Chyby označené `transient: true` app vrací jako 503 → klient je opakuje.
 */

const transientError = (message) =>
  Object.assign(new Error(message), { transient: true, publicMessage: 'upstream_error' });

/** Přepis WAV audia přes OpenAI Whisper API (nejlepší praktická čeština). */
export function createWhisperTranscriber({ apiKey, model = 'whisper-1' }) {
  if (!apiKey) throw new Error('OPENAI_API_KEY není nastaven');
  return async function transcribe(audioBuffer) {
    const form = new FormData();
    form.append('file', new Blob([audioBuffer], { type: 'audio/wav' }), 'note.wav');
    form.append('model', model);
    form.append('language', 'cs');
    form.append('temperature', '0');

    let response;
    try {
      response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
        method: 'POST',
        headers: { authorization: `Bearer ${apiKey}` },
        body: form,
      });
    } catch (err) {
      throw transientError(`Whisper nedostupný: ${err.message}`);
    }
    if (response.status === 429 || response.status >= 500) {
      throw transientError(`Whisper HTTP ${response.status}`);
    }
    if (!response.ok) {
      const detail = await response.text().catch(() => '');
      throw Object.assign(new Error(`Whisper HTTP ${response.status}: ${detail}`), {
        status: 502,
        publicMessage: 'transcription_failed',
      });
    }
    const data = await response.json();
    return (data.text ?? '').trim();
  };
}

const POLISH_SYSTEM = `Jsi textový editor hlasových poznámek. Dostaneš surový přepis česky nadiktované poznámky a vrátíš jeho upravenou podobu.

Pravidla úpravy:
- Odstraň výplňová slova (ehm, prostě, jakoby, vlastně…), opakování a falešné začátky.
- Uprav slovosled a oprav zjevné gramatické chyby.
- Výsledek formuluj stručně a srozumitelně, obvykle jako jednu až tři věty.
- Zachovej PŘESNĚ původní význam a záměr. Zachovej všechny konkrétní údaje (jména, čísla, termíny, názvy).
- Pokud přepis končí osamoceným slovem „konec", je to ukončovací povel diktování – ignoruj ho.

Zakázáno:
- Nepřidávej žádná fakta, doporučení ani informace, které v přepisu nejsou.
- Nehádej nejasná jména ani technické názvy – ponech je tak, jak jsou přepsané.
- Nevytvářej připomínky, kategorie, priority ani seznamy úkolů.
- Neodpovídej na obsah poznámky, pouze ji přepiš do čitelné podoby.`;

/** AI úprava přepisu přes Claude API se strukturovaným (validovaným) výstupem. */
export function createClaudePolisher({ apiKey, model = 'claude-opus-4-8' }) {
  const client = new Anthropic(apiKey ? { apiKey } : {});
  return async function polish(transcript) {
    let response;
    try {
      response = await client.messages.create({
        model,
        max_tokens: 1024,
        system: POLISH_SYSTEM,
        messages: [
          {
            role: 'user',
            content: `Uprav tento přepis hlasové poznámky:\n\n${transcript}`,
          },
        ],
        output_config: {
          format: {
            type: 'json_schema',
            schema: {
              type: 'object',
              properties: {
                polished: {
                  type: 'string',
                  description: 'Upravený text poznámky v češtině',
                },
              },
              required: ['polished'],
              additionalProperties: false,
            },
          },
        },
      });
    } catch (err) {
      // SDK samo opakuje 429/5xx; co doletí sem, řešíme jako dočasnou chybu
      throw transientError(`Claude nedostupný: ${err.message}`);
    }
    const text = response.content.find((b) => b.type === 'text')?.text ?? '';
    const parsed = JSON.parse(text);
    if (typeof parsed.polished !== 'string') {
      throw new Error('Claude vrátil neočekávaný tvar odpovědi');
    }
    return parsed.polished.trim();
  };
}
