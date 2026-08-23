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

### 6. `updateTx`'s account search only descended one level of sub-budget — FIXED

`TxService.updateTx` (both overloads) used to build its search list by hand: every asset account,
then every budget account plus its `getDirectSubBudgets()` — one level only. Replaced with
`Model.getAllAccounts()`, which recurses every level via `getAllSubBudgets()`. The
`toSearch.remove(source)` call afterwards stays safe because `getAllAccounts()` allocates a fresh
`ArrayList` on every call — see the comment left in `TxService.findTxPair` warning against
"optimising" that allocation away. Covered by `UpdateTxSubBudgetTest` (2nd-level counterpart is the
fix, 1st-level counterpart is the regression guard, both overloads).

**Caveat: `TxService.updateTx`/`Controller.updateTx` currently have no caller anywhere in the app.**
Their only caller used to be `AssetAccountDetailsActivity.updateEntryDescription`/`updateEntryAmount`,
which were removed earlier on this branch as dead code (their own only callers were commented-out
lines in the deleted `TxAdvancedAdapter`). So this fix closes a latent defect in a public API, not a
bug a user can trigger today. It is still worth having fixed now — `updateTx` is the only correct
implementation of "update one side and keep the counterpart in step" in this codebase, and the live
swipe-to-edit path has a related, worse gap of its own (see "Not yet fixed" below).

## Fixed in the Phase 6/7 cleanup

### 1. Asset-preview child rows registered the *parent's* radio buttons — FIXED

`ui/AccountPreviewList.populateAssetAccountsPreview` read the child row's radio buttons off
`newItem` (the parent list item) instead of `child`, so every child iteration re-registered the
parent's two buttons under a different child account, overwriting the mapping each time. No
child's own buttons were ever registered, and selecting a child as sender/receiver did not
resolve to that child.

The line above it already read `child.getReferenceAccount()` correctly, and the budget variant of
the same loop uses `child.getRBReceiver()` — which is what marked this as a copy-paste slip. Fixed
by reading both buttons off `child`.

### 2. Tx sum disagreed with the visible list while a search filter was active — FIXED

`TxListSection.applyFilter` summed the visible (filtered) rows; `onRefresh()` on both details
screens re-summed the whole account via `renderTxSum(mAccount.getSum())`. Editing or deleting a
transaction while filtering therefore repainted the sum as the account total while the list still
showed the filtered subset.

Both `onRefresh` methods now call `section.refresh()`, so the number always describes the rows on
screen. With no filter active that is the full account sum, exactly as before.

### 3. Swipe-delete mutated the model before `deleteTx` could persist it — FIXED

With no filter active, `applyFilter` handed the adapter the live model list:
`entriesSupplier.get()` returns `mAccount.getTxList()`, which `AccountBE` returns **by reference**
(`AccountBE.java:24-26`), and `TxListAdapter.setEntries` stores what it is given without copying.

So the optimistic `adapter.removeEntry(tx)` on swipe-left removed the transaction from the account
itself. When the Undo Snackbar expired, `TxService.deleteTx` found `getTxIndex(tx) == -1`, returned
`false`, and never called `saveOrRevert` — the delete was **never written to disk**. It usually
survived anyway, because `AbstractActivity.startActivity` saves on every navigation, so leaving the
screen persisted the already-mutated model; killing the app from the details screen lost it.

With a filter active the same path built a fresh list, so deletes persisted correctly. The bug only
bit in the unfiltered case, which is why it was never obvious.

Fixed by having `applyFilter` always build its own list and hand that same instance to both the
adapter and `lastVisibleEntries`, so swipe positions stay in step with what the adapter holds. The
model is now changed only by `Controller.deleteTx` — the one path that saves.

### 4. "New budget account" dialog was a hand-rolled duplicate — FIXED

`BudgetsActivity.openNewBudgetDialog` reimplemented `CreateBudgetAccountDialog` by hand. It showed
hardcoded English "Confirm"/"Cancel" buttons, set no title at all, and used a real positive-button
listener — so `AlertDialog` dismissed on every click and an unparseable amount closed the dialog,
discarding everything typed. It also reported "amount is empty" when the *name* was the missing
field, because it tested both in one condition. Replaced with the shared dialog.

### 5. Every budget account created in the app vanished on the next load — FIXED

Reported from the field as "the last added budget account does not persist". Reproducing it in a
JVM test showed it was broader: **all** newly created budget accounts were dropped, while asset
accounts in the same save file were untouched. Budget accounts that had survived a month rollover,
or whose renewal date had been edited by hand, also loaded fine — which is what made it look like
only the newest one was affected.

Three links, all required:

1. No `BudgetAccountBE` constructor set `nextRenewal`, and neither did
   `AccountService.createRootBudget`/`createSubBudget`. It was null.
2. `Util.serialise_BudgetAccount` called `put(JSON_TAG_RENEWAL_NEXT, null)` — and
   `JSONObject.put(key, null)` **removes** the key rather than storing a null. The account was
   written to the file complete, minus `renew_next`.
3. `Util.parseJSON_BudgetAccount` read it with `getString`, which throws on a missing key, and the
   catch returned `null` for the entire account. The caller drops nulls without reporting anything.

So the data was written correctly and discarded on read. The next save — triggered by any
navigation, via `AbstractActivity.startActivity` — then wrote the model back to disk without it,
making the loss permanent.

Fixed at both ends, because neither half suffices alone: `nextRenewal` now defaults to
`Util.getNextPeriod()` (the *next* period — `tryRenew()` renews as soon as it is not after the
present period, so defaulting to the present one would clear the account immediately), and the
parser treats a missing `renew_next`/`renew_period` as "use the default" rather than as fatal. The
second half is what lets files already written by the broken version load their budget accounts.

`ProjectBudgetBE` clears the default in its constructors — it overrides `tryRenew` and every
renewal setter to no-ops deliberately, and neither serialise nor parse touches its renewal fields.

**Worth noting:** `IntegrityChecker` already had a check for exactly this failure class ("... wurden
stillschweigend verworfen"), and its own test fixture used a budget entry missing `renew_next` as
the example. Running the integrity check would have named this bug. The check was right; nobody had
run it against a real save file.

Two related defects fixed in the same commit, being the same code:

- A *malformed* (rather than absent) `renew_next` made `setNextRenewal` throw
  `IllegalArgumentException`, which is not a `JSONException` and so escaped
  `parseJSON_BudgetAccount` uncaught, aborting the whole load. Reachable through the raw-JSON
  editor. It now drops just that account.
- `BudgetAccountBE.tryRenew()` on a null `nextRenewal` threw an NPE out of
  `Util.validatePeriod` (`Pattern.matcher(null)`); `tryRenew` catches only
  `IllegalArgumentException`. The non-null default makes it unreachable.

### 6. Guards written with `assert` never ran on device — FIXED

Java assertions are disabled unless the JVM is started with `-ea`, and Android never enables them.
`assert expr;` therefore does not merely skip the check — **`expr` is not evaluated at all**. Three
places used `assert` as load-bearing control flow, so the guarded branches were unreachable in
production.

`TxService.triggerRecurringTx`, which runs on every month rollover, guarded three cases with
`try { assert x; } catch (AssertionError e) { ...handle... }`. None of those handlers could run, so
each case fell through into a `NullPointerException` instead:

- a recurring order whose receiver account had since been renamed or deleted;
- a recurring order whose sender account had since been renamed or deleted;
- an order with an empty sender — which is how a recurring **income** is represented, so recurring
  income crashed the rollover every single time rather than being booked.

`TxService.addRecurringTx` used the same pattern to null-check the selected sender/receiver and
`return false`; it NPE'd on the following line instead.

`BudgetAccountTableRow.clearChildren` was the worst shape — the side effect itself was the assert
expression: `assert budgetListener.removeBudgetViewFromBackend(child);`. The row was therefore never
removed from the activity's `budgetViews` list. Note that entry 2 above records fixing a
`ConcurrentModificationException` in this same loop, and its comment explains that
`removeBudgetViewFromBackend` must not mutate `children` during iteration — it never mutated
anything, because it was never called.

**Why no test caught it:** Gradle enables assertions in test JVMs by default, so the suite ran with
assertions ON while the device runs with them OFF. The tests disagreed with production on exactly
the code that uses `assert`. `app/build.gradle` now sets `testOptions.unitTests.all
{ enableAssertions = false }` so JVM tests match the device. Flipping that single line makes
`RecurringTxTriggerTest` fail with the NPEs above against the old code.

**Rule going forward:** never put a side effect inside `assert`, and never use `assert` as a guard.
Use a plain `if`. The eleven bare `assert x != null;` statements that survived this fix (in
`AccountPreviewList`, `AccountService.deleteAccount`, `EntityService`) were removed in the hardening
pass, and `app/build.gradle`'s `banAssertStatements` task now fails the build if `assert` reappears
anywhere in `app/src/main` - verified by planting one.

## Fixed in the hardening pass

Items 1-7 and 11 were found by writing the tests the handover asked for rather than by anyone
reporting them, and share the shape of the two data-loss bugs that motivated the pass: the app
repaired or discarded something and said nothing. Items 8-10 were reported from the field while the
pass was running.

### 1. Budget accounts silently lost their auto-renew setting on every load — FIXED

`BudgetAccountBE(AccountBE source)` copied `name`, `txList` and `isActive` but not `autoRenew`.
`Util.parseJSON_BudgetAccount` builds a plain `AccountBE` first and then wraps it with exactly that
constructor, so the value was written to the save file correctly and thrown away on read, reverting
to the default on every load.

Reachable: `SettingsActivity.populateUI` iterates `model.getAllAccounts()`, which includes budget
accounts and every sub-budget, and renders an auto-renew checkbox for each. Unchecking it for a
budget account never survived a restart.

No renewal behaviour changed — `AccountService.renewAccounts` only consults `autoRenew` for asset
accounts — but the setting now persists. The constructor carries a comment saying why every field
`AccountBE` holds must be copied there.

### 2. A null string deleted its key instead of being written — FIXED

`JSONObject.put(key, null)` **removes** the key rather than storing a JSON null (see gotcha 1 in
the handover). Five sites wrote a nullable string that way, each safe only because the value
happens never to be null in practice:

- `JSON_TAG_DESCRIPTION` in `serialise_Entry` and `serialise_RecurringOrder` — the parser read it
  with `getString`, so the whole transaction or order was dropped.
- `JSON_TAG_SENDER`/`JSON_TAG_RECEIVER` in `serialise_RecurringOrder` — the whole order.
- `JSON_TAG_NAME` in `serialise_Account` — the whole account.
- `JSON_TAG_DEFAULT_ENTITY` and the per-entity name/sender/receiver in `serialise_Settings` —
  worse than the rest, because `parseJSON_Settings` rethrows and `loadAppSettings` then falls back
  to a blank `Settings`: one null discarded every entity default the user had.

`Util.putOrDefault(target, key, value, fallback)` now carries that substitution once and documents
why it exists. The other nullable writes were already guarded — `renew_next` by an explicit null
check, `to_other_entity` by a ternary.

### 3. A null transaction date aborted the entire save — FIXED

`Util.formatDateSave` delegates to `SimpleDateFormat.format`, which throws on null. That NPE is not
a `JSONException`, so it escaped `serialise_Entry`'s catch, propagated through `serialiseAll` and
`exportAccounts`, and failed the whole save — one malformed entry taking every account with it.

A date cannot be defaulted to anything truly correct, but for a record of money losing the amount
is worse than approximating the date, so the current time is substituted and logged at ERROR.

### 4. "Fatal" was the unstated default in every parser — FIXED

Any key `Util.parseJSON_*` could not read discarded the entire enclosing record, and the caller
dropped the resulting null without telling anyone. That is how one absent `renew_next` cost users
whole budget accounts.

Four fields are genuinely fatal now, because the record cannot be used without them: an account's
`name` (every lookup, transfer and recurring order resolves accounts by name), a transaction's
`amount`, and a recurring order's `sender` and `receiver`. The sender is fatal *because* the
default would be meaningful — an empty sender is how a recurring income is represented, so
defaulting a missing one would post phantom income on every rollover.

Everything else defaults. Three changed from fatal: `budget_year` (neither the account's identity
nor its money — discarding the account deleted every transaction it held), `tx` (an unreadable
transaction list used to take the account's name, budget and renewal settings with it), and
`defaultEntity` (see item 2). A malformed `renew_next` defaults too, rather than dropping the
account as the earlier narrowing left it.

### 5. Discards were silent — FIXED

`core/ParseReport` collects every defaulted field and every discarded record with its reason.
`SaveFileRepository` accumulates it on the `Model` (accumulates, not replaces: startup parses the
settings file and then a save file, and replacing would let the first report vanish — the exact
failure mode the class exists to prevent). `AbstractActivity.reportLoadProblems` drains it after
every load — startup, load-from-file and entity switch — showing a dialog when something was
discarded and a toast when fields were only defaulted.

`IntegrityChecker` folds the same notes into its findings, so it now names which field was missing
from which entry rather than only reporting that some entry was dropped. Its raw-vs-parsed count
check stays as a backstop; the two are complementary.

**Subtlety worth keeping:** `parseJSON_BudgetAccount` reads `budget_cur` with `getDouble`, not
`optDouble`. `optDouble` swallows a NaN and substitutes the fallback, which would keep NaN out of
the model and therefore out of `IntegrityChecker`'s numeric-sanity check. A corrupt value has to
survive parsing to be reportable. `IntegrityCheckerTest.nanBudget_isFlagged` catches that
regression.

### 6. Swipe-delete ignored whether the delete actually happened — FIXED

`ui/TxSwipeActions.onDeleteRequested` removes the row from the adapter immediately, then calls
`Controller.deleteTx` when the Undo Snackbar expires — and discarded the result. `deleteTx` returns
false without saving when it cannot find the transaction, so in that case the delete existed only
on screen: the row was gone, nothing was written, and the next load brought it back unexplained.

That is precisely the signature of "Swipe-delete mutated the model before `deleteTx` could persist
it" above. The aliasing was fixed there; the path that made it invisible was not. It now restores
the row and toasts.

Two related hardenings in the same commit, both currently unreachable but the same shape:
`TxService.createTx` dereferenced `model.currentSender`/`currentReceiver` without a null check
(`addFunds` already guarded its receiver exactly that way), and on its `return false` path it left
the two optimistically-added entries in the model, where the next navigation — `startActivity`
saves on every one — would have written them to disk anyway.

### 7. Budget branch of `completeTxRedirection` logged "asset" on a parse failure — FIXED

`TxRedirectionService.injectTx` reported `"Could not parse asset account object!"` regardless of
which array it was scanning, because the budget loop was a copy of the asset loop. Log text only.
It now uses the `accountKind` the codec already carries.

### 8. A transaction could be booked on one account only — FIXED

Reported from the field, intermittently, over a long period: a transaction entered on the main
screen appears on only one of the two accounts, as if the other had never been selected.

`model.currentSender`/`currentReceiver` hold account **objects**, and every load rebuilds every
account object (`importAccounts` replaces `asset_accounts`/`budget_accounts` wholesale). A
selection made before a load could go on pointing at the old object — right name, right
transactions, right type, but contained in no list the model holds. `addTx` on such an orphan
mutates something `exportAccounts` never visits, so that side is silently dropped while the live
side is written normally.

Three ways to acquire one, all closed:

- **A load.** `importAccounts` re-pointed a selection only when it matched the entity's recorded
  defaults. With no defaults yet, or a default naming an account that no longer exists, the
  pre-load object survived. It now re-points by name, or clears the selection when the account is
  gone. `currentInspectedAccount` gets the same treatment — a details screen driven by an orphan
  silently discards every edit made on it.
- **`resetAccountLists`** replaced all four lists but left the selections pointing into the
  discarded ones.
- **`deleteAccount`** removed the account and left it selected. Deleting the account you were
  about to spend from was enough to lose the next transaction's counter-entry.

`createTx` additionally refuses to book unless both sides are live and distinct — a transfer that
can only be half-written must not be half-written. The check is by **identity, not name**: a
same-named orphan is exactly the case that used to slip through. `Model.containsAccount` and
`Model.hasLiveTxSelection` carry that test.

An existing test had to change: `createTx_movesFundsBetweenSenderAndReceiverAndSaves` selected two
accounts it never added to the model, and passed only because the old code booked against orphans —
the fixture was reproducing the bug.

### 9. Swipe-to-edit left the row missing when the dialog was cancelled — FIXED

`ItemTouchHelper` assumes `onSwiped` means the item is gone and leaves the ViewHolder translated
off-screen. That is right for the LEFT/delete path, where `onDeleteRequested` actually removes the
row. RIGHT/edit is not a removal — it is "reveal a dialog" — and the only thing restoring the row
was a `notifyItemChanged` at the end of `TxSwipeActions.onConfirm`, which never runs if the user
cancels or dismisses. The row stayed missing until the activity was left and reopened.

`TxListSection.onSwiped` now restores it unconditionally in the RIGHT branch, which is where the
gesture is owned, so the guarantee does not depend on what any `TxActions` implementation does.

### 10. An edited transaction was rendered twice when it changed position — FIXED

`TxSwipeActions.onConfirm` ends with `account.sortTxByDate()` — which reorders the model list —
then `onChanged.onRefresh()`, and then called `adapter.notifyItemChanged(position)` as its last
statement, outside the try block. `onRefresh()` already goes `section.refresh()` → `applyFilter()`
→ `TxListAdapter.setEntries()` → `notifyDataSetChanged()`, a complete and correct rebind. The
trailing targeted notify then re-bound one row using the index the entry had *before* the re-sort,
which after a reorder identifies a different entry — so the edited entry appeared both in its new
place and in its old one.

Removed. Note that this is deliberately **not** the same fix as item 9: a cancelled dialog never
reaches `onConfirm` at all, so the swipe translation must be restored in `TxListSection`, while
content rebinding after a confirmed edit is already covered by the refresh. Collapsing the two back
into one `notifyItemChanged` reintroduces one bug or the other.

### 11. The live swipe-to-edit path never updated the counterpart transaction - FIXED

`ui/TxSwipeActions.onEditRequested` opened `EditTxDialog` and, on confirm, just sorted the account's
tx list and saved: it mutated the `TxBE` in place and never looked for the matching entry on the
other side of the transfer. Unlike the `TxService.updateTx` depth bug above this was not
depth-dependent - it desynced the two sides at *every* nesting level, including a transfer between
two top-level accounts, because it never called `updateTx` at all. This was the live edit path
reachable from the UI, so the two sides of an edited transfer drifted apart in normal use.

**Fix:** the dialog gained an opt-in "also change the counterpart" checkbox, default off, backed by
the new `TxService.updateTxPair`. `EditTxDialog` no longer mutates in place; it hands back an `Edit`
value carrying both the pre-edit and post-edit values, which is what lets `updateTxPair` find the
pair by the *original* date and description. When the box is ticked and no counterpart is found, the
entry is edited one-sidedly and the user is told.

**Why a checkbox rather than automatic propagation:** not every entry has a counterpart. An opening
balance and an income have none, and a same-day same-description entry on another account may be a
coincidence rather than the other half of a transfer. Guessing wrong silently rewrites an unrelated
transaction, so the user decides. Covered by `UpdateTxPairTest` (7 tests).

This also gave `Controller.updateTx`/`TxService.updateTx` callers again - they had none at all after
`AssetAccountDetailsActivity.updateEntryDescription`/`updateEntryAmount` were deleted as dead code
earlier on the branch.

## Not yet fixed

Nothing. Every code defect recorded above is fixed, and the branch is merged to `master`.

The two lifecycle hazards examined during the hardening pass were deliberately *not* fixed, and are
recorded in `docs/architecture.md` -> "Lifecycle and threading" rather than here: `MainActivity`
saves on the UI thread while `workingThread()` may still be populating the same lists, and nothing
cancels that thread or checks activity liveness before `runOnUiThread`. Both need the raw
`new Thread(...)` pattern in `AbstractActivity.onCreate` replaced, which is a redesign the refactor
stayed out of on purpose.

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
