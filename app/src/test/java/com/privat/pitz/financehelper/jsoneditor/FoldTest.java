package com.privat.pitz.financehelper.jsoneditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class FoldTest {

    private static String sample() throws Exception {
        return JsonCompactPrinter.format(SampleJson.build().toString());
    }

    // region JsonFoldRegions
    @Test
    public void bracketsInsideStringsAreIgnored() {
        String text = "{\n  \"d\": \"} ] { [ \\\" }\",\n  \"x\": [\n    1,\n    2\n  ]\n}";
        JsonFoldRegions.Scan scan = JsonFoldRegions.scan(text);
        assertEquals(2, scan.regions.size());
        JsonFoldRegions.Region root = scan.regions.get(0);
        assertEquals(0, root.openOffset);
        assertEquals(text.length() - 1, root.closeOffset);
        JsonFoldRegions.Region arr = scan.regions.get(1);
        assertTrue(arr.array);
        assertEquals("x", arr.key);
        assertEquals(1, arr.depth);
        assertEquals(2, arr.startLine);
        assertEquals(5, arr.endLine);
        assertTrue(arr.isMultiline());
    }

    @Test
    public void escapedBackslashBeforeClosingQuoteEndsTheString() {
        // the string is  a\\  (backslash, backslash) so the quote after it really closes it
        String text = "{\n  \"k\": \"a\\\\\",\n  \"y\": [\n    1\n  ]\n}";
        JsonFoldRegions.Scan scan = JsonFoldRegions.scan(text);
        assertEquals(2, scan.regions.size());
    }

    @Test
    public void unbalancedAndMismatchedBracketsYieldNoRegion() {
        assertTrue(JsonFoldRegions.scan("{ [ }").regions.isEmpty());
        assertTrue(JsonFoldRegions.scan("}}]]").regions.isEmpty());
        assertTrue(JsonFoldRegions.scan("{\n\"a\": [\n1\n").regions.isEmpty());
    }

    @Test
    public void namesKeysAndDepthsAreRecorded() throws Exception {
        String text = sample();
        JsonFoldRegions.Scan scan = JsonFoldRegions.scan(text);
        boolean sawGiro = false, sawKinoSub = false;
        for (JsonFoldRegions.Region r : scan.regions) {
            if ("Giro".equals(r.name)) {
                sawGiro = true;
                assertEquals(2, r.depth);
                assertEquals("assets", r.parent.key);
            }
            if ("Kino".equals(r.name) && r.depth == 6)
                sawKinoSub = true;
        }
        assertTrue(sawGiro);
        assertTrue("sub-sub-budget at depth 6", sawKinoSub);
    }

    @Test
    public void lineOfMapsOffsets() {
        JsonFoldRegions.Scan scan = JsonFoldRegions.scan("a\nbb\n\nccc");
        assertEquals(0, scan.lineOf(0));
        assertEquals(1, scan.lineOf(2));
        assertEquals(2, scan.lineOf(5));
        assertEquals(3, scan.lineOf(8));
    }
    // endregion

    // region render / extract
    @Test
    public void oneTapFoldsAWholeTopLevelArray() throws Exception {
        String full = sample();
        JsonFoldRegions.Region assets = null;
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if ("assets".equals(r.key))
                assets = r;
        assertNotNull(assets);
        FoldMath.Rendered rendered = FoldMath.render(full, new TreeSet<>(Arrays.asList(assets.openOffset)));
        assertEquals(1, rendered.placeholders.size());
        assertTrue(rendered.display, rendered.display.contains("\"assets\": [ … 2 ]"));
        assertFalse(rendered.display.contains("Spar"));
        assertTrue(rendered.display.contains("\"budgets\""));
    }

    @Test
    public void extractRestoresExactlyWhateverIsFolded() throws Exception {
        String full = sample();
        List<JsonFoldRegions.Region> regions = JsonFoldRegions.scan(full).regions;
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            TreeSet<Integer> folded = new TreeSet<>();
            for (JsonFoldRegions.Region r : regions)
                if (r.isMultiline() && random.nextInt(3) == 0)
                    folded.add(r.openOffset);
            FoldMath.Rendered rendered = FoldMath.render(full, folded);
            FoldMath.Extracted ex = FoldMath.extract(rendered.display, rendered.placeholders,
                    0, rendered.display.length());
            assertEquals(full, ex.full);
            // the fold state survives the round trip, nested (dormant) folds included
            assertEquals(folded, ex.folded);
            // and rendering the recovered state reproduces the same display text
            assertEquals(rendered.display, FoldMath.render(ex.full, ex.folded).display);
        }
    }

    @Test
    public void foldingEverythingAndEditingOutsideStillRestoresFully() throws Exception {
        String full = sample();
        TreeSet<Integer> all = new TreeSet<>();
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if (r.isMultiline())
                all.add(r.openOffset);
        FoldMath.Rendered rendered = FoldMath.render(full, all);
        assertEquals(1, rendered.placeholders.size()); // everything hides inside the root block
        // an edit after the placeholder shifts display offsets but not the hidden text
        FoldMath.Placeholder p = rendered.placeholders.get(0);
        String edited = rendered.display + "\n// trailing";
        FoldMath.Extracted ex = FoldMath.extract(edited, rendered.placeholders, 0, edited.length());
        assertEquals(full + "\n// trailing", ex.full);
        assertEquals(p.hidden.length(), p.fullEnd - p.fullStart);
    }

    @Test
    public void unfoldingAnOuterBlockBringsBackInnerFoldsAsPlaceholders() throws Exception {
        String full = sample();
        JsonFoldRegions.Region food = null, sub = null;
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions) {
            if ("Essen".equals(r.name)) food = r;
            if ("Freizeit".equals(r.name)) sub = r;
        }
        assertNotNull(food);
        assertNotNull(sub);
        TreeSet<Integer> both = new TreeSet<>(Arrays.asList(food.openOffset, sub.openOffset));
        FoldMath.Rendered folded = FoldMath.render(full, both);
        assertEquals(1, folded.placeholders.size());

        // unfold the outer block the way the view does: re-render just its hidden text
        FoldMath.Placeholder outer = folded.placeholders.get(0);
        TreeSet<Integer> dormant = new TreeSet<>();
        for (int d : outer.dormant)
            dormant.add(d);
        FoldMath.Rendered inner = FoldMath.render(outer.hidden, dormant);
        assertEquals("inner fold is folded again", 1, inner.placeholders.size());
        assertFalse(inner.display.contains("Eis"));
        assertTrue(inner.display.contains("Pizza"));
    }

    @Test
    public void foldedOffsetsThatAreNotMultilineOpenersAreIgnored() throws Exception {
        String full = sample();
        FoldMath.Rendered rendered = FoldMath.render(full, new TreeSet<>(Arrays.asList(0 + 5, 7)));
        assertEquals(full, rendered.display);
        assertTrue(rendered.placeholders.isEmpty());
    }

    @Test
    public void offsetMappingRoundTripsForVisibleText() throws Exception {
        String full = sample();
        int giro = full.indexOf("\"Giro\"");
        int target = full.indexOf("\"budgets\"");
        JsonFoldRegions.Region assets = null;
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if ("assets".equals(r.key))
                assets = r;
        FoldMath.Rendered rendered = FoldMath.render(full, new TreeSet<>(Arrays.asList(assets.openOffset)));
        int display = rendered.fullToDisplay(target);
        assertEquals("\"budgets\"", rendered.display.substring(display, display + 9));
        assertEquals(target, rendered.displayToFull(display));
        // text inside the fold has no visible position: it maps to the placeholder
        assertEquals(rendered.placeholders.get(0).displayStart, rendered.fullToDisplay(giro));
        FoldMath.Extracted ex = FoldMath.extract(rendered.display, rendered.placeholders, 0, rendered.display.length());
        assertEquals(target, ex.displayToFull(display));
        assertEquals(display, ex.fullToDisplay(target));
    }

    @Test
    public void rangeExtractReportsRelativeFoldState() throws Exception {
        String full = sample();
        JsonFoldRegions.Region sub = null;
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if ("Freizeit".equals(r.name)) sub = r;
        FoldMath.Rendered rendered = FoldMath.render(full, new TreeSet<>(Arrays.asList(sub.openOffset)));
        // fold the enclosing "Essen" block by extracting its inside from the display text
        JsonFoldRegions.Scan scan = JsonFoldRegions.scan(rendered.display);
        JsonFoldRegions.Region essen = null;
        for (JsonFoldRegions.Region r : scan.regions)
            if ("Essen".equals(r.name))
                essen = r;
        assertNotNull(essen);
        FoldMath.Extracted ex = FoldMath.extract(rendered.display, rendered.placeholders,
                essen.openOffset + 1, essen.closeOffset + 1);
        assertTrue(ex.full.endsWith("}"));
        assertTrue(ex.full.contains("Eis"));
        assertEquals(1, ex.folded.size());
        int rel = ex.folded.first();
        assertEquals('{', ex.full.charAt(rel));
    }

    @Test
    public void unfoldOverlappingRevealsOnlyWhatIsNeeded() throws Exception {
        String full = sample();
        TreeSet<Integer> all = new TreeSet<>();
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions)
            if (r.depth == 2 && r.isMultiline())
                all.add(r.openOffset);
        int hit = full.indexOf("Pizza");
        TreeSet<Integer> remaining = FoldMath.unfoldOverlapping(full, all, hit, hit + 5);
        assertEquals(all.size() - 1, remaining.size());
        FoldMath.Rendered rendered = FoldMath.render(full, remaining);
        assertTrue(rendered.display.contains("Pizza"));
        assertFalse(rendered.display.contains("Gehalt"));
    }

    @Test
    public void foldAtDepthTwoFoldsEachAccountAndNothingElse() throws Exception {
        String full = sample();
        TreeSet<Integer> folded = FoldMath.foldAtDepth(full, new TreeSet<Integer>(), 2);
        FoldMath.Rendered rendered = FoldMath.render(full, folded);
        // every account is multi-line, Bar included even though its "tx" is empty
        assertFalse(rendered.display.contains("Spar"));
        assertTrue(rendered.display.contains("\"assets\": ["));
        assertTrue(rendered.display.contains("\"budgets\": ["));
        assertTrue(rendered.display.contains("\"income\""));
        assertTrue(rendered.display.contains("Lohn"));
    }

    @Test
    public void placeholderTextCountsDirectEntries() {
        assertEquals(" … 3 ]", FoldMath.placeholderText("1, [1,2], \"a,b]\"]"));
        assertEquals(" … 0 }", FoldMath.placeholderText("\n}"));
        assertEquals(2, FoldMath.countEntries("{\"a\": [1, 2]}, {\"b\": 1}"));
    }
    // endregion

    // region AccountFocus
    @Test
    public void focusKeepsOnlyTheTargetAccountAndItsAncestorsOpen() throws Exception {
        String full = sample();
        AccountFocus.Plan plan = AccountFocus.plan(full, true, Arrays.asList("Essen", "Freizeit"));
        assertNotNull(plan);
        assertEquals("Freizeit", plan.target.name);
        FoldMath.Rendered rendered = FoldMath.render(full, plan.fold);
        assertTrue(rendered.display.contains("Eis"));      // target's own tx
        assertTrue(rendered.display.contains("Pizza"));    // ancestor's own tx
        assertFalse(rendered.display.contains("Spar"));    // asset accounts folded
        assertFalse(rendered.display.contains("Ticket"));  // the target's own sub-budget folded
    }

    @Test
    public void focusDistinguishesSameNamedAccountsByPath() throws Exception {
        String full = sample();
        AccountFocus.Plan root = AccountFocus.plan(full, true, Arrays.asList("Kino"));
        AccountFocus.Plan nested = AccountFocus.plan(full, true, Arrays.asList("Essen", "Freizeit", "Kino"));
        assertNotNull(root);
        assertNotNull(nested);
        assertEquals(2, root.target.depth);
        assertEquals(6, nested.target.depth);
    }

    @Test
    public void focusSeparatesAssetsFromBudgets() throws Exception {
        String full = sample();
        assertNotNull(AccountFocus.plan(full, false, Arrays.asList("Giro")));
        assertNull(AccountFocus.plan(full, true, Arrays.asList("Giro")));
        assertNull(AccountFocus.plan(full, false, Arrays.asList("Essen")));
        assertNull(AccountFocus.plan(full, true, Arrays.asList("Gibt es nicht")));
    }
    // endregion

    // region TextSearch
    @Test
    public void searchIsCaseInsensitiveAndFindsAllMatches() {
        int[] starts = TextSearch.findAll("Spar spar SPAR xsparx", "spar");
        assertTrue(Arrays.equals(new int[] {0, 5, 10, 16}, starts));
        assertEquals(0, TextSearch.findAll("abc", "").length);
    }

    @Test
    public void searchPickWrapsAround() {
        int[] s = {3, 10, 20};
        assertEquals(1, TextSearch.pick(s, 3, true, false));
        assertEquals(0, TextSearch.pick(s, 3, true, true));
        assertEquals(0, TextSearch.pick(s, 20, true, false)); // wraps
        assertEquals(1, TextSearch.pick(s, 20, false, false));
        assertEquals(2, TextSearch.pick(s, 3, false, false)); // wraps
        assertEquals(-1, TextSearch.pick(new int[0], 0, true, true));
    }
    // endregion
}
