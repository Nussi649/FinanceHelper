# Cloud sync

Status: the merge engine and the sync loop exist (`.../financehelper/sync/`), tested against
in-memory fakes. Not yet wired to the app or to OneDrive - see "Still to build".

## Model

Each save file (`YYYY-MM-Entity.jso`) exists twice, locally and in the cloud, plus a third copy
per file: the **base**, the version both sides last agreed on. A sync of one file is:

1. read local, remote (with its etag) and base
2. three-way merge (`SaveFileMerger`)
3. write the result to whichever side differs from it - the remote write only if the etag still
   matches, the local write only if the local file is still what was read
4. store the result as the new base

If either conditional write fails, the file is read and merged again (up to 5 times).

## Merge rules (`SaveFileMerger`)

- Transactions are matched by id (`i`), wherever they sit in the file.
- Only one side changed it: that side wins, no timestamp involved.
- Both changed it: the newer `m` wins, a tie goes to remote. A side that changed the content
  without advancing `m` is dated by its file's modification time.
- Deleted on one side, edited on the other: the edit survives.
- Deleted on one side, untouched on the other: deleted.
- Accounts match by name; their scalar properties merge per key (a real conflict goes to remote).
  An account deleted on one side and changed on the other stays.
- Recurring orders have no identity and merge as a set of whole values.
- Entries without an id (e.g. added by an external writer) get one; a repeated id gets a fresh one.
- First sync (no base): entries with identical content in the same account are taken to be the
  same transaction, everything else is unioned. A lost base therefore never loses data.

## Recoverability

Whenever a merge drops something (a deletion, the losing side of a conflict, a removed account),
the pre-merge file of the side that still holds it is copied to the trash first
(`<name>.<UTC timestamp>`, e.g. `2026-08-User.jso.20261013T140500Z`), locally and in the cloud
respectively. If that copy cannot be stored, the file is not touched. Ordinary syncs that drop
nothing write no trash. Nothing prunes the trash yet.

A file that is not valid JSON on either side is never overwritten; it is reported and skipped.

## Still to build

- Android implementations of `LocalFiles` / `SyncBaseStore` (the former must serialise with the
  app's own saves; the model must reload after a pull)
- `RemoteStore` on Microsoft Graph (`Apps/FinanceHelper`, `If-Match` on the etag - to be verified
  against a personal account; fallback is a GET-compare before the PUT)
- WorkManager triggers (debounced push after save, pull on start/resume), status UI
- Trash pruning, and a convention note for external writers (keep `i`, set `m` in UTC)
