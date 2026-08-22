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

`BudgetAccountDetailsActivity` layers its sub-budget tree (`BudgetAccountTableRow` rows in a
`TableLayout`) on top of the same `TxListSection` — see `data-model.md` for the sub-budget tree
structure itself.
