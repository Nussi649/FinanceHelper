package com.privat.pitz.financehelper.jsoneditor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A realistic save file for the editor tests: assets, nested budgets, recurring orders, income. */
final class SampleJson {
    private SampleJson() { }

    /**
     * Structural equality of two parsed JSON values. JSONObject.similar is not used: the compile
     * classpath resolves JSONObject to the Android SDK stub, which does not have it.
     */
    static boolean sameJson(Object a, Object b) {
        if (a instanceof JSONObject && b instanceof JSONObject) {
            JSONObject x = (JSONObject) a;
            JSONObject y = (JSONObject) b;
            if (x.length() != y.length())
                return false;
            java.util.Iterator<String> keys = x.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!y.has(key) || !sameJson(x.opt(key), y.opt(key)))
                    return false;
            }
            return true;
        }
        if (a instanceof JSONArray && b instanceof JSONArray) {
            JSONArray x = (JSONArray) a;
            JSONArray y = (JSONArray) b;
            if (x.length() != y.length())
                return false;
            for (int i = 0; i < x.length(); i++)
                if (!sameJson(x.opt(i), y.opt(i)))
                    return false;
            return true;
        }
        if (a instanceof Number && b instanceof Number)
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        return a == null ? b == null : a.equals(b);
    }

    static JSONObject tx(String t, double a, String d) throws JSONException {
        return new JSONObject().put("t", t).put("a", a).put("d", d);
    }

    static JSONObject build() throws JSONException {
        JSONObject girokonto = new JSONObject().put("name", "Giro").put("active", true)
                .put("tx", new JSONArray()
                        .put(tx("2026-10-06 12:41:00", -3.68, "Spar"))
                        .put(tx("2026-10-07 08:00:00", 1234.5, "Gehalt {Oktober} [netto]"))
                        .put(tx("2026-10-08 09:15:00", -0.99, "Say \"hi\" \\ back\\slash / ü € ä\n tab\t")));
        JSONObject bar = new JSONObject().put("name", "Bar").put("active", false).put("tx", new JSONArray());

        JSONObject sub2 = new JSONObject().put("name", "Kino").put("active", true)
                .put("budget_year", "120.0").put("budget_cur", "10.0")
                .put("tx", new JSONArray().put(tx("2026-10-01 20:00:00", -12, "Ticket ]")));
        JSONObject sub = new JSONObject().put("name", "Freizeit").put("active", true)
                .put("tx", new JSONArray().put(tx("2026-10-02 10:00:00", -5.25, "Eis")))
                .put("sub_budgets", new JSONArray().put(sub2));
        JSONObject food = new JSONObject().put("name", "Essen").put("active", true).put("proj", false)
                .put("renew_period", 1).put("renew_next", "2026-11-01")
                .put("tx", new JSONArray().put(tx("2026-10-03 12:00:00", -20.1, "Pizza")))
                .put("sub_budgets", new JSONArray().put(sub));
        JSONObject other = new JSONObject().put("name", "Kino").put("active", true)
                .put("tx", new JSONArray());

        return new JSONObject()
                .put("assets", new JSONArray().put(girokonto).put(bar))
                .put("budgets", new JSONArray().put(food).put(other))
                .put("recur_tx", new JSONArray().put(new JSONObject()
                        .put("a", "5.0").put("d", "Abo").put("t", "2026-10-01 00:00:00")
                        .put("sender", "Giro").put("receiver", "Essen")))
                .put("income", new JSONArray().put(tx("2026-10-01 00:00:00", 2000, "Lohn")).put(JSONObject.NULL));
    }
}
