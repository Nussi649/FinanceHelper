# Architecture

## Package layout

| Package | Location | Responsibility |
|---|---|---|
| `com.privat.pitz.financehelper` | `app/src/main/java/com/privat/pitz/financehelper/` | Activities (UI screens) + generated `R`. All extend `AbstractActivity` (or `AppCompatActivity` directly for `LauncherActivity`). |
| `com.privat.pitz.financehelper.core` | `.../financehelper/core/` | The `Controller` singleton (all persistence + business operations), `Model` (in-memory state holder), `Util` (formatting/JSON/serialization helpers), `Const` (string constants, JSON tag names, filename helpers), `IntegrityChecker`. |
| `com.privat.pitz.financehelper.data` | `.../financehelper/data/` | Business entity ("BE") classes: `AccountBE`, `BudgetAccountBE`, `ProjectBudgetBE`, `TxBE`, `RecurringTxBE`. See `data-model.md`. |
| `com.privat.pitz.financehelper.ui` | `.../financehelper/ui/` | Custom composite views (`AccountWidget`, `AccountWidgetRow`, `BudgetAccountTableRow`, `ListItemAccountPreview`), the `TxListSection`/`TxSwipeActions` composition helpers (see below), and small helper classes (`BudgetAccountListHandler` interface, `RefreshListener` interface). |
| `com.privat.pitz.financehelper.ui.adapter` | `.../financehelper/ui/adapter/` | RecyclerView adapters: `TxListAdapter`, `RecurringTxAdapter`. |
| `com.privat.pitz.financehelper.ui.dialog` | `.../financehelper/ui/dialog/` | One class per `AlertDialog`-based dialog (e.g. `EditTxDialog`, `EditRenewalDialog`), each takes an abstract `onConfirm(...)` callback the caller implements inline. |

The old top-level, un-namespaced `Backend`/`Logic`/`View` packages (plain Java packages with no
`com.privat...` prefix) were renamed into `core`/`data`/`ui` under `com.privat.pitz.financehelper`
as part of the clean-architecture refactor. There is now only the one package tree.

## `AbstractActivity` lifecycle pattern

File: `app/src/main/java/com/privat/pitz/financehelper/AbstractActivity.java`

Every screen activity extends `AbstractActivity`, which overrides `onCreate()` (line 42) to:

1. Grab the `Controller` singleton and its `Model` (lines 46-48).
2. Spawn a background `Thread` that calls `workingThread()` (line 53) — this is where subclasses
   do potentially slow work (primarily **loading/preparing account data from the `Model`/from
   disk** before the UI needs it — e.g. `BudgetAccountDetailsActivity.workingThread()` at
   `BudgetAccountDetailsActivity.java:51` casts `model.currentInspectedAccount` and recursively
   builds the sub-budget row tree).
3. Once `workingThread()` returns, hops back to the UI thread via `runOnUiThread` and calls
   `setupActionBar()` then `endWorkingThread()` (lines 54-59) — this is where subclasses inflate
   the layout and populate views, since it's guaranteed to run on the UI thread *after* the
   background work is done.

**Consequence for subclasses:** do not override `onCreate()` to do real work (most subclasses just
call `super.onCreate(savedInstanceState)` and nothing else, e.g.
`AssetAccountDetailsActivity.onCreate()`). Instead override the two hooks:
- `protected void workingThread()` — runs off the UI thread, no `findViewById`/view mutation here.
- `protected void endWorkingThread()` — runs on the UI thread, do `setContentView(...)` and view
  setup here.

Both hooks default to empty no-ops (`AbstractActivity.java:130,132`), so subclasses only override
what they need. `passedOnCreate` (line 39, reset to `false` in `onStop()` at line 126) is used by
several activities (`AssetsActivity`, `BudgetsActivity`, `BudgetAccountDetailsActivity`) to detect
"I'm being returned to, not created fresh" in `onStart()` and trigger a lighter-weight
refresh/reload instead of a full re-`onCreate`.

`AbstractActivity` also centralizes: toast helpers, a generic "are you sure?" confirm dialog
(`showConfirmDialog`), a generic single-EditText dialog (`getBasicEditDialog`, used by the raw
JSON export/import/edit screens), `startActivity(Class)` which always saves the current account
state to internal storage before navigating away (line 191-201), and `showErrorToast(Exception)`
which maps `JSONException`/`IOException`/`NumberFormatException`/`ParseException` to canned toast
strings.

## `Controller` / `Model` singleton

File: `app/src/main/java/com/privat/pitz/financehelper/core/Controller.java`

`Controller` is a classic lazily-constructed singleton: `Controller.instance` (static field,
`Controller.java:48`) is `null` until `Controller.createInstance(context)` is called (line 62-65,
called from `AbstractActivity.initController()` on first app start). The constructor is private;
`createInstance` also allocates a fresh `Model` (`initController()`, line 58-60). Every
`AbstractActivity` grabs `Controller controller = Controller.instance` as an instance field
(`AbstractActivity.java:34`) and mirrors `model = controller.getModel()` (line 35, 47).

`Model` (`core/Model.java`) is a plain data holder with no persistence logic of its own — see
`data-model.md` for its fields. `Controller` owns all file I/O and all "business operations" that
mutate the `Model` (create/delete accounts, post transactions, transfer budgets, etc.) — see
`file-format.md` for the persistence side and `data-model.md` for the entities being persisted.

Almost every mutating `Controller` method follows the same **manual optimistic-mutate /
save-to-disk / revert-on-failure** pattern instead of a transaction: mutate in-memory state first,
call `saveAccountsToInternal()`, and if that throws, undo the in-memory mutation and rethrow (e.g.
`Controller.createTx`, `Controller.deleteAccount`, `Controller.updateYearlyBudget`, all in
`Controller.java`). There is no database and no atomic multi-step transaction support — keep this
in mind when adding new mutating operations.

## Lifecycle and threading

This was deliberately left alone by the refactor (behaviour-preserving only) and had not been
examined since. Two things were looked at: the `workingThread`/`endWorkingThread` pattern's actual
thread-safety, and why `AbstractActivity`'s `Controller controller = Controller.instance;` field
initializer needs the patch-up in `onAppStartup()`/`initController()`. Findings below; nothing here
was changed.

**What the pattern actually does.** `AbstractActivity.onCreate()` (`AbstractActivity.java:43-64`)
runs on the UI thread, does `if (controller != null) model = controller.getModel();`
(`AbstractActivity.java:47-49`), then starts a raw `new Thread(...)` (line 51) that calls
`workingThread()` and, once that returns, posts `setupActionBar(); endWorkingThread();` back via
`runOnUiThread` (lines 55-61). `Controller` is a process-wide singleton (`Controller.instance`,
`core/Controller.java:21`); every activity's `Model model` field is the same shared `Model` object,
because `Controller.getModel()` (`Controller.java:74`) always returns the one `Model` the singleton
was constructed with. So `workingThread()` on activity A's background thread and the UI thread of
*any* currently-alive activity are potentially touching the same mutable `Model` instance.

### Hazards found, ranked by severity

**1. NPE crash if a non-`MainActivity` screen is the first activity constructed in a fresh process
— reachable.** Only `MainActivity.onCreate()` calls `onAppStartup()` (`MainActivity.java:68-71`),
which is the only place that calls `initController()` → `Controller.createInstance(this)`
(`AbstractActivity.java:253-275`, `Controller.java:69-72`) when `Controller.instance` is still
`null`. The other six activities (`AssetsActivity`, `AssetAccountDetailsActivity`,
`BudgetsActivity`, `BudgetAccountDetailsActivity`, `SettingsActivity`, `RecurringTxActivity`) never
override `onCreate()` to call it — they rely entirely on the field initializer
`Controller controller = Controller.instance;` (`AbstractActivity.java:35`) having captured a
non-null singleton at *construction* time, which is only true if some other activity in the same
process already ran `Controller.createInstance()` first. There is no custom `Application` class
(confirmed — no `extends Application` anywhere under `app/src/main/java`) that could bootstrap the
singleton earlier.

This is exactly the scenario Android's own task-restore behaviour produces: after the OS kills the
process in the background and the user returns via Recents (not the home-screen icon), only the
*top* activity of the saved back stack is recreated immediately; activities lower in the stack are
recreated lazily as the user presses Back. If the user's last screen before backgrounding was, say,
`BudgetAccountDetailsActivity`, that activity is now the first (and only) activity object built in
the new process. Its field initializer captures `null`. `AbstractActivity.onCreate()`'s
`if (controller != null)` guard (line 47) is false, so `model` is never assigned and stays `null`.
`workingThread()` then dereferences it immediately:
`AccountBE acc = getModel().currentInspectedAccount;` (`BudgetAccountDetailsActivity.java:63`) — a
`NullPointerException` on a background thread, which crashes the whole process (Android's default
`UncaughtExceptionHandler` kills the app for an uncaught exception on *any* thread, not just the UI
thread). The same shape exists in `BudgetsActivity.workingThread()` → `loadBudgets()` →
`model.budget_accounts` (`BudgetsActivity.java:143`), `AssetAccountDetailsActivity.workingThread()`
(`AssetAccountDetailsActivity.java:36`), and, one hop later on the UI thread inside
`endWorkingThread()`, in `AssetsActivity` (`AssetsActivity.java:58`, `model.asset_accounts`),
`SettingsActivity` (`SettingsActivity.java:30`, `model.getAllAccounts()`), and
`RecurringTxActivity` (`RecurringTxActivity.java:49`, `getModel().recurringTx`).

One path from the plan prompt turned out not to apply: a home-screen shortcut cannot land directly
on any of these activities. `AndroidManifest.xml:13-20` shows only `.LauncherActivity` is
`exported="true"` with the `MAIN`/`LAUNCHER` intent filter; every screen activity, including
`MainActivity`, is `exported="false"` (`AndroidManifest.xml:21-62`) and reachable only from inside
the app. `LauncherActivity` (`LauncherActivity.java:8-21`) unconditionally starts `MainActivity` and
finishes itself, so external launch always goes through `onAppStartup()`. The reachable trigger is
process death with a restored back stack, not an external shortcut.

Not verified on-device in this pass — this is reasoned from documented Android task-restore
behaviour, not reproduced with a debugger. It would be settled by force-stopping the app from
Settings (or `adb shell am kill`) while sitting on, say, `BudgetsActivity`, then reopening via
Recents.

**2. Unsynchronized read/write race on the shared `Model` between a still-running `workingThread()`
and `MainActivity.onStop()` — reachable, narrow window.** `MainActivity.onStop()`
(`MainActivity.java:138-147`) calls `controller.saveAccountsToInternal()` and
`controller.saveAppSettings()` on the UI thread, which serialises `model.asset_accounts` /
`model.budget_accounts` by iterating them. `MainActivity.workingThread()`
(`MainActivity.java:73-78`) populates those same lists on a background thread via
`initiateAccounts()` → `controller.setupAccounts(false)` (`MainActivity.java:618-632`). Nothing
joins the background thread to the UI thread before `onStop()` can run — `onCreate()` starts the
thread and returns immediately, so the normal Android lifecycle (`onStart`, `onResume`, and — if the
user backgrounds the app fast enough, e.g. a Home press or incoming call while the initial load is
still in flight — `onPause`/`onStop`) proceeds concurrently. If `onStop()`'s save lands while
`workingThread()` is still mid-populate, the UI thread iterates the same `ArrayList`s the background
thread is mutating: best case a `ConcurrentModificationException` crash, worst case a save file
written from a half-populated model — the same "silent data loss" failure shape the rest of this
hardening pass is about, just via a different mechanism than the JSON-null bug. The window is small
(loading a small local JSON file is fast), which is why this has plausibly never been observed, but
nothing bounds it — an unusually large save file or slow storage widens it. Only `MainActivity` has
this exposure; no other activity calls a `Controller` save method from `onStop()`, and other
activities' own click-handler-triggered `startActivity()` → `saveAccountsToInternal()` calls
(`AbstractActivity.startActivity`, referenced from the `Controller`/`Model` section above) happen
strictly after that activity's own `endWorkingThread()` has already run, since the view whose click
listener fires the save is itself only wired up inside `endWorkingThread()`.

**3. No cancellation or liveness check around the background thread — mostly latent.** The `Thread`
spawned in `AbstractActivity.onCreate()` (line 51) is not tied to the activity's lifecycle in any
way: no `isFinishing()`/`isDestroyed()` check, no interrupt, no join. If the activity is destroyed
while `workingThread()` is still running, the thread keeps going regardless, and
`runOnUiThread(...)` (line 55) still executes the posted `setupActionBar(); endWorkingThread();`
against the now-destroyed activity when the work finishes — `runOnUiThread` has no liveness check
of its own. Two consequences:
  - The anonymous `Runnable`s (and `AbstractActivity self = this;`, line 50) hold a strong reference
    to the destroyed activity and its view tree for as long as `workingThread()` keeps running — a
    transient memory leak, not a crash.
  - `reportLoadProblems()` (`AbstractActivity.java:190-213`), called from inside
    `MainActivity.workingThread()` via `initiateAccounts()`, itself does
    `runOnUiThread(() -> showScrollableMessageDialog(title, body));` (line 212) when there are
    discards to report. `showScrollableMessageDialog` calls `AlertDialog.show()`
    (`AbstractActivity.java:216-223`), which — unlike `setContentView`/`findViewById` — opens a new
    window and *can* throw `WindowManager.BadTokenException` if the hosting activity's window token
    is no longer valid. This is a different failure mode from the bug this method already fixes
    (the original crash was calling dialog code with no `Looper` at all, from the wrong thread); this
    one requires the activity to have actually been destroyed while the load was still running. Given
    every activity declares `android:configChanges="orientation"` (`AndroidManifest.xml:24` etc., so
    rotation does not recreate) and `MainActivity` is normally only backgrounded (stopped), not
    destroyed, by Back/Home, this is hard to trigger deterministically in the app's current flow —
    flagged as latent, not reachable in ordinary use, but there is no guard if the timing ever lines
    up (e.g. under memory pressure the system decides to destroy rather than merely stop).

**4. `BudgetAccountDetailsActivity.workingThread()` calls `finish()` directly from the background
thread — unclear severity, not confirmed either way.** When `getModel().currentInspectedAccount` is
not a `BudgetAccountBE`, the `else` branch calls `finish()` (`BudgetAccountDetailsActivity.java:69`)
without posting to the UI thread. `Activity.finish()` is documented/conventionally a main-thread
call; whether calling it off-thread here is actually unsafe on the API levels this app targets was
not established either way — it would need to be checked against the AOSP source for the app's
`minSdk`/`targetSdk` (not done in this pass) or reproduced on device. Flagging it because it
deviates from the calling convention every other UI-touching call in this codebase follows, not
because a concrete failure was observed.

### Examined, found nothing

- **`passedOnCreate`** (`AbstractActivity.java:40`, set `true` at line 45, cleared at line 128): read
  and written only from `onCreate`/`onStart`/`onStop`, all of which Android guarantees run on the UI
  thread. No cross-thread access exists, so the lack of `volatile`/synchronization does not matter
  here, despite the field being a plain unguarded `boolean`.
- **The `model`/`controller` field writes vs. the background thread's first read of them**: within
  one activity's own `onCreate()`, the assignment `model = controller.getModel();`
  (`AbstractActivity.java:48`) happens-before `new Thread(...).start()` (line 63) per the Java Memory
  Model's thread-start guarantee, so `workingThread()` always sees that particular write. The
  ordering hazard is entirely in hazard #1 above (the field initializer capturing a stale/null value
  at *construction* time, before `onCreate()` runs at all) — not a visibility/memory-model issue.
- **`Controller.instance` read from a background thread**: grepped every direct reference; there are
  exactly two (`AbstractActivity.java:35` and `:266`), both executed during activity construction or
  inside `initController()`, both guaranteed to run on the UI thread by the Android framework. No
  worker thread reads the static field directly.
- **View inflation off the UI thread**: `BudgetsActivity.workingThread()` → `loadBudgets()`
  (`BudgetsActivity.java:141-158`) and `BudgetAccountDetailsActivity.workingThread()` →
  `loadSubBudgets()` (`BudgetAccountDetailsActivity.java:409-418`) both call
  `BudgetAccountTableRow.getInstance()` (`ui/BudgetAccountTableRow.java:30-34`) — a plain
  `LayoutInflater.inflate()` — and `.init()` (lines 55-68), which only does `findViewById` on the
  still-unattached row (`initViews()`, lines 70-76) plus bookkeeping. Neither touches a `Handler`,
  posts anything, or attaches the row to a window. All of the actual view-mutating work
  (`updateUI()`, `populateUI()`, click listeners) is deferred to `endWorkingThread()` on the UI
  thread. Background-thread `LayoutInflater` use without an attached window is a documented-safe
  Android pattern (it's what `AsyncLayoutInflater` itself does), so this is fine as it stands — but
  it is fragile: anything added later to `init()`/`initViews()` that needs a `Handler` (an
  animation, a `post()`, a `Toast`) would silently move this from safe to broken.
- **`Toast` calls made directly from `MainActivity.workingThread()`** (`MainActivity.java:75-76`,
  `Looper.prepare()` with no matching `Looper.loop()`): this is pre-existing, already-relied-upon
  behaviour, not something newly verified here — the `reportLoadProblems()` doc comment
  (`AbstractActivity.java:186-188`) already records that Toast works on this thread but a dialog does
  not, which is why the dialog path was rerouted through `runOnUiThread` when that bug was fixed. What
  this pass adds: a check of every `workingThread()`/background-reachable call in the app for a
  *second* instance of the same bug shape (UI/dialog code called directly off a thread with no
  running `Looper`) — none was found. `BudgetsActivity.workingThread()` and
  `BudgetAccountDetailsActivity.workingThread()` call no `Toast`/`Dialog` code at all (see above);
  `AssetAccountDetailsActivity.workingThread()` and `RecurringTxActivity.workingThread()` touch only
  the `Model`, not the UI.

### Not fixed here

The highest-value fix is #1: give every `AbstractActivity` subclass's `onCreate()` the same
null-`Controller` guard `MainActivity.onCreate()` already has (call `onAppStartup()`, or move its
logic into `AbstractActivity.onCreate()` itself so subclasses can't skip it), so a directly-recreated
non-`MainActivity` activity re-bootstraps the singleton instead of crashing. Deliberately not done in
this pass — this is analysis and documentation only, per the task that produced this section.

## List screens: composition, not inheritance

`AssetAccountDetailsActivity`, `BudgetAccountDetailsActivity`, and `RecurringTxActivity` used to
form an inheritance chain (`BudgetAccountDetailsActivity`/`RecurringTxActivity extends
AssetAccountDetailsActivity`), which caused two field-shadowing bugs (a parent field silently
staying `null` under a subclass whose own same-named field shadowed it — one of which caused a real
NPE on swipe in the recurring-orders screen). That chain is gone. All three are now independent
leaves of `AbstractActivity` — none of them extends either of the others — and each owns a single
plain `mAccount`/data field with no shadowing possible.

They share their list behaviour through composition instead: `ui/TxListSection.java` wraps the
`component_tx_recycler_view.xml` component (a `SearchView`-filtered `RecyclerView` plus a summary
card) and is constructed with a `View root`, an adapter, a live-entries supplier, a filter
predicate, a sum renderer, and an optional `TxActions` (swipe handler). Taking a plain `View`
rather than an `Activity` is what lets `ui/dialog/CurrentIncomeDialog` reuse the exact same class
from inside an `AlertDialog`, where there is no activity to extend.

Swipe-to-edit/delete is opt-in via the `swipeActions` constructor argument: `AssetAccountDetailsActivity`
and `BudgetAccountDetailsActivity` both pass a shared `ui/TxSwipeActions` instance (see below);
`RecurringTxActivity` passes `null`, because recurring orders are deleted via a per-row delete
button on `RecurringTxAdapter` instead of a swipe gesture — passing `null` means `TxListSection`
skips attaching the `ItemTouchHelper` at all.

## RecyclerView tx-list + swipe pattern

`TxListSection` owns the `RecyclerView` wiring: it binds the adapter, sets up the `SearchView`
filter listener, and — when given a non-null `TxActions` — attaches an `ItemTouchHelper` with a
`SimpleCallback(0, LEFT | RIGHT)` that hand-draws the edit/delete background color and icon during
the swipe gesture and delegates to the `TxActions` callbacks on release:

- Swipe **left** → `TxActions.onDeleteRequested(position, tx)`.
- Swipe **right** → `TxActions.onEditRequested(position, tx)`.

`ui/TxSwipeActions.java` is the shared `TxActions` implementation used by both
`AssetAccountDetailsActivity` and `BudgetAccountDetailsActivity` (extracted from what used to be
two near-identical copies of this logic, one in each activity):

- `onDeleteRequested` — optimistically removes the row from the adapter, shows a `Snackbar` with an
  "Undo" action anchored on the `RecyclerView`; if the Snackbar is dismissed without Undo, calls
  `controller.deleteTx(account, tx)` and refreshes.
- `onEditRequested` — opens `ui/dialog/EditTxDialog`; on confirm, sorts the account's tx list by
  date, calls `controller.saveAccountsToInternal()`, and refreshes.

**Invariant: `TxListSection.applyFilter` always builds its own list.** `TxListAdapter.setEntries`
stores the list it is given *by reference* and `addEntry`/`removeEntry` mutate it in place, so if
`applyFilter` handed over `mAccount.getTxList()` — which `AccountBE` returns by reference — the
optimistic swipe removal would delete the transaction from the model before `Controller.deleteTx`
ever looked for it, and the delete would never be persisted. That was a real bug (see
`known-issues.md`). `applyFilter` therefore copies on both the filtered and the unfiltered path,
and hands that same instance to both the adapter and `lastVisibleEntries` so swipe positions stay
in step with what the adapter holds. The model is mutated only by `Controller.deleteTx`.

Both details screens' `onRefresh()` call `section.refresh()` rather than re-summing the account
directly, so the summary card always describes the rows actually on screen.

`BudgetAccountDetailsActivity` layers its sub-budget tree (`BudgetAccountTableRow` rows in a
`TableLayout`) on top of the same `TxListSection` — see `data-model.md` for the sub-budget tree
structure itself.

## Dialogs

Every dialog lives in `ui/dialog/` and is an abstract class with a single `onConfirm(...)` method
that call sites implement as an anonymous subclass, then `show()`.

The seven that validate their input extend `ui/dialog/BaseInputDialog`, which owns the shared
scaffold and exposes four hooks: `getLayoutRes()`, `getTitleRes()`, `bindViews(View)` and
`onConfirmClicked()`.

The non-obvious part it encapsulates is why the positive button is registered with a **null**
listener and only re-wired inside `setOnShowListener`: that is what stops `AlertDialog` from
dismissing itself on every click, so returning `false` from `onConfirmClicked()` leaves the dialog
open with the user's input intact. `getTitleRes()` is resolved in `show()` rather than the
constructor, so it may depend on subclass state — `CreateBudgetAccountDialog` uses that for its
project/budget title switch.

Four dialogs deliberately do **not** extend it, because they do no input validation: `LoadFileDialog`,
`SaveFileDialog` and `TransactionRedirectionDialog` use a real auto-dismissing positive listener,
and `EditSourceCodeDialog` is handed a prebuilt dialog by `AbstractActivity.getBasicEditDialog()`
rather than inflating one.
