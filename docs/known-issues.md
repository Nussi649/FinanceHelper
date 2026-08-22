# Known Issues

Concrete, verified bugs and fragile patterns found while reading the code. Each entry has a
file:line reference and the actual failure mode — no speculation. This is meant to be a living
list; add to it as the refactor uncovers more.

## Already fixed this session (kept for context — the underlying patterns can recur)

### 1. `mAccount` field-shadowing NPE risk in `AssetAccountDetailsActivity` — FIXED

`AssetAccountDetailsActivity.updateEntryDescription`/`updateEntryAmount`
(`app/src/main/java/com/privat/pitz/financehelper/AssetAccountDetailsActivity.java:340-375`) used
to reference the shadowed `mAccount` field directly instead of calling `getReference()`. Since
`BudgetAccountDetailsActivity` declares its own `mAccount` field of a different type
(`BudgetAccountDetailsActivity.java:33`) that shadows (does not override) the parent's
`AccountBE mAccount` (`AssetAccountDetailsActivity.java:39`), any call to these two methods on a
`BudgetAccountDetailsActivity` instance would read the parent's field, which is never assigned for
that subclass and stays `null` — an NPE waiting to happen for budget accounts. Fixed by switching
both methods to call `getReference()` (`AssetAccountDetailsActivity.java:261-263`), which **is**
properly overridden in `BudgetAccountDetailsActivity.java:151-154` and returns the correct
non-null field. See `architecture.md` → "Field shadowing gotcha" for the full explanation and the
list of methods that already do this correctly (`setTxSum`, `setTitle`, `hasEntries`, `getEntries`,
`getReference`, `sortAccountTx`, `onRefresh`).

**This pattern has recurred** — see "Not yet fixed" item 1 below for a second, structurally
identical instance (`listAdapter` shadowing between `AssetAccountDetailsActivity` and
`RecurringTxActivity`) that is still live.

### 2. `BudgetAccountTableRow.clearChildren()` `ConcurrentModificationException` — FIXED

`View/BudgetAccountTableRow.java:100-113` used to iterate `children` with a for-each loop while
calling `children.remove(child)` inside the loop body (via
`budgetListener.removeBudgetViewFromBackend(child)` mutating the same list the for-each was
iterating) — this throws `ConcurrentModificationException` on the second child onward whenever a
budget row with 2+ children was reloaded/cleared. Fixed by iterating over a snapshot copy
(`new ArrayList<>(children)`) and calling `children.clear()` once after the loop, rather than
removing elements one at a time from the live list mid-iteration.

### 3. `Controller.updateTx(...)` mutating the live `model.asset_accounts` list — FIXED

Both `updateTx` overloads (`Backend/Controller.java:1266-1378`, one for amount edits, one for
description edits) used to do `List<AccountBE> toSearch = model.asset_accounts;` — a **live
reference**, not a copy — and then mutate `toSearch` (`addAll(transformed_budget_accounts)`,
`remove(source)`). This permanently spliced budget accounts into the model's real asset-account
list on every call, and could silently drop accounts from `model.asset_accounts` (the `remove(source)`
call, if `source` happened to be an asset account, removed it from the live list, not a scratch
copy). Fixed by copying: `List<AccountBE> toSearch = new ArrayList<>(model.asset_accounts);`
(present in both overloads, `Controller.java:1281` and `Controller.java:1339`).

### 4. `listAdapter` field-shadowing NPE on swipe gesture in `RecurringTxActivity` — FIXED

Swiping any row in the recurring-orders screen threw an NPE (see full explanation below, kept as
written when this was still open). Fixed by overriding `initListGestures()` in
`RecurringTxActivity.java` as a no-op — recurring orders already have a dedicated per-row delete
button (`RecurringTxAdapter` → `parentActivity.deleteOrder(entry)`), so the base swipe handler
(built for `TxBE`/`TxListAdapter`) was never meant to be attached here in the first place.

**Superseded**: `RecurringTxActivity` no longer extends `AssetAccountDetailsActivity` at all — it
now extends `AbstractActivity` directly and owns a `ui/TxListSection` instead of inheriting a list.
The shadowed `listAdapter` field (and the `initListGestures()`/`hasEntries()`/`filterEntries()`
overrides that existed only to work around the inheritance) are gone from the file entirely. The
no-op override above was a correct fix at the time, but it was still a workaround for a shadowed
field that could, in principle, be reintroduced by a careless future edit; now that the field no
longer exists, this whole NPE class is structurally impossible rather than merely avoided.

### 5. Unguarded/locale-unsafe numeric parsing — FIXED

All six sites listed below were fixed by wrapping each parse in a `try/catch (NumberFormatException)`
with a toast, and replacing `Float.parseFloat`/`(float) Double.parseDouble` with a new shared
helper `Util.parseAmount(String)` (`Backend/Util.java`) that normalizes `,` to `.` before parsing,
matching the pattern already used in `MainActivity`. `EditRenewalDialog`'s `Integer.parseInt` got
the same try/catch treatment with a new string `toast_error_invalid_renewal_period` (no comma
normalization needed there — it's a whole-number month count, not a currency amount).
`AssetAccountDetailsActivity.updateEntryAmount` (the dead-code path) was left as-is since it's
unreachable and will be revisited if/when `TxAdvancedAdapter` is revived.

**`EditTxDialog.java:96` is the most likely explanation for the originally-reported "editing an
entry in a budget account crashes" bug** — it's the swipe-to-edit-transaction dialog, shared by both
asset and budget accounts, but budget-account entries are plausibly typed with comma-decimal amounts
(e.g. "12,50") far more often in practice than asset-account balance adjustments, which would explain
why the crash was reported as budget-account-specific even though the underlying code path wasn't.

## Not yet fixed

### 1. Asset-preview child rows register the *parent's* radio buttons

`ui/AccountPreviewList.populateAssetAccountsPreview` (lines 129 and 133) reads the child row's
radio buttons off the wrong object:

```java
RadioButton rbReceiveChild = newItem.getRBReceiver();   // :129  should be child.getRBReceiver()
RadioButton rbSendChild    = newItem.getRBSender();     // :133  should be child.getRBSender()
```

`newItem` is the **parent** list item; `child` is the row being processed. The budget variant of
the same loop gets it right — `populateBudgetAccountsPreview` uses `child.getRBReceiver()`
(line 63) — which is what makes this identifiable as a copy-paste slip rather than intent.

Consequence: for an asset account with children, every child iteration re-registers the *parent's*
two radio buttons in the `RbAccountManager`, overwriting the mapping each time. The child accounts'
own radio buttons are never registered at all, so selecting a child as sender/receiver does not
resolve to that child. The last child processed wins the parent's mapping.

Found while moving these two methods out of `core/Util` into the UI layer (the layering refactor).
Deliberately **preserved as-is** during that move so the commit stayed behaviour-preserving. Fix
is a two-token change plus a check of what selecting a child row is then expected to do.

### 2. Tx sum disagrees with the visible list while a search filter is active

Both details screens render the sum two different ways depending on which code path ran last:

- `TxListSection.applyFilter` sums the **visible (filtered)** entries — so typing a search query
  shows the sum of the matches.
- `onRefresh()` in both `AssetAccountDetailsActivity` and `BudgetAccountDetailsActivity` renders
  the **full, unfiltered** account sum (`renderTxSum(mAccount.getSum())`).

So editing or deleting a transaction while a filter is active repaints the sum as the whole
account's total while the list still shows only the filtered subset. Clearing and retyping the
query flips it back.

Pre-existing — the old `filterEntries`/`setTxSum(getReference().getSum())` pair behaved exactly the
same way. Preserved deliberately through the composition refactor so those commits stayed
behaviour-preserving. The fix is one line in each `onRefresh` (call `section.refresh()` instead),
but it is a behaviour change and needs a decision about which number is actually wanted.

## Historical: issues as originally found (kept for reference)

### 1. `listAdapter` field-shadowing NPE on swipe gesture in `RecurringTxActivity`

`RecurringTxActivity extends AssetAccountDetailsActivity`
(`app/src/main/java/com/privat/pitz/financehelper/RecurringTxActivity.java:17`) and declares its
own `RecurringTxAdapter listAdapter;` field (`RecurringTxActivity.java:18`), shadowing
`AssetAccountDetailsActivity`'s `TxListAdapter listAdapter;` field
(`AssetAccountDetailsActivity.java:40`) — this shadow is effectively forced, since
`Backend/RecurringTxAdapter.java:21` extends `RecyclerView.Adapter` directly rather than
`TxListAdapter`, so it can't be assigned to a `TxListAdapter`-typed field.
`RecurringTxActivity.initListAdapter()` (`:70-75`) correctly sets its own shadowed field and wires
it to the `RecyclerView`, so the list displays fine. But `RecurringTxActivity` never overrides
`initListGestures()`, so the inherited swipe-to-edit/delete handler
(`AssetAccountDetailsActivity.java:105-178`, attached unconditionally in `endWorkingThread()`)
runs unchanged and its `onSwiped()` callback reads `listAdapter.getTxAtPosition(position)` at
`AssetAccountDetailsActivity.java:117` — resolving to the **parent's** `listAdapter` field at
compile time (because that's the field visible in the code where the method is textually defined),
which is never assigned for a `RecurringTxActivity` instance and stays `null`. **Swiping any row
left or right in the recurring-orders screen (`RecurringTxActivity`) throws a
`NullPointerException`.** The screen otherwise appears to work (list renders, the trash-can button
per row calls `RecurringTxAdapter`'s own `deleteButton` click listener →
`parentActivity.deleteOrder(entry)`, `RecurringTxAdapter.java:72-74`, which is unaffected), so this
bug is easy to miss unless someone actually swipes a row. Fix options: override
`initListGestures()` in `RecurringTxActivity` to no-op (or to call `deleteOrder`/a
`RecurringTxBE`-aware edit flow instead), or restructure so the swipe handler goes through an
overridable accessor instead of the raw field, matching the pattern used for `mAccount`.

### 2. Unguarded/locale-unsafe numeric parsing — crashes on comma decimal input (German locale)

Several dialogs parse a `EditText`'s text directly with `Float.parseFloat`/`Double.parseDouble`/
`Integer.parseInt` with **no try/catch** and **no comma-to-dot normalization**, unlike the correct
pattern already used in `MainActivity` (`am.replace(",", ".")` wrapped in
`try { ... } catch (NumberFormatException e) { ... }`, see `MainActivity.java:189-195` and
`:224-236`). Since the app's UI is German and `dialog_edit_tx.xml:47` sets
`android:inputType="numberDecimal"` on the amount field (which presents a comma `,` as the decimal
separator on a German-locale keyboard), a user typing e.g. `12,50` hits an uncaught
`NumberFormatException` and the app crashes:

- `View/Dialogs/EditTxDialog.java:96` — `float amount = Float.parseFloat(amountString);` inside
  the positive-button click handler, no try/catch at all. This is the swipe-to-edit-transaction
  dialog reached from `AssetAccountDetailsActivity.showEditTxDialog`, the most user-facing
  instance of this bug.
- `View/Dialogs/CreateBudgetAccountDialog.java:70-71` — two unguarded
  `Double.parseDouble(...)` calls (yearly budget, current-month budget) when creating a new budget
  or project budget.
- `View/Dialogs/SetYearlyBudgetDialog.java:55` — unguarded `Double.parseDouble(budgetString)`.
- `View/Dialogs/TransferAvailableBudgetDialog.java:66` — unguarded
  `Double.parseDouble(amountString)`.
- `View/Dialogs/EditRenewalDialog.java:60` — unguarded `Integer.parseInt(renewalPeriodString)`
  (less locale-sensitive since it's a whole-number month count, but still crashes on any
  non-numeric input, e.g. accidental extra characters).
- `com/privat/pitz/financehelper/BudgetsActivity.java:214-215` — same unguarded
  `Double.parseDouble` pair as `CreateBudgetAccountDialog`, duplicated here for the "new root
  budget" dialog (`openNewBudgetDialog()`).
- `com/privat/pitz/financehelper/AssetAccountDetailsActivity.java:363` — `updateEntryAmount`'s
  `float newAmountFloat = Float.parseFloat(newAmount);` is inside a `try` block, but that block
  only catches `JSONException`/`IOException` (`:362-373`); `NumberFormatException` is unchecked and
  would propagate to the caller uncaught. Currently low-risk in practice because the only caller
  (`Backend/TxAdvancedAdapter.java:89`) is commented-out dead code (see `architecture.md`), but it
  would need a fix if that class is ever revived.

Contrast: `View/Dialogs/AddIncomeDialog.java:57` **does** wrap its `Float.parseFloat(amountString)`
in a `try/catch (NumberFormatException e)` (`:56-62`), so it doesn't crash — but it still doesn't
normalize commas to dots, so a German user typing `12,50` there gets a rejected/invalid-amount
toast instead of a crash. Not a crash bug, but the same root cause (no comma normalization) and
worth fixing alongside the others.

**Suggested fix for all of the above:** factor the `replace(",", ".")` + try/catch pattern already
used in `MainActivity` out into a shared `Util` helper (e.g. `Util.parseLocaleFloat(String)`) and
have every dialog call it instead of `Float.parseFloat`/`Double.parseDouble` directly.

## Notes / things checked and found clean

- Other `for`-each-plus-`remove`-on-the-same-list sites were checked and are **not** buggy:
  `Backend/RbAccountManager.removeAccount` (`RbAccountManager.java:41-49`) correctly uses
  `Iterator.remove()`; `BudgetsActivity.clearTable()` (`BudgetsActivity.java:169-178`) uses an
  index-based loop with an explicit `i--` after each removal, which is safe against
  `ConcurrentModificationException` (though it's an easy-to-misread pattern worth simplifying in
  the refactor, e.g. `container.removeAllViews()` + selectively re-adding, or iterating backwards).
- `View/Dialogs/TransferAvailableBudgetDialog`/`TransferSubBudgetDialog` mutate the
  `List<BudgetAccountBE>` passed into their constructors (`.remove(self)`/`.remove(currentAccount)`
  at `TransferAvailableBudgetDialog.java:31`, `TransferSubBudgetDialog.java:35`), but every call
  site passes a freshly-built list from `Model.getAllBudgetAccounts()` (which allocates a new
  `ArrayList` each call, `Model.java:82-89`), so this does not alias/corrupt the live model state
  the way the fixed `Controller.updateTx` bug did — flagged only as a fragile-if-reused pattern,
  not an active bug.
