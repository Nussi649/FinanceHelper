package com.privat.pitz.financehelper.jsoneditor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Works out which blocks to fold so that exactly one account (and the path leading to it) stays
 * open. Used by the per-account "JSON bearbeiten" entry points.
 *
 * <p>An "account block" is an object that is an element of the top-level "assets" or "budgets"
 * array, or of a "sub_budgets" array. The JSON key names mirror Const.JSON_TAG_*; they are
 * repeated here so this class stays free of anything Android-flavoured and testable on the JVM.
 */
public final class AccountFocus {
    private static final String KEY_ASSETS = "assets";
    private static final String KEY_BUDGETS = "budgets";
    private static final String KEY_SUB_BUDGETS = "sub_budgets";

    private AccountFocus() { }

    public static final class Plan {
        /** The account block to show. */
        public final JsonFoldRegions.Region target;
        /** Openers (offsets in the scanned text) of every other multi-line account block. */
        public final TreeSet<Integer> fold;

        Plan(JsonFoldRegions.Region target, TreeSet<Integer> fold) {
            this.target = target;
            this.fold = fold;
        }
    }

    /**
     * @param path account names from the top: one name for an asset account or a root budget,
     *             more for a sub-budget (root first)
     * @return null if no such account exists in the text
     */
    public static Plan plan(String text, boolean budget, List<String> path) {
        if (path.isEmpty())
            return null;
        List<JsonFoldRegions.Region> regions = JsonFoldRegions.scan(text).regions;

        JsonFoldRegions.Region current = null;
        for (int level = 0; level < path.size(); level++) {
            JsonFoldRegions.Region found = null;
            for (JsonFoldRegions.Region r : regions) {
                if (r.array || r.parent == null || !path.get(level).equals(r.name))
                    continue;
                JsonFoldRegions.Region list = r.parent;
                boolean matches;
                if (level == 0)
                    matches = list.array && list.depth == 1 && (budget ? KEY_BUDGETS : KEY_ASSETS).equals(list.key);
                else
                    matches = list.array && KEY_SUB_BUDGETS.equals(list.key) && list.parent == current;
                if (matches) {
                    found = r;
                    break;
                }
            }
            if (found == null)
                return null;
            current = found;
        }

        Set<JsonFoldRegions.Region> keepOpen = new HashSet<>();
        for (JsonFoldRegions.Region r = current; r != null; r = r.parent)
            keepOpen.add(r);

        TreeSet<Integer> fold = new TreeSet<>();
        for (JsonFoldRegions.Region r : regions) {
            if (isAccountBlock(r) && r.isMultiline() && !keepOpen.contains(r))
                fold.add(r.openOffset);
        }
        return new Plan(current, fold);
    }

    private static boolean isAccountBlock(JsonFoldRegions.Region r) {
        if (r.array || r.parent == null || !r.parent.array)
            return false;
        String key = r.parent.key;
        if (KEY_SUB_BUDGETS.equals(key))
            return true;
        return r.parent.depth == 1 && (KEY_ASSETS.equals(key) || KEY_BUDGETS.equals(key));
    }
}
