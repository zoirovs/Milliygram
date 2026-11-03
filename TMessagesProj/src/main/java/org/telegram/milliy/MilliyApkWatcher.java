package org.telegram.milliy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.FileObserver;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «Hamma APK'ni avto-o'chirish» kaliti yoqilganda umumiy xotiradagi yuklanish papkalarini kuzatadi.
 * Faqat APK kengaytmali fayllarga tegadi, o'chirilgan har bir fayl jurnalga yoziladi.
 * Istisnolar: Milliygramning o'z imzosi bilan yangilanish fayli va ruxsat etilgan o'rnatish oynasi (ZIRH tasdig'i).
 * Ilovaning shaxsiy keshiga tegmaydi: ZIRH qaytargan APK o'sha yerda turadi.
 */
public class MilliyApkWatcher {

    private static final int MAX_DEPTH = 3;
    private static final int MASK = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE;

    private static final List<FileObserver> observers = new ArrayList<>();
    private static final Set<String> watchedDirs = new HashSet<>();

    /** Sozlama yoki ruxsat o'zgarganda chaqiriladi: kerak bo'lsa ishga tushadi, kerak bo'lmasa to'xtaydi. */
    public static void refresh() {
        Utilities.globalQueue.postRunnable(() -> {
            synchronized (observers) {
                stopLocked();
                if (!MilliyApkGuard.isEnabled() || !MilliyApkGuard.isAutoDelete() || !MilliyApkGuard.hasAllFilesAccess()) {
                    return;
                }
                for (File root : roots()) {
                    watchTree(root, 0);
                }
            }
        });
    }

    private static void stopLocked() {
        for (FileObserver o : observers) {
            try {
                o.stopWatching();
            } catch (Throwable ignore) {
            }
        }
        observers.clear();
        watchedDirs.clear();
    }

    private static List<File> roots() {
        List<File> list = new ArrayList<>();
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null) {
            return list;
        }
        list.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        list.add(new File(ext, "Telegram"));
        String[] clients = {"org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "uz.codingtech.messengerapp"};
        for (String client : clients) {
            list.add(new File(ext, "Android/media/" + client + "/Telegram"));
        }
        list.add(new File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"));
        return list;
    }

    /** Mavjud fayllarni tozalaydi va har bir papkaga kuzatuvchi qo'yadi (FileObserver rekursiv emas). */
    private static void watchTree(File dir, int depth) {
        if (dir == null || depth > MAX_DEPTH || !dir.isDirectory()) {
            return;
        }
        final String path = dir.getAbsolutePath();
        if (!watchedDirs.add(path)) {
            return;
        }
        FileObserver observer = new FileObserver(path, MASK) {
            @Override
            public void onEvent(int event, String name) {
                if (name == null) {
                    return;
                }
                File f = new File(path, name);
                int type = event & FileObserver.ALL_EVENTS;
                if (type == FileObserver.CREATE) {
                    if (f.isDirectory()) {
                        Utilities.globalQueue.postRunnable(() -> {
                            synchronized (observers) {
                                if (!observers.isEmpty()) {
                                    watchTree(f, depth + 1);
                                }
                            }
                        });
                    }
                    return; // fayl yozib bo'lingach (CLOSE_WRITE) o'chiriladi
                }
                if (MilliyApkGuard.isApkName(name)) {
                    Utilities.globalQueue.postRunnable(() -> deleteIfAllowed(f), 500);
                }
            }
        };
        observer.startWatching();
        observers.add(observer);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    watchTree(f, depth + 1);
                }
            } else if (MilliyApkGuard.isApkName(f.getName())) {
                deleteIfAllowed(f);
            }
        }
    }

    private static void deleteIfAllowed(File f) {
        if (!f.isFile() || !MilliyApkGuard.isEnabled() || !MilliyApkGuard.isAutoDelete() || MilliyApkGuard.isInstallAllowedNow()) {
            return;
        }
        String pkg = null;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
            if (pi != null) {
                pkg = pi.packageName;
                if (ctx.getPackageName().equals(pkg) && isOwnSignature(f)) {
                    return;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        final String name = f.getName();
        if (f.delete() || !f.exists()) {
            MilliyApkGuard.addDeleted(name, pkg, true);
        }
    }

    private static boolean isOwnSignature(File f) {
        MilliyApkGuard.ApkCheckResult r = MilliyApkGuard.checkApk(f);
        return r.certSha256 != null && !r.certSha256.isEmpty() && r.certSha256.equals(MilliyApkGuard.ownCert());
    }
}
