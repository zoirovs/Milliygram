package org.telegram.milliy;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

/**
 * «Milliygram» xizmat chati (Telegram'ning 777000 chatiga o'xshash): rasmiy bot orqali firibgarlik ogohlantirishlari
 * va e'lonlar keladi. Ilova botni har akkauntda bir marta tuman bilan ishga tushiradi; yangi xabar kelgach chat
 * 24 soat ro'yxatning eng tepasida turadi, keyin vaqti bo'yicha joylashadi. Belgisi — ✓ RASMIY (server badges).
 */
public class MilliyService {

    private static final String KEY_STARTED = "milliy_service_started_";
    private static final long CONFIG_TTL = 6 * 60 * 60 * 1000L;
    private static final int HOT_SECONDS = 24 * 60 * 60;

    private static long botId;
    private static String botUsername;

    static {
        apply(MilliyData.cached("config"));
    }

    private static void apply(String body) {
        if (body == null) {
            return;
        }
        try {
            JSONObject o = new JSONObject(body);
            botId = o.optLong("service_bot_id");
            botUsername = o.optString("service_bot", null);
        } catch (Exception ignore) {
        }
    }

    public static String getBotUsername() {
        return botUsername != null && !botUsername.isEmpty() ? botUsername : "milliygramsupportbot";
    }

    public static long getBotId() {
        return botId;
    }

    /** dialogComparator uchun: xizmat chatida oxirgi 24 soatda xabar bo'lsa — eng tepada. */
    public static boolean isHot(TLRPC.Dialog d) {
        return botId != 0 && d != null && d.id == botId
                && System.currentTimeMillis() / 1000 - d.last_message_date < HOT_SECONDS;
    }

    /** DialogsActivity.onResume: sozlamani yangilaydi va bot hali ishga tushirilmagan bo'lsa — ishga tushiradi. */
    public static void refresh(int account) {
        MilliyData.get("config", CONFIG_TTL, body -> {
            apply(body);
            ensureStarted(account);
        }, null);
    }

    private static void ensureStarted(int account) {
        if (botUsername == null || botUsername.isEmpty() || botId == 0 || !UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        final SharedPreferences prefs = MessagesController.getMainSettings(account);
        final String key = KEY_STARTED + botId;
        if (prefs.getBoolean(key, false)) {
            return;
        }
        final MessagesController mc = MessagesController.getInstance(account);
        mc.getUserNameResolver().resolve(botUsername, peerId -> {
            if (peerId == null || peerId != botId) {
                return;
            }
            TLRPC.User bot = mc.getUser(botId);
            if (bot == null || prefs.getBoolean(key, false)) {
                return;
            }
            String district = MilliyData.getDistrict(account);
            // start parametri: faqat A-Z a-z 0-9 _ - (64 belgigacha)
            String param = district == null ? "milliy" : ("d_" + district.replaceAll("[^A-Za-z0-9_-]", "-"));
            mc.sendBotStart(bot, param.length() > 64 ? param.substring(0, 64) : param);
            prefs.edit().putBoolean(key, true).apply();
        });
    }

    /** Hudud o'zgarganda serverga xabar beradi (xizmat xabarlari hudud bo'yicha yuborilishi uchun). */
    public static void districtChanged(int account) {
        if (botId == 0) {
            return;
        }
        String district = MilliyData.getDistrict(account);
        long uid = UserConfig.getInstance(account).getClientUserId();
        if (district == null || uid == 0) {
            return;
        }
        MilliyData.get("service/district?user_id=" + uid + "&district=" + MilliyData.enc(district), 0, b -> {}, null);
    }
}
