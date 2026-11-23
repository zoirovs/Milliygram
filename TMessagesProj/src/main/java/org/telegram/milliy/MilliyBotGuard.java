package org.telegram.milliy;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.util.HashSet;
import java.util.Set;

/**
 * Bot havolalaridan himoya. Firibgarlar «bepul sovg'a / pul / Premium» deb botga olib kirib,
 * Telegram kodi, parol yoki karta ma'lumotini so'raydi.
 *
 * Qaysi yo'l bilan ochilmasin (havola, @mention, tugma, qidiruv) — bot chati ochilganda tekshiriladi:
 *  - Milliygram FIRIBGAR belgisi → chat bloklanadi;
 *  - RASMIY / TASDIQLANGAN / Telegram tasdiqlagan bot, o'zimizning botlar va ilgari yozishilgan bot → indamaydi;
 *  - yangi, noma'lum bot → bir martalik ogohlantirish (Davom etish / Ortga).
 */
public class MilliyBotGuard {

    private static final String KEY_SEEN = "bot_guard_seen_";
    private static final Set<Long> askedNow = new HashSet<>();

    public static boolean enabled() {
        return MilliyPro.get("bot_guard", true);
    }

    private static Set<String> seen(int account) {
        return new HashSet<>(MilliyPro.prefs().getStringSet(KEY_SEEN + account, new HashSet<>()));
    }

    private static void markSeen(int account, long botId) {
        Set<String> set = seen(account);
        set.add(String.valueOf(botId));
        MilliyPro.prefs().edit().putStringSet(KEY_SEEN + account, set).apply();
    }

    /** Ishonchli botmi (ogohlantirish kerak emas). */
    private static boolean trusted(int account, TLRPC.User bot) {
        if (bot.verified || MessagesController.isSupportUser(bot)) {
            return true;
        }
        int badge = MilliyBadges.of(bot);
        if (badge == MilliyBadges.OFFICIAL || badge == MilliyBadges.VERIFIED) {
            return true;
        }
        if (bot.id == MilliyService.getBotId() || bot.id == MilliyService.getZirhBotId()) {
            return true;
        }
        if (seen(account).contains(String.valueOf(bot.id))) {
            return true;
        }
        // ilgari yozishilgan bot (ro'yxatda bor) — tanish
        return MessagesController.getInstance(account).dialogs_dict.get(bot.id) != null;
    }

    /**
     * ChatActivity.updateTitle'dan chaqiriladi. true — bot firibgar deb bloklandi
     * (umumiy firibgar ogohlantirishi bu holda ko'rsatilmasin).
     */
    public static boolean check(ChatActivity fragment) {
        if (fragment == null || fragment.getChatMode() != ChatActivity.MODE_DEFAULT || !enabled()) {
            return false;
        }
        final TLRPC.User bot = fragment.getCurrentUser();
        if (bot == null || !bot.bot) {
            return false;
        }
        final int account = fragment.getCurrentAccount();
        final boolean scam = MilliyBadges.of(bot) == MilliyBadges.SCAM;
        if (!scam && trusted(account, bot)) {
            return false;
        }
        if (!askedNow.add(bot.id)) {
            return scam;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() == null || fragment.isFinished) {
                askedNow.remove(bot.id);
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
            if (scam) {
                b.setTitle(LocaleController.getString(R.string.MilliyBotGuardScamTitle));
                b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyBotGuardScamText)));
                b.setPositiveButton(LocaleController.getString(R.string.MilliyBotGuardBack), (d, w) -> fragment.finishFragment());
                b.setOnCancelListener(d -> fragment.finishFragment());
            } else {
                b.setTitle(LocaleController.getString(R.string.MilliyBotGuardTitle));
                b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyBotGuardText,
                        bot.username != null ? "@" + bot.username : bot.first_name)));
                b.setPositiveButton(LocaleController.getString(R.string.MilliyBotGuardBack), (d, w) -> {
                    askedNow.remove(bot.id);
                    fragment.finishFragment();
                });
                b.setNegativeButton(LocaleController.getString(R.string.MilliyBotGuardContinue), (d, w) -> markSeen(account, bot.id));
                b.setOnCancelListener(d -> markSeen(account, bot.id));
            }
            AlertDialog dialog = b.create();
            dialog.setCanceledOnTouchOutside(false);
            fragment.showDialog(dialog);
            if (scam) {
                android.view.View button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
                if (button instanceof android.widget.TextView) {
                    ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            }
        }, 350);
        return scam;
    }
}
