package com.privat.pitz.financehelper.jsoneditor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Iterator;

/**
 * Pretty-prints JSON for the save file editor: nested structure is indented, but every object or
 * array whose values are all primitives goes on a single line.
 *
 * <p>WHY: {@code JSONObject.toString(4)} puts every field of every transaction on its own line,
 * which turns a few hundred transactions into thousands of lines of scrolling. With this printer a
 * transaction is one line, {@code {"t": "2026-10-06 12:41:00", "a": -3.68, "d": "Spar"}}, so a
 * transaction list reads like a table.
 *
 * <p>Pure Java apart from org.json, so it runs in JVM unit tests. Key order is whatever the
 * {@code keys()} iterator yields, which is insertion order for both Android's and the test
 * org.json. Strings and numbers are written with the same org.json routines its own toString uses,
 * so the output parses back to an identical value.
 */
public final class JsonCompactPrinter {
    private static final String INDENT = "  ";

    private JsonCompactPrinter() { }

    public static String format(String json) throws JSONException {
        return format(new JSONTokener(json).nextValue());
    }

    public static String format(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int level) {
        if (value instanceof JSONObject) {
            writeObject(sb, (JSONObject) value, level);
        } else if (value instanceof JSONArray) {
            writeArray(sb, (JSONArray) value, level);
        } else {
            writePrimitive(sb, value);
        }
    }

    private static void writeObject(StringBuilder sb, JSONObject obj, int level) {
        if (obj.length() == 0) {
            sb.append("{}");
            return;
        }
        boolean flat = isFlat(obj);
        sb.append('{');
        Iterator<String> keys = obj.keys();
        boolean first = true;
        while (keys.hasNext()) {
            String key = keys.next();
            if (!first)
                sb.append(flat ? ", " : ",");
            first = false;
            if (!flat)
                newline(sb, level + 1);
            sb.append(JSONObject.quote(key)).append(": ");
            write(sb, obj.opt(key), level + 1);
        }
        if (!flat)
            newline(sb, level);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, JSONArray arr, int level) {
        if (arr.length() == 0) {
            sb.append("[]");
            return;
        }
        boolean flat = isFlat(arr);
        sb.append('[');
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0)
                sb.append(flat ? ", " : ",");
            if (!flat)
                newline(sb, level + 1);
            write(sb, arr.opt(i), level + 1);
        }
        if (!flat)
            newline(sb, level);
        sb.append(']');
    }

    private static void writePrimitive(StringBuilder sb, Object value) {
        if (value == null || JSONObject.NULL.equals(value))
            sb.append("null");
        else if (value instanceof Number)
            sb.append(numberToString((Number) value));
        else if (value instanceof Boolean)
            sb.append(value);
        else
            sb.append(JSONObject.quote(value.toString()));
    }

    private static String numberToString(Number n) {
        try {
            return JSONObject.numberToString(n);
        } catch (JSONException e) {
            // only NaN/Infinity, which a parsed document cannot contain; never lose the value
            return n.toString();
        }
    }

    /** True if no value is a non-empty container; empty ones print as {} / [] and count as flat. */
    private static boolean isFlat(JSONObject obj) {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            if (isNonEmptyContainer(obj.opt(keys.next())))
                return false;
        }
        return true;
    }

    private static boolean isFlat(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            if (isNonEmptyContainer(arr.opt(i)))
                return false;
        }
        return true;
    }

    private static boolean isNonEmptyContainer(Object v) {
        if (v instanceof JSONObject)
            return ((JSONObject) v).length() > 0;
        if (v instanceof JSONArray)
            return ((JSONArray) v).length() > 0;
        return false;
    }

    private static void newline(StringBuilder sb, int level) {
        sb.append('\n');
        for (int i = 0; i < level; i++)
            sb.append(INDENT);
    }
}
