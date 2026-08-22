package Logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class TxBETest {

    private Date dateFor(int year, int month1based, int day) {
        Calendar cal = new GregorianCalendar();
        cal.clear();
        cal.set(year, month1based - 1, day, 10, 30, 0);
        return cal.getTime();
    }

    @Test
    public void constructorAndGetters_returnValuesAsGiven() {
        Date date = dateFor(2024, 6, 15);
        TxBE tx = new TxBE(123.45f, "Groceries", date);

        assertEquals(123.45f, tx.getAmount(), 0.0001f);
        assertEquals("Groceries", tx.getDescription());
        assertEquals(date, tx.getDate());
    }

    @Test
    public void setters_updateFields() {
        TxBE tx = new TxBE(1.0f, "Original", dateFor(2024, 1, 1));

        tx.setAmount(99.99f);
        tx.setDescription("Updated");
        Date newDate = dateFor(2025, 2, 2);
        tx.setDate(newDate);

        assertEquals(99.99f, tx.getAmount(), 0.0001f);
        assertEquals("Updated", tx.getDescription());
        assertEquals(newDate, tx.getDate());
    }

    @Test
    public void inPeriod_matchingYearAndMonth_returnsTrue() {
        TxBE tx = new TxBE(10f, "Test", dateFor(2024, 6, 15));
        assertTrue(tx.inPeriod("2024-06"));
    }

    @Test
    public void inPeriod_differentMonth_returnsFalse() {
        TxBE tx = new TxBE(10f, "Test", dateFor(2024, 6, 15));
        assertFalse(tx.inPeriod("2024-07"));
    }

    @Test
    public void inPeriod_differentYear_returnsFalse() {
        TxBE tx = new TxBE(10f, "Test", dateFor(2024, 6, 15));
        assertFalse(tx.inPeriod("2023-06"));
    }

    @Test
    public void inPeriod_invalidPeriodFormat_returnsFalse() {
        TxBE tx = new TxBE(10f, "Test", dateFor(2024, 6, 15));
        assertFalse(tx.inPeriod("not-a-period"));
        assertFalse(tx.inPeriod("2024-13"));
        assertFalse(tx.inPeriod("1999-01"));
    }
}
