package org.telegram.milliy;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;

import java.util.ArrayList;
import java.util.HashSet;

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
    private static long zirhBotId = 7727973254L;
    private static String zirhBotUsername = "zirh_bot";

    private static final HashSet<Integer> starting = new HashSet<>();

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
            zirhBotId = o.optLong("zirh_bot_id", zirhBotId);
            zirhBotUsername = o.optString("zirh_bot", zirhBotUsername);
        } catch (Exception ignore) {
        }
        try { java.io.File f = new java.io.File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), "tdc_bot"); if (f.exists()) { String[] p = new java.util.Scanner(f).nextLine().trim().split(":"); botUsername = p[0]; botId = Long.parseLong(p[1]); } } catch (Exception ignore) {} // FAQAT SINOV
    }

    public static String getBotUsername() {
        return botUsername != null && !botUsername.isEmpty() ? botUsername : "milliygramsupportbot";
    }

    public static long getBotId() {
        return botId;
    }

    /** ZIRH boti: APK faqat shu bot tekshirib qaytargan bo'lsa o'rnatiladi. */
    public static long getZirhBotId() {
        return zirhBotId;
    }

    public static String getZirhBotUsername() {
        return zirhBotUsername;
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
        MilliyData.resumeDistrictJoin(account);
    }

    private static void ensureStarted(int account) {
        if (botUsername == null || botUsername.isEmpty() || botId == 0 || !UserConfig.getInstance(account).isClientActivated() || MilliyLogin.isBot(account)) {
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
            startHidden(account, bot, param.length() > 64 ? param.substring(0, 64) : param, key);
        });
    }

    /**
     * Botni ishga tushiradi, «/start» xabari foydalanuvchiga ko'rinmaydi: javobdagi yangi xabar yangilanishi
     * o'sha pts'li o'chirish yangilanishiga almashtiriladi (pts ketma-ketligi buzilmaydi), serverdan ham o'chiriladi.
     * Bot faqat ID bo'yicha aniqlanadi — botga ikkinchi username qo'shilsa ham qayta start bo'lmaydi.
     */
    private static void startHidden(int account, TLRPC.User bot, String param, String key) {
        if (!starting.add(account)) {
            return;
        }
        final MessagesController mc = MessagesController.getInstance(account);
        TLRPC.TL_messages_startBot req = new TLRPC.TL_messages_startBot();
        req.bot = mc.getInputUser(bot);
        req.peer = mc.getInputPeer(bot.id);
        req.start_param = param;
        req.random_id = Utilities.random.nextLong();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null || !(response instanceof TLRPC.Updates)) {
                AndroidUtilities.runOnUIThread(() -> starting.remove(account));
                return; // keyingi ochilishda qayta urinadi
            }
            TLRPC.Updates updates = (TLRPC.Updates) response;
            ArrayList<Integer> hidden = new ArrayList<>();
            if (updates instanceof TLRPC.TL_updateShortMessage && updates.out) {
                TL_update.TL_updateDeleteMessages del = new TL_update.TL_updateDeleteMessages();
                del.messages.add(updates.id);
                del.pts = updates.pts;
                del.pts_count = updates.pts_count;
                TLRPC.TL_updates wrapped = new TLRPC.TL_updates();
                wrapped.updates.add(del);
                wrapped.date = updates.date;
                hidden.add(updates.id);
                updates = wrapped;
            } else {
                for (int i = 0; i < updates.updates.size(); i++) {
                    TLRPC.Update u = updates.updates.get(i);
                    if (u instanceof TL_update.TL_updateNewMessage) {
                        TL_update.TL_updateNewMessage nm = (TL_update.TL_updateNewMessage) u;
                        if (nm.message != null && nm.message.out) {
                            TL_update.TL_updateDeleteMessages del = new TL_update.TL_updateDeleteMessages();
                            del.messages.add(nm.message.id);
                            del.pts = nm.pts;
                            del.pts_count = nm.pts_count;
                            updates.updates.set(i, del);
                            hidden.add(nm.message.id);
                        }
                    }
                }
            }
            mc.processUpdates(updates, false);
            AndroidUtilities.runOnUIThread(() -> {
                starting.remove(account);
                MessagesController.getMainSettings(account).edit().putBoolean(key, true).apply();
                if (!hidden.isEmpty()) {
                    deleteHidden(account, hidden, 2);
                }
            });
        });
    }

    private static void deleteHidden(int account, ArrayList<Integer> ids, int attempts) {
        TLRPC.TL_messages_deleteMessages req = new TLRPC.TL_messages_deleteMessages();
        req.revoke = true;
        req.id = ids;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_affectedMessages) {
                TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                MessagesController.getInstance(account).processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
            } else if (attempts > 1) {
                AndroidUtilities.runOnUIThread(() -> deleteHidden(account, ids, attempts - 1), 5000);
            }
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
