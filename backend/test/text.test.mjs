import { test } from 'node:test';
import assert from 'node:assert/strict';
import { stripEndCommand, isEmptyTranscript } from '../src/text.mjs';

test('odstraní povel „konec" z konce přepisu', () => {
  assert.equal(stripEndCommand('Zavolat sklenáři konec'), 'Zavolat sklenáři');
});

test('odstraní povel s interpunkcí a velkým písmenem', () => {
  assert.equal(stripEndCommand('Zavolat sklenáři. Konec.'), 'Zavolat sklenáři.');
  assert.equal(stripEndCommand('Zavolat sklenáři, konec!'), 'Zavolat sklenáři');
});

test('ponechá „konec" uprostřed textu', () => {
  assert.equal(
    stripEndCommand('Na konec měsíce naplánovat úklid garáže'),
    'Na konec měsíce naplánovat úklid garáže'
  );
});

test('neodstraní „nakonec" na konci', () => {
  assert.equal(
    stripEndCommand('Rozhodl jsem se to udělat nakonec'),
    'Rozhodl jsem se to udělat nakonec'
  );
});

test('prázdný vstup vrací prázdný řetězec', () => {
  assert.equal(stripEndCommand(''), '');
  assert.equal(stripEndCommand(null), '');
});

test('samotný povel dá prázdný přepis', () => {
  assert.equal(stripEndCommand('Konec.'), '');
});

test('isEmptyTranscript: prázdné a interpunkční přepisy', () => {
  assert.equal(isEmptyTranscript(''), true);
  assert.equal(isEmptyTranscript('   '), true);
  assert.equal(isEmptyTranscript('...'), true);
  assert.equal(isEmptyTranscript('Ahoj'), false);
});
