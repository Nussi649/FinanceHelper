package com.privat.pitz.financehelper.jsoneditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TreeSet;

public class FoldLabelsTest {

    /** Hidden text of a block = everything after the opener. */
    private static String hidden(String block) {
        return block.substring(1);
    }

    @Test
    public void accountLabelNamesAndCountsEntries() {
        String acc = "{\n \"name\": \"Bargeld\",\n \"tx\": [\n {\"d\":\"a\"},\n {\"d\":\"b\"}\n ]\n}";
        assertEquals(" Bargeld · 2 Einträge }", FoldLabels.label(hidden(acc), null));
        assertEquals(" Bargeld · 1 Eintrag }",
                FoldLabels.label(hidden("{\"name\":\"Bargeld\",\n\"tx\":[{\"d\":\"a\"}]\n}"), null));
        assertEquals(" Bargeld · 0 Einträge }",
                FoldLabels.label(hidden("{\"name\":\"Bargeld\",\n\"tx\":[]\n}"), null));
        assertEquals(" Bargeld · 0 Einträge }", FoldLabels.label(hidden("{\n\"name\":\"Bargeld\"\n}"), null));
    }

    @Test
    public void budgetLabelAppendsSubBudgets() {
        String one = "{\"name\":\"B\",\"tx\":[],\n\"sub_budgets\":[{\"name\":\"S\",\"tx\":[]}]}";
        assertEquals(" B · 0 Einträge · 1 Unterbudget }", FoldLabels.label(hidden(one), null));
        String three = "{\"name\":\"B\",\"tx\":[],\n\"sub_budgets\":[{\"name\":\"S\"},{\"name\":\"T\"},{\"name\":\"U\"}]}";
        assertEquals(" B · 0 Einträge · 3 Unterbudgets }", FoldLabels.label(hidden(three), null));
        String none = "{\"name\":\"B\",\"tx\":[],\n\"sub_budgets\":[]}";
        assertEquals(" B · 0 Einträge }", FoldLabels.label(hidden(none), null));
    }

    @Test
    public void arrayLabelsByKey() {
        assertEquals(" … 10 Konten ]", FoldLabels.label(hidden(arr(10)), "assets"));
        assertEquals(" … 1 Konto ]", FoldLabels.label(hidden(arr(1)), "assets"));
        assertEquals(" … 8 Budgets ]", FoldLabels.label(hidden(arr(8)), "budgets"));
        assertEquals(" … 1 Budget ]", FoldLabels.label(hidden(arr(1)), "budgets"));
        assertEquals(" … 1 Unterbudget ]", FoldLabels.label(hidden(arr(1)), "sub_budgets"));
        assertEquals(" … 2 Unterbudgets ]", FoldLabels.label(hidden(arr(2)), "sub_budgets"));
        assertEquals(" … 8 Einträge ]", FoldLabels.label(hidden(arr(8)), "tx"));
        assertEquals(" … 1 Eintrag ]", FoldLabels.label(hidden(arr(1)), "tx"));
        assertEquals(" … 3 Einträge ]", FoldLabels.label(hidden(arr(3)), "income"));
        assertEquals(" … 7 Daueraufträge ]", FoldLabels.label(hidden(arr(7)), "recur_tx"));
        assertEquals(" … 1 Dauerauftrag ]", FoldLabels.label(hidden(arr(1)), "recur_tx"));
        assertEquals(" … 0 Einträge ]", FoldLabels.label(hidden("[\n]"), "tx"));
    }

    private static String arr(int n) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < n; i++)
            sb.append(i > 0 ? ",\n" : "").append("{\"d\":\"x,]\",\"a\":\"1\"}");
        return sb.append("\n]").toString();
    }

    @Test
    public void spreadTransactionLabelShowsDescriptionAndAmount() {
        String tx = "{\n \"d\": \"Spar\",\n \"a\": \"-3.68\",\n \"t\": \"2026-10-06 12:41:00\"\n}";
        assertEquals(" Spar · -3.68 }", FoldLabels.label(hidden(tx), null));
        String num = "{\n \"d\": \"Spar\",\n \"a\": -3.68\n}";
        assertEquals(" Spar · -3.68 }", FoldLabels.label(hidden(num), null));
    }

    @Test
    public void fallsBackToGenericLabel() {
        assertEquals(" … 2 ]", FoldLabels.label(hidden("[\n1,\n2\n]"), "other"));
        assertEquals(" … 2 ]", FoldLabels.label(hidden("[\n1,\n2\n]"), null));
        assertEquals(" … 2 }", FoldLabels.label(hidden("{\n\"x\":1,\n\"y\":2\n}"), null));
        assertEquals(" … 1 ]", FoldLabels.label(hidden("[\n{\"d\": ,\n2\n]"), "tx"));
        assertEquals(" … 2 }", FoldLabels.label(hidden("{\n\"name\": \"A\",\n\"tx\": [oops\n}"), null));
        assertEquals(" … 1 }", FoldLabels.label(hidden("{\n\"d\": \"only description\"\n}"), null));
        assertEquals(" … 1 }", FoldLabels.label(hidden("{\n\"name\": 5\n}"), null));
        assertEquals(" … 0 ?", FoldLabels.label("", null));
        assertEquals(" … 1 ]", FoldLabels.label("\"unterminated\n]", "tx"));
    }

    @Test
    public void namesAreKeptOnOneLine() {
        String acc = "{\"name\":\"A\\nB\\u0009C\",\"tx\":[]\n}";
        assertEquals(" A B C · 0 Einträge }", FoldLabels.label(hidden(acc), null));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 100; i++)
            longName.append('x');
        String label = FoldLabels.label(hidden("{\"name\":\"" + longName + "\",\"tx\":[]\n}"), null);
        assertTrue(label, label.length() < 80 && label.contains("…"));
    }

    @Test
    public void renderedDisplayUsesTypedLabelsAndRoundTrips() throws Exception {
        String full = JsonCompactPrinter.format(SampleJson.build().toString());
        TreeSet<Integer> all = new TreeSet<>();
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if (r.isMultiline())
                all.add(r.openOffset);
        FoldMath.Rendered rendered = FoldMath.render(full, all);
        assertEquals(1, rendered.placeholders.size()); // root folded, everything else dormant
        assertEquals(full, FoldMath.extract(rendered.display, rendered.placeholders, 0,
                rendered.display.length()).full);

        TreeSet<Integer> accounts = new TreeSet<>();
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if (r.isMultiline() && r.name != null && r.depth == 2 && "assets".equals(r.parent.key))
                accounts.add(r.openOffset);
        rendered = FoldMath.render(full, accounts);
        assertTrue(rendered.display, rendered.display.contains("{ Giro · 3 Einträge }"));
        assertTrue(rendered.display, rendered.display.contains("{ Bar · 0 Einträge }"));
        FoldMath.Extracted back = FoldMath.extract(rendered.display, rendered.placeholders, 0,
                rendered.display.length());
        assertEquals(full, back.full);
        assertEquals(accounts, back.folded);

        TreeSet<Integer> arrays = new TreeSet<>();
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if (r.isMultiline() && r.depth == 1)
                arrays.add(r.openOffset);
        rendered = FoldMath.render(full, arrays);
        assertTrue(rendered.display, rendered.display.contains("\"assets\": [ … 2 Konten ],"));
        assertEquals(full, FoldMath.extract(rendered.display, rendered.placeholders, 0,
                rendered.display.length()).full);
    }

    @Test
    public void labelTextLookingLikeDocumentContentCannotConfuseExtract() {
        // names and descriptions that mimic a placeholder label
        String full = "{\n  \"assets\": [\n    {\n      \"name\": \" … 9 }\",\n      \"tx\": [\n"
                + "        {\"d\": \" … 1 }\", \"a\": \"1\", \"t\": \"x\"}\n      ]\n    }\n  ]\n}";
        TreeSet<Integer> all = new TreeSet<>();
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if (r.isMultiline() && r.depth >= 1)
                all.add(r.openOffset);
        FoldMath.Rendered rendered = FoldMath.render(full, all);
        assertEquals(full, FoldMath.extract(rendered.display, rendered.placeholders, 0,
                rendered.display.length()).full);
        assertFalse(rendered.display.contains("\"tx\""));
    }
}
