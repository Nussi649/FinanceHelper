# Finance Helper: Hinweise für den Agenten (OneDrive)

Die Finance-Helper-App synchronisiert ihre Dateien automatisch mit dem OneDrive-Ordner
`Apps/FinanceHelper`. Wer dort Dateien liest oder ändert, kann das gefahrlos tun, wenn er diese
Regeln beachtet. Alles, was nicht ausdrücklich verboten ist, funktioniert.

## Dateien

- Pro Monat und Person eine Datei: `JJJJ-MM-Name.jso`, z. B. `2026-10-David.jso`.
- Der Inhalt ist JSON. Aufbau siehe `docs/file-format.md` im Projekt; die Kurzform:
  `assets` (Konten mit `tx`), `budgets` (Budgets, ggf. mit `sub_budgets`), `recur_tx`
  (Daueraufträge), `income`.
- Eine Buchung sieht so aus:
  `{ "d": "Miete", "a": "-850.00", "t": "01.10.2026 09:00", "i": "3fa9c01b77de", "m": "2026-10-01T07:12:44Z" }`
  - `d` Beschreibung, `a` Betrag (Punkt als Dezimaltrenner, Zeichen = Richtung),
    `t` Buchungsdatum `TT.MM.JJJJ HH:MM`.
  - `i` ist die **ID** der Buchung. `m` ist der **Zeitpunkt der letzten Änderung** in UTC,
    ISO-8601 (`2026-10-13T14:05:00Z`).

## Regeln

1. **IDs (`i`) behalten.** Beim Ändern einer vorhandenen Buchung bleibt `i` wie es ist. Über die ID
   erkennt die App, dass es dieselbe Buchung ist; ohne sie sähe eine Änderung wie "gelöscht und neu
   angelegt" aus.
2. **`m` auf jetzt setzen, wenn du eine Buchung änderst oder neu anlegst** (UTC, ISO-8601, ganze
   Sekunden). Haben Handy und Agent dieselbe Buchung gleichzeitig geändert, gewinnt die mit dem
   neueren `m`. Vergisst du `m`, nimmt die App ersatzweise den Zeitpunkt, an dem die Datei
   gespeichert wurde. Das funktioniert, ist aber ungenauer.
3. **Neue Buchungen:** `i` darf fehlen, die App vergibt eine und schreibt sie zurück. Du kannst auch
   selbst eine erzeugen (12 Zeichen Hex, z. B. zufällig).
4. **Die ganze Datei zu schreiben ist in Ordnung.** Du musst nicht gezielt einzelne Stellen ändern.
   Alles, was du nicht angefasst hast, bleibt erhalten; auch unbekannte zusätzliche Felder an
   Buchungen werden mitgeführt.
5. **Löschen:** Eine Buchung zu entfernen löscht sie auch in der App. Wurde sie in der Zwischenzeit
   am Handy geändert, bleibt die Änderung erhalten (Änderungen gehen vor Löschungen).
6. **Gültiges JSON schreiben.** Eine Datei, die nicht als JSON lesbar ist, wird von der App nicht
   angefasst und nicht synchronisiert, bis sie wieder gültig ist. Es geht dabei nichts verloren.
7. **Nicht anfassen:** den Unterordner `trash` (Sicherungskopien, siehe unten) und alle Dateien,
   die nicht `JJJJ-MM-Name.jso` heißen.
8. **Alte Monate** nur im Ausnahmefall ändern. Technisch geht es, die Änderung kommt an.

## Wann siehst du Änderungen vom Handy, wann sieht das Handy deine?

- Handy → OneDrive: etwa 5 bis 10 Sekunden nach dem Speichern in der App.
- OneDrive → Handy: spätestens wenn die App geöffnet wird, sonst alle paar Minuten, solange sie
  offen ist. Ist ein Konto-Detailbildschirm offen, schließt er sich dabei mit einem Hinweis.
- Schreiben Agent und Handy fast gleichzeitig, wird zusammengeführt (siehe 1 bis 5); die App
  schreibt nie blind über eine Datei, die sich zwischenzeitlich geändert hat.

## Wiederherstellen

Wird bei einer Zusammenführung etwas verworfen (eine Löschung, die unterlegene Fassung eines
Konflikts, ein entferntes Konto), legt die App vorher eine Kopie der betroffenen Datei unter
`trash/JJJJ-MM-Name.jso.JJJJMMTTTHHMMSSZ` ab. Zusätzlich führt OneDrive einen Versionsverlauf
jeder Datei.
