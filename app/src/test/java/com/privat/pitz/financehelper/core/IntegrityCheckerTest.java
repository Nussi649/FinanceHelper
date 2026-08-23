package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

public class IntegrityCheckerTest {

    // no cross-entity redirects in any test fixture below, so the Controller is never dereferenced
    private final IntegrityChecker checker = new IntegrityChecker(null);

    private Date date(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private String buildSaveFile(List<AccountBE> assets, List<BudgetAccountBE> budgets, List<TxBE> income) throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray assetsJson = new JSONArray();
        for (AccountBE acc : assets)
            assetsJson.put(Util.serialise_Account(acc));
        json.put(Const.JSON_TAG_ASSET_ACCOUNTS, assetsJson);

        JSONArray budgetsJson = new JSONArray();
        for (BudgetAccountBE acc : budgets)
            budgetsJson.put(Util.serialise_BudgetAccount(acc));
        json.put(Const.JSON_TAG_BUDGET_ACCOUNTS, budgetsJson);

        json.put(Const.JSON_TAG_RECURRING_TX, new JSONArray());
        json.put(Const.JSON_TAG_CURRENT_INCOME, Util.serialise_Income(income));
        return json.toString();
    }

    // region single-file structural checks (IntegrityChecker.checkParsed)

    @Test
    public void cleanFile_noFindings() throws JSONException {
        AccountBE account = new AccountBE("Girokonto");
        account.addTx(new TxBE(50f, "Gehalt", date(2026, 8, 1, 9, 0)));

        String data = buildSaveFile(Arrays.asList(account), new ArrayList<>(), new ArrayList<>());
        IntegrityChecker.Result result = checker.checkParsed("2026-08-User.jso", data);

        assertTrue(result.parsedOk);
        assertTrue("expected no findings but got: " + describe(result), result.isClean());
    }

    @Test
    public void duplicateAccountName_isFlagged() throws JSONException {
        AccountBE a1 = new AccountBE("Girokonto");
        AccountBE a2 = new AccountBE("Girokonto");

        String data = buildSaveFile(Arrays.asList(a1, a2), new ArrayList<>(), new ArrayList<>());
        IntegrityChecker.Result result = checker.checkParsed("2026-08-User.jso", data);

        assertTrue(containsMessageContaining(result, "mehrfach"));
    }

    @Test
    public void nanBudget_isFlagged() throws JSONException {
        BudgetAccountBE budget = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        budget.setNextRenewal("2026-09");
        budget.setIndivAvailableBudget(Float.NaN);

        String data = buildSaveFile(new ArrayList<>(), Arrays.asList(budget), new ArrayList<>());
        IntegrityChecker.Result result = checker.checkParsed("2026-08-User.jso", data);

        assertTrue(containsMessageContaining(result, "NaN/Infinity"));
    }

    @Test
    public void incomeEntryWithoutAccountCounterpart_isFlagged() throws JSONException {
        AccountBE account = new AccountBE("Girokonto");
        TxBE income = new TxBE(500f, "Gehalt", date(2026, 8, 1, 9, 0));
        // deliberately NOT added to account.txList, unlike the real Controller.addFunds always does

        String data = buildSaveFile(Arrays.asList(account), new ArrayList<>(), Arrays.asList(income));
        IntegrityChecker.Result result = checker.checkParsed("2026-08-User.jso", data);

        assertTrue(containsMessageContaining(result, "Einkommens-Eintrag"));
    }

    @Test
    public void incomeEntryWithAccountCounterpart_isNotFlagged() throws JSONException {
        AccountBE account = new AccountBE("Girokonto");
        TxBE income = new TxBE(500f, "Gehalt", date(2026, 8, 1, 9, 0));
        account.addTx(income); // same object in both lists, exactly like Controller.addFunds does

        String data = buildSaveFile(Arrays.asList(account), new ArrayList<>(), Arrays.asList(income));
        IntegrityChecker.Result result = checker.checkParsed("2026-08-User.jso", data);

        assertFalse(containsMessageContaining(result, "Einkommens-Eintrag"));
    }

    @Test
    public void malformedBudgetAccountEntry_isReportedAsSilentlyDropped() throws JSONException {
        // a non-project budget account missing the required "budget_year" field fails to parse
        // (Util.parseJSON_BudgetAccount returns null for it) and would otherwise vanish with no
        // error anywhere - this is exactly the corruption class this check exists to catch.
        // NB: this fixture used to omit "renew_next" instead. That is no longer a parse failure:
        // it is the shape every budget account created in the app was written in, and treating it
        // as fatal is what made them all disappear on the next load.
        JSONObject malformed = new JSONObject();
        malformed.put(Const.JSON_TAG_NAME, "Broken");
        malformed.put(Const.JSON_TAG_ISACTIVE, true);
        malformed.put(Const.JSON_TAG_AUTO_RENEW, true);
        malformed.put(Const.JSON_TAG_TRANSACTIONS, new JSONArray());
        malformed.put(Const.JSON_TAG_PROJECT_BUDGET, false);
        malformed.put(Const.JSON_TAG_RENEWAL_PERIOD, 1);
        malformed.put(Const.JSON_TAG_RENEWAL_NEXT, "2026-09");
        // JSON_TAG_YEARLY_BUDGET deliberately omitted
        malformed.put(Const.JSON_TAG_TO_OTHER, "");

        JSONObject json = new JSONObject();
        json.put(Const.JSON_TAG_ASSET_ACCOUNTS, new JSONArray());
        JSONArray budgets = new JSONArray();
        budgets.put(malformed);
        json.put(Const.JSON_TAG_BUDGET_ACCOUNTS, budgets);
        json.put(Const.JSON_TAG_RECURRING_TX, new JSONArray());
        json.put(Const.JSON_TAG_CURRENT_INCOME, new JSONArray());

        IntegrityChecker.Result result = checker.checkParsed("2026-08-User.jso", json.toString());

        assertTrue("expected a 'silently dropped' finding but got: " + describe(result),
                containsMessageContaining(result, "stillschweigend verworfen"));
    }

    // endregion

    // region total-sum conservation (IntegrityChecker.compareParsed) - the main check

    @Test
    public void normalTransfer_totalUnchanged_isClean() throws JSONException {
        // a normal expense: money leaves the asset account, the budget account's "spent" tracker
        // grows by the same amount - the grand total (asset sums + budget sums) must stay put
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal("2026-09");
        String before = buildSaveFile(Arrays.asList(girokonto), Arrays.asList(lebensmittel), new ArrayList<>());

        AccountBE girokontoAfter = new AccountBE("Girokonto");
        girokontoAfter.addTx(new TxBE(-20f, "Pizza", date(2026, 8, 22, 12, 0)));
        BudgetAccountBE lebensmittelAfter = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittelAfter.setNextRenewal("2026-09");
        lebensmittelAfter.addTx(new TxBE(20f, "Pizza", date(2026, 8, 22, 12, 0)));
        String after = buildSaveFile(Arrays.asList(girokontoAfter), Arrays.asList(lebensmittelAfter), new ArrayList<>());

        IntegrityChecker.Result result = checker.compareParsed("2026-08-User.jso", before, "2026-08-User.jso", after);

        assertTrue("expected the paired transfer to net to zero but got: " + describe(result), result.isClean());
    }

    @Test
    public void income_explainsExactIncrease_isClean() throws JSONException {
        AccountBE girokonto = new AccountBE("Girokonto");
        String before = buildSaveFile(Arrays.asList(girokonto), new ArrayList<>(), new ArrayList<>());

        AccountBE girokontoAfter = new AccountBE("Girokonto");
        TxBE income = new TxBE(500f, "Gehalt", date(2026, 8, 1, 9, 0));
        girokontoAfter.addTx(income);
        String after = buildSaveFile(Arrays.asList(girokontoAfter), new ArrayList<>(), Arrays.asList(income));

        IntegrityChecker.Result result = checker.compareParsed("2026-08-User.jso", before, "2026-08-User.jso", after);

        assertTrue("expected income to fully explain the increase but got: " + describe(result), result.isClean());
    }

    @Test
    public void unexplainedIncrease_withoutIncome_isFlagged() throws JSONException {
        AccountBE girokonto = new AccountBE("Girokonto");
        String before = buildSaveFile(Arrays.asList(girokonto), new ArrayList<>(), new ArrayList<>());

        // money appeared out of nowhere: no paired debit anywhere, and not recorded as income either
        AccountBE girokontoAfter = new AccountBE("Girokonto");
        girokontoAfter.addTx(new TxBE(500f, "???", date(2026, 8, 1, 9, 0)));
        String after = buildSaveFile(Arrays.asList(girokontoAfter), new ArrayList<>(), new ArrayList<>());

        IntegrityChecker.Result result = checker.compareParsed("2026-08-User.jso", before, "2026-08-User.jso", after);

        assertTrue(containsMessageContaining(result, "Gesamtsumme"));
        assertTrue(containsMessageContaining(result, "Konten-Übersicht"));
    }

    @Test
    public void budgetReset_clearingWholeTxList_isExplainedNotFlagged() throws JSONException {
        BudgetAccountBE before = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        before.setNextRenewal("2026-09");
        before.addTx(new TxBE(80f, "Wocheneinkauf", date(2026, 8, 10, 12, 0)));
        String beforeData = buildSaveFile(new ArrayList<>(), Arrays.asList(before), new ArrayList<>());

        // BudgetAccountBE.tryRenew()/reset() clears the tx list wholesale without carrying the
        // spent amount forward anywhere - this must be recognized as a legitimate reset, not flagged
        BudgetAccountBE after = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        after.setNextRenewal("2026-10");
        String afterData = buildSaveFile(new ArrayList<>(), Arrays.asList(after), new ArrayList<>());

        IntegrityChecker.Result result = checker.compareParsed("2026-08-User.jso", beforeData, "2026-09-User.jso", afterData);

        assertTrue("expected a clean reset to be explained but got: " + describe(result), result.isClean());
    }

    @Test
    public void partialEntryLoss_notAFullReset_isFlaggedDistinctly() throws JSONException {
        BudgetAccountBE before = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        before.setNextRenewal("2026-09");
        before.addTx(new TxBE(30f, "Brot", date(2026, 8, 5, 10, 0)));
        before.addTx(new TxBE(50f, "Fleisch", date(2026, 8, 10, 10, 0)));
        String beforeData = buildSaveFile(new ArrayList<>(), Arrays.asList(before), new ArrayList<>());

        // only ONE of the two entries survives - not a clean wholesale reset, looks like data loss
        BudgetAccountBE after = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        after.setNextRenewal("2026-09");
        after.addTx(new TxBE(30f, "Brot", date(2026, 8, 5, 10, 0)));
        String afterData = buildSaveFile(new ArrayList<>(), Arrays.asList(after), new ArrayList<>());

        IntegrityChecker.Result result = checker.compareParsed("2026-08-User.jso", beforeData, "2026-08-User.jso", afterData);

        assertTrue("expected a partial-loss finding but got: " + describe(result),
                containsMessageContaining(result, "aber nicht alle"));
    }

    @Test
    public void subBudgetTransactions_participateInTotal() throws JSONException {
        AccountBE asset = new AccountBE("Girokonto");
        BudgetAccountBE root = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        root.setNextRenewal("2026-09");
        BudgetAccountBE sub = new BudgetAccountBE("Restaurants", 50f, 600f);
        sub.setNextRenewal("2026-09");
        root.addSubBudget(sub);
        String before = buildSaveFile(Arrays.asList(asset), Arrays.asList(root), new ArrayList<>());

        AccountBE assetAfter = new AccountBE("Girokonto");
        assetAfter.addTx(new TxBE(-20f, "Pizza", date(2026, 8, 22, 12, 0)));
        BudgetAccountBE rootAfter = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        rootAfter.setNextRenewal("2026-09");
        BudgetAccountBE subAfter = new BudgetAccountBE("Restaurants", 50f, 600f);
        subAfter.setNextRenewal("2026-09");
        subAfter.addTx(new TxBE(20f, "Pizza", date(2026, 8, 22, 12, 0)));
        rootAfter.addSubBudget(subAfter);
        String after = buildSaveFile(Arrays.asList(assetAfter), Arrays.asList(rootAfter), new ArrayList<>());

        IntegrityChecker.Result result = checker.compareParsed("2026-08-User.jso", before, "2026-08-User.jso", after);

        assertTrue("expected the sub-budget entry to net out but got: " + describe(result), result.isClean());
    }

    // endregion

    private boolean containsMessageContaining(IntegrityChecker.Result result, String snippet) {
        for (IntegrityChecker.Finding f : result.findings)
            if (f.message.contains(snippet))
                return true;
        return false;
    }

    private String describe(IntegrityChecker.Result result) {
        StringBuilder sb = new StringBuilder();
        for (IntegrityChecker.Finding f : result.findings)
            sb.append(f.message).append(" | ");
        return sb.toString();
    }
}
