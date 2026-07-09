/**
 * Úpravy přepisu před AI zpracováním.
 */

/**
 * Odstraní ukončovací povel („konec") z konce přepisu, včetně interpunkce
 * a variant, které kolem něj Whisper typicky vyprodukuje.
 * Povel uprostřed textu se nechává být – tam je to obsah poznámky.
 */
export function stripEndCommand(transcript, command = 'konec') {
  if (!transcript) return '';
  // Povel musí stát jako samostatné slovo na úplném konci (začátek řetězce
  // nebo mezera před ním) – „nakonec" se tedy nedotkne. Interpunkce
  // předchozí věty zůstává zachovaná.
  const pattern = new RegExp(`(^|\\s)${command}[\\s.!?…]*$`, 'iu');
  return transcript
    .replace(pattern, '$1')
    .replace(/[\s,\-–—]+$/u, '')
    .trim();
}

/** Konzervativní detekce prázdného přepisu (jen bílé znaky / interpunkce). */
export function isEmptyTranscript(transcript) {
  if (!transcript) return true;
  return transcript.replace(/[\s.,!?…\-–—]/gu, '').length === 0;
}
