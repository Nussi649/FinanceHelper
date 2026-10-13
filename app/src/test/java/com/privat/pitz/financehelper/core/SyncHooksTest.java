package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Calendar;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/** The two places the app tells the cloud sync that something happened. */
public class SyncHooksTest {

    private static final String FILE = "2026-08-User.jso";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private InMemorySavefileStorage storage;
    private Controller controller;
    private int saved;

    @Before
    public void setUp() throws Exception {
        storage = new InMemorySavefileStorage();
        controller = new Controller(storage);
        AccountBE giro = new AccountBE("Girokonto");
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.AUGUST, 3, 9, 0, 0);
        giro.addTx(new TxBE(100f, "Eroeffnung", cal.getTime()));
        controller.getModel().asset_accounts.add(giro);
        controller.setCurrentFileName(FILE);
        controller.saveAccountsToInternal();
        saved = 0;
        controller.setSavefileWrittenListener(() -> saved++);
    }

    // region "a save file was written"

    @Test
    public void savingTheOpenFile_notifiesTheListener() throws Exception {
        controller.saveAccountsToInternal();
        assertEquals(1, saved);
    }

    @Test
    public void everyOperationThatSaves_notifiesTheListener() throws Exception {
        controller.createAssetAccount("Sparkonto");
        assertTrue(saved >= 1);
    }

    @Test
    public void savingTheSettingsFile_doesNotNotify() throws Exception {
        controller.saveAppSettings();
        assertEquals(0, saved);
    }

    @Test
    public void readingAFile_doesNotNotify() throws Exception {
        controller.readAccountsFromInternal(FILE);
        assertEquals(0, saved);
    }

    @Test
    public void aFailedWrite_doesNotNotify() {
        storage.failWrites = true;
        try {
            controller.saveAccountsToInternal();
        } catch (Exception expected) {
            // the failure itself is covered elsewhere
        }
        assertEquals(0, saved);
    }

    // endregion

    // region "a sync replaced the open file"

    @Test
    public void aReloadOfTheOpenFile_bumpsTheGeneration_andNotifiesListeners() throws Exception {
        int[] told = {0};
        controller.addSavefileReloadListener(() -> told[0]++);
        int before = controller.getSavefileReloadGeneration();

        controller.onSavefileReplacedBySync(FILE);

        assertEquals(before + 1, controller.getSavefileReloadGeneration());
        assertEquals(1, told[0]);
    }

    @Test
    public void aReplacedFileThatIsNotOpen_changesNothing() throws Exception {
        int[] told = {0};
        controller.addSavefileReloadListener(() -> told[0]++);
        int before = controller.getSavefileReloadGeneration();

        controller.onSavefileReplacedBySync("2026-07-User.jso");

        assertEquals(before, controller.getSavefileReloadGeneration());
        assertEquals(0, told[0]);
    }

    @Test
    public void aRemovedListener_isNotTold() throws Exception {
        int[] told = {0};
        Runnable listener = () -> told[0]++;
        controller.addSavefileReloadListener(listener);
        controller.removeSavefileReloadListener(listener);

        controller.onSavefileReplacedBySync(FILE);

        assertEquals(0, told[0]);
    }

    @Test
    public void theReload_picksUpWhatIsOnDisk() throws Exception {
        String changed = storage.read(FILE).replace("Eroeffnung", "Neu aus der Cloud");
        storage.write(FILE, changed);

        controller.onSavefileReplacedBySync(FILE);

        assertEquals("Neu aus der Cloud",
                controller.getModel().getAssetAccountByName("Girokonto").getTxList().get(0).getDescription());
    }

    // endregion

    @Test
    public void everyStorageOverTheSameKindOfDirectory_sharesOneLock() throws Exception {
        DirectoryStorage a = new DirectoryStorage(tempFolder.newFolder("a"));
        DirectoryStorage b = new DirectoryStorage(tempFolder.newFolder("b"));
        assertSame(a.lock(), b.lock());
    }
}
