package com.syslikoffnet.overglow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Сохранения: настройки, золото, мета-усиления, открытые герои, статистика.
 * Всё в SharedPreferences (JSON), полностью офлайн.
 */
public final class S {

    private S() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences("overglow", Context.MODE_PRIVATE);
    }

    // --- Настройки ---

    public static boolean sound(Context c) {
        return sp(c).getBoolean("sound", true);
    }

    public static void sound(Context c, boolean v) {
        sp(c).edit().putBoolean("sound", v).apply();
    }

    public static boolean music(Context c) {
        return sp(c).getBoolean("music", true);
    }

    public static void music(Context c, boolean v) {
        sp(c).edit().putBoolean("music", v).apply();
    }

    public static boolean vibrate(Context c) {
        return sp(c).getBoolean("vibrate", true);
    }

    public static void vibrate(Context c, boolean v) {
        sp(c).edit().putBoolean("vibrate", v).apply();
    }

    /** "ru" | "en" */
    public static String lang(Context c) {
        return sp(c).getString("lang", "ru");
    }

    public static void lang(Context c, String v) {
        sp(c).edit().putString("lang", v).apply();
        L.ru = "ru".equals(v);
    }

    /** Применить сохранённый язык к L. */
    public static void applyLang(Context c) {
        L.ru = "ru".equals(lang(c));
    }

    // --- Мета-прогресс ---

    public static int gold(Context c) {
        return sp(c).getInt("gold", 0);
    }

    public static void addGold(Context c, int delta) {
        sp(c).edit().putInt("gold", Math.max(0, gold(c) + delta)).apply();
    }

    /** Уровни мета-усилений: 0=HP 1=DMG 2=SPD 3=MAGNET 4=GREED (0..5). */
    public static int[] meta(Context c) {
        int[] out = new int[5];
        try {
            JSONArray a = new JSONArray(sp(c).getString("meta", "[]"));
            for (int i = 0; i < 5 && i < a.length(); i++) out[i] = a.optInt(i, 0);
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void setMeta(Context c, int[] m) {
        JSONArray a = new JSONArray();
        for (int v : m) a.put(v);
        sp(c).edit().putString("meta", a.toString()).apply();
    }

    /** Открытые герои: 0 Pulse и 1 Tesla открыты всегда. */
    public static boolean[] chars(Context c) {
        boolean[] out = {true, true, false, false};
        try {
            JSONArray a = new JSONArray(sp(c).getString("chars", "[]"));
            for (int i = 0; i < 4 && i < a.length(); i++) out[i] = a.optBoolean(i, i < 2);
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void unlockChar(Context c, int id) {
        boolean[] ch = chars(c);
        if (id >= 0 && id < 4) ch[id] = true;
        JSONArray a = new JSONArray();
        for (boolean b : ch) a.put(b);
        sp(c).edit().putString("chars", a.toString()).apply();
    }

    public static int selectedChar(Context c) {
        return sp(c).getInt("char", 0);
    }

    public static void selectedChar(Context c, int id) {
        sp(c).edit().putInt("char", id).apply();
    }

    // --- Статистика ---

    public static void addRun(Context c, int timeSec, int kills, int level, int gold) {
        SharedPreferences.Editor e = sp(c).edit();
        e.putInt("runs", sp(c).getInt("runs", 0) + 1);
        e.putInt("totalKills", sp(c).getInt("totalKills", 0) + kills);
        e.putInt("totalGold", sp(c).getInt("totalGold", 0) + gold);
        if (timeSec > sp(c).getInt("bestTime", 0)) e.putInt("bestTime", timeSec);
        if (level > sp(c).getInt("bestLvl", 0)) e.putInt("bestLvl", level);
        e.apply();
    }

    public static JSONObject stats(Context c) {
        try {
            return new JSONObject()
                    .put("runs", sp(c).getInt("runs", 0))
                    .put("totalKills", sp(c).getInt("totalKills", 0))
                    .put("totalGold", sp(c).getInt("totalGold", 0))
                    .put("bestTime", sp(c).getInt("bestTime", 0))
                    .put("bestLvl", sp(c).getInt("bestLvl", 0))
                    .put("gold", gold(c));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // --- Достижения (10) ---

    public static boolean[] achievements(Context c) {
        boolean[] out = new boolean[10];
        try {
            JSONArray a = new JSONArray(sp(c).getString("ach", "[]"));
            for (int i = 0; i < 10 && i < a.length(); i++) out[i] = a.optBoolean(i, false);
        } catch (JSONException ignored) {
        }
        return out;
    }

    /** Награды за ачивки (золото). */
    public static final int[] ACH_REWARD = {50, 75, 300, 150, 400, 200, 150, 100, 75, 100};

    /** Отметить ачивку. Возвращает true, если она новая. */
    public static boolean setAchievement(Context c, int id) {
        if (id < 0 || id > 9) return false;
        boolean[] ach = achievements(c);
        if (ach[id]) return false;
        ach[id] = true;
        JSONArray a = new JSONArray();
        for (boolean b : ach) a.put(b);
        sp(c).edit().putString("ach", a.toString()).apply();
        return true;
    }

    public static void reset(Context c) {
        sp(c).edit().clear().apply();
    }
}
