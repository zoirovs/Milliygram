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

    public static final String API = "https://platforma.cyberhimoya.uz/api/v1/";

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
        MilliyService.districtChanged(account);
    }

    /** Hudud kanali sifatida allaqachon ishlangan kanallar — ularga qayta qo'shilmaydi. */
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

    // ---------- Hudud kanallariga avtomatik qo'shilish ----------

    private static final String KEY_JOIN_SYNC = "milliy_join_sync";
    private static final String KEY_JOIN_PENDING = "milliy_join_pending";
    private static final Set<Integer> joining = new HashSet<>();

    /** Hudud tanlanganda chaqiriladi: yangi hudud kanallariga fonda qo'shiladi, tasdiq so'ralmaydi. */
    public static void joinDistrictChannels(int account) {
        MessagesController.getMainSettings(account).edit().putBoolean(KEY_JOIN_SYNC, true).apply();
        resumeDistrictJoin(account);
    }

    /**
     * Tugallanmagan ishni davom ettiradi (tarmoq uzilgan yoki ilova yopilgan bo'lsa).
     * Ish qolmagan bo'lsa tarmoqqa chiqmaydi. Bir marta qo'shilgan kanalga qayta qo'shmaydi:
     * foydalanuvchi chiqib ketsa, chiqib ketgani qoladi.
     */
    public static void resumeDistrictJoin(int account) {
        if (joining.contains(account) || !UserConfig.getInstance(account).isClientActivated() || MilliyLogin.isBot(account)) {
            return;
        }
        final SharedPreferences prefs = MessagesController.getMainSettings(account);
        if (!prefs.getBoolean(KEY_JOIN_SYNC, false)) {
            if (!prefs.getStringSet(KEY_JOIN_PENDING, new HashSet<>()).isEmpty()) {
                joining.add(account);
                joinNextPending(account);
            }
            return;
        }
        final String district = getDistrict(account);
        if (TextUtils.isEmpty(district)) {
            prefs.edit().remove(KEY_JOIN_SYNC).apply();
            return;
        }
        joining.add(account);
        get("channels?district=" + enc(district), 0, body -> {
            ArrayList<String> usernames = new ArrayList<>();
            for (Channel c : parseChannels(body, getOffered(account))) {
                usernames.add(c.username);
            }
            addOffered(account, usernames);
            Set<String> pending = new HashSet<>(prefs.getStringSet(KEY_JOIN_PENDING, new HashSet<>()));
            pending.addAll(usernames);
            prefs.edit().putStringSet(KEY_JOIN_PENDING, pending).remove(KEY_JOIN_SYNC).apply();
            joinNextPending(account);
        }, () -> joining.remove(account));
    }

    private static void joinNextPending(int account) {
        final SharedPreferences prefs = MessagesController.getMainSettings(account);
        final Set<String> pending = new HashSet<>(prefs.getStringSet(KEY_JOIN_PENDING, new HashSet<>()));
        if (pending.isEmpty() || !UserConfig.getInstance(account).isClientActivated()) {
            joining.remove(account);
            if (prefs.getBoolean(KEY_JOIN_SYNC, false)) { // jarayon davomida hudud yana o'zgargan
                resumeDistrictJoin(account);
            }
            return;
        }
        final String username = pending.iterator().next();
        final MessagesController controller = MessagesController.getInstance(account);
        controller.getUserNameResolver().resolve(username, peerId -> {
            if (peerId == null && !ApplicationLoader.isNetworkOnline()) { // tarmoq yo'q: keyingi safar davom etadi
                joining.remove(account);
                return;
            }
            pending.remove(username);
            prefs.edit().putStringSet(KEY_JOIN_PENDING, pending).apply();
            if (peerId != null && peerId < 0 && peerId != Long.MIN_VALUE) {
                TLRPC.Chat chat = controller.getChat(-peerId);
                if (chat != null && ChatObject.isNotInChat(chat)) {
                    controller.addUserToChat(chat.id, UserConfig.getInstance(account).getCurrentUser(), 0, null, null, null);
                }
            }
            AndroidUtilities.runOnUIThread(() -> joinNextPending(account), 1500);
        });
    }

    public static boolean isEmpty(String s) {
        return TextUtils.isEmpty(s);
    }
}
