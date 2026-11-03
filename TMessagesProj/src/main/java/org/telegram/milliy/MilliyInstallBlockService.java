package org.telegram.milliy;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tizim o'rnatish oynasini avtomatik yopadi («Aqlli APK Qo'riqchi»).
 *
 * Xizmat barcha oyna hodisalarini oladi (qurilma ishlab chiqaruvchilarining o'rnatuvchilari turlicha nomlanadi),
 * lekin paket nomi o'rnatuvchi bo'lmasa darhol qaytadi: boshqa ilovalar, jumladan chatlar oynasi o'qilmaydi.
 * O'rnatuvchi oynasidagi matn faqat shu yerda tugma topish uchun ishlatiladi: saqlanmaydi, yuborilmaydi, jurnalga yozilmaydi.
 *
 * O'chirish (uninstall) oynasi hech qachon yopilmaydi, aks holda foydalanuvchi troyanni o'chira olmay qoladi.
 */
public class MilliyInstallBlockService extends AccessibilityService {

    private static final long REPEAT_MS = 800;
    private static final long NOTICE_MS = 3000;
    private static final int MAX_NODES = 300;

    private static final String[] CANCEL_IDS = {"button2", "cancel_button", "negative_button", "cancel"};
    private static final String[] CANCEL_TEXTS = {
            "cancel", "bekor", "bekor qilish", "бекор", "бекор қилиш", "отмена", "отменить", "отклонить",
            "no", "нет", "not now", "не сейчас", "deny", "batal", "tidak", "iptal", "取消"
    };
    private static final String[] UNINSTALL_SIGNS = {
            "uninstall", "deinstal", "удал", "o'chir", "oʻchir", "o‘chir", "ўчир", "hapus", "kaldır", "卸载"
    };
    private static final String[] INSTALL_SIGNS = {
            "install", "o'rnat", "oʻrnat", "o‘rnat", "ўрнат", "установ", "обнов", "yangila", "янгила", "update",
            "instal", "yükle", "安装", "安裝"
    };
    private static final String[] FINISHED_SIGNS = {
            "installed", "o'rnatildi", "oʻrnatildi", "o‘rnatildi", "ўрнатилди", "установлено", "not installed", "не установлено"
    };

    private String lastWindowClass = "";
    private long lastAction;
    private long lastNotice;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Qayta yuklangandan keyin jarayonni aynan shu xizmat ishga tushirishi mumkin
        ApplicationLoader.postInitApplication();
        MilliyApkWatcher.refresh();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        String pkg = event.getPackageName().toString();
        if (!pkg.toLowerCase(Locale.US).contains("packageinstaller")) {
            return; // boshqa ilova: hech narsa o'qilmaydi
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getClassName() != null) {
            lastWindowClass = event.getClassName().toString().toLowerCase(Locale.US);
        }
        if (!MilliyApkGuard.isEnabled() || !MilliyApkGuard.isBlockInstalls() || MilliyApkGuard.isInstallAllowedNow()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAction < REPEAT_MS) {
            return;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null || !isInstallConfirmation(root)) {
                return;
            }
            lastAction = now;
            if (!clickCancel(root, pkg)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
            MilliyApkGuard.onInstallBlocked();
            if (now - lastNotice > NOTICE_MS) {
                lastNotice = now;
                startActivity(new Intent(this, MilliyInstallBlockedActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            recycle(root);
        }
    }

    /** Faqat o'rnatishni tasdiqlash oynasi: o'chirish oynasi va tugagan o'rnatish natijasi emas. */
    private boolean isInstallConfirmation(AccessibilityNodeInfo root) {
        if (lastWindowClass.contains("uninstall")) {
            return false;
        }
        ArrayList<String> texts = new ArrayList<>();
        collectTexts(root, texts);
        boolean install = lastWindowClass.contains("install");
        for (String text : texts) {
            if (containsAny(text, UNINSTALL_SIGNS) || containsAny(text, FINISHED_SIGNS)) {
                return false;
            }
            if (containsAny(text, INSTALL_SIGNS)) {
                install = true;
            }
        }
        return install;
    }

    private static boolean containsAny(String text, String[] signs) {
        for (String sign : signs) {
            if (text.contains(sign)) {
                return true;
            }
        }
        return false;
    }

    private void collectTexts(AccessibilityNodeInfo root, ArrayList<String> out) {
        ArrayList<AccessibilityNodeInfo> queue = new ArrayList<>();
        addChildren(root, queue);
        CharSequence rootText = root.getText();
        if (rootText != null) {
            out.add(rootText.toString().toLowerCase(Locale.getDefault()));
        }
        for (int i = 0; i < queue.size() && i < MAX_NODES; i++) {
            AccessibilityNodeInfo node = queue.get(i);
            CharSequence text = node.getText();
            if (text != null) {
                out.add(text.toString().toLowerCase(Locale.getDefault()));
            }
            addChildren(node, queue);
        }
        for (AccessibilityNodeInfo node : queue) {
            recycle(node);
        }
    }

    private static void addChildren(AccessibilityNodeInfo node, ArrayList<AccessibilityNodeInfo> queue) {
        for (int i = 0; i < node.getChildCount() && queue.size() < MAX_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                queue.add(child);
            }
        }
    }

    private boolean clickCancel(AccessibilityNodeInfo root, String pkg) {
        for (String id : CANCEL_IDS) {
            if (clickFirst(root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id))
                    || clickFirst(root.findAccessibilityNodeInfosByViewId("android:id/" + id))) {
                return true;
            }
        }
        for (String text : CANCEL_TEXTS) {
            List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(text);
            if (found == null) {
                continue;
            }
            ArrayList<AccessibilityNodeInfo> exact = new ArrayList<>();
            for (AccessibilityNodeInfo node : found) {
                CharSequence value = node.getText();
                if (value != null && value.toString().trim().equalsIgnoreCase(text)) {
                    exact.add(node);
                } else {
                    recycle(node);
                }
            }
            if (clickFirst(exact)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickFirst(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return false;
        }
        boolean clicked = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (!clicked) {
                AccessibilityNodeInfo target = node;
                int guard = 0;
                while (target != null && !target.isClickable() && guard++ < 5) {
                    target = target.getParent();
                }
                if (target != null && target.isClickable() && target.isEnabled()) {
                    clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
            recycle(node);
        }
        return clicked;
    }

    private static void recycle(AccessibilityNodeInfo node) {
        if (node != null) {
            try {
                node.recycle();
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
