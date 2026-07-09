

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

/** AI úprava přepisu přes OpenAI API se strukturovaným výstupem. */
export function createOpenAiPolisher({
  apiKey,
  model = 'gpt-4.1-mini',
}) {
  if (!apiKey) {
    throw new Error('OPENAI_API_KEY není nastaven');
  }

  return async function polish(transcript) {
    let response;

    try {
      response = await fetch('https://api.openai.com/v1/chat/completions', {
        method: 'POST',
        headers: {
          authorization: `Bearer ${apiKey}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          model,
          temperature: 0,
          messages: [
            {
              role: 'system',
              content: POLISH_SYSTEM,
            },
            {
              role: 'user',
              content: `Uprav tento přepis hlasové poznámky:\n\n${transcript}`,
            },
          ],
          response_format: {
            type: 'json_schema',
            json_schema: {
              name: 'polished_note',
              strict: true,
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
        }),
      });
    } catch (err) {
      throw transientError(`OpenAI není dostupné: ${err.message}`);
    }

    if (response.status === 429 || response.status >= 500) {
      throw transientError(`OpenAI HTTP ${response.status}`);
    }

    if (!response.ok) {
      const detail = await response.text().catch(() => '');

      throw Object.assign(
        new Error(`OpenAI HTTP ${response.status}: ${detail}`),
        {
          status: 502,
          publicMessage: 'polish_failed',
        },
      );
    }

    const data = await response.json();
    const content = data.choices?.[0]?.message?.content ?? '';

    let parsed;

    try {
      parsed = JSON.parse(content);
    } catch {
      throw new Error('OpenAI vrátil neplatný JSON');
    }

    if (
      typeof parsed.polished !== 'string' ||
      parsed.polished.trim().length === 0
    ) {
      throw new Error('OpenAI vrátil neočekávaný tvar odpovědi');
    }

    return parsed.polished.trim();
  };
}