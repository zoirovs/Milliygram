package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserInfoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Chat ichidagi himoya:
 *  - Telegram xizmat chati (777000, kirish kodlari): qizil «kodni hech kimga bermang» paneli (forward/nusxa MessagesController'da yopilgan);
 *  - notanish odam bilan shaxsiy chat: tepada ma'lumot paneli (raqam davlati, ro'yxatdan o'tgan oy, umumiy guruhlar) + bloklash/shikoyat;
 *  - notanish odam xabarlarida xavfli iboralar (karta, SMS kod, yutuq, pul o'tkazish) — sariq ogohlantirish;
 *  - foydalanuvchi o'zi karta raqami yoki kod yozayotganda — eslatma.
 */
public class MilliyChatGuard implements NotificationCenter.NotificationCenterDelegate {

    private static final long SERVICE_ID = 777000;
    private static final String KEY_HIDDEN = "milliy_guard_hidden_";

    /** Xavfli iboralar (lotin; kirill matn avval lotinga o'giriladi) — o'zbekcha va ruscha. */
    private static final Pattern DANGER = Pattern.compile(
            "karta\\s*(raqam|nomer|ma'lumot)|kartangiz|kartangizni|karta\\s*orqasidagi|cvv|cvc|amal\\s*qilish\\s*muddati" +
            "|sms\\s*kod|smsdagi\\s*kod|kodni\\s*(yubor|ayt|tashla|bering|jo'nat)|kelgan\\s*kod|tasdiqlash\\s*kodi|parolingiz" +
            "|yutuq|yutib\\s*oldingiz|g'olib\\s*bo'ldingiz|sovrin|bonus\\s*oling|pul\\s*(o'tkaz|tashla|yubor)|o'tkazib\\s*(bering|yubor)" +
            "|oldindan\\s*to'lov|komissiya\\s*to'la|hisobingiz\\s*bloklan" +
            "|номер\\s*карт|данные\\s*карт|код\\s*из\\s*смс|смс[\\s-]*код|пришедший\\s*код|назовите\\s*код|пароль" +
            "|выигр|приз|переведите|перевод\\s*денег|предоплат|ваша\\s*карта\\s*заблокирован");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(8600|9860|5614|6262|4\\d{3}|5[1-5]\\d{2})[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)");
    private static final Pattern CODE = Pattern.compile("^\\s*\\d{5,6}\\s*$");

    private static final HashSet<Long> warnedIncoming = new HashSet<>();
    private static final HashSet<String> warnedTyping = new HashSet<>();

    private final ChatActivity fragment;
    private final AnimatedLinearLayout layout;
    private final int account;
    private final long dialogId;
    private final boolean service;
    private LinearLayout panel;
    private TextView text;

    private MilliyChatGuard(ChatActivity fragment, AnimatedLinearLayout layout, long dialogId, boolean service) {
        this.fragment = fragment;
        this.layout = layout;
        this.account = fragment.getCurrentAccount();
        this.dialogId = dialogId;
        this.service = service;
    }

    /** ChatActivity.createView oxirida chaqiriladi; mos kelmasa null. */
    public static MilliyChatGuard attach(ChatActivity fragment, AnimatedLinearLayout layout) {
        if (fragment == null || layout == null || fragment.getContext() == null || fragment.getChatMode() != ChatActivity.MODE_DEFAULT) {
            return null;
        }
        TLRPC.User user = fragment.getCurrentUser();
        if (user == null || fragment.getCurrentEncryptedChat() != null) {
            return null;
        }
        boolean service = user.id == SERVICE_ID;
        if (!service && (user.bot || user.contact || UserObject.isUserSelf(user) || MessagesController.isSupportUser(user) || UserObject.isReplyUser(user) || UserObject.isDeleted(user))) {
            // kontakt bilan chat: faqat yozish paytidagi eslatma ishlaydi
            return new MilliyChatGuard(fragment, layout, user.id, false).noPanel();
        }
        MilliyChatGuard g = new MilliyChatGuard(fragment, layout, user.id, service);
        g.createPanel(fragment.getContext());
        NotificationCenter nc = NotificationCenter.getInstance(g.account);
        nc.addObserver(g, NotificationCenter.peerSettingsDidLoad);
        nc.addObserver(g, NotificationCenter.userInfoDidLoad);
        nc.addObserver(g, NotificationCenter.didReceiveNewMessages);
        nc.addObserver(g, NotificationCenter.messagesDidLoad);
        g.update();
        return g;
    }

    private boolean panelless;

    private MilliyChatGuard noPanel() {
        panelless = true;
        return this;
    }

    public void destroy() {
        if (panelless) {
            return;
        }
        NotificationCenter nc = NotificationCenter.getInstance(account);
        nc.removeObserver(this, NotificationCenter.peerSettingsDidLoad);
        nc.removeObserver(this, NotificationCenter.userInfoDidLoad);
        nc.removeObserver(this, NotificationCenter.didReceiveNewMessages);
        nc.removeObserver(this, NotificationCenter.messagesDidLoad);
    }

    private SharedPreferences prefs() {
        return MessagesController.getNotificationsSettings(account);
    }

    // ---------- Panel ----------

    private void createPanel(Context context) {
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(8), dp(10), dp(8));
        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        text.setLineSpacing(dp(1), 1f);
        panel.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (service) {
            panel.setBackgroundColor(0xFFD93025);
            text.setTextColor(0xFFFFFFFF);
            text.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyCodePanel)));
        } else {
            final boolean dark = Theme.isCurrentThemeDark();
            panel.setBackgroundColor(dark ? 0xFF3A3212 : 0xFFFFF4D2);
            text.setTextColor(dark ? 0xFFFFE08A : 0xFF5A4300);
            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);
            buttons.addView(button(context, R.string.MilliyGuardHide, dark ? 0xFFBFAF7A : 0xFF8A7440, () -> {
                prefs().edit().putBoolean(KEY_HIDDEN + dialogId, true).apply();
                layout.setViewVisible(panel, false, true);
            }));
            buttons.addView(button(context, R.string.MilliyReportShort, 0xFFD93025, () -> MilliyShield.report(fragment, dialogId, null)));
            buttons.addView(button(context, R.string.MilliyBlock, 0xFFD93025, () -> {
                MessagesController.getInstance(account).blockPeer(dialogId);
                Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.MilliyBlocked), Toast.LENGTH_SHORT).show();
                layout.setViewVisible(panel, false, true);
            }));
            panel.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        }
        layout.addView(panel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.setPriority(panel, 30);
        layout.setDebugName(panel, "milliy guard");
    }

    private TextView button(Context context, int res, int color, Runnable onClick) {
        TextView b = new TextView(context);
        b.setText(LocaleController.getString(res));
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        b.setTypeface(AndroidUtilities.bold());
        b.setTextColor(color);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(color, 0.15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void update() {
        if (panel == null) {
            return;
        }
        if (service) {
            layout.setViewVisible(panel, true, false);
            return;
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
        if (user == null || user.contact || prefs().getBoolean(KEY_HIDDEN + dialogId, false)) {
            layout.setViewVisible(panel, false, false);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyGuardNotContact)));
        TLRPC.PeerSettings ps = MessagesController.getInstance(account).getPeerSettings(dialogId);
        if (ps != null && ps.phone_country != null) {
            sb.append(" · ").append(LocaleController.getString(R.string.MilliyGuardPhone)).append(" ")
                    .append(LocaleController.getCountryWithFlag(ps.phone_country, 12));
        }
        if (ps != null && ps.registration_month != null) {
            sb.append(" · ").append(LocaleController.getString(R.string.MilliyGuardRegistered)).append(" ")
                    .append(UserInfoCell.displayDate(ps.registration_month));
        }
        TLRPC.UserFull full = MessagesController.getInstance(account).getUserFull(dialogId);
        if (full != null) {
            sb.append(" · ").append(full.common_chats_count > 0
                    ? LocaleController.formatString(R.string.MilliyGuardCommon, full.common_chats_count)
                    : LocaleController.getString(R.string.MilliyGuardNoCommon));
        }
        text.setText(sb);
        layout.setViewVisible(panel, true, false);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.peerSettingsDidLoad || id == NotificationCenter.userInfoDidLoad) {
            update();
        } else if (id == NotificationCenter.didReceiveNewMessages || id == NotificationCenter.messagesDidLoad) {
            if (args.length > 0 && args[0] instanceof Long && (Long) args[0] == dialogId) {
                AndroidUtilities.runOnUIThread(this::scanIncoming, 300);
            }
        }
    }

    // ---------- Xavfli iboralar ----------

    private static boolean dangerous(CharSequence t) {
        if (TextUtils.isEmpty(t)) {
            return false;
        }
        String s = MilliyTranslit.isMostlyCyrillic(t) ? MilliyTranslit.toLatin(t) + " " + t : t.toString();
        s = s.toLowerCase().replace('ʻ', '\'').replace('ʼ', '\'').replace('‘', '\'').replace('’', '\'').replace('`', '\'');
        return DANGER.matcher(s).find();
    }

    private void scanIncoming() {
        if (service || warnedIncoming.contains(dialogId) || fragment.getParentActivity() == null) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
        if (user == null || user.contact) {
            return;
        }
        ArrayList<MessageObject> list = fragment.messages;
        for (int i = 0, n = Math.min(list.size(), 40); i < n; i++) {
            MessageObject m = list.get(i);
            if (m != null && !m.isOut() && m.getId() > 0 && dangerous(m.messageText)) {
                warnedIncoming.add(dialogId);
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip,
                        LocaleController.getString(R.string.MilliyDangerTitle), LocaleController.getString(R.string.MilliyDangerText),
                        LocaleController.getString(R.string.MilliyReportShort), () -> MilliyShield.report(fragment, dialogId, m))
                        .setDuration(Bulletin.DURATION_PROLONG * 2).show();
                return;
            }
        }
    }

    /** ChatActivity'dagi kiritish maydoni o'zgarganda (statik — kontakt chatlarida ham ishlaydi). */
    public static void onTyping(ChatActivity fragment, CharSequence t) {
        if (fragment == null || TextUtils.isEmpty(t) || t.length() > 400) {
            return;
        }
        final long did = fragment.getDialogId();
        TLRPC.User user = fragment.getCurrentUser();
        final boolean stranger = user != null && !user.contact && !user.bot && !UserObject.isUserSelf(user);
        int res = 0;
        String kind = null;
        if (CARD.matcher(t).find()) {
            res = R.string.MilliyTypingCard;
            kind = "card";
        } else if (stranger && CODE.matcher(t).matches()) {
            res = R.string.MilliyTypingCode;
            kind = "code";
        }
        if (res == 0 || !warnedTyping.add(did + ":" + kind)) {
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.MilliyTypingTitle),
                LocaleController.getString(res)).setDuration(Bulletin.DURATION_PROLONG).show();
    }
}
