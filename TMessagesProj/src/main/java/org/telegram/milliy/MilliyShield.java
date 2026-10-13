package org.telegram.milliy;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * ZIRH himoyasi: tashqi havolalarni ochishdan va APK o'rnatishdan oldin tekshirish, firibgarlik shikoyati.
 * Serverga faqat havolaning o'zi / faylning o'zi boradi (xabar matni emas). Natija: 0 xavfsiz, 1 shubhali, 2 xavfli.
 */
public class MilliyShield {

    public static final int SAFE = 0, SUSPICIOUS = 1, MALICIOUS = 2;
    private static final int MAX_APK = 20 * 1024 * 1024;

    /** Tekshiruvdan ozod domenlar (Telegram'ning o'zi) — qolganini server oq ro'yxati hal qiladi. */
    private static final String[] SKIP_HOSTS = {"t.me", "telegram.me", "telegram.org", "telegra.ph", "telesco.pe", "fragment.com",
            "cyberhimoya.uz", "milliysi.uz"};

    private static final HashMap<String, Integer> verdicts = new HashMap<>();
    private static final HashSet<String> approved = new HashSet<>();

    // ---------- HTTP ----------

    private interface Result {
        void run(int code, String body);
    }

    private static void http(String method, String path, byte[] body, String contentType, int timeoutMs, Result result) {
        Utilities.globalQueue.postRunnable(() -> {
            int code = 0;
            String text = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(MilliyData.API + path).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(10000);
                c.setReadTimeout(timeoutMs);
                c.setRequestProperty("User-Agent", "Milliygram/" + BuildVars.BUILD_VERSION_STRING);
                String district = MilliyData.getDistrict(UserConfig.selectedAccount);
                if (district != null) {
                    c.setRequestProperty("X-Milliy-District", district);
                }
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", contentType);
                    c.setFixedLengthStreamingMode(body.length);
                    try (OutputStream out = c.getOutputStream()) {
                        out.write(body);
                    }
                }
                code = c.getResponseCode();
                try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream()) {
                    if (in != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                        text = out.toString("UTF-8");
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
            final int fc = code;
            final String ft = text;
            AndroidUtilities.runOnUIThread(() -> result.run(fc, ft));
        });
    }

    private static JSONObject json(int code, String body) {
        if (code != 200 || body == null) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** null — natija yo'q (hali tekshirilmoqda yoki noma'lum). */
    private static Integer status(JSONObject o) {
        return o == null || o.isNull("status") ? null : o.optInt("status");
    }

    private static String reason(JSONObject o) {
        String r = o == null || o.isNull("reason") ? null : o.optString("reason");
        return TextUtils.isEmpty(r) ? "" : "\n\n" + r;
    }

    // ---------- Havolalar ----------

    /**
     * Browser.openUrl boshida chaqiriladi. true — havola ushlab qolindi (tekshiruvdan keyin proceed chaqiriladi yoki bekor qilinadi).
     */
    public static boolean interceptUrl(Context context, Uri uri, Runnable proceed) {
        if (!(context instanceof Activity) || uri == null) {
            return false;
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https") || host.isEmpty()) {
            return false;
        }
        for (String h : SKIP_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) {
                return false;
            }
        }
        final String url = uri.toString();
        if (approved.remove(url)) {
            return false;
        }
        Integer known = verdicts.get(url);
        if (known != null && known == SAFE) {
            return false;
        }
        final Activity activity = (Activity) context;
        final Runnable open = () -> {
            approved.add(url);
            proceed.run();
        };
        if (known != null) {
            showUrlVerdict(activity, host, known, "", open);
            return true;
        }
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        final boolean[] cancelled = {false};
        progress.setOnCancelListener(d -> cancelled[0] = true);
        final Runnable showProgress = () -> {
            if (!cancelled[0] && !activity.isFinishing()) {
                try {
                    progress.show();
                } catch (Exception ignore) {
                }
            }
        };
        AndroidUtilities.runOnUIThread(showProgress, 350);
        final long started = System.currentTimeMillis();
        final byte[] body;
        try {
            body = new JSONObject().put("url", url).toString().getBytes("UTF-8");
        } catch (Exception e) {
            AndroidUtilities.cancelRunOnUIThread(showProgress);
            return false;
        }
        new Object() {
            void request() {
                http("POST", "scan/url", body, "application/json", 20000, (code, text) -> {
                    if (cancelled[0]) {
                        return;
                    }
                    JSONObject o = json(code, text);
                    Integer st = status(o);
                    boolean pending = o != null && o.optBoolean("pending");
                    if (st == null && pending && System.currentTimeMillis() - started < 25000) {
                        AndroidUtilities.runOnUIThread(this::request, 2500);
                        return;
                    }
                    AndroidUtilities.cancelRunOnUIThread(showProgress);
                    try {
                        progress.dismiss();
                    } catch (Exception ignore) {
                    }
                    if (st == null) {
                        // server ishlamadi yoki tekshiruv tugamadi — havolani to'smaymiz
                        if (pending) {
                            Toast.makeText(activity, LocaleController.getString(R.string.MilliyScanUnfinished), Toast.LENGTH_SHORT).show();
                        }
                        open.run();
                        return;
                    }
                    verdicts.put(url, st);
                    if (st == SAFE) {
                        open.run();
                    } else {
                        showUrlVerdict(activity, host, st, reason(o), open);
                    }
                });
            }
        }.request();
        return true;
    }

    private static void showUrlVerdict(Activity activity, String host, int st, String reason, Runnable open) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        if (st == MALICIOUS) {
            b.setTitle(LocaleController.getString(R.string.MilliyUrlDangerTitle));
            b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyUrlDangerText, host) + reason));
            b.setPositiveButton(LocaleController.getString(R.string.MilliyClose), null);
            b.setNegativeButton(LocaleController.getString(R.string.MilliyOpenAnyway), (d, w) -> open.run());
            b.makeRed(DialogInterface.BUTTON_NEGATIVE);
        } else {
            b.setTitle(LocaleController.getString(R.string.MilliyUrlSuspiciousTitle));
            b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyUrlSuspiciousText, host) + reason));
            b.setPositiveButton(LocaleController.getString(R.string.MilliyOpen), (d, w) -> open.run());
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        }
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ---------- APK ----------

    public static boolean isApk(String fileName, String mime) {
        return fileName != null && fileName.toLowerCase(Locale.US).endsWith(".apk") || "application/vnd.android.package-archive".equals(mime);
    }

    /**
     * APK ochilishidan oldin chaqiriladi. true — ushlab qolindi; tekshiruvdan so'ng (xavfsiz bo'lsa yoki foydalanuvchi rozi bo'lsa)
     * proceed chaqiriladi va u qayta shu metodga kelganda bir martalik ruxsat bilan o'tadi.
     */
    public static boolean interceptApk(Activity activity, File f, String fileName, String mime, Runnable proceed) {
        if (activity == null || f == null || !f.exists() || !isApk(fileName != null ? fileName : f.getName(), mime)) {
            return false;
        }
        final String key = f.getAbsolutePath() + ":" + f.length() + ":" + f.lastModified();
        if (approved.remove(key)) {
            return false;
        }
        final Runnable install = () -> {
            approved.add(key);
            proceed.run();
        };
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        final boolean[] cancelled = {false};
        progress.setOnCancelListener(d -> cancelled[0] = true);
        try {
            progress.show();
        } catch (Exception ignore) {
        }
        final String name = fileName != null ? fileName : f.getName();
        Utilities.globalQueue.postRunnable(() -> {
            final String sha = sha256(f);
            AndroidUtilities.runOnUIThread(() -> {
                if (cancelled[0]) {
                    return;
                }
                if (sha == null) {
                    finishApk(activity, progress, null, null, install);
                    return;
                }
                new Object() {
                    final long started = System.currentTimeMillis();
                    boolean uploaded;

                    void query() {
                        http("GET", "scan/apk?sha256=" + sha, null, null, 15000, (code, text) -> {
                            if (cancelled[0]) {
                                return;
                            }
                            JSONObject o = json(code, text);
                            Integer st = status(o);
                            if (st != null || o == null) {
                                finishApk(activity, progress, st, o, install);
                            } else if (!uploaded && !o.optBoolean("pending")) {
                                upload();
                            } else if (System.currentTimeMillis() - started < 150000) {
                                AndroidUtilities.runOnUIThread(this::query, 4000);
                            } else {
                                finishApk(activity, progress, null, null, install);
                            }
                        });
                    }

                    void upload() {
                        uploaded = true;
                        if (f.length() > MAX_APK) {
                            finishApk(activity, progress, null, null, install);
                            return;
                        }
                        Utilities.globalQueue.postRunnable(() -> {
                            byte[] data = readAll(f);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (data == null) {
                                    finishApk(activity, progress, null, null, install);
                                    return;
                                }
                                http("POST", "scan/apk?sha256=" + sha + "&name=" + MilliyData.enc(name), data,
                                        "application/octet-stream", 90000, (code, text) -> {
                                            if (cancelled[0]) {
                                                return;
                                            }
                                            JSONObject o = json(code, text);
                                            if (status(o) != null) {
                                                finishApk(activity, progress, status(o), o, install);
                                            } else if (o == null) {
                                                finishApk(activity, progress, null, null, install);
                                            } else {
                                                AndroidUtilities.runOnUIThread(this::query, 4000);
                                            }
                                        });
                            });
                        });
                    }
                }.query();
            });
        });
        return true;
    }

    private static void finishApk(Activity activity, AlertDialog progress, Integer st, JSONObject o, Runnable install) {
        try {
            progress.dismiss();
        } catch (Exception ignore) {
        }
        if (st != null && st == SAFE) {
            Toast.makeText(activity, LocaleController.getString(R.string.MilliyApkSafe), Toast.LENGTH_SHORT).show();
            install.run();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        if (st != null && st == MALICIOUS) {
            // xavfli — o'rnatish tugmasi yo'q
            b.setTitle(LocaleController.getString(R.string.MilliyApkDangerTitle));
            b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkDangerText) + reason(o)));
            b.setPositiveButton(LocaleController.getString(R.string.MilliyClose), null);
        } else {
            b.setTitle(LocaleController.getString(st != null ? R.string.MilliyApkSuspiciousTitle : R.string.MilliyApkUnknownTitle));
            b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(st != null ? R.string.MilliyApkSuspiciousText : R.string.MilliyApkUnknownText) + reason(o)));
            b.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
            b.setNegativeButton(LocaleController.getString(R.string.MilliyInstallAnyway), (d, w) -> install.run());
            b.makeRed(DialogInterface.BUTTON_NEGATIVE);
        }
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) {
                sb.append(String.format(Locale.US, "%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static byte[] readAll(File f) {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // ---------- Shikoyat ----------

    private static final String[] REASON_KEYS = {"fraud", "money", "code", "fake", "spam", "other"};
    private static final int[] REASON_TEXTS = {R.string.MilliyReportFraud, R.string.MilliyReportMoney, R.string.MilliyReportCode,
            R.string.MilliyReportFake, R.string.MilliyReportSpam, R.string.MilliyReportOther};

    /** Bot API ko'rinishidagi ID: foydalanuvchi musbat, kanal/supergroup -100…, oddiy guruh -id. */
    public static long botApiId(int account, long dialogId) {
        if (dialogId >= 0) {
            return dialogId;
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        return chat != null && ChatObject.isChannel(chat) ? -1000000000000L + dialogId : dialogId;
    }

    /**
     * Firibgarlik shikoyati. dialogId — shikoyat qilinayotgan foydalanuvchi/kanal; message — ixtiyoriy (xabar menyusidan).
     */
    public static void report(BaseFragment fragment, long dialogId, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null || dialogId == 0) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        CharSequence[] items = new CharSequence[REASON_TEXTS.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = LocaleController.getString(REASON_TEXTS[i]);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyReport));
        b.setItems(items, (d, which) -> send(fragment, account, dialogId, message, REASON_KEYS[which]));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private static void send(BaseFragment fragment, int account, long dialogId, MessageObject message, String reason) {
        final MessagesController mc = MessagesController.getInstance(account);
        String username = null, title = null;
        if (dialogId > 0) {
            TLRPC.User u = mc.getUser(dialogId);
            if (u != null) {
                username = UserObject.getPublicUsername(u);
                title = UserObject.getUserName(u);
            }
        } else {
            TLRPC.Chat c = mc.getChat(-dialogId);
            if (c != null) {
                username = ChatObject.getPublicUsername(c);
                title = c.title;
            }
        }
        byte[] body;
        try {
            JSONObject o = new JSONObject()
                    .put("peer_id", botApiId(account, dialogId))
                    .put("username", username == null ? "" : username)
                    .put("title", title == null ? "" : title)
                    .put("reason", reason)
                    .put("reporter_id", UserConfig.getInstance(account).getClientUserId());
            if (message != null && message.messageText != null) {
                String t = message.messageText.toString();
                o.put("message_text", t.length() > 2000 ? t.substring(0, 2000) : t);
            }
            body = o.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return;
        }
        http("POST", "report", body, "application/json", 20000, (code, text) -> {
            if (fragment.getParentActivity() == null) {
                return;
            }
            Toast.makeText(fragment.getParentActivity(), LocaleController.getString(code == 200 ? R.string.MilliyReportSent : R.string.MilliyNetworkError),
                    Toast.LENGTH_LONG).show();
        });
    }
}
