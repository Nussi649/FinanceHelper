# FinanceHelper — Developer Docs

FinanceHelper is a solo-hobbyist, single-module Android app for personal finance tracking. It
manages **asset accounts** (bank accounts, cash, etc.) and **budget accounts** (monthly/yearly
spending budgets, arranged in a tree of sub-budgets) for one or more "financial entities" (e.g.
different people or households sharing the same install), persisted as plain JSON files in
app-internal storage, one file per `(year, month, entity)`. All UI strings are German. There is
no backend/server and no cloud sync beyond a manual zip-export/import backup feature.

- Package: `com.privat.pitz.financehelper`
- Language: Java, single Gradle module `app`
- `minSdk` 26, `targetSdk`/`compileSdk` 33
- Build: `./gradlew assembleDebug` (produces a debug APK) or `./gradlew compileDebugJavaWithJavac`
  (fastest way to check the code compiles without packaging/signing)
- Unit tests: `app/src/test/java/` has real JUnit coverage for the `Logic` package
  (`AccountBETest`, `BudgetAccountBETest`, `ProjectBudgetBETest`, `TxBETest`) and
  `Backend/Util` (`UtilTest`) — run with `./gradlew testDebugUnitTest`. `app/build.gradle` sets
  `testOptions.unitTests.returnDefaultValues = true` (Android stub methods like
  `android.util.Log.println` throw by default under plain JVM unit tests otherwise) and pulls in
  a real `org.json:json` artifact for tests, since the Android SDK's `org.json` stub throws "not
  mocked". There is no `androidTest` (instrumentation) coverage beyond the default
  Android-Studio-generated scaffolding, and no tests at all for the `Backend/Controller`,
  `com.privat.pitz.financehelper` (Activities), or `View` layers.

## Where to look

- [`architecture.md`](architecture.md) — package layout, the `AbstractActivity` background-load
  lifecycle, the `Controller`/`Model` singleton, the RecyclerView tx-list + swipe pattern, and a
  **load-bearing gotcha about field shadowing** between `AssetAccountDetailsActivity` and
  `BudgetAccountDetailsActivity` that any new code must respect.
- [`data-model.md`](data-model.md) — the `Logic/*BE` entity classes (`AccountBE` →
  `BudgetAccountBE` → `ProjectBudgetBE`, `TxBE` → `RecurringTxBE`), the sub-budget tree, and what
  `Backend/Model.java` actually holds.
- [`file-format.md`](file-format.md) — the on-disk JSON save-file format, `settings.json`, and the
  zip-based backup/restore feature.
- [`known-issues.md`](known-issues.md) — a running list of concrete, verified bugs and fragile
  patterns, each with a file:line reference. Three are already fixed (kept for context since the
  underlying pattern — field shadowing across activity subclasses — has recurred at least once
  more and is worth watching for during the planned refactor).

## Orientation for an agent arriving cold

This codebase is years old, was never documented, and is about to be refactored. The single
biggest structural hazard is **Java field shadowing across the activity inheritance chain**:
`AssetAccountDetailsActivity` is subclassed by both `BudgetAccountDetailsActivity` and
`RecurringTxActivity`, and both subclasses declare their own fields (`mAccount`, `listAdapter`)
with the *same name* as a parent field but a *different, incompatible type*. Because Java resolves
field access statically (by the declared type of the code doing the accessing, not the runtime
object), any method defined in the parent class that touches its own field directly — instead of
going through an overridden accessor/method — will silently operate on `null` when the runtime
object is actually the subclass. This has already caused at least one shipped NPE-class bug (fixed
this session, see `known-issues.md`) and there is a second, not-yet-fixed instance affecting
`RecurringTxActivity`'s swipe-to-edit/delete gesture. Read `architecture.md`'s "Field shadowing"
section before touching any of `AssetAccountDetailsActivity`, `BudgetAccountDetailsActivity`, or
`RecurringTxActivity`. Beyond that: persistence is synchronous file I/O on the calling thread
(mostly the UI thread, guarded only by the `AbstractActivity` background-thread pattern used for
initial load), there is no database, and every state-changing `Controller` method follows a
manual try/mutate/save/catch-and-revert pattern instead of using transactions.
