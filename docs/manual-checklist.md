# NoteCat MVP manual checklist

1. App asks for microphone permission.
2. Launcher opens the chronological notes list.
3. Quick Settings tile opens capture directly.
4. Capture starts recording immediately.
5. Visible stop button is always available.
6. Saying “Konec” stops capture.
7. Similar words inside dictation do not stop unless recognized as command.
8. Silence does not stop recording.
9. Stop safely finalizes `.m4a` in app internal storage.
10. A local pending note is created before backend upload.
11. User sees “Uloženo, zpracovávám.” immediately.
12. WorkManager enqueues processing after local save.
13. Processing changes status to processing.
14. Successful backend response stores transcript and note text.
15. Successful processing deletes audio.
16. Failed processing keeps audio and shows failed state.
17. Retry reuses the existing note and idempotency key.
18. Search filters note text and original transcript.
19. Detail allows manual edit and save.
20. Delete removes database row and local audio file if present.
