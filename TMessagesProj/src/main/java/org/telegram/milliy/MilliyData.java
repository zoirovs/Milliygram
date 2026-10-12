package org.telegram.milliy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Milliygram server bilan ishlash (hududlar, kanallar, valyuta, ob-havo) va akkaunt bo'yicha sozlamalar. */
public class MilliyData {

    public static final String API = "https://api.milliygram.uz/api/v1/";

    private static final String KEY_DISTRICT = "milliy_district";
    private static final String KEY_DISTRICT_NAME = "milliy_district_name";
    private static final String KEY_OFFERED = "milliy_offered";

    // ---------- HTTP + kesh ----------

    private static SharedPreferences cachePrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("milliy_cache", Context.MODE_PRIVATE);
    }

    /** Keshdagi javob (bo'lsa). */
    public static String cached(String path) {
        return cachePrefs().getString("body_" + path, null);
    }

    /**
     * GET so'rov. maxAgeMs ichidagi kesh bo'lsa — tarmoqqa chiqmaydi.
     * Tarmoq ishlamasa, eski kesh qaytariladi; u ham bo'lmasa onError.
     */
    public static void get(String path, long maxAgeMs, Utilities.Callback<String> onResult, Runnable onError) {
        final SharedPreferences prefs = cachePrefs();
        final String old = prefs.getString("body_" + path, null);
        if (old != null && System.currentTimeMillis() - prefs.getLong("time_" + path, 0) < maxAgeMs) {
            onResult.run(old);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String body = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API + path).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "Milliygram/" + BuildVars.BUILD_VERSION_STRING);
                if (connection.getResponseCode() == 200) {
                    try (InputStream in = connection.getInputStream()) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                        body = out.toString("UTF-8");
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            if (body != null) {
                prefs.edit().putString("body_" + path, body).putLong("time_" + path, System.currentTimeMillis()).apply();
            }
            final String result = body != null ? body : old;
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null) {
                    onResult.run(result);
                } else if (onError != null) {
                    onError.run();
                }
            });
        });
    }

    public static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ---------- Akkaunt sozlamalari ----------

    public static String getDistrict(int account) {
        return MessagesController.getMainSettings(account).getString(KEY_DISTRICT, null);
    }

    public static String getDistrictName(int account) {
        return MessagesController.getMainSettings(account).getString(KEY_DISTRICT_NAME, null);
    }

    public static void setDistrict(int account, String code, String name) {
        MessagesController.getMainSettings(account).edit().putString(KEY_DISTRICT, code).putString(KEY_DISTRICT_NAME, name).apply();
    }

    /** Foydalanuvchiga allaqachon taklif qilingan kanallar — ularga qayta ulanmaydi. */
    public static Set<String> getOffered(int account) {
        return new HashSet<>(MessagesController.getMainSettings(account).getStringSet(KEY_OFFERED, new HashSet<>()));
    }

    public static void addOffered(int account, List<String> usernames) {
        Set<String> set = getOffered(account);
        for (String u : usernames) {
            set.add(u.toLowerCase());
        }
        MessagesController.getMainSettings(account).edit().putStringSet(KEY_OFFERED, set).apply();
    }

    // ---------- Kanallar ----------

    public static class Channel {
        public String username, title, scope;
        public int subscribers;
    }

    public static ArrayList<Channel> parseChannels(String json, Set<String> skip) {
        ArrayList<Channel> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Channel c = new Channel();
                c.username = o.getString("username");
                c.title = o.optString("title", c.username);
                c.scope = o.optString("scope");
                c.subscribers = o.optInt("subscribers");
                if (skip == null || !skip.contains(c.username.toLowerCase())) {
                    list.add(c);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return list;
    }

    /** Kanallarga ketma-ket (flood bo'lmasligi uchun oraliq bilan) obuna qiladi. */
    public static void joinChannels(int account, List<String> usernames, Runnable onDone) {
        joinNext(account, new ArrayList<>(usernames), 0, onDone);
    }

    private static void joinNext(int account, ArrayList<String> queue, int index, Runnable onDone) {
        if (index >= queue.size()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        controller.getUserNameResolver().resolve(queue.get(index), peerId -> {
            if (peerId != null && peerId < 0 && peerId != Long.MIN_VALUE) {
                TLRPC.Chat chat = controller.getChat(-peerId);
                if (chat != null && ChatObject.isNotInChat(chat)) {
                    controller.addUserToChat(chat.id, UserConfig.getInstance(account).getCurrentUser(), 0, null, null, null);
                }
            }
            AndroidUtilities.runOnUIThread(() -> joinNext(account, queue, index + 1, onDone), 1200);
        });
    }

    public static boolean isEmpty(String s) {
        return TextUtils.isEmpty(s);
    }
}
