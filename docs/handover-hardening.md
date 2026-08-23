# Handover: the hardening pass is done; what is left is on-device verification and a merge

Rewritten at the end of the hardening session. Read `docs/architecture.md` and
`docs/known-issues.md` alongside this — both are current as of the last commit and this document
does not repeat them.

## Where things stand

- Branch `refactor/clean-architecture`, **15 commits**, **not pushed**. `master` untouched.
- `origin/refactor/clean-architecture` still holds the old 34-commit history. The branch was
  squashed, so publishing it needs a **force-push**. The user asked to keep everything local until
  the work is finished and to do that push themselves.
- The pre-squash history is preserved locally on **`refactor/pre-squash-archive`** (also not
  pushed). Push that one *before* force-pushing the rewritten branch.
- **227 unit tests, all passing.** `./gradlew clean assembleDebug testDebugUnitTest --rerun-tasks`
  is green.
- Test count over the two sessions: 101 → 141 → 227.

Everything in the previous handover's plan is done. What remains is the on-device checklist at the
bottom, one open issue that needs a decision, and the merge.

---

## What the hardening pass changed

Details for each are in `docs/known-issues.md` under "Fixed in the hardening pass" and in the
commit messages. Summary of the shape:

**Save-file round-trip integrity.** Field-level round-trip tests now exist for every persisted type
(`SaveFileRoundTripTest`, 43 tests). They found three real defects on the first run: budget
accounts silently lost their auto-renew setting on every load, five `put(key, null)` sites deleted
their key rather than writing a value, and a null transaction date aborted the entire save.

**Parse policy.** "Fatal" is no longer the unstated default. Four fields genuinely justify
discarding a record; everything else defaults. `core/ParseReport` collects every defaulted field
and every discarded record with its reason, `SaveFileRepository` accumulates it on the `Model`, and
`AbstractActivity.reportLoadProblems` surfaces it after every load — a dialog for discards, a toast
for repairs. **The app can no longer lose a record without telling the user.**

**Month rollover.** `MonthRolloverTest`, 16 tests, end to end against `InMemorySavefileStorage`:
balances carry, budgets renew at every sub-budget level, project budgets are excluded, recurring
orders fire exactly once, the income list resets, the previous period's file stays byte-for-byte
intact, and a second rollover over an existing current period is refused.

**Assert discipline.** The eleven remaining bare asserts are gone, replaced per site with real
checks or deleted where the following line would throw a clearer error anyway. `app/build.gradle`
now fails the build if `assert` reappears in `app/src/main` — verified by planting one.

**Silent failures.** Swipe-delete no longer ignores whether the delete actually persisted;
`createTx` guards its null sender/receiver and no longer leaves optimistic entries in the model
when it gives up.

**Integrity check.** Now runs automatically after every load rather than sitting in the overflow
menu behind two file pickers. Two tests guard against it crying wolf: a file the app just wrote,
and a file produced by a rollover, must both come back clean.

**Deep sub-budget search.** `updateTx` recurses the whole sub-budget tree via
`model.getAllAccounts()`, with the ~28 shared lines extracted into `findTxPair`.

**Editing a transfer.** The swipe-to-edit dialog gained an opt-in "also change the counterpart"
checkbox (default off), backed by `TxService.updateTxPair`. Before this, editing a transfer changed
only the side the user swiped, at every nesting level, so the two accounts silently drifted apart.
It is a checkbox rather than automatic because not every entry has a counterpart — an opening
balance and an income have none, and a same-day same-description entry elsewhere may be a
coincidence.

**Process-death crash.** Returning to the app through Recents after Android killed the process
crashed on any screen except Main. See the commit and `architecture.md` → "Lifecycle and threading".

---

## Still open

### 1. Decision needed — nothing else

There are no other outstanding decisions. `docs/known-issues.md` → "Not yet fixed" is now empty of
code defects; the remaining lifecycle hazards are recorded in `architecture.md` rather than fixed,
deliberately (see below).

### 2. Lifecycle hazards examined but not fixed

`docs/architecture.md` → "Lifecycle and threading" records the full analysis. Two are worth knowing
about:

- **`MainActivity.onStop()` saves the model on the UI thread while `workingThread()` may still be
  populating those same lists on a background thread.** Nothing joins them. The window is small
  (a local file load) but unbounded. Fixing it properly means replacing the raw
  `new Thread(...)` / `runOnUiThread` pattern in `AbstractActivity.onCreate`, which is a redesign
  the refactor deliberately stayed out of.
- **No cancellation or liveness check** on that thread; `runOnUiThread` still fires against a
  destroyed activity. Mostly a transient leak.

Neither is a reason to hold the merge. Both are a reason not to add more work to
`workingThread()` without thinking.

---

## Decisions already made — please do not re-litigate

Unchanged from the previous handover, all still current:

- **German strings in `core/IntegrityChecker` stay put** — and `core/ParseReport` follows the same
  precedent for the same reasons. Both are Android-free and JVM-tested; reaching `strings.xml`
  means handing them a `Context` or building a message-provider indirection. There is one `values/`
  folder and no second locale.
- **`Const.DESC_OPENING` stays a constant.** It is written into save files as a transaction
  description, not shown as UI text.
- **`MainActivity.onRefresh` keeps its second `setupActionBar()` call.** It is what repopulates the
  entity spinner.
- **Activities stay in `com.privat.pitz.financehelper`, not `.ui`.** Moving them renames the
  launcher activity and breaks pinned shortcuts.
- **`updateTx`'s two overloads stay separate.** Merging them behind four lambdas was considered and
  rejected; `findTxPair` carries the shared part instead.

---

## Gotchas this codebase will bite you with

The first four are unchanged and still load-bearing. The last three are new.

1. **`JSONObject.put(key, null)` REMOVES the key.** It does not store a JSON null. Write nullable
   strings through `Util.putOrDefault`, which exists for exactly this.
2. **`assert` is a no-op on Android** — the expression is not even evaluated.
   `testOptions.unitTests.all { enableAssertions = false }` in `app/build.gradle` makes the JVM
   tests match the device. **Do not remove that line.** The `banAssertStatements` gradle task now
   fails the build if `assert` reappears in main sources.
3. **`AccountBE.getTxList()` returns the live list by reference**, and `TxListAdapter.setEntries`
   stores what it is given without copying. `TxListSection.applyFilter` must always build its own
   list.
4. **Gradle test-JVM defaults differ from Android in more places than assertions.** Treat any
   "works in tests, fails on device" report as a fidelity gap first.
5. **Spending on a budget account is stored as a POSITIVE amount**, with the matching negative on
   the asset account that paid. Getting this backwards is silently plausible and made three
   rollover tests fail on their first run.
6. **`parseJSON_BudgetAccount` reads `budget_cur` with `getDouble`, not `optDouble`.** `optDouble`
   swallows a NaN and substitutes the fallback, which would keep corrupt values out of the model
   and therefore out of `IntegrityChecker`'s numeric-sanity check. A corrupt value has to survive
   parsing to be reportable. `IntegrityCheckerTest.nanBudget_isFlagged` catches the regression.
7. **`Model.getAllAccounts()` allocating a fresh list is load-bearing**, not incidental:
   `TxService.findTxPair` mutates the returned list. Do not "optimise" that allocation away.

Process notes: the `Bash` tool mangles heredocs containing apostrophes — write Python helper
scripts to a scratchpad file and run those instead. Gradle test runs need `--rerun-tasks` or they
report `UP-TO-DATE` without running. When several agents share this working tree, have each one
`git add` its own files explicitly and never `git add -A` — a concurrent `git commit` will
otherwise sweep up another agent's staged work.

---

## Verification

After every commit:

```bash
./gradlew clean assembleDebug testDebugUnitTest --rerun-tasks
```

`assembleDebug` matters as well as the tests — resource linking catches deleted strings and
drawables that `compileDebugJavaWithJavac` does not, and custom views referenced by fully-qualified
name in layout XML fail at inflation time, not compile time.

**Prove new tests have teeth by mutation.** Every fix in this pass was validated that way, and the
evidence is recorded in each commit message. Green tests are not evidence until you have seen them
go red.

### On-device checklist before merging

Nothing here is covered by the unit tests, and several items exercise code that changed in this
pass. Highest risk first.

- **Month rollover.** Point the app at a save file from a prior month, ideally with a recurring
  income (empty sender) and a recurring order referencing a since-deleted account. Confirm balances
  carry, budgets renew, recurring transactions fire once, and the previous month's file is
  untouched. Now covered by 16 JVM tests, but never yet run on a device.
- **Process death.** Open a details screen, force-stop the app from the system settings, then
  reopen it from Recents. It must land on the main screen with data loaded, not crash. This is the
  fix in the last commit and cannot be unit-tested.
- **Load reporting.** Hand-edit a save file through the raw-JSON editor to remove an account's
  `name`, then load it. A dialog must name what was dropped and why. Remove a `renew_next` instead
  and it should be a toast, with the account intact.
- **Integrity check on load.** Confirm a healthy file produces no toast on startup — if it does,
  the check will be trained away and is worse than useless.
- **Editing a transfer.** Swipe-edit one side of a transfer with the new checkbox OFF (only that
  side changes, as before) and then ON (both sides change, including the date). Tick it on an
  income entry, which has no counterpart, and confirm it edits that entry and says so.
- **Auto-renew on a budget account.** Untick it in Settings, restart the app, confirm it is still
  unticked. This never survived a restart before.
- Create a budget account, close the app, reopen — it must still be there with its transactions.
- Sub-budget create / transfer / set yearly budget / edit renewal.
- Swipe-delete with no filter active, let the Snackbar expire, force-stop from that screen, reopen —
  the transaction must stay deleted.
- Pick a *child* account as sender and receiver; the transaction must land on the child.
- Zip backup export + import; SAF folder push + pull.

### Merging

Once the checklist passes:

```bash
git push origin refactor/pre-squash-archive
git push --force-with-lease origin refactor/clean-architecture
```

Push the archive branch first, so the original history is safe on the remote before the rewritten
branch overwrites the old one. Then merge into `master` however you prefer — the branch is a clean
sequence of one refactor commit followed by labelled behaviour changes, so a merge commit preserves
more than a squash would.
