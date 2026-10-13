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

## What a sync costs

Only what changed. Per file the engine remembers the base text and the remote etag it was written
under.

- `syncAll()` lists the remote folder once (names + etags). A file whose etag equals the remembered
  one still holds the base, so it is not downloaded; a local file whose text equals the base has not
  been changed by the app, so there is nothing to push. Such a file costs nothing further.
- A file changed only locally is pushed with one conditional write (`If-Match` on the remembered
  etag), without downloading first. If the cloud had changed in the meantime the write is refused,
  and only then is the file read and merged.
- A file changed in the cloud is downloaded once.
- `syncLocalChanges(names)` is for "the app just saved this": no listing, no download; a file that
  is unchanged locally causes no request at all.
- A missing or unknown etag is never trusted - it just means one full read.
- Measured on the device with 20 files: first run 42 s (learning the etags), unchanged runs 0.8 s.

## Recoverability

Whenever a merge drops something (a deletion, the losing side of a conflict, a removed account),
the pre-merge file of the side that still holds it is copied to the trash first
(`<name>.<UTC timestamp>`, e.g. `2026-08-User.jso.20261013T140500Z`), locally and in the cloud
respectively. If that copy cannot be stored, the file is not touched. Ordinary syncs that drop
nothing write no trash. Nothing prunes the trash yet.

A file that is not valid JSON on either side is never overwritten; it is reported and skipped.

## Android side (done)

- `StorageLocalFiles` / `StorageSyncBaseStore` run on the app's own `SavefileStorage`; the base
  copies and the trash live in `filesDir/sync-base` and `filesDir/sync-trash`, which the save-file
  code never lists (`DirectoryStorage.list()` now returns files only).
- `DirectoryStorage.write` is atomic (scratch file + rename), so the sync thread never reads half
  a file.
- A pull replaces the file on the **main thread** (`SyncSetup`), the thread that makes all of the
  app's own changes, and then `Controller.onSavefileReplacedBySync` reloads the open file into the
  model. Without that, the app's next save would write its stale model back over the pulled file
  and the sync would read that as the app deleting the agent's entries.
  `Controller.addSavefileReloadListener` tells screens to refresh (UI wiring still to do).
- `CloudSyncIntegrationTest` covers this with the real Controller and engine.

## OneDrive side (done, not yet tried on a device)

- `sync/graph/GraphRemoteStore` talks to Microsoft Graph in the app folder (`Apps/FinanceHelper`,
  `special/approot`); copies of overwritten files go to its `trash` sub-folder. Writes are
  conditional: `If-Match` with the eTag read earlier (412 -> `EtagMismatchException`), creation with
  `conflictBehavior=fail` (409 -> same). Metadata is read before content, so a file that changes in
  between makes the later write fail and retry - never the reverse.
- The content download is a 302 to a pre-authorised address; the store follows it itself so the
  bearer token is not sent there. `UrlConnectionTransport` never follows redirects.
- `verifyConditionalWrites()` proves on the real account that OneDrive enforces both conditions
  (writes a scratch file, tries a stale-eTag write and a create-over-existing, deletes it). The
  sync must not be trusted before this has passed once.
- `MsalTokenProvider` (MSAL 8.5.0, personal account, scope `Files.ReadWrite.AppFolder`): one
  interactive `signIn`, afterwards silent tokens from background workers. Client id and redirect
  URI are in `res/raw/msal_config.json`; the signature-hash path is also in the manifest
  (`BrowserTabActivity`). Both are tied to the **debug signing key**; a build signed with another
  key needs its own hash in Azure, the config and the manifest.
- MSAL needs one Microsoft-only artifact from a Microsoft Maven feed; `build.gradle` allows that
  repository for that one group only.
- Tested against `FakeGraph` (eTags, If-Match, 302 download, paging, 401, throttling) and end to end
  with the engine (`SyncOverGraphTest`).

## Automatic sync

One background job type (`CloudSyncWorker`, WorkManager, unique name `cloud-sync`) runs `syncAll`
and repeats up to three rounds while the app keeps saving during the run
(`hasPendingLocalChanges`). It waits for a network, retries with growing delays after a failure,
and survives the app being closed. A missing sign-in ends it quietly (only the user can fix that);
the outcome of every run is kept for the status line (`CloudSyncStatus`).

Triggers (`CloudSyncScheduler.requestSync`, a new request replaces the pending one):

- every successful write of a save file, 5 s later - so a burst of saves becomes one sync
  (`Controller.setSavefileWrittenListener`, wired in `AbstractActivity.initController`)
- a screen coming to the front (at most every 30 s) and every 5 minutes while one is open
- signing in

Only after the user signed in (`CloudSyncStatus.isEnabled`); signing out switches it off.
Runs exclude each other (`CloudSync.RUN_LOCK`), also against the "sync now" button.

### When a pull changes what the app shows

`Controller.onSavefileReplacedBySync` reloads the open file and bumps a generation counter.
Screens compare the counter with the one they saw when created (`AbstractActivity`), so they notice
a reload even if it happened while they were not in front. Default reaction: `onRefresh()`. Screens
that hold one account object (account details, settings) close with a toast, because that object is
now a stale copy; the lists reload themselves. The raw JSON editor holds a copy of the file as text,
so automatic syncs are held back while it is open (`CloudSync.pause`).

The worker may run without any screen, even without a `Controller`; the reload hook looks the
`Controller` up at the moment a file is replaced, on the main thread.

## Still to build

- Trash pruning, and a convention note for external writers (keep `i`, set `m` in UTC)
- Release signing (the sign-in redirect is tied to the debug key)
