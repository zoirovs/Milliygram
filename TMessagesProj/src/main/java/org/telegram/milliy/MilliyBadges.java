package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Milliygram belgilari: rasmiy kanallar (yashil «✓ RASMIY») va firibgar kanallar (qizil «! FIRIBGAR»).
 * Ro'yxat serverdagi /panel/badges dan boshqariladi. Telegram'ning o'z scam/fake belgisi bo'lsa, u ustun turadi.
 */
public class MilliyBadges {

    public static final int NONE = 0, OFFICIAL = 1, SCAM = 2;

    private static final String PATH = "badges";
    private static final long MAX_AGE = 60 * 60 * 1000L;
    private static final long CHANNEL_SHIFT = -1000000000000L; // Bot API: kanal ID = -100…

    private static HashMap<Long, Integer> byId = new HashMap<>();
    private static HashMap<String, Integer> byUsername = new HashMap<>();
    private static String loadedBody;
    private static final HashSet<Long> warned = new HashSet<>();

    static {
        apply(MilliyData.cached(PATH));
    }

    /** Serverdan yangilaydi (kesh 1 soat). O'zgargan bo'lsa ro'yxatlar qayta chiziladi. */
    public static void refresh() {
        MilliyData.get(PATH, MAX_AGE, body -> {
            if (body != null && !body.equals(loadedBody)) {
                apply(body);
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                    }
                }
            }
        }, null);
    }

    private static void apply(String body) {
        if (body == null) {
            return;
        }
        HashMap<Long, Integer> ids = new HashMap<>();
        HashMap<String, Integer> names = new HashMap<>();
        try {
            JSONArray items = new JSONObject(body).getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                int type = "s".equals(o.optString("t")) ? SCAM : OFFICIAL;
                if (!o.isNull("id")) {
                    // ID ma'lum bo'lsa faqat shu bo'yicha: username boshqa birovga o'tsa belgi ko'chmaydi
                    ids.put(o.getLong("id"), type);
                } else if (!o.isNull("u")) {
                    names.put(o.getString("u").toLowerCase(), type);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        byId = ids;
        byUsername = names;
        loadedBody = body;
    }

    public static int of(TLRPC.Chat chat) {
        if (chat == null || chat.scam || chat.fake) {
            return NONE;
        }
        Integer t = byId.get(CHANNEL_SHIFT - chat.id);
        if (t == null) {
            t = byId.get(-chat.id); // oddiy guruh
        }
        return t != null ? t : ofUsernames(chat.username, chat.usernames);
    }

    public static int of(TLRPC.User user) {
        if (user == null || user.scam || user.fake) {
            return NONE;
        }
        Integer t = byId.get(user.id);
        return t != null ? t : ofUsernames(user.username, user.usernames);
    }

    public static int of(TLRPC.Chat chat, TLRPC.User user) {
        return chat != null ? of(chat) : of(user);
    }

    private static int ofUsernames(String username, java.util.ArrayList<TLRPC.TL_username> usernames) {
        if (byUsername.isEmpty()) {
            return NONE;
        }
        if (username != null) {
            Integer t = byUsername.get(username.toLowerCase());
            if (t != null) {
                return t;
            }
        }
        if (usernames != null) {
            for (TLRPC.TL_username u : usernames) {
                if (u.active && u.username != null) {
                    Integer t = byUsername.get(u.username.toLowerCase());
                    if (t != null) {
                        return t;
                    }
                }
            }
        }
        return NONE;
    }

    public static String description(int type) {
        return LocaleController.getString(type == SCAM ? R.string.MilliyBadgeScamDescr : R.string.MilliyBadgeOfficialDescr);
    }

    /** Firibgar kanal/foydalanuvchi ochilganda bir marta (ilova sessiyasida) ogohlantiradi. */
    public static void warnIfScam(BaseFragment fragment, long dialogId, int type) {
        if (type != SCAM || !warned.add(dialogId)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() == null || fragment.isFinished) {
                warned.remove(dialogId);
                return;
            }
            AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                    .setTitle(LocaleController.getString(R.string.MilliyScamWarnTitle))
                    .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyScamWarnText)))
                    .setPositiveButton(LocaleController.getString(R.string.MilliyScamWarnOk), null)
                    .create();
            fragment.showDialog(dialog);
        }, 400);
    }

    // ---------- Belgi ----------

    private static final HashMap<Integer, BadgeDrawable> cache = new HashMap<>();

    /** Dialoglar ro'yxati uchun umumiy (keshlangan) belgi; alohida view'larga new BadgeDrawable(type) bering. */
    public static Drawable drawable(int type) {
        BadgeDrawable d = cache.get(type);
        if (d == null) {
            cache.put(type, d = new BadgeDrawable(type));
        }
        d.checkText();
        return d;
    }

    public static class BadgeDrawable extends Drawable {
        private final int type;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private String label;
        private int labelWidth;

        public BadgeDrawable(int type) {
            this.type = type;
            bg.setColor(type == SCAM ? 0xFFD93025 : 0xFF1E9E5A);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(dp(10));
            text.setTypeface(AndroidUtilities.bold());
            icon.setColor(0xFFFFFFFF);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setStrokeJoin(Paint.Join.ROUND);
            icon.setStrokeWidth(dp(1.6f));
            checkText();
        }

        void checkText() {
            String s = LocaleController.getString(type == SCAM ? R.string.MilliyBadgeScam : R.string.MilliyBadgeOfficial);
            if (!s.equals(label)) {
                label = s;
                labelWidth = (int) Math.ceil(text.measureText(label));
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(5 + 8 + 3 + 5) + labelWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(16);
        }

        @Override
        public void draw(Canvas canvas) {
            rect.set(getBounds());
            // sarlavhalarda bounds kengroq berilishi mumkin — o'z o'lchamimizda, vertikal markazda chizamiz
            float top = rect.centerY() - dp(8);
            rect.set(rect.left, top, rect.left + getIntrinsicWidth(), top + dp(16));
            canvas.drawRoundRect(rect, dp(8), dp(8), bg);
            float ix = rect.left + dp(5), cy = rect.centerY();
            path.reset();
            if (type == SCAM) {
                canvas.drawLine(ix + dp(4), cy - dp(4.5f), ix + dp(4), cy + dp(1), icon);
                canvas.drawPoint(ix + dp(4), cy + dp(4), icon);
            } else {
                path.moveTo(ix + dp(0.5f), cy);
                path.lineTo(ix + dp(3), cy + dp(2.7f));
                path.lineTo(ix + dp(7.5f), cy - dp(3));
                canvas.drawPath(path, icon);
            }
            canvas.drawText(label, ix + dp(8 + 3), cy + dp(3.6f), text);
        }

        @Override
        public void setAlpha(int alpha) {
            bg.setAlpha(alpha);
            icon.setAlpha(alpha);
            text.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
