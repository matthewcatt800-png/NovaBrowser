package com.matthew.novabrowser;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

public class DataStore {
    private static SharedPreferences sp;

    public static void init(Context c) {
        sp = c.getSharedPreferences("nova_browser", Context.MODE_PRIVATE);
    }

    public static JSONArray getArr(String key) {
        try {
            return new JSONArray(sp.getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void putArr(String key, JSONArray a) {
        sp.edit().putString(key, a.toString()).apply();
    }

    public static JSONArray bookmarks() {
        return getArr("bookmarks");
    }

    public static boolean isBookmarked(String url) {
        JSONArray a = bookmarks();
        for (int i = 0; i < a.length(); i++) {
            try {
                if (a.getJSONObject(i).optString("url", "").equals(url)) return true;
            } catch (Exception e) {
            }
        }
        return false;
    }

    public static void addBookmark(String title, String url) {
        try {
            JSONArray a = bookmarks();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optString("url", "").equals(url)) {
                    o.put("title", title);
                    putArr("bookmarks", a);
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("url", url);
            a.put(o);
            putArr("bookmarks", a);
        } catch (Exception e) {
        }
    }

    public static void removeBookmark(String url) {
        try {
            JSONArray a = bookmarks();
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("url", "").equals(url)) b.put(o);
            }
            putArr("bookmarks", b);
        } catch (Exception e) {
        }
    }

    public static JSONArray history() {
        return getArr("history");
    }

    public static void addHistory(String title, String url) {
        try {
            if (url == null || url.length() == 0 || url.startsWith("data:") || url.startsWith("about:")) return;
            JSONArray a = history();
            JSONArray b = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("title", title == null || title.length() == 0 ? url : title);
            o.put("url", url);
            o.put("time", System.currentTimeMillis());
            b.put(o);
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.getJSONObject(i);
                if (!e.optString("url", "").equals(url)) b.put(e);
            }
            while (b.length() > 300) b.remove(b.length() - 1);
            putArr("history", b);
        } catch (Exception e) {
        }
    }

    public static void removeHistory(String url) {
        try {
            JSONArray a = history();
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("url", "").equals(url)) b.put(o);
            }
            putArr("history", b);
        } catch (Exception e) {
        }
    }

    public static void clearHistory() {
        sp.edit().remove("history").apply();
    }

    public static String searchEngine() {
        return sp.getString("engine", "DuckDuckGo");
    }

    public static void setSearchEngine(String e) {
        sp.edit().putString("engine", e).apply();
    }

    public static String searchUrl(String q) {
        String enc;
        try {
            enc = URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            enc = q;
        }
        String eng = searchEngine();
        if ("Google".equals(eng)) return "https://www.google.com/search?q=" + enc;
        if ("Bing".equals(eng)) return "https://www.bing.com/search?q=" + enc;
        if ("Brave".equals(eng)) return "https://search.brave.com/search?q=" + enc;
        if ("Startpage".equals(eng)) return "https://www.startpage.com/sp/search?query=" + enc;
        return "https://duckduckgo.com/?q=" + enc;
    }

    public static String homepage() {
        return sp.getString("homepage", "https://duckduckgo.com");
    }

    public static void setHomepage(String u) {
        sp.edit().putString("homepage", u).apply();
    }

    public static JSONArray savedTabs() {
        return getArr("tabs");
    }

    public static void saveTabs(JSONArray a) {
        putArr("tabs", a);
    }
}
