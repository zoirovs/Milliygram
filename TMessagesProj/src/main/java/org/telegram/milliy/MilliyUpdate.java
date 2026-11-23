package org.telegram.milliy;

import android.content.pm.PackageInfo;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.DialogsHintCell;

/**
 * Chatlar ro'yxati tepasidagi karta: «Yangi versiya chiqdi» va paneldan yuboriladigan yangilik.
 * Ma'lumot /api/v1/config ichida ("update", "news"), panel: /panel/app. Karta yopilsa — shu versiya/yangilik
 * qayta chiqmaydi.
 */
public class MilliyUpdate {

    private static boolean requested;

    /** O'rnatilgan versiya raqami (Play/ABI qo'shimchasisiz, gradle.properties APP_VERSION_CODE). */
    public static int installedCode() {
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return info.versionCode / 10;
        } catch (Exception e) {
            FileLog.e(e);
            return Integer.MAX_VALUE;
        }
    }

    private static JSONObject config() {
        try {
            String body = MilliyData.cached("config");
            return body == null ? null : new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * DialogsActivity.updateDialogsHint ichidan. Ko'rsatadigan narsa bo'lsa kartani to'ldirib true qaytaradi.
     * Birinchi chaqiruvda config yangilanadi va tayyor bo'lgach refresh chaqiriladi.
     */
    public static boolean hint(BaseFragment fragment, DialogsHintCell cell, Runnable refresh) {
        if (!requested) {
            requested = true;
            MilliyData.get("config", 60 * 60 * 1000L, b -> refresh.run(), null);
        }
        JSONObject o = config();
        if (o == null) {
            return false;
        }
        // 1. Yangi versiya
        JSONObject u = o.optJSONObject("update");
        if (u != null) {
            final int code = u.optInt("version_code");
            final String url = u.optString("url");
            if (code > installedCode() && !TextUtils.isEmpty(url) && MilliyPro.prefs().getInt("update_closed", 0) < code) {
                cell.setText(emoji(cell, u.optString("title", "Yangi versiya chiqdi")), u.optString("text"));
                cell.setOnClickListener(v -> Browser.openUrl(fragment.getParentActivity(), url));
                cell.setOnCloseListener(v -> {
                    MilliyPro.prefs().edit().putInt("update_closed", code).apply();
                    refresh.run();
                });
                return true;
            }
        }
        // 2. Yangilik
        JSONObject n = o.optJSONObject("news");
        if (n != null && n.optInt("id") > 0 && !TextUtils.isEmpty(n.optString("title"))) {
            final int id = n.optInt("id");
            if (MilliyPro.prefs().getInt("news_closed", 0) != id) {
                final String url = n.optString("url");
                cell.setText(emoji(cell, n.optString("title")), n.optString("text"));
                cell.setOnClickListener(v -> {
                    if (!TextUtils.isEmpty(url)) {
                        Browser.openUrl(fragment.getParentActivity(), url);
                    }
                });
                cell.setOnCloseListener(v -> {
                    MilliyPro.prefs().edit().putInt("news_closed", id).apply();
                    refresh.run();
                });
                return true;
            }
        }
        return false;
    }

    private static CharSequence emoji(DialogsHintCell cell, String s) {
        return Emoji.replaceEmoji(s, cell.titleView.getPaint().getFontMetricsInt(), false);
    }
}
