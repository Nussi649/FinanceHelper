package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import com.privat.pitz.financehelper.data.BudgetAccountBE;

/**
 * The load path must never lose a record without saying so.
 *
 * <p>A budget account once disappeared because the parser returned null for it and the caller
 * dropped that null in silence; the next save then wrote the model back without it and the loss
 * became permanent. The parser is now allowed to default a field or discard a record, but not to
 * do either quietly - every such decision lands in a {@link ParseReport} that reaches the caller.
 */
public class LoadReportTest {

    private static final String FILE = "2026-08-User.jso";

    private Controller freshController(InMemorySavefileStorage storage) {
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = FILE;
        return controller;
    }

    /** Writes a valid save file, then strips one key from the first budget account. */
    private InMemorySavefileStorage fileWithBrokenBudgetAccount(String keyToRemove)
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = freshController(storage);
        writer.createAssetAccount("Girokonto");
        writer.createRootBudget("Lebensmittel", 100f, 1200f);
        writer.saveAccountsToInternal();

        JSONObject saved = new JSONObject(storage.read(FILE));
        saved.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS).getJSONObject(0).remove(keyToRemove);
        storage.write(FILE, saved.toString());
        return storage;
    }

    @Test
    public void discardedAccount_isReportedToTheCaller() throws JSONException, IOException {
        // no name is the one thing a budget account cannot survive - every lookup resolves by it
        InMemorySavefileStorage storage = fileWithBrokenBudgetAccount(Const.JSON_TAG_NAME);

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        ParseReport report = reader.getModel().takeLoadReport();

        assertTrue(reader.getModel().budget_accounts.isEmpty());
        assertTrue("losing an account must not be silent", report.hasDiscards());
        assertEquals(1, report.countDiscards());
    }

    @Test
    public void defaultedField_isReportedButNotAsADiscard() throws JSONException, IOException {
        // this is the shape every budget account written by the broken version has: the account
        // must load, and the repair must still be visible
        InMemorySavefileStorage storage =
                fileWithBrokenBudgetAccount(Const.JSON_TAG_RENEWAL_NEXT);

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        ParseReport report = reader.getModel().takeLoadReport();

        BudgetAccountBE reloaded = reader.getModel().getBudgetAccountByName("Lebensmittel");
        assertEquals(1200f, reloaded.indivYearlyBudget, 0.001f);
        assertFalse("a repaired field is not a data loss", report.hasDiscards());
        assertFalse("but it still has to be reported", report.isEmpty());
    }

    @Test
    public void cleanFile_producesAnEmptyReport() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = freshController(storage);
        writer.createAssetAccount("Girokonto");
        writer.createRootBudget("Lebensmittel", 100f, 1200f);
        writer.saveAccountsToInternal();

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);

        assertTrue("a healthy file must not raise noise",
                reader.getModel().takeLoadReport().isEmpty());
    }

    @Test
    public void takingTheReport_clearsIt() throws JSONException, IOException {
        InMemorySavefileStorage storage = fileWithBrokenBudgetAccount(Const.JSON_TAG_NAME);
        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);

        assertFalse(reader.getModel().takeLoadReport().isEmpty());
        assertTrue("the same problem must not be reported twice",
                reader.getModel().takeLoadReport().isEmpty());
    }

    @Test
    public void reportsAccumulateAcrossSettingsAndSaveFile() throws JSONException, IOException {
        // app startup parses the settings file and then a save file. The UI surfaces both
        // together afterwards, so the first must not be overwritten by the second.
        InMemorySavefileStorage storage = fileWithBrokenBudgetAccount(Const.JSON_TAG_NAME);

        JSONObject settings = new JSONObject();
        JSONArray entities = new JSONArray();
        entities.put(new JSONObject());  // an entry with no name - unusable, must be discarded
        settings.put(Const.JSON_TAG_DEFAULT_ACCOUNTS, entities);
        settings.put(Const.JSON_TAG_DEFAULT_ENTITY, "User");
        storage.write(Const.APPLICATION_SETTINGS_FILENAME, settings.toString());

        Controller reader = new Controller(storage);
        reader.loadAppSettings();
        reader.readAccountsFromInternal(FILE);

        assertEquals("both parses must be represented", 2,
                reader.getModel().takeLoadReport().countDiscards());
    }

    @Test
    public void integrityCheck_namesTheDiscardedEntryRatherThanJustCountingIt()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = fileWithBrokenBudgetAccount(Const.JSON_TAG_NAME);
        Controller controller = new Controller(storage);

        IntegrityChecker.Result result = new IntegrityChecker(controller).check(FILE);

        boolean namesTheReason = false;
        for (IntegrityChecker.Finding finding : result.findings)
            if (finding.message.contains(Const.JSON_TAG_NAME))
                namesTheReason = true;
        assertTrue("the checker should say which field was missing, not only that one entry was "
                + "dropped: " + result.findings, namesTheReason);
    }
}
