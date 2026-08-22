package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.ProjectBudgetBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;
import com.privat.pitz.financehelper.data.TxBE;

public class UtilTest {

    private Date dateFor(int year, int month1based, int day, int hour, int minute) {
        Calendar cal = new GregorianCalendar();
        cal.clear();
        cal.set(year, month1based - 1, day, hour, minute, 0);
        return cal.getTime();
    }

    // region float / filename formatting

    @Test
    public void formatFloatSave_usesDotDecimalSeparatorAndTwoDecimals() {
        assertEquals("123.45", Util.formatFloatSave(123.45f));
        assertEquals("-50.50", Util.formatFloatSave(-50.5f));
        assertEquals("0.00", Util.formatFloatSave(0f));
    }

    @Test
    public void formatFloatDisplay_producesTwoDecimalPlaces() {
        assertEquals("123.45", Util.formatFloatDisplay(123.45f).replace(',', '.'));
    }

    @Test
    public void reduceFileTypeEnding_stripsKnownExtensions() {
        assertEquals("report", Util.reduceFileTypeEnding("report.txt"));
        assertEquals("data", Util.reduceFileTypeEnding("data.jso"));
        assertEquals("archive", Util.reduceFileTypeEnding("archive.json"));
        assertEquals("my.file", Util.reduceFileTypeEnding("my.file.jso"));
    }

    @Test
    public void reduceFileTypeEnding_leavesUnknownExtensionsAndNoExtensionUnchanged() {
        assertEquals("noextension", Util.reduceFileTypeEnding("noextension"));
        assertEquals("file.xyz", Util.reduceFileTypeEnding("file.xyz"));
    }

    @Test
    public void serializeFileName_and_parseFileName_roundTrip() {
        Util.FileNameParts parts = new Util.FileNameParts(2024, 5, "Household");
        String fileName = Util.serializeFileName(parts);
        assertEquals("2024-05-Household.jso", fileName);

        Util.FileNameParts parsed = Util.parseFileName(fileName);
        assertEquals(2024, parsed.year);
        assertEquals(5, parsed.month);
        assertEquals("Household", parsed.entityName);
    }

    @Test
    public void serializeFileName_nullParts_throws() {
        try {
            Util.serializeFileName(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void serializeFileName_emptyEntityName_throws() {
        try {
            Util.serializeFileName(new Util.FileNameParts(2024, 5, ""));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void parseFileName_nullOrEmpty_throws() {
        try {
            Util.parseFileName(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            Util.parseFileName("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void parseFileName_wrongExtension_throws() {
        try {
            Util.parseFileName("2024-05-Household.txt");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void entityNameContainingDash_breaksParseFileNameRoundTrip() {
        // Characterizes an asymmetry in the current code: serializeFileName() does not forbid
        // dashes in the entity name, but parseFileName() splits on "[.-]", so a dash in the name
        // produces more than the expected 4 chunks and the round trip throws.
        Util.FileNameParts parts = new Util.FileNameParts(2024, 5, "House-hold");
        String fileName = Util.serializeFileName(parts);
        assertEquals("2024-05-House-hold.jso", fileName);

        try {
            Util.parseFileName(fileName);
            fail("Expected IllegalArgumentException due to the extra dash-induced chunk");
        } catch (IllegalArgumentException expected) {
            // expected - documents current (surprising) behavior
        }
    }

    // endregion

    // region isValidSavefileName

    @Test
    public void isValidSavefileName_validName_returnsTrue() {
        assertTrue(Util.isValidSavefileName("2024-05-Household.jso"));
    }

    @Test
    public void isValidSavefileName_matchAnywhereInString_returnsTrue() {
        // Pattern.find() is not anchored, so a valid pattern occurring anywhere in the string matches.
        assertTrue(Util.isValidSavefileName("prefix_2024-05-Household.jso_suffix"));
    }

    @Test
    public void isValidSavefileName_singleDigitMonth_returnsFalse() {
        assertFalse(Util.isValidSavefileName("2024-5-Household.jso"));
    }

    @Test
    public void isValidSavefileName_noYearMonthPrefix_returnsFalse() {
        assertFalse(Util.isValidSavefileName("Household.jso"));
    }

    @Test
    public void isValidSavefileName_emptyString_returnsFalse() {
        assertFalse(Util.isValidSavefileName(""));
    }

    @Test
    public void isValidSavefileName_jsonExtension_alsoMatchesDueToUnanchoredPattern() {
        // Characterizes a surprising quirk: the regex "\.jso" (no end anchor) is satisfied by the
        // first four characters of ".json" as well, so a ".json" file is also accepted even though
        // the intent (see ACCOUNTS_FILE_TYPE / knownFileTypes) appears to be ".jso" specifically.
        assertTrue(Util.isValidSavefileName("2024-05-Household.json"));
    }

    // endregion

    // region period helpers

    @Test
    public void validatePeriod_boundaryYearsAndMonths() {
        assertTrue(Util.validatePeriod("2000-01"));
        assertTrue(Util.validatePeriod("2050-12"));
        assertFalse(Util.validatePeriod("1999-12"));
        assertFalse(Util.validatePeriod("2051-01"));
        assertFalse(Util.validatePeriod("2024-00"));
        assertFalse(Util.validatePeriod("2024-13"));
        assertFalse(Util.validatePeriod("2024-1"));
        assertFalse(Util.validatePeriod("garbage"));
    }

    @Test
    public void isAfter_comparesYearAndMonth() throws Exception {
        assertTrue(Util.isAfter("2024-02", "2024-01"));
        assertTrue(Util.isAfter("2025-01", "2024-12"));
        assertFalse(Util.isAfter("2024-01", "2024-02"));
        assertFalse(Util.isAfter("2024-01", "2024-01"));
    }

    @Test
    public void isAfter_invalidPeriod_throws() {
        try {
            Util.isAfter("garbage", "2024-01");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // endregion

    // region calculateAdvancedPercentage

    @Test
    public void calculateAdvancedPercentage_currentBudgetAboveAllotted_normalizesByCurrentBudget() {
        float result = Util.calculateAdvancedPercentage(200f, 50f, 100f);
        assertEquals(0.25f, result, 0.0001f);
    }

    @Test
    public void calculateAdvancedPercentage_currentBudgetPositiveButBelowAllotted_normalizesByAverage() {
        float result = Util.calculateAdvancedPercentage(50f, 25f, 100f);
        assertEquals(2 * 25f / (50f + 100f), result, 0.0001f);
    }

    @Test
    public void calculateAdvancedPercentage_currentBudgetZeroOrNegative_startsAboveHundredPercent() {
        float result = Util.calculateAdvancedPercentage(-20f, 30f, 100f);
        assertEquals(1 + ((30f - (-20f)) / 100f), result, 0.0001f);

        float resultZero = Util.calculateAdvancedPercentage(0f, 10f, 100f);
        assertEquals(1 + ((10f - 0f) / 100f), resultZero, 0.0001f);
    }

    // endregion

    // region JSON round trips

    @Test
    public void accountRoundTrip_preservesNameFlagsAndTransactions() {
        AccountBE original = new AccountBE("Checking");
        original.setActive(false);
        original.setAutoRenew(true);
        original.addTx(new TxBE(123.45f, "Groceries", dateFor(2024, 6, 15, 10, 30)));
        original.addTx(new TxBE(-50.5f, "Refund", dateFor(2024, 6, 20, 8, 0)));

        JSONObject json = Util.serialise_Account(original);
        AccountBE parsed = Util.parseJSON_Account(json);

        assertEquals("Checking", parsed.getName());
        assertFalse(parsed.getIsActive());
        assertTrue(parsed.getAutoRenew());
        assertEquals(2, parsed.getTxList().size());

        TxBE tx0 = parsed.getTxList().get(0);
        assertEquals(123.45f, tx0.getAmount(), 0.001f);
        assertEquals("Groceries", tx0.getDescription());
        assertEquals(dateFor(2024, 6, 15, 10, 30), tx0.getDate());

        TxBE tx1 = parsed.getTxList().get(1);
        assertEquals(-50.5f, tx1.getAmount(), 0.001f);
        assertEquals("Refund", tx1.getDescription());
        assertEquals(dateFor(2024, 6, 20, 8, 0), tx1.getDate());
    }

    @Test
    public void budgetAccountRoundTrip_preservesNestedSubBudgetTree() {
        BudgetAccountBE root = new BudgetAccountBE("Root", 100f, 1200f);
        root.setRenewalPeriod(1);
        root.setNextRenewal("2024-06");
        root.setToOtherEntity("ExternalEntity");
        root.addTx(new TxBE(10f, "root-tx", dateFor(2024, 1, 1, 9, 0)));

        BudgetAccountBE child = new BudgetAccountBE("Child", 20f, 240f);
        child.setRenewalPeriod(2);
        child.setNextRenewal("2024-07");
        root.addSubBudget(child);

        BudgetAccountBE grandchild = new BudgetAccountBE("Grandchild", 5f, 60f);
        grandchild.setRenewalPeriod(1);
        grandchild.setNextRenewal("2024-08");
        child.addSubBudget(grandchild);

        JSONObject json = Util.serialise_BudgetAccount(root);
        BudgetAccountBE parsed = Util.parseJSON_BudgetAccount(json);

        assertFalse(parsed instanceof ProjectBudgetBE);
        assertEquals("Root", parsed.getName());
        assertEquals(1200f, parsed.indivYearlyBudget, 0.001f);
        assertEquals(100f, parsed.indivAvailableBudget, 0.001f);
        assertEquals(1, parsed.getRenewalPeriod());
        assertEquals("2024-06", parsed.getNextRenewal());
        assertEquals("ExternalEntity", parsed.getOtherEntity());
        assertEquals(1, parsed.getTxList().size());

        assertEquals(1, parsed.getDirectSubBudgets().size());
        BudgetAccountBE parsedChild = parsed.getDirectSubBudgets().get(0);
        assertEquals("Child", parsedChild.getName());
        assertEquals(240f, parsedChild.indivYearlyBudget, 0.001f);
        assertEquals(20f, parsedChild.indivAvailableBudget, 0.001f);
        assertEquals(2, parsedChild.getRenewalPeriod());
        assertEquals("2024-07", parsedChild.getNextRenewal());

        assertEquals(1, parsedChild.getDirectSubBudgets().size());
        BudgetAccountBE parsedGrandchild = parsedChild.getDirectSubBudgets().get(0);
        assertEquals("Grandchild", parsedGrandchild.getName());
        assertEquals(60f, parsedGrandchild.indivYearlyBudget, 0.001f);
        assertEquals(5f, parsedGrandchild.indivAvailableBudget, 0.001f);
        assertEquals("2024-08", parsedGrandchild.getNextRenewal());
    }

    @Test
    public void budgetAccountRoundTrip_withoutSubBudgets_omitsSubBudgetsKey() {
        BudgetAccountBE leaf = new BudgetAccountBE("Leaf", 10f, 120f);
        leaf.setRenewalPeriod(1);
        leaf.setNextRenewal("2024-06");

        JSONObject json = Util.serialise_BudgetAccount(leaf);
        assertFalse(json.has(Const.JSON_TAG_SUB_BUDGETS));

        BudgetAccountBE parsed = Util.parseJSON_BudgetAccount(json);
        assertTrue(parsed.getDirectSubBudgets().isEmpty());
    }

    @Test
    public void projectBudgetRoundTrip_preservesProjectFlagAndSkipsRenewalFields() throws org.json.JSONException {
        ProjectBudgetBE proj = new ProjectBudgetBE("Vacation", 3000f);
        proj.addTx(new TxBE(-500f, "flight", dateFor(2024, 1, 1, 12, 0)));

        JSONObject json = Util.serialise_BudgetAccount(proj);
        assertTrue(json.getBoolean(Const.JSON_TAG_PROJECT_BUDGET));
        assertFalse(json.has(Const.JSON_TAG_RENEWAL_NEXT));
        assertFalse(json.has(Const.JSON_TAG_RENEWAL_PERIOD));

        BudgetAccountBE parsed = Util.parseJSON_BudgetAccount(json);

        assertTrue(parsed instanceof ProjectBudgetBE);
        assertEquals(3000f, parsed.indivYearlyBudget, 0.001f);
        assertEquals(3000f, parsed.indivAvailableBudget, 0.001f);
        assertEquals(1, parsed.getTxList().size());
    }

    @Test
    public void budgetAccountRoundTrip_nonProjectWithoutNextRenewalSet_failsToParseBack() {
        // Characterizes current behavior: BudgetAccountBE.put(KEY, null) removes the key
        // (org.json semantics), so an account whose nextRenewal was never set serializes without
        // JSON_TAG_RENEWAL_NEXT. On parse, the missing key is treated as a hard error for
        // non-project accounts and parseJSON_BudgetAccount() returns null for the whole account,
        // silently discarding it rather than reporting a partial/default value.
        BudgetAccountBE b = new BudgetAccountBE("NoRenewalSet", 100f);
        b.setRenewalPeriod(1);
        // setNextRenewal() intentionally never called

        JSONObject json = Util.serialise_BudgetAccount(b);
        assertFalse(json.has(Const.JSON_TAG_RENEWAL_NEXT));

        BudgetAccountBE parsed = Util.parseJSON_BudgetAccount(json);
        assertNull(parsed);
    }

    @Test
    public void recurringOrderRoundTrip_preservesAllFields() {
        RecurringTxBE original = new RecurringTxBE(75.25f, "Rent", dateFor(2024, 3, 1, 7, 15),
                "Checking", "Landlord");

        JSONObject json = Util.serialise_RecurringOrder(original);
        RecurringTxBE parsed = Util.parseJSON_RecurringOrder(json);

        assertEquals(75.25f, parsed.getAmount(), 0.001f);
        assertEquals("Rent", parsed.getDescription());
        assertEquals(dateFor(2024, 3, 1, 7, 15), parsed.getDate());
        assertEquals("Checking", parsed.getSenderStr());
        assertEquals("Landlord", parsed.getReceiverStr());
    }

    @Test
    public void incomeListRoundTrip_preservesAllEntries() {
        List<TxBE> income = Arrays.asList(
                new TxBE(1000f, "Salary", dateFor(2024, 1, 1, 0, 0)),
                new TxBE(200.5f, "Bonus", dateFor(2024, 2, 1, 0, 0)));

        JSONArray json = Util.serialise_Income(income);
        List<TxBE> parsed = Util.parseJSON_IncomeList(json);

        assertEquals(2, parsed.size());
        assertEquals(1000f, parsed.get(0).getAmount(), 0.001f);
        assertEquals("Salary", parsed.get(0).getDescription());
        assertEquals(200.5f, parsed.get(1).getAmount(), 0.001f);
        assertEquals("Bonus", parsed.get(1).getDescription());
    }

    // endregion
}
