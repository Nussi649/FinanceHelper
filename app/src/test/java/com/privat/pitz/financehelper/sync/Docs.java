package com.privat.pitz.financehelper.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Tiny builders for save-file documents, so merge tests read as what they are about. */
public final class Docs {
    private Docs() {
    }

    public static final String T = "01.08.2026 09:00";

    /** A transaction. {@code id} / {@code m} may be null to leave them out, as a foreign writer might. */
    public static JSONObject tx(String id, String desc, String amount, String m) {
        try {
            JSONObject o = new JSONObject();
            o.put("d", desc);
            o.put("a", amount);
            o.put("t", T);
            if (id != null) o.put("i", id);
            if (m != null) o.put("m", m);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JSONObject account(String name, JSONObject... txs) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("active", true);
            o.put("renew_auto", true);
            o.put("tx", array(txs));
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JSONObject withSub(JSONObject budget, JSONObject... subs) {
        return set(budget, "sub_budgets", array(subs));
    }

    public static JSONObject doc(JSONObject... assets) {
        try {
            JSONObject o = new JSONObject();
            o.put("assets", array(assets));
            o.put("budgets", new JSONArray());
            o.put("recur_tx", new JSONArray());
            o.put("income", new JSONArray());
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JSONObject set(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JSONArray array(JSONObject... items) {
        JSONArray a = new JSONArray();
        for (JSONObject i : items) a.put(i);
        return a;
    }

    /** The tx array of the named top-level asset account, or null if there is no such account. */
    public static JSONArray txOf(JSONObject doc, String account) {
        JSONArray assets = doc.optJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            if (account.equals(assets.optJSONObject(i).optString("name"))) {
                return assets.optJSONObject(i).optJSONArray("tx");
            }
        }
        return null;
    }

    public static JSONObject account(JSONObject doc, String name) {
        JSONArray assets = doc.optJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            if (name.equals(assets.optJSONObject(i).optString("name"))) return assets.optJSONObject(i);
        }
        return null;
    }

    public static JSONObject find(JSONArray txs, String id) {
        for (int i = 0; i < txs.length(); i++) {
            if (id.equals(txs.optJSONObject(i).optString("i"))) return txs.optJSONObject(i);
        }
        return null;
    }
}
