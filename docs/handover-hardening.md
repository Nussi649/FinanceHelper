# The refactor and hardening pass: what changed, and what it left behind

**Status: complete.** The branch was squashed, hardened, verified on device, and merged to `master`.
This document is no longer a handover — it is the record of what the pass changed and, more
usefully, the set of traps this codebase will spring on the next person to touch it.

Read `docs/architecture.md` and `docs/known-issues.md` alongside it. Both are current and this
document does not repeat them.

## Outcome

- `refactor/clean-architecture`, **21 commits**, merged to `master` as `4ae1744` with a merge commit
  so the individual commits survive.
- The pre-squash 34-commit history is preserved on **`refactor/pre-squash-archive`**, pushed. It is
  the only copy of that history now that the rewritten branch has overwritten the old remote ref —
  **do not delete it.**
- **240 unit tests, all passing.** Test count across the two sessions: 101 → 141 → 240.
- The regression checklist at the bottom was run against a real install and passed.

---

## What the pass changed

Details for each are in `docs/known-issues.md` under "Fixed in the hardening pass" and in the commit
messages. The shape of it:

**Save-file round-trip integrity.** Field-level round-trip tests now exist for every persisted type
(`SaveFileRoundTripTest`, 43 tests). They found three real defects on the first run: budget accounts
silently lost their auto-renew setting on every load, five `put(key, null)` sites deleted their key
rather than writing a value, and a null transaction date aborted the entire save.

**Parse policy.** "Fatal" is no longer the unstated default. Four fields genuinely justify discarding
a record; everything else defaults. `core/ParseReport` collects every defaulted field and every
discarded record with its reason, `SaveFileRepository` accumulates it on the `Model`, and
`AbstractActivity.reportLoadProblems` surfaces it after every load — a dialog for discards, a toast
for repairs. **The app can no longer lose a record without telling the user.**

**Month rollover.** `MonthRolloverTest`, 16 tests, end to end against `InMemorySavefileStorage`:
balances carry, budgets renew at every sub-budget level, project budgets are excluded, recurring
orders fire exactly once, the income list resets, the previous period's file stays byte-for-byte
intact, and a second rollover over an existing current period is refused.

**Assert discipline.** The eleven remaining bare asserts are gone, replaced per site with real checks
or deleted where the following line would throw a clearer error anyway. `app/build.gradle` now fails
the build if `assert` reappears in `app/src/main` — verified by planting one.

**Silent failures.** Swipe-delete no longer ignores whether the delete actually persisted; `createTx`
guards its null sender/receiver and no longer leaves optimistic entries in the model when it gives
up.

**Integrity check.** Runs automatically after every load rather than sitting in the overflow menu
behind two file pickers. Two tests guard against it crying wolf: a file the app just wrote, and a
file produced by a rollover, must both come back clean.

**Deep sub-budget search.** `updateTx` recurses the whole sub-budget tree via
`model.getAllAccounts()`, with the ~28 shared lines extracted into `findTxPair`.

**Editing a transfer.** The swipe-to-edit dialog gained an opt-in "also change the counterpart"
checkbox (default off), backed by `TxService.updateTxPair`. Before this, editing a transfer changed
only the side the user swiped, at every nesting level, so the two accounts silently drifted apart.

**Three bugs reported from the field during the session**, all fixed, all in `known-issues.md` items
8–10: a transaction booked on one account only (a selection left pointing at an account object a load
had replaced), the swipe-to-edit row staying missing when the dialog was cancelled, and an edited
transaction rendered twice when it moved position in the sorted list.

**Process-death crash.** Returning through Recents after Android killed the process crashed on any
screen except Main. See `architecture.md` → "Lifecycle and threading".

---

## Decisions already made — please do not re-litigate

- **German strings in `core/IntegrityChecker` stay put** — and `core/ParseReport` follows the same
  precedent for the same reasons. Both are Android-free and JVM-tested; reaching `strings.xml` means
  handing them a `Context` or building a message-provider indirection. There is one `values/` folder
  and no second locale.
- **`Const.DESC_OPENING` stays a constant.** It is written into save files as a transaction
  description, not shown as UI text.
- **`MainActivity.onRefresh` keeps its second `setupActionBar()` call.** It is what repopulates the
  entity spinner.
- **Activities stay in `com.privat.pitz.financehelper`, not `.ui`.** Moving them renames the launcher
  activity and breaks pinned shortcuts.
- **`updateTx`'s two overloads stay separate.** Merging them behind four lambdas was considered and
  rejected; `findTxPair` carries the shared part instead.

---

## Gotchas this codebase will bite you with

This is the part of the document worth keeping. Every item cost real debugging time at least once.

1. **`JSONObject.put(key, null)` REMOVES the key.** It does not store a JSON null. Write nullable
   strings through `Util.putOrDefault`, which exists for exactly this.
2. **`assert` is a no-op on Android** — the expression is not even evaluated.
   `testOptions.unitTests.all { enableAssertions = false }` in `app/build.gradle` makes the JVM tests
   match the device. **Do not remove that line.** The `banAssertStatements` task fails the build if
   `assert` reappears in main sources.
3. **`AccountBE.getTxList()` returns the live list by reference**, and `TxListAdapter.setEntries`
   stores what it is given without copying. `TxListSection.applyFilter` must always build its own
   list.
4. **Gradle test-JVM defaults differ from Android in more places than assertions.** Treat any "works
   in tests, fails on device" report as a fidelity gap first.
5. **Spending on a budget account is stored as a POSITIVE amount**, with the matching negative on the
   asset account that paid. Getting this backwards is silently plausible and made three rollover
   tests fail on their first run.
6. **`parseJSON_BudgetAccount` reads `budget_cur` with `getDouble`, not `optDouble`.** `optDouble`
   swallows a NaN and substitutes the fallback, which would keep corrupt values out of the model and
   therefore out of `IntegrityChecker`'s numeric-sanity check. A corrupt value has to survive parsing
   to be reportable. `IntegrityCheckerTest.nanBudget_isFlagged` catches the regression.
7. **`Model.getAllAccounts()` allocating a fresh list is load-bearing**, not incidental:
   `TxService.findTxPair` mutates the returned list. Do not "optimise" that allocation away.
8. **`currentSender`/`currentReceiver`/`currentInspectedAccount` hold account objects, and every load
   replaces every account object.** A selection kept across a load is an orphan: correct name,
   correct transactions, contained in no list the model holds, and `addTx` on it writes somewhere the
   serialiser never visits. Anything that rebuilds the account lists must run the selections through
   `Model.reattachSelection`, and anything that removes an account must clear them.
   `Model.containsAccount` tests this by **identity, not name** — a same-named orphan is exactly the
   case that used to slip through.

Process notes: the `Bash` tool mangles heredocs whose body contains apostrophes or nested heredoc
markers, even with a quoted delimiter — write the file with the editor tool, or put the content in a
Python helper in the scratchpad and run that. Gradle test runs need `--rerun-tasks` or they report
`UP-TO-DATE` without running. When several agents share this working tree, have each one `git add`
its own files explicitly and never `git add -A` — a concurrent `git commit` will otherwise sweep up
another agent's staged work.

---

## Verification

After every commit:

```bash
./gradlew clean assembleDebug testDebugUnitTest --rerun-tasks
```

`assembleDebug` matters as well as the tests — resource linking catches deleted strings and drawables
that `compileDebugJavaWithJavac` does not, and custom views referenced by fully-qualified name in
layout XML fail at inflation time, not compile time.

**Prove new tests have teeth by mutation.** Every fix in this pass was validated that way, and the
evidence is recorded in each commit message. Green tests are not evidence until you have seen them go
red.

Keep the CLI on the same JVM as Android Studio. Gradle cannot share a daemon across JVMs, so a
mismatch silently runs two daemons and trips an Android Studio sync warning that has its own EDT bug
(the sync itself succeeds; the exception comes from the post-sync listener). `org.gradle.java.home`
is set in the **user-level** `~/.gradle/gradle.properties`, deliberately not in the committed project
file, because it is a machine-specific path.

### Regression checklist (run on a device)

None of this is covered by the unit tests, and all of it exercises code the pass changed. It passed
before the merge; re-run the relevant parts after touching any of these areas.

- **Month rollover.** Point the app at a save file from a prior month, ideally with a recurring
  income (empty sender) and a recurring order referencing a since-deleted account. Balances carry,
  budgets renew, recurring transactions fire once, previous month's file untouched.
- **Process death.** Open a details screen, force-stop from system settings, reopen from Recents. It
  must land on the main screen with data loaded.
- **Load reporting.** Hand-edit a save file to remove an account's `name`: a dialog must name what
  was dropped and why. Remove a `renew_next` instead and it should be a toast, account intact.
- **Integrity check on load.** A healthy file must produce no toast on startup — if it does, the
  check gets trained away and is worse than useless.
- **Editing a transfer.** Swipe-edit one side with the checkbox OFF (only that side changes) and ON
  (both change, including the date). Tick it on an income, which has no counterpart, and confirm it
  edits that entry and says so.
- **Swipe-to-edit, then cancel.** The row reappears immediately, without leaving the activity.
- **Edit an entry's date so it sorts elsewhere.** It appears exactly once, in its new place.
- **Both sides of a transaction.** Create one, confirm it lands on both accounts. Then provoke a
  refusal: delete the account selected as sender and confirm the app declines rather than writing one
  side. Load a different save file and immediately enter a transaction — the path that used to
  produce the one-sided entry.
- **Auto-renew on a budget account.** Untick in Settings, restart, confirm still unticked.
- Create a budget account, close, reopen — still there with its transactions.
- Sub-budget create / transfer / set yearly budget / edit renewal.
- Swipe-delete with no filter active, let the Snackbar expire, force-stop from that screen, reopen —
  the transaction stays deleted.
- Pick a *child* account as sender and receiver; the transaction lands on the child.
- Zip backup export + import; SAF folder push + pull.
