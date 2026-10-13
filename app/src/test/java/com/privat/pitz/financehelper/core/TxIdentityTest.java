package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import com.privat.pitz.financehelper.data.TxBE;

/** Transaction id ("i") and modification stamp ("m") added for cloud sync. */
public class TxIdentityTest {

    private static final Date BOOKED = new Date(1_786_000_000_000L);

    @Test
    public void newTransactions_getDistinctIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            ids.add(new TxBE(1f, "x", BOOKED).getId());
        }
        assertEquals(5000, ids.size());
    }

    @Test
    public void idAndModified_surviveRoundTrip() throws Exception {
        TxBE tx = new TxBE(-5f, "Kaffee", BOOKED, "abc123def456", new Date(1_786_100_000_000L));
        TxBE back = Util.parseJSON_Entry(Util.serialise_Entry(tx));
        assertEquals("abc123def456", back.getId());
        assertEquals(tx.getModified(), back.getModified());
    }

    @Test
    public void modifiedIsWrittenAsIso8601Utc() throws Exception {
        TxBE tx = new TxBE(1f, "x", BOOKED, "id1", new Date(0L));
        JSONObject json = Util.serialise_Entry(tx);
        assertEquals("1970-01-01T00:00:00Z", json.getString("m"));
    }

    @Test
    public void modifiedFormat_isIndependentOfDefaultTimeZone() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            assertEquals("1970-01-01T00:00:00Z", Util.formatModified(new Date(0L)));
            assertEquals(new Date(86_400_000L), Util.parseModified("1970-01-02T00:00:00Z"));
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    public void legacyEntry_withoutIdAndModified_getsFreshIdAndEpoch() throws Exception {
        JSONObject legacy = new JSONObject("{\"d\":\"Miete\",\"a\":\"-850.00\",\"t\":\"01.08.2026 09:00\"}");
        TxBE tx = Util.parseJSON_Entry(legacy);
        assertFalse(tx.getId().isEmpty());
        assertEquals(new Date(0L), tx.getModified());
    }

    @Test
    public void legacyEntry_loadIsNotReportedAsAProblem() throws Exception {
        JSONObject legacy = new JSONObject("{\"d\":\"Miete\",\"a\":\"-850.00\",\"t\":\"01.08.2026 09:00\"}");
        ParseReport report = new ParseReport();
        Util.parseJSON_Entry(legacy, report, "x");
        assertTrue(report.getNotes().toString(), report.isEmpty());
    }

    @Test
    public void fractionalSecondsFromAnotherWriter_areAccepted() {
        assertEquals(new Date(1_000L), Util.parseModified("1970-01-01T00:00:01.789Z"));
    }

    @Test
    public void unreadableModified_isTreatedAsNeverModified() {
        assertEquals(new Date(0L), Util.parseModified("gestern"));
        assertEquals(new Date(0L), Util.parseModified(null));
    }

    @Test
    public void editingAField_advancesModifiedButKeepsId() {
        TxBE tx = new TxBE(1f, "x", BOOKED, "keep", new Date(0L));
        tx.setAmount(2f);
        assertTrue(tx.getModified().after(new Date(0L)));
        assertEquals("keep", tx.getId());

        tx.setModified(new Date(0L));
        tx.setDescription("y");
        assertTrue(tx.getModified().after(new Date(0L)));

        tx.setModified(new Date(0L));
        tx.setDate(new Date());
        assertTrue(tx.getModified().after(new Date(0L)));
    }

    @Test
    public void modifiedIsWholeSeconds() {
        assertEquals(0L, new TxBE(1f, "x", BOOKED).getModified().getTime() % 1000L);
    }

    @Test
    public void ids_differBetweenTwoCopiesOfTheSameBooking() {
        assertNotEquals(new TxBE(1f, "x", BOOKED).getId(), new TxBE(1f, "x", BOOKED).getId());
    }
}
