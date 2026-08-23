# Handover: from "refactor complete" to "hardened and merged"

Written at the end of the refactor session, for the session that picks this up. Read
`docs/architecture.md` and `docs/known-issues.md` alongside this — they are current as of the last
commit and this document does not repeat them.

## Where things stand

- Branch `refactor/clean-architecture`, **32 commits**, pushed to `origin`. `master` is untouched.
- `107 files changed, 4792 insertions(+), 3374 deletions(-)` against `master`.
- **141 unit tests, all passing.** `./gradlew clean assembleDebug testDebugUnitTest` is green.
- Verified on device by the user: transaction delete/undo, the details screens, the dialogs.
- **Not yet verified on device: month rollover.** It was changed in the final commit and is the
  least-covered path in the app. See the checklist at the bottom.

The structural refactor is done. What remains is a hardening pass, then the leftover known issues,
then merge.

**No decisions are outstanding.** The commit grouping in step 1 and the deep sub-budget fix in
step 3.1 were both approved by the user; everything below is execution.

---

## Step 1 — Squash, but not into a single commit

The user asked for one commit "or more if you object to one". **I object to one.** Recommended
grouping is **four**:

| # | Commit | Contents (current SHAs) |
|---|---|---|
| 1 | `refactor: ...` — the whole structural pass, behaviour-preserving | 1–19, 21, 25–28 |
| 2 | `fix: budget accounts created in the app vanished on the next load` | 29 + 30 (its doc commit) |
| 3 | `fix: guards written with assert never ran on device` | 31 + 32 (its doc commit) |
| 4 | `fix: four smaller defects found during the refactor` | 20, 22, 23, 24 |

Why not one:

- **Commits 2 and 3 fix silent data loss and a crash.** If either causes a regression months from
  now, `git bisect` should land on a focused commit, not on a 107-file refactor. They also want to
  stay findable via `git log --grep`.
- **It preserves the property that makes commit 1 trustworthy.** The whole refactor was run under
  the rule "every commit is behaviour-preserving; behaviour changes get their own labelled commit".
  Folding the fixes in destroys the one claim that makes the refactor commit safe to reason about.
- **Squashing 1–19/21/25–28 together is genuinely fine.** Those intermediate steps were sequencing
  scaffolding — extract, then move, then delete — and are not independently meaningful once the
  end state compiles and passes.

Commit 4 could be split into its four originals if you prefer; they are all small, and each has a
message worth keeping. Do not merge commit 4 into commit 1.

**Before rewriting history**, keep an escape hatch — the current messages contain forensic detail
(exact failure chains, mutation-test results) that is expensive to reconstruct:

```bash
git branch refactor/pre-squash-archive refactor/clean-architecture
```

Push that archive branch too, and carry the important reasoning into the squashed message bodies
rather than discarding it.

---

## Step 2 — The hardening pass

This is the main work. Its shape is dictated by what the last two bugs had in common: **the app
fails silently, and the failure mode is losing financial records.** Both bugs were years old. Both
were found by reasoning from a symptom, not by the test suite.

Priority order below is deliberate. 2.1 is where the value is.

### 2.1 Save-file round-trip integrity — highest priority

The budget-account bug was: serialise wrote a null → `JSONObject.put(key, null)` **removed the key**
→ the parser treated the missing key as fatal → the whole account was dropped, and the caller
discarded the null without telling anyone.

Nothing structural prevents that from recurring for another field. Do all four:

1. **Field-level round-trip tests for every persisted type** — `AccountBE`, `BudgetAccountBE`
   (including nested sub-budgets and `ProjectBudgetBE`), `TxBE`, `RecurringTxBE`, and app settings.
   For each field: set a distinctive value, serialise, parse, assert it came back. This is exactly
   the test that would have caught the renewal bug on day one.
2. **Audit every `put(key, value)` in `Util.serialise_*` where value can be null.** Candidates
   already identified: `JSON_TAG_DEFAULT_ENTITY`, `JSON_TAG_SENDER`/`JSON_TAG_RECEIVER` in both the
   settings and recurring-order writers, `JSON_TAG_DESCRIPTION`, `JSON_TAG_TIME`. Each is currently
   safe only because the value happens never to be null in practice — that is not a guarantee.
3. **Decide a policy per field in `Util.parseJSON_*`: is a missing key fatal, or does it default?**
   Right now "fatal" is the unstated default and it discards an entire account. Most fields have a
   sensible default. Very few genuinely justify dropping the record.
4. **Make discards loud.** Today `parseJSON_BudgetAccount` returns `null` and the caller skips it in
   silence. Have parsing collect and report what it dropped and why — surfaced through
   `IntegrityChecker` or a toast on load. A user must never lose an account without being told.

### 2.2 Month rollover — the most destructive path, the least covered

`EntityService.initiateNewPeriod` closes every account, carries balances into a new period, renews
budgets and triggers recurring transactions. If it goes wrong it goes wrong across the whole file.
`TxService.triggerRecurringTx` now has 4 tests; `initiateNewPeriod` as a whole has none.

Cover end-to-end against `InMemorySavefileStorage`: balances carry forward correctly; budgets renew
(and `renewalPeriod > 1` and the December→January boundary both work); project budgets are excluded
from renewal; recurring transactions fire exactly once; the income list resets; and the previous
period's file is left intact.

### 2.3 Null-safety and precondition discipline

- **Eleven bare `assert x != null;` remain** in `AccountPreviewList`, `AccountService.deleteAccount`
  and `EntityService`. They are no-ops on device. None currently carries a side effect, so they
  change nothing — but they read as guards and are not. Replace with real checks or delete them.
- **Ban `assert` going forward.** A grep in CI, or a lint rule, is cheap insurance. The rule:
  never put a side effect inside `assert`, never use `assert` as a guard.
- `Util.validatePeriod(null)` throws `NullPointerException` from `Pattern.matcher(null)` rather
  than returning `false`. Anything calling it with a nullable value inherits that.
- `AccountService.deleteAccount` and `EntityService` date parsing both dereference values that the
  removed asserts pretended to guard.

### 2.4 Make silent failures visible

Across `core`, failures log to `Log.ERROR` and return `false`/`null` with no user-visible signal.
`saveOrRevert` is the good pattern — it reverts, logs, and rethrows so the activity can toast. The
parse and load paths have no equivalent. Go through the `return false` / `return null` sites and
decide which need to reach the user.

### 2.5 Wire up the integrity check

`IntegrityChecker` already detects the exact "silently discarded" class that hid the budget bug —
its own test fixture used a budget entry missing `renew_next` as the example. It was correct; it had
just never been run against a real file. Consider running it automatically after load, or on a
schedule, rather than leaving it as a manual menu action.

### 2.6 Lifecycle and concurrency

Lower priority, but unexamined: the `AbstractActivity` `workingThread`/`endWorkingThread` pattern,
and the `Controller.instance` field-initializer ordering that `onAppStartup()` patches up
afterwards. Both were deliberately left alone by the refactor (see "Out of scope" in the plan).

---

## Step 3 — Known issues not covered by hardening

Both are recorded in `docs/known-issues.md` under "Not yet fixed".

### 3.1 `updateTx` only descends one level of sub-budget — APPROVED, go ahead

**The user approved this fix.** No further decision needed; it just needs doing, with a test, in its
own commit labelled as a behaviour change.

Both overloads — `TxService.updateTx` at `:176` (amount) and `:228` (description) — build their
search list by hand: every asset account, then every budget account plus its
`getDirectSubBudgets()`. That is **one level only**, so editing a transaction whose counterpart sits
in a 2nd-level-or-deeper sub-budget fails to find it, and the two sides of the transfer silently
drift apart.

Replace that ~15-line block with `model.getAllAccounts()`, which recurses all levels via
`getAllSubBudgets()`.

Two details that make this safe and easy to get wrong:

- The existing code carries a `(copy the list! it must not be mutated, since it's the live model
  list)` warning, because it goes on to call `toSearch.remove(source)`. `Model.getAllAccounts()`
  already returns a freshly allocated `ArrayList` on every call, so the removal stays safe — but do
  not "optimise" that allocation away later.
- With the search list no longer hand-built, the two overloads differ only in the single mutation
  they apply. Extract `findTxPair(Date, String, AccountBE)` for the ~28 genuinely identical lines
  and let each overload keep its own mutation and revert. **Do not** parameterise over four lambdas
  to force them into one method — that was considered and rejected during the refactor.

Tests to write: a transaction pair whose counterpart lives in a **2nd-level** sub-budget updates
both sides (this is the fix), and one whose counterpart is a **1st-level** sub-budget still updates
both sides (this is the regression guard). Both are plain JVM tests against
`InMemorySavefileStorage`.

### 3.2 The budget branch of `completeTxRedirection` logs "asset" on a parse failure

Cosmetic, one line, in `TxRedirectionService.injectTx`. Now that the message exists once instead of
twice, just fix it.

---

## Decisions already made — please do not re-litigate

These look like unfinished plan items. They were considered and deliberately declined, with reasons
recorded in the relevant commit messages:

- **German strings in `core/IntegrityChecker` stay put.** That class is Android-free and JVM-tested.
  Reaching `strings.xml` means handing it a `Context` (undoing the layering work) or building a
  message-provider indirection for a diagnostics-only feature. There is one `values/` folder and no
  second locale.
- **`Const.DESC_OPENING` stays a constant.** It is not UI text — `AccountBE` writes it into save
  files as a transaction description. Localising it would make the same account serialise
  differently on different devices.
- **`MainActivity.onRefresh` keeps its second `setupActionBar()` call.** It is wasteful, but it is
  also what repopulates the entity spinner from `model.availableEntities`. Removing it risks a stale
  spinner for a cosmetic gain.
- **Activities stay in `com.privat.pitz.financehelper`, not `.ui`.** Moving them renames the
  launcher activity (breaking pinned home-screen shortcuts) and churns nine manifest entries for no
  benefit against the actual goals.

---

## Gotchas this codebase will bite you with

Learned the hard way this session. All four are load-bearing:

1. **`JSONObject.put(key, null)` REMOVES the key.** It does not store a JSON null. This is the root
   of the budget-account bug. Assume any nullable value written this way silently vanishes.
2. **`assert` is a no-op on Android** — the expression is not even evaluated. `build.gradle` now
   sets `testOptions.unitTests.all { enableAssertions = false }` so the JVM tests match the device.
   **Do not remove that line.** With assertions enabled, the suite disagrees with production on
   exactly the code that uses `assert`, which is what hid the crash.
3. **`AccountBE.getTxList()` returns the live list by reference**, and `TxListAdapter.setEntries`
   stores what it is given without copying. `TxListSection.applyFilter` must therefore always build
   its own list — that invariant is documented in `architecture.md` and is not enforced by the type
   system. Breaking it silently stops swipe-deletes from persisting.
4. **Gradle test-JVM defaults differ from Android in more places than assertions.**
   `returnDefaultValues = true` is already set to stub `android.util.Log`. Treat any "works in
   tests, fails on device" report as a fidelity gap first.

Process notes: the `Bash` tool in this environment mangles heredocs containing apostrophes — write
Python helper scripts to a scratchpad file and run those instead. Gradle test runs need
`--rerun-tasks` or they report `UP-TO-DATE` without running.

---

## Verification

After every commit:

```bash
./gradlew clean assembleDebug testDebugUnitTest --rerun-tasks
```

`assembleDebug` matters as well as the tests — resource linking catches deleted strings and
drawables that `compileDebugJavaWithJavac` does not, and custom views referenced by fully-qualified
name in layout XML fail at inflation time, not compile time.

**Prove new tests have teeth by mutation.** Both critical fixes this session were validated that
way: removing the default renewal date failed 4 tests; making the parser strict again failed 2;
flipping `enableAssertions` back on made the recurring-transaction tests pass against broken code.
Green tests are not evidence until you have seen them go red.

### On-device checklist before merging

Highest risk first:

- **Month rollover.** Point the app at a save file from a prior month, ideally with a recurring
  income (empty sender) and a recurring order referencing a since-deleted account — both crashed
  before the final commit. Confirm balances carry, budgets renew, recurring transactions fire once.
- **Create a budget account, close the app, reopen.** It must still be there, with its transactions.
- Sub-budget create / transfer / set yearly budget / edit renewal.
- Swipe-delete with no filter active, let the Snackbar expire, force-stop from that screen, reopen —
  the transaction must stay deleted.
- Pick a *child* account as sender and receiver; the transaction must land on the child.
- Zip backup export + import; SAF folder push + pull.
- Integrity check against a real save file.
