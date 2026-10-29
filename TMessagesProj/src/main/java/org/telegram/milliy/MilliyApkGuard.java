package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * «Aqlli APK Himoyachi»:
 *  - Oq ro'yxat (Click, Payme, Uzum va rasmiy bank/xizmat ilovalari): avtomatik xavfsiz deb tanish;
 *  - Qora ro'yxat (Ma'lum troyanlar va firibgar APKlar): ogohlantirish bilan to'xtatish yoki o'chirish;
 *  - Dinamik yangilanish: internet orqali oq/qora ro'yxatni yangilab turish;
 *  - Heuristika: SMS kodlarni o'g'irlovchi xavfli ruxsatnomalarni tahlil qilish.
 */
public class MilliyApkGuard {

    private static final String PREFS_NAME = "milliy_apk_guard";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AUTO_TRUST_WHITELIST = "auto_trust_whitelist";
    private static final String KEY_WARN_DANGEROUS = "warn_dangerous";
    private static final String KEY_AUTO_DELETE_BLACKLIST = "auto_delete_blacklist";
    private static final String KEY_LAST_SYNC = "last_rules_sync";
    private static final String KEY_RULES_CACHE = "rules_cache";

    public static final int STATUS_SAFE = 0;
    public static final int STATUS_TROJAN = 1;
    public static final int STATUS_SUSPICIOUS = 2;
    public static final int STATUS_UNKNOWN = 3;

    private static final HashSet<String> approvedSession = new HashSet<>();

    // ---------- Standart Oq va Qora ro'yxatlar (Offline fallback) ----------

    private static final Map<String, String> DEFAULT_WHITELIST = new HashMap<String, String>() {{
        put("uz.dida.payme", "Payme");
        put("com.click.pay", "Click Up");
        put("uz.uzumbank.app", "Uzum Bank");
        put("uz.fido.anorbank", "Anorbank");
        put("com.fido.anorbank", "Anorbank");
        put("uz.humocard.humopay", "HumoPay");
        put("uz.paynet.client", "Paynet");
        put("uz.agrobank.mobile", "Agrobank");
        put("uz.tbcbank.retail", "TBC Bank");
        put("uz.ipotekabank.mobile", "Ipoteka Bank");
        put("uz.xb.mobile", "Xalq Banki");
        put("uz.nbu.mobile", "Milliy Bank (NBU)");
        put("uz.kapitalbank.retail", "Kapitalbank");
        put("uz.ipakyoilibank.mobile", "Ipak Yo'li Mobile");
        put("uz.hamkorbank.mobile", "Hamkor Mobile");
        put("uz.asakabank.mobile", "Asakabank");
        put("uz.ofb.mobile", "Orient Finans Bank");
        put("uz.aloqabank.mobile", "Aloqabank (Zoomrad)");
        put("uz.trastbank.mobile", "Trastpay");
        put("uz.turonbank.mobile", "Turonbank");
        put("uz.davrbank.mobile", "Davrbank");
        put("uz.infinbank.mobile", "InFinBank");
        put("uz.sqb.joyda", "SQB Joyda");
        put("uz.mygov.app", "MyGov");
        put("uz.yt.eimzo", "E-IMZO");
        put("uz.eimzo", "E-IMZO");
        put("com.ubt.soliq", "Soliq");
        put("ru.yandex.taxi", "Yandex Go");
        put("uz.yandex.taxi", "Yandex Go");
        put("uz.express24.client", "Express24");
        put("uz.uzum.app", "Uzum Market");
        put("org.telegram.messenger", "Telegram");
        put("org.telegram.milliy", "Milliygram");
        put("uz.milliy.klaviatura", "Milliy Klaviatura");
    }};

    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<String>() {{
        add("com.system.security.update");
        add("com.android.service.sms");
        add("com.telegram.photos");
        add("com.pay.fake");
        add("com.telegram.ovoz");
        add("uz.photo.fake");
        add("uz.ovoz.bering");
        add("com.android.support.photo");
        add("org.telegram.update.bonus");
        add("com.bonus.click");
        add("com.payme.fake");
    }};

    public static class ApkCheckResult {
        public int status = STATUS_UNKNOWN;
        public String packageName;
        public String appName;
        public String versionName;
        public int versionCode;
        public long fileSize;
        public String sha256;
        public Drawable icon;
        public ArrayList<String> dangerPerms = new ArrayList<>();
        public String matchedRule;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY_ENABLED, value).apply();
    }

    public static boolean isAutoTrustWhitelist() {
        return prefs().getBoolean(KEY_AUTO_TRUST_WHITELIST, true);
    }

    public static void setAutoTrustWhitelist(boolean value) {
        prefs().edit().putBoolean(KEY_AUTO_TRUST_WHITELIST, value).apply();
    }

    public static boolean isWarnDangerous() {
        return prefs().getBoolean(KEY_WARN_DANGEROUS, true);
    }

    public static void setWarnDangerous(boolean value) {
        prefs().edit().putBoolean(KEY_WARN_DANGEROUS, value).apply();
    }

    public static boolean isAutoDeleteBlacklist() {
        return prefs().getBoolean(KEY_AUTO_DELETE_BLACKLIST, false);
    }

    public static void setAutoDeleteBlacklist(boolean value) {
        prefs().edit().putBoolean(KEY_AUTO_DELETE_BLACKLIST, value).apply();
    }

    public static long getLastSyncTime() {
        return prefs().getLong(KEY_LAST_SYNC, 0L);
    }

    public static int getWhitelistedCount() {
        Map<String, String> wl = getDynamicWhitelist();
        return wl != null ? wl.size() : DEFAULT_WHITELIST.size();
    }

    // ---------- Ro'yxatlarni yuklash va yangilash ----------

    public static Map<String, String> getDynamicWhitelist() {
        Map<String, String> map = new HashMap<>(DEFAULT_WHITELIST);
        String raw = prefs().getString(KEY_RULES_CACHE, null);
        if (raw != null) {
            try {
                JSONObject obj = new JSONObject(raw);
                if (obj.has("whitelist")) {
                    JSONArray arr = obj.getJSONArray("whitelist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String pkg = item.optString("package", item.optString("pkg"));
                        String name = item.optString("name", pkg);
                        if (!TextUtils.isEmpty(pkg)) {
                            map.put(pkg, name);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return map;
    }

    public static Set<String> getDynamicBlacklist() {
        Set<String> set = new HashSet<>(DEFAULT_BLACKLIST);
        String raw = prefs().getString(KEY_RULES_CACHE, null);
        if (raw != null) {
            try {
                JSONObject obj = new JSONObject(raw);
                if (obj.has("blacklist")) {
                    JSONArray arr = obj.getJSONArray("blacklist");
                    for (int i = 0; i < arr.length(); i++) {
                        Object val = arr.get(i);
                        String pkg = null;
                        if (val instanceof JSONObject) {
                            pkg = ((JSONObject) val).optString("package", ((JSONObject) val).optString("pkg"));
                        } else if (val instanceof String) {
                            pkg = (String) val;
                        }
                        if (!TextUtils.isEmpty(pkg)) {
                            set.add(pkg.toLowerCase(Locale.US));
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return set;
    }

    public static int getBlacklistedCount() {
        return getDynamicBlacklist().size();
    }

    public static void syncRules(boolean force, Runnable onDone) {
        long last = getLastSyncTime();
        long now = System.currentTimeMillis();
        if (!force && now - last < 12 * 3600 * 1000L) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL("https://webpanel.cyberhimoya.uz/api/v1/apk-guard/rules").openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "Milliygram-ApkGuard");
                if (conn.getResponseCode() == 200) {
                    java.io.InputStream in = conn.getInputStream();
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        baos.write(buf, 0, read);
                    }
                    in.close();
                    String body = new String(baos.toByteArray(), "UTF-8");
                    if (!TextUtils.isEmpty(body)) {
                        new JSONObject(body); // validatsiya
                        prefs().edit()
                                .putString(KEY_RULES_CACHE, body)
                                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                                .apply();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        });
    }

    // ---------- APK Faylini Tahlil Qilish (On-Device Inspection) ----------

    public static ApkCheckResult checkApk(File file) {
        ApkCheckResult r = new ApkCheckResult();
        if (file == null || !file.exists()) {
            return r;
        }
        r.fileSize = file.length();
        r.sha256 = calculateSha256(file);

        try {
            Context ctx = ApplicationLoader.applicationContext;
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
            if (pi != null) {
                r.packageName = pi.packageName;
                r.versionName = pi.versionName != null ? pi.versionName : "";
                if (Build.VERSION.SDK_INT >= 28) {
                    r.versionCode = (int) pi.getLongVersionCode();
                } else {
                    r.versionCode = pi.versionCode;
                }
                if (pi.applicationInfo != null) {
                    pi.applicationInfo.sourceDir = file.getAbsolutePath();
                    pi.applicationInfo.publicSourceDir = file.getAbsolutePath();
                    try {
                        r.appName = pi.applicationInfo.loadLabel(pm).toString();
                        r.icon = pi.applicationInfo.loadIcon(pm);
                    } catch (Throwable th) {
                        r.appName = file.getName();
                    }
                }
                if (pi.requestedPermissions != null) {
                    checkDangerousPermissions(pi.requestedPermissions, r.dangerPerms);
                }
            } else {
                r.appName = file.getName();
            }
        } catch (Throwable e) {
            FileLog.e(e);
            r.appName = file.getName();
        }

        // Qoidalarga solishtirish
        Map<String, String> whitelist = getDynamicWhitelist();
        Set<String> blacklist = getDynamicBlacklist();

        String pkg = r.packageName != null ? r.packageName.toLowerCase(Locale.US) : "";
        String hash = r.sha256 != null ? r.sha256.toLowerCase(Locale.US) : "";

        if (blacklist.contains(pkg) || blacklist.contains(hash)) {
            r.status = STATUS_TROJAN;
            r.matchedRule = "Blacklist";
        } else if (whitelist.containsKey(pkg)) {
            r.status = STATUS_SAFE;
            r.matchedRule = whitelist.get(pkg);
        } else if (!r.dangerPerms.isEmpty()) {
            r.status = STATUS_SUSPICIOUS;
        } else {
            r.status = STATUS_UNKNOWN;
        }

        return r;
    }

    private static void checkDangerousPermissions(String[] requested, ArrayList<String> danger) {
        if (requested == null) return;
        boolean hasSms = false;
        boolean hasA11y = false;
        boolean hasOverlay = false;
        boolean hasContacts = false;
        boolean hasCamMic = false;

        for (String p : requested) {
            if (p == null) continue;
            if (p.contains("RECEIVE_SMS") || p.contains("READ_SMS") || p.contains("SEND_SMS")) {
                hasSms = true;
            } else if (p.contains("BIND_ACCESSIBILITY_SERVICE")) {
                hasA11y = true;
            } else if (p.contains("SYSTEM_ALERT_WINDOW")) {
                hasOverlay = true;
            } else if (p.contains("READ_CONTACTS") || p.contains("WRITE_CONTACTS")) {
                hasContacts = true;
            } else if (p.contains("RECORD_AUDIO") || p.contains("CAMERA")) {
                hasCamMic = true;
            }
        }
        if (hasSms) {
            danger.add(LocaleController.getString(R.string.MilliyApkPermSms));
        }
        if (hasA11y) {
            danger.add(LocaleController.getString(R.string.MilliyApkPermA11y));
        }
        if (hasOverlay) {
            danger.add(LocaleController.getString(R.string.MilliyApkPermOverlay));
        }
        if (hasContacts) {
            danger.add(LocaleController.getString(R.string.MilliyApkPermContacts));
        }
        if (hasCamMic) {
            danger.add(LocaleController.getString(R.string.MilliyApkPermCamera));
        }
    }

    private static String calculateSha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- APK Faylni ushlab qolish va dialog ko'rsatish ----------

    public static boolean interceptApk(Activity activity, MessageObject m, File f, Runnable proceed) {
        if (!isEnabled()) {
            return false;
        }
        if (activity == null || f == null || !f.exists()) {
            return false;
        }
        final String sessionKey = f.getAbsolutePath() + ":" + f.length();
        if (approvedSession.contains(sessionKey)) {
            return false;
        }

        // Tahlil qilish
        ApkCheckResult res = checkApk(f);

        // 1. Oq ro'yxatda bo'lsa (XAVFSIZ)
        if (res.status == STATUS_SAFE && isAutoTrustWhitelist()) {
            showSafeDialog(activity, res, () -> {
                approvedSession.add(sessionKey);
                proceed.run();
            });
            return true;
        }

        // 2. Qora ro'yxatda bo'lsa (TROYAN)
        if (res.status == STATUS_TROJAN) {
            if (isAutoDeleteBlacklist()) {
                f.delete();
                Toast.makeText(activity, LocaleController.getString(R.string.MilliyApkFileDeleted), Toast.LENGTH_LONG).show();
                return true;
            }
            showTrojanDialog(activity, m, f, res, () -> {
                approvedSession.add(sessionKey);
                proceed.run();
            });
            return true;
        }

        // 3. Shubhali ruxsatlar bo'lsa (SMS / ACCESSIBILITY)
        if (res.status == STATUS_SUSPICIOUS && isWarnDangerous()) {
            showSuspiciousDialog(activity, m, f, res, () -> {
                approvedSession.add(sessionKey);
                proceed.run();
            });
            return true;
        }

        // 4. Oddiy noma'lum ilova
        showUnknownDialog(activity, m, f, res, () -> {
            approvedSession.add(sessionKey);
            proceed.run();
        });
        return true;
    }

    // ---------- Dialoglar ----------

    private static void showSafeDialog(Activity activity, ApkCheckResult r, Runnable install) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkSafeTitle));

        String officialName = r.matchedRule != null ? r.matchedRule : (r.appName != null ? r.appName : "Ilova");
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyApkSafeDesc, officialName)));
        b.setPositiveButton(LocaleController.getString(R.string.MilliyApkInstall), (d, w) -> install.run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void showTrojanDialog(Activity activity, MessageObject m, File f, ApkCheckResult r, Runnable installAnyway) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkTrojanTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkTrojanDesc)));
        b.setPositiveButton(LocaleController.getString(R.string.MilliyApkDeleteFile), (d, w) -> {
            if (f != null) {
                f.delete();
            }
            Toast.makeText(activity, LocaleController.getString(R.string.MilliyApkFileDeleted), Toast.LENGTH_SHORT).show();
        });
        b.setNeutralButton(LocaleController.getString(R.string.MilliyApkCheck), (d, w) -> {
            if (m != null) {
                MilliyShield.sendToZirh(m);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.MilliyApkInstallAnyway), (d, w) -> installAnyway.run());
        b.makeRed(DialogInterface.BUTTON_NEGATIVE);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void showSuspiciousDialog(Activity activity, MessageObject m, File f, ApkCheckResult r, Runnable installAnyway) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkSuspiciousTitle));

        StringBuilder sb = new StringBuilder();
        for (String p : r.dangerPerms) {
            sb.append("• ").append(p).append("\n");
        }
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyApkSuspiciousDesc, sb.toString().trim())));

        b.setPositiveButton(LocaleController.getString(R.string.MilliyApkCheck), (d, w) -> {
            if (m != null) {
                MilliyShield.sendToZirh(m);
            }
        });
        b.setNeutralButton(LocaleController.getString(R.string.MilliyApkDeleteFile), (d, w) -> {
            if (f != null) {
                f.delete();
            }
            Toast.makeText(activity, LocaleController.getString(R.string.MilliyApkFileDeleted), Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton(LocaleController.getString(R.string.MilliyApkInstallAnyway), (d, w) -> installAnyway.run());
        b.makeRed(DialogInterface.BUTTON_NEGATIVE);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void showUnknownDialog(Activity activity, MessageObject m, File f, ApkCheckResult r, Runnable install) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkUnknownTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkUnknownDesc)));
        b.setPositiveButton(LocaleController.getString(R.string.MilliyApkInstall), (d, w) -> install.run());
        b.setNeutralButton(LocaleController.getString(R.string.MilliyApkCheck), (d, w) -> {
            if (m != null) {
                MilliyShield.sendToZirh(m);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
