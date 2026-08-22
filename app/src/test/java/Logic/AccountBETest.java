package Logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import Backend.Const;

public class AccountBETest {

    private AccountBE account;

    @Before
    public void setUp() {
        account = new AccountBE("Checking");
    }

    private Date dateFor(int year, int month1based, int day) {
        Calendar cal = new GregorianCalendar();
        cal.clear();
        cal.set(year, month1based - 1, day, 10, 30, 0);
        return cal.getTime();
    }

    @Test
    public void constructor_setsDefaults() {
        assertEquals("Checking", account.getName());
        assertTrue(account.getIsActive());
        assertTrue(account.getAutoRenew());
        assertTrue(account.getTxList().isEmpty());
    }

    @Test
    public void getSum_emptyList_returnsZero() {
        assertEquals(0.0f, account.getSum(), 0.0001f);
    }

    @Test
    public void getSum_sumsAllTransactionAmounts() {
        account.addTx(new TxBE(100.0f, "A", dateFor(2024, 1, 1)));
        account.addTx(new TxBE(-25.5f, "B", dateFor(2024, 1, 2)));
        account.addTx(new TxBE(10.25f, "C", dateFor(2024, 1, 3)));

        assertEquals(84.75f, account.getSum(), 0.0001f);
    }

    @Test
    public void getSumWithPeriod_filtersByPeriod() {
        account.addTx(new TxBE(100.0f, "Jan", dateFor(2024, 1, 15)));
        account.addTx(new TxBE(50.0f, "Feb", dateFor(2024, 2, 15)));
        account.addTx(new TxBE(25.0f, "Jan2", dateFor(2024, 1, 20)));

        assertEquals(125.0f, account.getSum("2024-01"), 0.0001f);
        assertEquals(50.0f, account.getSum("2024-02"), 0.0001f);
        assertEquals(0.0f, account.getSum("2024-03"), 0.0001f);
    }

    @Test
    public void getSumWithPeriod_invalidPeriod_returnsZero() {
        account.addTx(new TxBE(100.0f, "Jan", dateFor(2024, 1, 15)));
        assertEquals(0.0f, account.getSum("garbage"), 0.0001f);
    }

    @Test
    public void addTx_appendsToEnd() {
        TxBE t1 = new TxBE(1f, "one", dateFor(2024, 1, 1));
        TxBE t2 = new TxBE(2f, "two", dateFor(2024, 1, 2));
        account.addTx(t1);
        account.addTx(t2);

        assertEquals(2, account.getTxList().size());
        assertEquals(t1, account.getTxList().get(0));
        assertEquals(t2, account.getTxList().get(1));
    }

    @Test
    public void addTx_atPosition_insertsAtIndex() {
        TxBE t1 = new TxBE(1f, "one", dateFor(2024, 1, 1));
        TxBE t2 = new TxBE(2f, "two", dateFor(2024, 1, 2));
        TxBE t3 = new TxBE(3f, "three", dateFor(2024, 1, 3));
        account.addTx(t1);
        account.addTx(t2);
        account.addTx(1, t3);

        assertEquals(3, account.getTxList().size());
        assertEquals(t1, account.getTxList().get(0));
        assertEquals(t3, account.getTxList().get(1));
        assertEquals(t2, account.getTxList().get(2));
    }

    @Test
    public void removeTx_removesGivenEntry() {
        TxBE t1 = new TxBE(1f, "one", dateFor(2024, 1, 1));
        TxBE t2 = new TxBE(2f, "two", dateFor(2024, 1, 2));
        account.addTx(t1);
        account.addTx(t2);

        account.removeTx(t1);

        assertEquals(1, account.getTxList().size());
        assertEquals(t2, account.getTxList().get(0));
    }

    @Test
    public void dropLastTx_removesFinalEntry() {
        TxBE t1 = new TxBE(1f, "one", dateFor(2024, 1, 1));
        TxBE t2 = new TxBE(2f, "two", dateFor(2024, 1, 2));
        account.addTx(t1);
        account.addTx(t2);

        account.dropLastTx();

        assertEquals(1, account.getTxList().size());
        assertEquals(t1, account.getTxList().get(0));
    }

    @Test
    public void dropLastTx_onEmptyList_doesNotThrow() {
        account.dropLastTx();
        assertTrue(account.getTxList().isEmpty());
    }

    @Test
    public void getTxIndex_returnsCorrectIndex() {
        TxBE t1 = new TxBE(1f, "one", dateFor(2024, 1, 1));
        TxBE t2 = new TxBE(2f, "two", dateFor(2024, 1, 2));
        account.addTx(t1);
        account.addTx(t2);

        assertEquals(0, account.getTxIndex(t1));
        assertEquals(1, account.getTxIndex(t2));
    }

    @Test
    public void getTxIndex_notPresent_returnsMinusOne() {
        TxBE t1 = new TxBE(1f, "one", dateFor(2024, 1, 1));
        assertEquals(-1, account.getTxIndex(t1));
    }

    @Test
    public void sortTxByDate_sortsAscendingByDate() {
        TxBE late = new TxBE(1f, "late", dateFor(2024, 3, 1));
        TxBE early = new TxBE(2f, "early", dateFor(2024, 1, 1));
        TxBE mid = new TxBE(3f, "mid", dateFor(2024, 2, 1));
        account.addTx(late);
        account.addTx(early);
        account.addTx(mid);

        account.sortTxByDate();

        assertEquals(early, account.getTxList().get(0));
        assertEquals(mid, account.getTxList().get(1));
        assertEquals(late, account.getTxList().get(2));
    }

    @Test
    public void tryRenew_replacesTxListWithSingleOpeningEntryEqualToOldSum() {
        account.addTx(new TxBE(100.0f, "A", dateFor(2024, 1, 1)));
        account.addTx(new TxBE(-40.0f, "B", dateFor(2024, 1, 2)));

        account.tryRenew();

        assertEquals(1, account.getTxList().size());
        TxBE opening = account.getTxList().get(0);
        assertEquals(60.0f, opening.getAmount(), 0.0001f);
        assertEquals(Const.DESC_OPENING, opening.getDescription());
    }

    @Test
    public void tryRenew_onEmptyAccount_createsZeroOpeningEntry() {
        account.tryRenew();

        assertEquals(1, account.getTxList().size());
        assertEquals(0.0f, account.getTxList().get(0).getAmount(), 0.0001f);
    }

    @Test
    public void reset_emptiesTxList() {
        account.addTx(new TxBE(100.0f, "A", dateFor(2024, 1, 1)));
        account.reset();
        assertTrue(account.getTxList().isEmpty());
    }

    @Test
    public void setActive_and_setAutoRenew_updateFlags() {
        account.setActive(false);
        account.setAutoRenew(false);

        assertFalse(account.getIsActive());
        assertFalse(account.getAutoRenew());
    }
}
