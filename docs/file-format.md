# Persistence / File Format

All persistence is plain-text JSON on **app-internal storage**
(`context.getFilesDir()` — private to the app, not visible to the user or other apps without
ADB/root, not covered by scoped-storage rules). There is no database, no `SharedPreferences` for
app data, and (until this session's backup feature) no way to get data out of the app except the
raw "export/import code" text dialogs (`MainActivity.showExportDialog`/`showImportDialog`, which
round-trip the entire JSON payload for the current save file through an editable text box).

## Save files: one per `(year, month, entity)`

Filename pattern: **`YYYY-MM-EntityName.jso`** — e.g. `2026-08-User.jso`.

- `Const.ACCOUNTS_FILE_TYPE = ".jso"` (`Backend/Const.java:8`).
- `Util.FileNameParts` (`Backend/Util.java:44-54`) — a small struct `{year, month, entityName}`.
- `Util.serializeFileName(FileNameParts)` (`:439-455`) — builds the filename via
  `String.format("%04d-%02d-%s.jso", year, month, entityName)`; throws `IllegalArgumentException`
  if `entityName` is null/empty.
- `Util.parseFileName(String)` (`:457-484`) — splits on `[.-]`, so the entity name itself **must
  not contain a literal `-` or `.`** (splitting on those characters would desync the parts);
  requires exactly 4 tokens with the last equal to `"jso"`, else throws `IllegalArgumentException`.
- `Const.getCurrentMonthFileName(entityName)` / `Const.getLastMonthFileName(entityName)` — build a
  filename for the current/previous calendar month using `Calendar.getInstance()`.
- `Util.isValidSavefileName(String)` (`:339-341`) — regex `\d{4}-\d{2}-\S+\.jso` used to filter
  `context.getFilesDir().listFiles()` down to real save files (also used by the backup export to
  decide what to zip).

A new month is detected/handled by `Controller.initiateNewPeriod()` (`Controller.java:1139-1207`):
loads the most recent prior save file for the current entity, calls `renewAccounts()` (rolls asset
accounts' balances forward via `AccountBE.tryRenew()` and budget accounts via
`BudgetAccountBE.tryRenew()`), resets `currentIncome`, sets `model.currentFileName` to the new
current-month filename, and fires any due `RecurringTxBE` orders (`triggerRecurringTx()`). This is
invoked from `Controller.setupAccounts(false)` when no save file exists yet for the current month
(`Controller.java:1215-1263`).

## JSON schema

Top-level tags are declared in `Backend/Const.java:11-27` (`JSON_TAG_*` constants). Read/write
logic lives in `Backend/Util.java`, region `parse JSON to BE objects` (`:486-696`) and `serialise
BE to JSON objects` (`:698-841`); orchestrated per-save-file by
`Controller.exportAccounts()`/`importAccounts(String)` (`Controller.java:115-207`).

Top-level object:

```json
{
  "assets": [ /* array of Account objects, see below */ ],
  "budgets": [ /* array of BudgetAccount objects (each may nest sub_budgets) */ ],
  "recur_tx": [ /* array of RecurringOrder objects */ ],
  "income": [ /* array of Entry objects, this period's income log */ ]
}
```

**Account object** (`Util.serialise_Account`/`parseJSON_Account`, shared base shape for both plain
assets and budget accounts):

```json
{
  "name": "Girokonto",
  "active": true,
  "renew_auto": true,
  "tx": [ /* array of Entry objects, see below */ ]
}
```

**Entry object** (a `TxBE`; `Util.serialise_Entry`/`parseJSON_Entry`) — note the terse single-letter
keys:

```json
{ "d": "Miete", "a": "-850.00", "t": "01.08.2026 09:00" }
```

- `d` = description (`Const.JSON_TAG_DESCRIPTION`)
- `a` = amount, formatted via `Util.formatFloatSave` — always `.` as decimal separator regardless
  of device locale (`Const.JSON_TAG_AMOUNT`)
- `t` = timestamp, format `Const.DATE_FORMAT_SAVE = "dd.MM.yyyy HH:mm"` (`Const.JSON_TAG_TIME`)

**BudgetAccount object** (`Util.serialise_BudgetAccount`/`parseJSON_BudgetAccount`) — extends the
Account shape:

```json
{
  "name": "Lebensmittel",
  "active": true,
  "renew_auto": true,
  "tx": [ { "d": "Supermarkt", "a": "-45.30", "t": "05.08.2026 18:20" } ],
  "proj": false,
  "renew_period": 1,
  "renew_next": "2026-09",
  "budget_year": "3600.00",
  "to_other_entity": "",
  "budget_cur": "280.50",
  "sub_budgets": [ /* nested array of BudgetAccount objects, recursive */ ]
}
```

- `proj` (`Const.JSON_TAG_PROJECT_BUDGET`) — `true` if this node deserializes as a
  `ProjectBudgetBE` instead of a plain `BudgetAccountBE`. When `true`, `renew_period`/`renew_next`
  are omitted (projects don't renew — `Util.java:772-778`).
- `renew_period` / `renew_next` (`Const.JSON_TAG_RENEWAL_PERIOD`/`JSON_TAG_RENEWAL_NEXT`) — months
  between renewals, and the next `"YYYY-MM"` renewal will occur. Omitted for project budgets.
- `budget_year` (`Const.JSON_TAG_YEARLY_BUDGET`) — `BudgetAccountBE.indivYearlyBudget`. **Required**
  — if this key is missing, `parseJSON_BudgetAccount` logs an error and returns `null`, dropping
  the account (`Util.java:591-605`).
- `to_other_entity` (`Const.JSON_TAG_TO_OTHER`) — see `BudgetAccountBE.toOtherEntity` in
  `data-model.md`; empty string if unset.
- `budget_cur` (`Const.JSON_TAG_CURRENT_BUDGET`) — `indivAvailableBudget`. **Optional on read**: if
  absent, it's recomputed as `getMeanAllottedIndivBudget()` (`Util.java:607-617`). **Omitted on
  write** if the in-memory value is the `-1.0f` sentinel (`Util.java:789-791`).
- `sub_budgets` (`Const.JSON_TAG_SUB_BUDGETS`) — recursive array of the same BudgetAccount shape;
  omitted entirely if there are no sub-budgets (`Util.java:797-812`).

**RecurringOrder object** (`Util.serialise_RecurringOrder`/`parseJSON_RecurringOrder`):

```json
{
  "a": "500.00",
  "d": "Gehalt",
  "t": "01.08.2026 00:00",
  "sender": "",
  "receiver": "Girokonto"
}
```

An empty `"sender"` means this recurring order is treated as recurring income rather than a
transfer between two accounts (see `data-model.md` / `Controller.triggerRecurringTx`).

## `settings.json`

`Const.APPLICATION_SETTINGS_FILENAME = "settings.json"` — a single file (not per-entity), also
stored via `context.getFilesDir()`. Read/written by `Controller.loadAppSettings()`/
`saveAppSettings()` (`Controller.java:542-592`), serialized by
`Util.parseJSON_Settings`/`serialise_Settings` (`Util.java:487-511`, `:699-727`).

```json
{
  "defaultEntity": "User",
  "syncFolderUri": "content://com.google.android.apps.docs.storage/tree/...",
  "defaultAccounts": [
    { "name": "User", "sender": "Girokonto", "receiver": "Lebensmittel" }
  ]
}
```

- `defaultEntity` (`Const.JSON_TAG_DEFAULT_ENTITY`) → `Model.Settings.defaultEntityName` — which
  entity to load on app start.
- `syncFolderUri` (`Const.JSON_TAG_SYNC_FOLDER_URI`) → `Model.Settings.syncFolderUri` — **optional**,
  absent on any settings.json written before the folder-sync feature (see below) existed; a
  `String.valueOf(Uri)` of the SAF tree the user picked for folder sync, or omitted entirely if none
  has been chosen (never written as a JSON `null` — `Util.serialise_Settings` only puts the key when
  non-null, since `org.json.JSONObject.put(key, null)` removes the key rather than writing anything).
- `defaultAccounts` (`Const.JSON_TAG_DEFAULT_ACCOUNTS`) → `Model.Settings.entityDefaultsMap`, one
  entry per entity that has ever had a sender/receiver selected, mapping entity name → `{sender,
  receiver}` account **names** (`Model.EntityDefaults`). Resolved back to live `AccountBE`
  references at load time in `Controller.readAccountsFromInternal`/`importAccounts` by looking the
  names up via `Model.getAccountByName`.

If `settings.json` doesn't exist or fails to parse, `loadAppSettings()` falls back to
`currentEntity = "User"` and immediately writes fresh defaults back to disk
(`Controller.java:551-564`).

## Backup / restore (added this session, not yet committed)

A manual, user-triggered, all-files zip export/import — the only way to get save data off the
device today (aside from the raw JSON export/import text dialogs, which only cover the single
currently-loaded save file). Implemented in `Backend/Controller.java`:

- **`exportAllSavefilesToUri(Uri targetUri)`** (`Controller.java:273-298`) — lists everything in
  `context.getFilesDir()`, keeps only files that pass `Util.isValidSavefileName(...)` or equal
  `Const.APPLICATION_SETTINGS_FILENAME`, and streams each into a `ZipOutputStream` opened against
  the caller-supplied `Uri`'s `OutputStream`. Returns the count of files written.
- **`importSavefilesFromZipUri(Uri sourceUri)`** (`Controller.java:302-329`) — reads a
  `ZipInputStream` from the `Uri`, and for each entry takes **only the bare filename**
  (`new File(entry.getName()).getName()`) — this strips any directory components, which is a
  deliberate defense against zip-slip/path-traversal from a malicious/corrupted zip — filters again
  through `isValidSavefileName`/`APPLICATION_SETTINGS_FILENAME`, and writes accepted entries
  straight into `context.getFilesDir()`, **overwriting any existing file of the same name with no
  confirmation**. Returns the count of files restored.

Wired up in `MainActivity` via the Storage Access Framework (no runtime permission needed):

- `createBackupLauncher` (`MainActivity.java:52-53`) —
  `ActivityResultContracts.CreateDocument("application/zip")`, launched from `startBackupAll()`
  (`:407-417`) with a default filename `FinanceHelper_Backup_<yyyy-MM-dd>.zip`. Saves the current
  in-memory state to disk first (`saveAccountsToInternal()`) so the backup is up to date. Result
  handled by `onBackupTargetSelected(Uri)` (`:419-429`).
- `openRestoreLauncher` (`:54-55`) — `ActivityResultContracts.OpenDocument()`, launched from
  `startRestoreAll()` (`:431-433`) accepting `application/zip`, `application/x-zip-compressed`, or
  `*/*` (many file-picker/cloud-storage providers don't set the zip MIME type correctly). Result
  handled by `onRestoreSourceSelected(Uri)` (`:435-457`), which reloads app settings and available
  entities from the now-restored files and calls `recreate()` to refresh the activity.
- Two new menu items in `res/menu/menu_main.xml`: `item_backup_all` / `item_restore_all`
  (`menu_main.xml:38-45`), under the "File" submenu, dispatched in
  `MainActivity.onOptionsItemSelected` (`:98-101`).

This bundles **all** entities' save files (every period, every entity) plus `settings.json` into
one zip — it is not scoped to the currently-loaded entity/period. There is no encryption; the zip
is plain JSON, same as the on-device files.

## Folder sync (added this session, not yet committed)

A second, complementary mechanism to the zip backup above — kept alongside it rather than
replacing it (the zip is a simple portable single-file fallback). Where the zip is a manual
one-shot export/import bundle, folder sync writes/reads the **individual, unzipped** `.jso`/
`settings.json` files directly inside a folder the user grants persistent access to via SAF's
`ACTION_OPEN_DOCUMENT_TREE` — e.g. a folder inside the Google Drive app, which exposes itself as a
`DocumentsProvider` and shows up in the system folder picker like any local folder. This means a
tool with direct access to that Drive folder (e.g. an AI agent with a Drive connector) can read and
edit the plain JSON save files directly, with no zip step in the way.

Implemented in `Backend/Controller.java`:

- **`pushSavefilesToFolder(Uri treeUri)`** (`Controller.java:337-361`) — same file-selection logic
  as the zip export, but writes each accepted file as its own `DocumentFile` (`createFile` with
  `application/octet-stream` — deliberately generic, to avoid SAF providers guessing/mangling the
  `.jso` extension based on a more specific MIME type), deleting any existing same-named file first
  via `DocumentFile.findFile`. Returns the count written.
- **`pullSavefilesFromFolder(Uri treeUri)`** (`:373-397`) — lists the tree's children via
  `DocumentFile.listFiles()`, filters through the same `isValidSavefileName`/
  `APPLICATION_SETTINGS_FILENAME` check as the zip import, and overwrites files of the same name in
  `context.getFilesDir()`. Returns the count restored.

Both require `androidx.documentfile:documentfile` (added as an explicit dependency in
`app/build.gradle`, even though it was already present transitively, to avoid relying on a
transitive version).

Wired up in `MainActivity`:

- `pickSyncFolderLauncher` (`MainActivity.java:57-58`) —
  `ActivityResultContracts.OpenDocumentTree()`. On a non-null result, `onSyncFolderPicked` (`:482-`)
  calls `takePersistableUriPermission(...)` so the grant survives app/device restarts, and persists
  the tree `Uri` into `model.settings.syncFolderUri` (see above).
- The folder is only picked **once** — `triggerSync(PendingSyncAction)` (`:472-`) checks
  `model.settings.syncFolderUri` first and reuses the stored `Uri` (via `Uri.parse`) if already set,
  only falling back to the picker if it's still null. A 3-state `PendingSyncAction` enum (`NONE` /
  `PUSH` / `PULL`, `:59`) tracks which action (if any) should run once the picker returns, so the
  same picker/callback serves "push", "pull", and "just change the folder, do nothing yet" (the
  `item_sync_change_folder` menu action) without triggering an unwanted sync as a side effect of
  changing folders.
- `performSync(Uri, PendingSyncAction)` (`:501-`) does the actual push/pull. On pull, it reloads
  `settings.json` from the freshly-pulled files (which may be a different device's copy) and then
  **re-asserts and re-saves** the local `syncFolderUri` afterwards, so a pull can never silently
  break the local sync-folder link even if the pulled `settings.json` had a stale/missing one. On a
  caught `SecurityException` (the persisted grant became invalid — folder deleted/unshared,
  permission revoked outside the app, etc.), clears `syncFolderUri` back to `null` so the next sync
  attempt re-prompts the picker instead of repeatedly failing against a dead `Uri`.
- Three new menu items in `res/menu/menu_main.xml`: `item_sync_push` / `item_sync_pull` /
  `item_sync_change_folder`, under the same "File" submenu as the zip backup items.

Like the zip backup, this covers **all** entities/periods plus `settings.json` in one pass — no
partial/single-file sync UI. Sync is manual (menu tap), not automatic on app open/close — that was
a deliberate scope decision (simpler, no background-reliability risk to validate without a physical
device on hand), so a stale local copy can persist until the next manual pull.

**Known caveat, confirmed in real use**: files added to the sync folder by something other than
this app (e.g. an external tool writing directly via the Drive API) don't necessarily show up
immediately via "Aus Sync-Ordner laden" — the Google Drive Android app maintains its own local
index that it syncs with the cloud on its own schedule, and `DocumentFile.listFiles()` only sees
what that index currently knows about. If a pull seems to be missing recently-added files, open
the Google Drive app itself and let it finish syncing (or pull-to-refresh in it) before retrying -
this is a Drive-app-side latency, not a bug in `pullSavefilesFromFolder`.
