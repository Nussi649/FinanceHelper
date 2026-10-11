package com.privat.pitz.financehelper.jsoneditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class JsonCompactPrinterTest {

    @Test
    public void roundTripOfRealisticSampleIsIdentical() throws Exception {
        JSONObject original = SampleJson.build();
        String printed = JsonCompactPrinter.format(original.toString(4));
        JSONObject reparsed = new JSONObject(printed);
        assertTrue(printed, SampleJson.sameJson(original, reparsed));
    }

    @Test
    public void everyTransactionIsOneLine() throws Exception {
        String printed = JsonCompactPrinter.format(SampleJson.build().toString());
        // key order is not asserted: the JVM test org.json is HashMap based, Android's keeps insertion order
        boolean sparLine = false;
        for (String line : printed.split("\n")) {
            if (line.contains("\"d\": \"Spar\"") && line.contains("\"a\": -3.68") && line.contains("2026-10-06 12:41:00"))
                sparLine = true;
            String trimmed = line.trim();
            // a line that opens an object with a quoted key must hold all of it
            if (trimmed.startsWith("{\""))
                assertTrue(line, trimmed.endsWith("}") || trimmed.endsWith("},"));
        }
        assertTrue(printed, sparLine);
    }

    @Test
    public void nestedContainersAreIndented() throws Exception {
        String printed = JsonCompactPrinter.format("{\"a\": [{\"b\": 1}, [1, 2]]}");
        // root children flush left, one indent per level below that
        assertEquals("{\n\"a\": [\n  {\"b\": 1},\n  [1, 2]\n]\n}", printed);
    }

    @Test
    public void emptyContainersPrintInlineButKeepTheirParentMultiLine() throws Exception {
        // an account without entries must still get its own lines (and a fold marker)
        assertEquals("{\n\"x\": []\n}", JsonCompactPrinter.format("{\"x\":[]}"));
        assertEquals("[\n{},\n[]\n]", JsonCompactPrinter.format("[{},[]]"));
    }

    @Test
    public void primitivesRoundTrip() throws Exception {
        String src = "{\"i\":-7,\"d\":-3.68,\"big\":12345678901,\"b\":true,\"n\":null,\"s\":\"\\u00e4\\n\\\"\"}";
        JSONObject original = new JSONObject(src);
        JSONObject reparsed = new JSONObject(JsonCompactPrinter.format(src));
        assertTrue(SampleJson.sameJson(original, reparsed));
        assertTrue(reparsed.isNull("n"));
        assertEquals(-3.68, reparsed.getDouble("d"), 0.0);
    }

    @Test
    public void outputHasNoTrailingWhitespace() throws Exception {
        String printed = JsonCompactPrinter.format(SampleJson.build().toString());
        for (String line : printed.split("\n"))
            assertEquals(line, line.replaceAll("\\s+$", ""), line);
        assertFalse(printed.endsWith("\n"));
    }
}
