# Data Model

All business entities live in the `Logic` package (`app/src/main/java/Logic/`). They are plain
Java objects with getters/setters; persistence (JSON (de)serialization) lives entirely in
`Backend/Util.java`, not on the entities themselves.

## Entity hierarchy

```
AccountBE                       (Logic/AccountBE.java)
 └─ BudgetAccountBE              (Logic/BudgetAccountBE.java)
     └─ ProjectBudgetBE          (Logic/ProjectBudgetBE.java)

TxBE                            (Logic/TxBE.java)
 └─ RecurringTxBE                (Logic/RecurringTxBE.java)
```

### `AccountBE` (`Logic/AccountBE.java`)

Base type for any account that holds a list of transactions.

- Fields: `name` (String), `txList` (`List<TxBE>`), `isActive` (boolean), `autoRenew` (boolean).
  All `protected`, so subclasses reach in directly (see `BudgetAccountBE` constructors).
- `getSum()` / `getSum(String period)` — sums `txList`, optionally filtered to a `"YYYY-MM"`
  period via `TxBE.inPeriod`.
- `addTx`/`removeTx`/`dropLastTx`/`getTxIndex`/`sortTxByDate`.
- `tryRenew()` (`:95-100`) — used for **asset** accounts at month rollover: replaces `txList` with
  a single opening-balance `TxBE` (`Const.DESC_OPENING`, German "Eröffnung") carrying the previous
  balance forward. Only called if `autoRenew` is true (see `Controller.renewAccounts()`).
- `reset()` (`:102-104`) — empties `txList` with no opening-balance entry (used when discarding
  unsaved state, e.g. `Controller.deleteCurrentSave()`/`resetAccounts()`).

### `BudgetAccountBE` (`Logic/BudgetAccountBE.java`)

A budgeted account with a yearly/available budget, a renewal cadence, and a tree of sub-budgets.

- Own fields: `indivYearlyBudget` (public float), `indivAvailableBudget` (public float, default
  `-1.0f` sentinel meaning "not yet set" — see `Util.parseJSON_BudgetAccount`), `renewalPeriod`
  (int, months, default 1), `nextRenewal` (String, `"YYYY-MM"`), `subBudgets`
  (`List<BudgetAccountBE>`, private), `toOtherEntity` (String — if set, payments received on this
  account are relayed as income on another financial entity's save file; see the transaction
  redirection flow in `Controller.createTx`/`startTxRedirection`/`completeTxRedirection`).
- Four constructors, notably `BudgetAccountBE(AccountBE source)` (`:32-37`) which converts an
  already-parsed plain `AccountBE` into a `BudgetAccountBE` by copying over `txList`/`isActive`
  from the source (used by `Util.parseJSON_BudgetAccount`, which first parses the fields common to
  all accounts via `parseJSON_Account` and then wraps the result into the richer budget type — the
  `ProjectBudgetBE(AccountBE source)` constructor, `ProjectBudgetBE.java:14-16`, does the same for
  project budgets).
- `tryRenew()` override (`:212-227`): if `nextRenewal` is not after the current period
  (`Util.isAfter`), rolls the available budget forward (`indivAvailableBudget +=
  getMeanAllottedIndivBudget() - getSum()`), clears `txList`, and increments `nextRenewal` by
  `renewalPeriod` months (`incrementRenewalPeriod()`, `:89-106`) — then always recurses into
  `subBudgets` regardless of whether this level renewed.
- `addBudgetAccountViewsToContainer(List<BudgetAccountTableRow>, TableLayout)` (`:249-269`) —
  finds this account's own `BudgetAccountTableRow` in a supplied pool and adds it (plus its
  sub-budgets, recursively) to an Android `TableLayout` container. **This is Android UI code living
  inside the entity class** — see "Known coupling issue" below.

### `ProjectBudgetBE` (`Logic/ProjectBudgetBE.java`)

A `BudgetAccountBE` specialization for one-off projects with a single total budget and **no
renewal cycle**. `indivYearlyBudget` is repurposed to mean "total project budget" (see the
constructor comment at `:7`). All renewal-related overrides (`tryRenew`, `setAutoRenew`,
`setRenewalPeriod`, `setNextRenewal`, `incrementRenewalPeriod`) are no-ops (`:24-40`), and
`getMeanAllottedIndivBudget()` returns the flat `indivYearlyBudget` instead of pro-rating by
`renewalPeriod` (`:48-51`). `reset()` clears `txList` but does **not** reset
`indivAvailableBudget` (deliberately — a project budget carries its running total across resets;
contrast with `BudgetAccountBE.reset()` which does reset it).

### `TxBE` (`Logic/TxBE.java`)

A single transaction/ledger entry: `mAmount` (float), `mDescription` (String), `mDate` (Date).
Plain getters/setters, `toString()` for display, `inPeriod(String)` to test whether `mDate` falls
in a given `"YYYY-MM"` period.

### `RecurringTxBE` (`Logic/RecurringTxBE.java`)

`extends TxBE`, adds `senderAccountStr`/`receiverAccountStr` (account names, resolved by name via
`Model.getAccountByName` at trigger time, not object references — see
`Controller.triggerRecurringTx()`). A recurring order with an empty sender string is treated as
recurring **income** rather than a transfer (`Controller.java:916-925`).

## Sub-budget tree

`BudgetAccountBE.subBudgets` (private `List<BudgetAccountBE>`) forms an arbitrarily deep tree —
top-level budgets live in `Model.budget_accounts`, and each budget account can have its own
children (sub-budgets, including nested `ProjectBudgetBE`s).

- `getDirectSubBudgets()` — this node's immediate children only.
- `getAllSubBudgets()` (`:139-147`) — recursively flattens the entire subtree (children +
  grandchildren + ...).
- `getSubBudgetParent(BudgetAccountBE target)` (`:171-181`) — recursive search for the direct
  parent of `target` within this node's subtree; returns `null` if not found. Used by
  `Controller.deleteAccount` to locate and detach a sub-budget being deleted.
- `transferSubBudget(BudgetAccountBE subBudget, BudgetAccountBE target)` (`:238-247`) — moves a
  direct child from this node to `target` (`target.addSubBudget`, then removes from `this`).

**Sum/budget roll-up** — each of these has an "indiv" (this node only) and a "total" (this node +
entire subtree) variant:

| Method | Meaning |
|---|---|
| `getSum()` / `getSum(period)` (inherited from `AccountBE`) | This node's own tx sum. |
| `getSubSum()` / `getSubSum(period)` | Sum of `getTotalSum()` across direct children (i.e. already includes grandchildren). |
| `getTotalSum()` / `getTotalSum(period)` | `getSum() + getSubSum()` — this node + entire subtree. |
| `getTotalYearlyBudget()` | `indivYearlyBudget + getSubYearlyBudget()`. |
| `getTotalAvailableBudget()` | `indivAvailableBudget + getSubAvailableBudget()`. |
| `getMeanAllottedIndivBudget()` | `indivYearlyBudget * renewalPeriod / 12` — the "expected" budget for one renewal cycle (overridden in `ProjectBudgetBE` to just return `indivYearlyBudget`, since projects don't have a renewal cycle). |
| `getMeanAllottedTotalBudget()` | `getMeanAllottedIndivBudget() + getMeanAllottedSubBudget()` (subBudget variant sums the *total* — not indiv — allotted budget of each direct child, i.e. already recursive). |

These roll-ups are used throughout the UI (`BudgetAccountDetailsActivity.updateUITotalSums()`,
`BudgetAccountTableRow.updateUINumbers()`, `BudgetsActivity`'s running totals) to show "spent /
budget (%)" at every level of the tree, and to color-code over/under-budget status via
`Util.calculateAdvancedPercentage`/`Util.evaluatePercentageBG`.

## `Backend/Model.java`

Plain in-memory state holder owned by `Controller`. No persistence logic of its own (see
`file-format.md` for how `Controller`/`Util` (de)serialize it). Fields, read directly from
`Model.java`:

- `settings` (`Model.Settings`) — see below.
- `availableEntities` (`List<String>`) — names of all financial entities that have at least one
  save file on disk (populated by `Controller.getAllAvailableEntities()`, used to drive the
  entity-switcher spinner in the action bar).
- `currentEntity` (String) — the entity name currently loaded.
- `currentFileName` (String) — the exact save-file name currently loaded/active (e.g.
  `"2026-08-User.jso"`).
- `currentFileAttributes` (`Util.FileNameParts`) — the parsed `(year, month, entityName)` of
  `currentFileName`.
- `asset_accounts` (`List<AccountBE>`) — top-level asset accounts for the current entity/period.
- `budget_accounts` (`List<BudgetAccountBE>`) — top-level budget accounts (each may have a
  sub-budget subtree; use `getAllBudgetAccounts()` to flatten).
- `recurringTx` (`List<RecurringTxBE>`) — standing recurring orders for the current entity.
- `currentIncome` (`List<TxBE>`) — income entries recorded for the current period (separate from
  any one account's tx list; used for the "current income" summary dialog and the delta shown in
  `MainActivity`'s title).
- `currentSender` / `currentReceiver` (`AccountBE`) — the two accounts currently selected as the
  transfer source/destination on the main screen (radio-button selection, persisted per-entity via
  `Settings.entityDefaultsMap`).
- `currentInspectedAccount` (`AccountBE`) — the account the details activities
  (`AssetAccountDetailsActivity`/`BudgetAccountDetailsActivity`) were navigated to inspect; set by
  the caller just before `startActivity(...)`.

`Model` also hosts a handful of query/aggregate helper methods used by the UI:
`getAllBudgetAccounts()` (flattens the budget tree), `getAllAccounts()` (assets + all budgets
flattened), `getAccountByName`/`getAssetAccountByName`/`getBudgetAccountByName`/
`getRootBudgetAccountByName` (name-based lookup — account names are the de facto unique identifier
throughout the app, there is no separate id field), and `sumAllExpenses`/`sumLoadedPeriodExpenses`/
`sumAllAssets`/`sumAllIncome`.

### `Model.Settings` / `Model.EntityDefaults` (nested classes in `Model.java`)

- `Settings.defaultEntityName` — which entity to auto-load on app start.
- `Settings.entityDefaultsMap` (`Map<String, EntityDefaults>`) — per-entity default
  sender/receiver account names, keyed by entity name.
- `EntityDefaults.defaultSender` / `defaultReceiver` — account **names** (String), resolved to
  `AccountBE` objects by `Controller.readAccountsFromInternal`/`importAccounts` after load.

See `file-format.md` for how this maps to `settings.json` on disk.

## Known coupling issue: Android imports in business-entity classes

`Logic/BudgetAccountBE.java` imports `android.util.Log` (`:3`, used for an error log in
`tryRenew()`, `:220-223`) and `android.widget.TableLayout` (`:4`, used as a parameter type in
`addBudgetAccountViewsToContainer(List<BudgetAccountTableRow>, TableLayout)`, `:249`). A pure
business-entity class depending on the Android UI framework (and, transitively, on
`View.BudgetAccountTableRow`) is a separation-of-concerns problem — this class cannot be unit
tested outside the Android framework/Robolectric, and UI-layer concerns leak into what should be a
plain data/logic layer.

Checked the other four entity classes for the same problem — **none of them have Android
imports**:

- `Logic/AccountBE.java` — no `android.*` imports.
- `Logic/TxBE.java` — no `android.*` imports.
- `Logic/RecurringTxBE.java` — no `android.*` imports.
- `Logic/ProjectBudgetBE.java` — no `android.*` imports.

So `BudgetAccountBE` is the sole offender. A refactor should move
`addBudgetAccountViewsToContainer` (and ideally the `Log.println` call in `tryRenew`) out of
`Logic/BudgetAccountBE.java` and into the `View`/`Backend` layer, leaving the entity classes
Android-free.
