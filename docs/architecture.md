# Architecture

## Package layout

| Package | Location | Responsibility |
|---|---|---|
| `com.privat.pitz.financehelper` | `app/src/main/java/com/privat/pitz/financehelper/` | Activities (UI screens) + generated `R`. All extend `AbstractActivity` (or `AppCompatActivity` directly for `LauncherActivity`). |
| `Backend` | `app/src/main/java/Backend/` | The `Controller` singleton (all persistence + business operations), `Model` (in-memory state holder), `Util` (formatting/JSON/serialization helpers), `Const` (string constants, JSON tag names, filename helpers), RecyclerView adapters (`TxListAdapter`, `TxAdvancedAdapter`, `RecurringTxAdapter`), and small helper classes (`RbAccountManager`, `BudgetAccountListHandler` interface, `RefreshListener` interface). |
| `Logic` | `app/src/main/java/Logic/` | Business entity ("BE") classes: `AccountBE`, `BudgetAccountBE`, `ProjectBudgetBE`, `TxBE`, `RecurringTxBE`. See `data-model.md`. |
| `View` | `app/src/main/java/View/` | Custom composite views (`AccountWidget`, `AccountWidgetRow`, `BudgetAccountTableRow`, `ListItemAccountPreview`) and `View/Dialogs/*` (one class per `AlertDialog`-based dialog, each takes an abstract `onConfirm(...)` callback the caller implements inline). |

Note the two different packages named similarly: `com.privat.pitz.financehelper` (the Android
app package, holds Activities) vs. the top-level, un-namespaced `Backend`/`Logic`/`View` packages
(plain Java packages with no `com.privat...` prefix — this is how the source tree is actually
laid out, not a typo).

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
`AssetAccountDetailsActivity.java:48-51`). Instead override the two hooks:
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

File: `app/src/main/java/Backend/Controller.java`

`Controller` is a classic lazily-constructed singleton: `Controller.instance` (static field,
`Controller.java:48`) is `null` until `Controller.createInstance(context)` is called (line 62-65,
called from `AbstractActivity.initController()` on first app start). The constructor is private;
`createInstance` also allocates a fresh `Model` (`initController()`, line 58-60). Every
`AbstractActivity` grabs `Controller controller = Controller.instance` as an instance field
(`AbstractActivity.java:34`) and mirrors `model = controller.getModel()` (line 35, 47).

`Model` (`Backend/Model.java`) is a plain data holder with no persistence logic of its own — see
`data-model.md` for its fields. `Controller` owns all file I/O and all "business operations" that
mutate the `Model` (create/delete accounts, post transactions, transfer budgets, etc.) — see
`file-format.md` for the persistence side and `data-model.md` for the entities being persisted.

Almost every mutating `Controller` method follows the same **manual optimistic-mutate /
save-to-disk / revert-on-failure** pattern instead of a transaction: mutate in-memory state first,
call `saveAccountsToInternal()`, and if that throws, undo the in-memory mutation and rethrow (e.g.
`Controller.createTx`, `Controller.deleteAccount`, `Controller.updateYearlyBudget`, all in
`Controller.java`). There is no database and no atomic multi-step transaction support — keep this
in mind when adding new mutating operations.

## Field shadowing gotcha (load-bearing — read before touching these classes)

`BudgetAccountDetailsActivity extends AssetAccountDetailsActivity` (`BudgetAccountDetailsActivity.java:32`)
and declares its **own** field:

```java
// AssetAccountDetailsActivity.java:39
AccountBE mAccount;

// BudgetAccountDetailsActivity.java:33
BudgetAccountBE mAccount;   // shadows the parent field, does NOT override it
```

Java fields are **not polymorphic** — field access is resolved at compile time based on the
*static type of the reference*, not the runtime object. A method defined in
`AssetAccountDetailsActivity` that reads `mAccount` directly always reads
`AssetAccountDetailsActivity`'s own field. When that method runs on a `BudgetAccountDetailsActivity`
instance, `AssetAccountDetailsActivity.workingThread()` (line 55, `mAccount =
getModel().currentInspectedAccount;`) is **not** the method that actually runs — the subclass
overrides `workingThread()` too (`BudgetAccountDetailsActivity.java:51-60`) and populates its own
`mAccount` — but *any parent method that is not overridden* and touches `mAccount` directly would
read the parent's copy, which was **never set** for a `BudgetAccountDetailsActivity` instance and
stays `null`.

**This exact bug was found and fixed this session**: `AssetAccountDetailsActivity.updateEntryDescription`
and `updateEntryAmount` (`AssetAccountDetailsActivity.java:340-375`) used to reference `mAccount`
directly, which meant they silently operated on the (always-null) parent field whenever invoked on
a `BudgetAccountDetailsActivity` instance — an NPE risk for budget accounts. They were changed to
call `getReference()` instead, which **is** properly overridden in `BudgetAccountDetailsActivity`
(`BudgetAccountDetailsActivity.java:151-154`) and returns the correct, non-null subclass field. See
`known-issues.md` for the fixed-issue writeup.

**The invariant going forward:** any new method added to `AssetAccountDetailsActivity` must
**never** reference `mAccount` directly. Always go through the overridden accessor/hook methods,
which `BudgetAccountDetailsActivity` correctly overrides for exactly this reason:

- `getReference()` — returns the account (`AssetAccountDetailsActivity.java:261-263` /
  `BudgetAccountDetailsActivity.java:151-154`)
- `getEntries()` — the tx list (`:257-259` / `:145-149`)
- `hasEntries()` — whether the tx list is non-empty (`:194-196` / `:139-143`)
- `setTxSum(float)` — updates the sum label (`:184-187` / `:111-130`, budget variant also shows
  yearly budget + percentage)
- `setTitle()` — sets the action-bar title (`:189-192` / `:132-137`)
- `sortAccountTx()` — sorts the tx list by date (`:317-319` / `:190-193`)
- `onRefresh()` — the `RefreshListener` callback (`:252-255` / `:182-188`)

If you add a new method to `AssetAccountDetailsActivity` that needs the account, route it through
one of the above (or add a new overridable hook) rather than touching `mAccount`.

**A second, structurally identical instance of this pattern exists and has NOT been fixed.**
`RecurringTxActivity extends AssetAccountDetailsActivity` (`RecurringTxActivity.java:17`) and
declares its own `RecurringTxAdapter listAdapter;` field (`RecurringTxActivity.java:18`), shadowing
`AssetAccountDetailsActivity`'s `TxListAdapter listAdapter;` field
(`AssetAccountDetailsActivity.java:40`) — necessarily, since `RecurringTxAdapter` extends
`RecyclerView.Adapter` directly rather than `TxListAdapter` and so isn't assignment-compatible with
the parent field's type. `RecurringTxActivity` never overrides `initListGestures()`, so the
inherited swipe-to-edit/delete handler (`AssetAccountDetailsActivity.java:105-178`) runs unchanged
and calls `listAdapter.getTxAtPosition(position)` at `AssetAccountDetailsActivity.java:117` —
reading the **parent's** `listAdapter` field, which is never assigned for a `RecurringTxActivity`
instance and is always `null`. Swiping a row in the recurring-orders screen therefore hits an NPE
in `onSwiped()`. See `known-issues.md` for details — this one is filed as **not yet fixed**.

## RecyclerView tx-list + swipe pattern

`AssetAccountDetailsActivity` drives a `RecyclerView` (`recyclerView`, `:41`) backed by
`Backend.TxListAdapter` (`Backend/TxListAdapter.java`), a straightforward
`RecyclerView.Adapter<TxListAdapter.EntryViewHolder>` over `List<TxBE>` with `setEntries`,
`addEntry`, `removeEntry`, `getTxAtPosition`. `initListGestures()`
(`AssetAccountDetailsActivity.java:105-178`) attaches an `ItemTouchHelper` with a
`SimpleCallback(0, LEFT | RIGHT)`:

- Swipe **left** → `deleteTx(position, tx)` — optimistically removes from the adapter, shows a
  `Snackbar` with an "Undo" action; if not undone, calls `controller.deleteTx(getReference(), tx)`
  on dismiss (`:291-315`).
- Swipe **right** → `showEditTxDialog(position, tx)` — opens `View.Dialogs.EditTxDialog`; on
  confirm, calls `sortAccountTx()`, `controller.saveAccountsToInternal()`, and `onRefresh()`
  (`:266-289`).

`onChildDraw` (`:128-173`) hand-draws the edit/delete background color and icon during the swipe
gesture.

`BudgetAccountDetailsActivity` reuses this same `RecyclerView`/`TxListAdapter`/gesture setup
unchanged (via inheritance) for its own tx list, and layers the sub-budget tree
(`BudgetAccountTableRow` rows in a `TableLayout`) on top — see `data-model.md` for the sub-budget
tree structure itself.

`RecurringTxActivity` also extends `AssetAccountDetailsActivity` and reuses `initListGestures()`
unchanged, but swaps in `Backend.RecurringTxAdapter` for the underlying data — this is the source
of the shadowing bug described above.

## `Backend/TxAdvancedAdapter.java` — dead code, needs a decision

`TxAdvancedAdapter extends TxListAdapter` and appears to be a half-built inline-editing variant of
the tx list (long-press a row's description/amount label to switch to an `EditText`, and on focus
loss call `parentActivity.updateEntryDescription(...)`/`updateEntryAmount(...)`). Its core logic is
**commented out**: the `EditText`/`ViewSwitcher` field lookups
(`TxAdvancedAdapter.java:31-35`) and the entire `onFocusChange`/`onLongClick` wiring
(`TxAdvancedAdapter.java:58-94`) are dead, commented-out code. Nothing in the codebase currently
instantiates `TxAdvancedAdapter` (only `TxListAdapter` and `RecurringTxAdapter` are ever assigned
to the `listAdapter` field). The refactor should either finish wiring this class up (uncomment and
complete the inline-edit flow) or delete it outright — it should not be left half-alive.
