package org.telegram.milliy;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * «Aqlli APK Qo'riqchi» motori — alohida himoya ilovasini o'rnatishga hojat qoldirmaydi:
 *  - o'rnatilgan ilovalar va xotiradagi APK fayllarni baholaydi (qora ro'yxat, imzo, ruxsatlar, nom o'xshashligi);
 *  - yangi o'rnatilgan xavfli ilova haqida darhol xabar beradi;
 *  - MilliyInstallBlockService tizim o'rnatish oynasini yopadi, MilliyApkWatcher APK fayllarni o'chiradi.
 *
 * Paket nomining o'zi hech qachon «xavfsiz» dalili emas: firibgar APK istalgan paket nomini qo'ya oladi.
 * Fayl faqat imzo sertifikati (serverdagi oq ro'yxat, "cert"/"sha256") mos kelsa xavfsiz hisoblanadi.
 * Ilova ichidagi APK uchun MilliyShield qoidasi o'zgarmaydi: u faqat ZIRH qaytargan bo'lsa ochiladi.
 */
public class MilliyApkGuard {

    private static final String PREFS_NAME = "milliy_apk_guard";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BLOCK_INSTALLS = "block_installs";
    private static final String KEY_AUTO_DELETE = "auto_delete_all";
    private static final String KEY_LAST_SYNC = "last_rules_sync";
    private static final String KEY_RULES_CACHE = "rules_cache";
    private static final String KEY_DELETED = "deleted_journal";
    private static final String KEY_BLOCKED_COUNT = "blocked_count";
    private static final String KEY_KNOWN_PACKAGES = "known_packages";

    private static final String RULES_URL = "https://webpanel.cyberhimoya.uz/api/v1/apk-guard/rules";
    private static final String CHANNEL = "milliy_apk_guard";
    private static final int DELETED_JOURNAL_MAX = 100;
    private static final long ALLOW_INSTALL_MS = 2 * 60 * 1000L;

    public static final int STATUS_SAFE = 0;
    public static final int STATUS_TROJAN = 1;
    public static final int STATUS_SUSPICIOUS = 2;
    public static final int STATUS_UNKNOWN = 3;

    /** Play'da borligi tekshirilgan rasmiy paketlar. Sertifikat serverdan keladi. */
    private static final Map<String, String> DEFAULT_WHITELIST = new HashMap<String, String>() {{
        put("uz.dida.payme", "Payme");
        put("uz.uzum.app", "Uzum Market");
        put("uz.agrobank.mobile", "Agrobank");
        put("uz.davrbank.mobile", "Davr Mobile");
        put("uz.hamkorbank.mobile", "Hamkor Mobile");
        put("uz.paynet.app", "Paynet");
        put("uz.soliq.mobile", "Soliq");
        put("ru.yandex.taxi", "Yandex Go");
        put("org.telegram.messenger", "Telegram");
    }};

    /** Firibgarlar niqob qiladigan nomlar: ilova nomida bo'lsa, lekin rasmiy paket bo'lmasa — shubhali. */
    private static final String[] OFFICIAL_NAMES = {
            "payme", "click", "uzum", "anorbank", "paynet", "agrobank", "kapitalbank", "hamkor", "asakabank",
            "xalq banki", "zoomrad", "mygov", "soliq", "telegram", "humo", "uzcard", "e-imzo"
    };

    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "com.system.security.update", "com.android.service.sms", "com.telegram.photos", "com.pay.fake",
            "com.telegram.ovoz", "uz.photo.fake", "uz.ovoz.bering", "com.android.support.photo",
            "org.telegram.update.bonus", "com.bonus.click", "com.payme.fake"
    ));

    private static final Set<String> TRUSTED_INSTALLERS = new HashSet<>(Arrays.asList(
            "com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket",
            "com.xiaomi.mipicks", "com.xiaomi.market", "com.oppo.market", "com.heytap.market",
            "com.vivo.appstore", "com.amazon.venezia"
    ));

    private static final String[] APK_EXTENSIONS = {".apk", ".apks", ".xapk", ".apkm"};

    public static class ApkCheckResult {
        public int status = STATUS_UNKNOWN;
        public String packageName;
        public String appName;
        public String versionName;
        public long fileSize;
        public String sha256;
        public String certSha256;
        public Drawable icon;
        public boolean sideloaded = true;
        /** Nima uchun shu baho berilgani (foydalanuvchiga ko'rsatiladi). */
        public final ArrayList<String> reasons = new ArrayList<>();
    }

    public static class ScannedApk {
        public final File file;
        public final ApkCheckResult result;
        public boolean deleted;

        public ScannedApk(File file, ApkCheckResult result) {
            this.file = file;
            this.result = result;
        }
    }

    public static class ScanResult {
        public final ArrayList<ApkCheckResult> apps = new ArrayList<>();
        public final ArrayList<ScannedApk> files = new ArrayList<>();
        public boolean hasStorageAccess;
    }

    public interface ScanListener {
        void onProgress(int filesSeen, int apksFound, String current);

        void onComplete(ScanResult result);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------- Sozlamalar ----------

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY_ENABLED, value).apply();
        MilliyApkWatcher.refresh();
    }

    public static boolean isBlockInstalls() {
        return prefs().getBoolean(KEY_BLOCK_INSTALLS, true);
    }

    public static void setBlockInstalls(boolean value) {
        prefs().edit().putBoolean(KEY_BLOCK_INSTALLS, value).apply();
    }

    /** Standart o'chiq: yoqilganda istalgan APK fayl tekshirilmasdan o'chadi, shuning uchun tasdiq bilan yoqiladi. */
    public static boolean isAutoDelete() {
        return prefs().getBoolean(KEY_AUTO_DELETE, false);
    }

    public static void setAutoDelete(boolean value) {
        prefs().edit().putBoolean(KEY_AUTO_DELETE, value).apply();
        MilliyApkWatcher.refresh();
    }

    /** Foydalanuvchi tizim sozlamalarida xizmatni yoqqanmi. */
    public static boolean isAccessibilityOn() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            String list = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(list)) {
                return false;
            }
            ComponentName me = new ComponentName(ctx, MilliyInstallBlockService.class);
            for (String item : list.split(":")) {
                ComponentName c = ComponentName.unflattenFromString(item);
                if (me.equals(c)) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return Build.VERSION.SDK_INT < 23 || ApplicationLoader.applicationContext.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------- Ruxsat etilgan o'rnatish ----------

    private static volatile long allowInstallUntil;

    /** ZIRH tasdiqlagan yoki Milliygramning o'z yangilanishi: tizim o'rnatish oynasini qisqa vaqt to'smaymiz. */
    public static void allowInstall() {
        allowInstallUntil = System.currentTimeMillis() + ALLOW_INSTALL_MS;
    }

    public static boolean isInstallAllowedNow() {
        return System.currentTimeMillis() < allowInstallUntil;
    }

    public static void onInstallBlocked() {
        SharedPreferences p = prefs();
        p.edit().putInt(KEY_BLOCKED_COUNT, p.getInt(KEY_BLOCKED_COUNT, 0) + 1).apply();
    }

    public static int getBlockedCount() {
        return prefs().getInt(KEY_BLOCKED_COUNT, 0);
    }

    // ---------- Ishga tushirish ----------

    private static boolean inited;

    public static void init() {
        if (inited) {
            return;
        }
        inited = true;
        try {
            registerPackageReceiver();
            Utilities.globalQueue.postRunnable(() -> {
                syncRules(false, null);
                if (isEnabled()) {
                    MilliyApkWatcher.refresh();
                    auditNewPackages();
                }
            }, 4000);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void registerPackageReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Uri data = intent.getData();
                if (data == null || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) || !isEnabled()) {
                    return;
                }
                String pkg = data.getSchemeSpecificPart();
                Utilities.globalQueue.postRunnable(() -> checkNewPackage(pkg));
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        Context ctx = ApplicationLoader.applicationContext;
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ctx.registerReceiver(receiver, filter);
        }
    }

    /** Ilova yopiq turgan paytda o'rnatilganlarni ham ushlash: oldingi ro'yxat bilan solishtiriladi. */
    private static void auditNewPackages() {
        try {
            PackageManager pm = ApplicationLoader.applicationContext.getPackageManager();
            Set<String> current = new HashSet<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    current.add(ai.packageName);
                }
            }
            Set<String> known = prefs().getStringSet(KEY_KNOWN_PACKAGES, null);
            if (known != null) {
                for (String pkg : current) {
                    if (!known.contains(pkg)) {
                        checkNewPackage(pkg);
                    }
                }
            }
            prefs().edit().putStringSet(KEY_KNOWN_PACKAGES, current).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void checkNewPackage(String pkg) {
        ApkCheckResult r = checkInstalled(pkg);
        if (r != null && (r.status == STATUS_TROJAN || r.status == STATUS_SUSPICIOUS)) {
            notifyThreat(r);
        }
    }

    private static void notifyThreat(ApkCheckResult r) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, LocaleController.getString(R.string.MilliyApkGuardTitle), NotificationManager.IMPORTANCE_HIGH));
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            PendingIntent uninstall = PendingIntent.getActivity(ctx, r.packageName.hashCode(), uninstallIntent(r.packageName), flags);
            String title = LocaleController.formatString(r.status == STATUS_TROJAN ? R.string.MilliyGuardNotifyDanger : R.string.MilliyGuardNotifySuspicious, r.appName);
            String text = TextUtils.join(", ", r.reasons);
            NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(uninstall)
                    .addAction(0, LocaleController.getString(R.string.MilliyGuardUninstall), uninstall);
            nm.notify(r.packageName.hashCode(), nb.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static Intent uninstallIntent(String pkg) {
        return new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    // ---------- Qoidalar ----------

    public static long getLastSyncTime() {
        return prefs().getLong(KEY_LAST_SYNC, 0L);
    }

    private static volatile RuleSet ruleSet;

    private static class RuleSet {
        final Map<String, String> whitelist = new HashMap<>(DEFAULT_WHITELIST);
        final Map<String, String> pinnedCerts = new HashMap<>();
        final Set<String> blacklist = new HashSet<>(DEFAULT_BLACKLIST);
    }

    private static RuleSet rules() {
        RuleSet set = ruleSet;
        if (set != null) {
            return set;
        }
        set = new RuleSet();
        String raw = prefs().getString(KEY_RULES_CACHE, null);
        if (raw != null) {
            try {
                JSONObject obj = new JSONObject(raw);
                JSONArray white = obj.optJSONArray("whitelist");
                if (white != null) {
                    for (int i = 0; i < white.length(); i++) {
                        JSONObject item = white.getJSONObject(i);
                        String pkg = item.optString("package", "").toLowerCase(Locale.US);
                        if (TextUtils.isEmpty(pkg)) {
                            continue;
                        }
                        set.whitelist.put(pkg, item.optString("name", pkg));
                        // Oq ro'yxatda "sha256" maydoni — ilova imzo sertifikatining xeshi
                        String cert = item.optString("cert", item.optString("sha256", "")).replace(":", "").toLowerCase(Locale.US);
                        if (!TextUtils.isEmpty(cert)) {
                            set.pinnedCerts.put(pkg, cert);
                        }
                    }
                }
                JSONArray black = obj.optJSONArray("blacklist");
                if (black != null) {
                    for (int i = 0; i < black.length(); i++) {
                        Object val = black.get(i);
                        if (val instanceof JSONObject) {
                            JSONObject item = (JSONObject) val;
                            addLower(set.blacklist, item.optString("package", ""));
                            addLower(set.blacklist, item.optString("sha256", ""));
                            addLower(set.blacklist, item.optString("cert", ""));
                        } else if (val instanceof String) {
                            addLower(set.blacklist, (String) val);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        ruleSet = set;
        return set;
    }

    private static void addLower(Set<String> set, String value) {
        if (!TextUtils.isEmpty(value)) {
            set.add(value.replace(":", "").toLowerCase(Locale.US));
        }
    }

    public static int getBlacklistedCount() {
        return rules().blacklist.size();
    }

    public static void syncRules(boolean force, Runnable onDone) {
        if (!force && System.currentTimeMillis() - getLastSyncTime() < 12 * 3600 * 1000L) {
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(RULES_URL).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "Milliygram-ApkGuard");
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = in.read(buf)) != -1) {
                            out.write(buf, 0, read);
                        }
                        String body = out.toString("UTF-8");
                        JSONObject obj = new JSONObject(body);
                        if (obj.has("whitelist") && obj.has("blacklist")) {
                            prefs().edit().putString(KEY_RULES_CACHE, body).putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
                            ruleSet = null;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        });
    }

    // ---------- Baholash ----------

    private static final int INFO_FLAGS = PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS;

    /** Faylni tahlil qiladi. Sekin (xesh): UI oqimida chaqirilmasin. */
    public static ApkCheckResult checkApk(File file) {
        ApkCheckResult r = new ApkCheckResult();
        if (file == null || !file.exists()) {
            return r;
        }
        r.fileSize = file.length();
        r.sha256 = sha256(file);
        r.appName = file.getName();
        PackageInfo pi = null;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            PackageManager pm = ctx.getPackageManager();
            pi = pm.getPackageArchiveInfo(file.getAbsolutePath(), INFO_FLAGS | PackageManager.GET_SIGNATURES);
            if (pi != null) {
                r.packageName = pi.packageName;
                r.versionName = pi.versionName != null ? pi.versionName : "";
                r.certSha256 = certOf(pi);
                if (pi.applicationInfo != null) {
                    pi.applicationInfo.sourceDir = file.getAbsolutePath();
                    pi.applicationInfo.publicSourceDir = file.getAbsolutePath();
                    r.appName = pi.applicationInfo.loadLabel(pm).toString();
                    r.icon = pi.applicationInfo.loadIcon(pm);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        evaluate(r, pi, false);
        return r;
    }

    /** O'rnatilgan ilovani baholaydi. Tizim ilovasi yoki Milliygramning o'zi bo'lsa null. */
    public static ApkCheckResult checkInstalled(String pkg) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx.getPackageName().equals(pkg)) {
                return null;
            }
            PackageManager pm = ctx.getPackageManager();
            int certFlag = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo pi = pm.getPackageInfo(pkg, INFO_FLAGS | certFlag);
            if (pi.applicationInfo == null || (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return null;
            }
            ApkCheckResult r = new ApkCheckResult();
            r.packageName = pkg;
            r.versionName = pi.versionName != null ? pi.versionName : "";
            r.appName = pi.applicationInfo.loadLabel(pm).toString();
            r.icon = pi.applicationInfo.loadIcon(pm);
            r.certSha256 = certOf(pi);
            r.sideloaded = !TRUSTED_INSTALLERS.contains(installerOf(pm, pkg));
            evaluate(r, pi, true);
            return r;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String installerOf(PackageManager pm, String pkg) {
        try {
            String installer;
            if (Build.VERSION.SDK_INT >= 30) {
                installer = pm.getInstallSourceInfo(pkg).getInstallingPackageName();
            } else {
                installer = pm.getInstallerPackageName(pkg);
            }
            return installer != null ? installer : "";
        } catch (Throwable e) {
            return "";
        }
    }

    private static void evaluate(ApkCheckResult r, PackageInfo pi, boolean installed) {
        RuleSet set = rules();
        Context ctx = ApplicationLoader.applicationContext;
        String pkg = r.packageName != null ? r.packageName.toLowerCase(Locale.US) : "";
        String cert = r.certSha256 != null ? r.certSha256 : "";
        String hash = r.sha256 != null ? r.sha256 : "";

        if (set.blacklist.contains(pkg) || set.blacklist.contains(hash) || set.blacklist.contains(cert)) {
            r.status = STATUS_TROJAN;
            r.reasons.add(LocaleController.getString(R.string.MilliyGuardReasonBlacklist));
            return;
        }
        if (!installed && ctx.getPackageName().equals(r.packageName) && !cert.isEmpty() && cert.equals(ownCert())) {
            r.status = STATUS_SAFE;
            return;
        }
        if (set.whitelist.containsKey(pkg)) {
            String pinned = set.pinnedCerts.get(pkg);
            if (!TextUtils.isEmpty(pinned)) {
                if (pinned.equals(cert)) {
                    r.status = STATUS_SAFE;
                    r.reasons.add(LocaleController.getString(R.string.MilliyGuardReasonOfficial));
                } else {
                    r.status = STATUS_TROJAN;
                    r.reasons.add(LocaleController.formatString(R.string.MilliyGuardReasonFakeSignature, set.whitelist.get(pkg)));
                }
            } else if (installed && !r.sideloaded) {
                r.status = STATUS_SAFE;
            } else {
                r.status = STATUS_SUSPICIOUS;
                r.reasons.add(LocaleController.formatString(R.string.MilliyGuardReasonOfficialNotStore, set.whitelist.get(pkg)));
            }
            return;
        }
        if (installed && !r.sideloaded) {
            r.status = STATUS_SAFE;
            return;
        }

        int score = scorePermissions(pi, r.reasons);
        if (installed && ctx.getPackageManager().getLaunchIntentForPackage(r.packageName) == null) {
            score += 2;
            r.reasons.add(LocaleController.getString(R.string.MilliyGuardReasonHidden));
        }
        boolean impersonates = matchesOfficialName(r.appName);
        if (impersonates) {
            r.reasons.add(0, LocaleController.getString(R.string.MilliyGuardReasonImpersonate));
        }
        if (score >= 5 || impersonates && score >= 3) {
            r.status = STATUS_TROJAN;
        } else if (score >= 2 || impersonates) {
            r.status = STATUS_SUSPICIOUS;
        } else {
            r.status = STATUS_UNKNOWN;
            r.reasons.add(LocaleController.getString(R.string.MilliyGuardReasonNotStore));
        }
    }

    private static boolean matchesOfficialName(String label) {
        if (label == null) {
            return false;
        }
        String low = label.toLowerCase(Locale.US);
        for (String name : OFFICIAL_NAMES) {
            if (low.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** Bank troyanlariga xos imkoniyatlar og'irlik bilan sanaladi: bitta oddiy ruxsat xavf belgisi emas. */
    private static int scorePermissions(PackageInfo pi, ArrayList<String> reasons) {
        if (pi == null) {
            return 0;
        }
        boolean readSms = false, sendSms = false, overlay = false, contacts = false, calls = false, installs = false, media = false;
        if (pi.requestedPermissions != null) {
            for (String p : pi.requestedPermissions) {
                if (p == null) {
                    continue;
                }
                switch (p) {
                    case "android.permission.RECEIVE_SMS":
                    case "android.permission.READ_SMS":
                        readSms = true;
                        break;
                    case "android.permission.SEND_SMS":
                        sendSms = true;
                        break;
                    case "android.permission.SYSTEM_ALERT_WINDOW":
                        overlay = true;
                        break;
                    case "android.permission.READ_CONTACTS":
                        contacts = true;
                        break;
                    case "android.permission.READ_CALL_LOG":
                        calls = true;
                        break;
                    case "android.permission.REQUEST_INSTALL_PACKAGES":
                        installs = true;
                        break;
                    case "android.permission.RECORD_AUDIO":
                    case "android.permission.CAMERA":
                        media = true;
                        break;
                }
            }
        }
        boolean a11y = false;
        if (pi.services != null) {
            for (ServiceInfo s : pi.services) {
                if ("android.permission.BIND_ACCESSIBILITY_SERVICE".equals(s.permission)) {
                    a11y = true;
                    break;
                }
            }
        }
        boolean admin = false;
        if (pi.receivers != null) {
            for (ActivityInfo a : pi.receivers) {
                if ("android.permission.BIND_DEVICE_ADMIN".equals(a.permission)) {
                    admin = true;
                    break;
                }
            }
        }
        int score = 0;
        if (readSms) {
            score += 3;
            reasons.add(LocaleController.getString(R.string.MilliyApkPermSms));
        }
        if (sendSms) {
            score += 2;
            if (!readSms) {
                reasons.add(LocaleController.getString(R.string.MilliyApkPermSms));
            }
        }
        if (a11y) {
            score += 2;
            reasons.add(LocaleController.getString(R.string.MilliyApkPermA11y));
        }
        if (admin) {
            score += 2;
            reasons.add(LocaleController.getString(R.string.MilliyGuardPermAdmin));
        }
        if (overlay) {
            score += 1;
            reasons.add(LocaleController.getString(R.string.MilliyApkPermOverlay));
        }
        if (contacts) {
            score += 1;
            reasons.add(LocaleController.getString(R.string.MilliyApkPermContacts));
        }
        if (calls) {
            score += 1;
            reasons.add(LocaleController.getString(R.string.MilliyGuardPermCalls));
        }
        if (installs) {
            score += 1;
            reasons.add(LocaleController.getString(R.string.MilliyGuardPermInstalls));
        }
        if (media) {
            reasons.add(LocaleController.getString(R.string.MilliyApkPermCamera));
        }
        return score;
    }

    private static String certOf(PackageInfo pi) {
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
            signatures = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners() : pi.signingInfo.getSigningCertificateHistory();
        }
        if (signatures == null || signatures.length == 0) {
            signatures = pi.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return "";
        }
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
        } catch (Exception e) {
            return "";
        }
    }

    /** Milliygramning o'z imzosi: uning yangilanish fayli o'chirilmasligi uchun. */
    static String ownCert() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            int certFlag = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return certOf(ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), certFlag));
        } catch (Throwable e) {
            return "";
        }
    }

    private static String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static boolean isApkName(String name) {
        if (name == null) {
            return false;
        }
        String low = name.toLowerCase(Locale.US);
        for (String ext : APK_EXTENSIONS) {
            if (low.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    // ---------- Skaner ----------

    private static volatile boolean scanning;
    private static ScanResult lastResult;

    public static boolean isScanning() {
        return scanning;
    }

    public static synchronized ScanResult getLastResult() {
        return lastResult;
    }

    public static void stopScan() {
        scanning = false;
    }

    public static void startScan(ScanListener listener) {
        if (scanning) {
            return;
        }
        scanning = true;
        Utilities.globalQueue.postRunnable(() -> {
            ScanResult result = new ScanResult();
            result.hasStorageAccess = hasAllFilesAccess();
            try {
                PackageManager pm = ApplicationLoader.applicationContext.getPackageManager();
                for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                    if (!scanning) {
                        break;
                    }
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        continue;
                    }
                    ApkCheckResult r = checkInstalled(ai.packageName);
                    if (r != null) {
                        result.apps.add(r);
                    }
                }
                Collections.sort(result.apps, (a, b) -> Integer.compare(rank(a.status), rank(b.status)));

                int[] seen = {0};
                Set<String> visited = new HashSet<>();
                for (File dir : storageRoots()) {
                    scanDir(dir, 0, result.files, seen, visited, listener);
                }
                Collections.sort(result.files, (a, b) -> Integer.compare(rank(a.result.status), rank(b.result.status)));
            } catch (Throwable e) {
                FileLog.e(e);
            }
            scanning = false;
            synchronized (MilliyApkGuard.class) {
                lastResult = result;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (listener != null) {
                    listener.onComplete(result);
                }
            });
        });
    }

    private static int rank(int status) {
        return status == STATUS_TROJAN ? 0 : status == STATUS_SUSPICIOUS ? 1 : status == STATUS_UNKNOWN ? 2 : 3;
    }

    private static List<File> storageRoots() {
        List<File> dirs = new ArrayList<>();
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) {
            dirs.add(ext);
        }
        File own = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        if (own != null) {
            dirs.add(own);
        }
        return dirs;
    }

    private static void scanDir(File dir, int depth, ArrayList<ScannedApk> found, int[] seen, Set<String> visited, ScanListener listener) {
        if (!scanning || dir == null || depth > 6 || !dir.canRead()) {
            return;
        }
        try {
            if (!visited.add(dir.getCanonicalPath())) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        if (depth > 0 && dir.getName().startsWith(".")) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!scanning) {
                return;
            }
            if (f.isDirectory()) {
                scanDir(f, depth + 1, found, seen, visited, listener);
                continue;
            }
            seen[0]++;
            boolean apk = isApkName(f.getName());
            if (apk) {
                found.add(new ScannedApk(f, checkApk(f)));
            }
            if ((apk || seen[0] % 200 == 0) && listener != null) {
                final int s = seen[0], a = found.size();
                final String name = f.getName();
                AndroidUtilities.runOnUIThread(() -> listener.onProgress(s, a, name));
            }
        }
    }

    public static boolean deleteScanned(ScannedApk item) {
        if (item == null || item.file == null) {
            return false;
        }
        boolean ok = false;
        try {
            ok = item.file.delete();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (ok) {
            item.deleted = true;
            addDeleted(item.file.getName(), item.result != null ? item.result.packageName : null, false);
        }
        return ok;
    }

    // ---------- O'chirilganlar jurnali ----------

    public static synchronized void addDeleted(String fileName, String pkg, boolean auto) {
        try {
            JSONArray old = new JSONArray(prefs().getString(KEY_DELETED, "[]"));
            JSONArray out = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("n", fileName);
            item.put("p", pkg != null ? pkg : "");
            item.put("t", System.currentTimeMillis());
            item.put("a", auto);
            out.put(item);
            for (int i = 0; i < old.length() && out.length() < DELETED_JOURNAL_MAX; i++) {
                out.put(old.get(i));
            }
            prefs().edit().putString(KEY_DELETED, out.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static JSONArray getDeletedJournal() {
        try {
            return new JSONArray(prefs().getString(KEY_DELETED, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Xavfsizlik darajasi: xavfli va shubhali ilovalar kamaytiradi, oddiy noma'lum ilova kamaytirmaydi. */
    public static int securityScore(ScanResult result) {
        int score = 100;
        for (ApkCheckResult r : result.apps) {
            if (r.status == STATUS_TROJAN) {
                score -= 30;
            } else if (r.status == STATUS_SUSPICIOUS) {
                score -= 8;
            }
        }
        return Math.max(score, 0);
    }
}
