package org.telegram.milliy;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** Sozlamalar → Milliygram → «Milliygram sozlamalari»: hikoyalar, xabarlar, ghost, qulf, seyf, begonalar va boshqalar. */
public class MilliyProActivity extends BaseFragment {

    private LinearLayout root;
    private final boolean insideTabs;

    public MilliyProActivity() {
        this(false);
    }

    /** insideTabs: pastki paneldagi «Milliygram» tabi — orqaga tugmasi yo'q, pastda panel uchun joy. */
    public MilliyProActivity(boolean insideTabs) {
        this.insideTabs = insideTabs;
    }

    @Override
    public View createView(Context context) {
        if (!insideTabs) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setTitle(LocaleController.getString(insideTabs ? R.string.MilliyTab : R.string.MilliyPro));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        if (insideTabs) { // tab ichida sarlavha sahifa ustida turadi — kontent uning tagidan boshlansin
            outer.setPadding(0, org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? org.telegram.messenger.AndroidUtilities.statusBarHeight : 0), 0, 0);
        }

        // Qidiruv: 40+ sozlama ichidan kerakligini darhol topish
        search = new org.telegram.ui.Components.EditTextBoldCursor(context);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        search.setHint(LocaleController.getString(R.string.MilliySettingsSearch));
        search.setSingleLine(true);
        search.setPadding(org.telegram.messenger.AndroidUtilities.dp(14), 0, org.telegram.messenger.AndroidUtilities.dp(14), 0);
        search.setBackground(Theme.createRoundRectDrawable(org.telegram.messenger.AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundWhite)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable e) { filter(e.toString()); }
        });
        outer.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 12, 10, 12, 6));

        ScrollView scroll = new ScrollView(context);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        outer.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        fragmentView = outer;
        build();
        return outer;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (root != null) {
            build();
        }
    }

    /** Bitta sahifa: bo'limlar Novagram tartibida, har bir qatorda qisqa izoh. */
    private void build() {
        root.removeAllViews();
        sections.clear();
        tilesView = null;
        loadIcons();
        buildQuick();
        buildStories();
        buildMessages();
        buildAutoreply();
        buildGhost();
        buildLock();
        buildStrangers();
        buildConfirm();
        buildMedia();
        buildView();
        buildAdmins();
        buildChats();
        buildTranslate();
        buildVoice();
        buildAi();
        buildStatus();
        buildContacts();
        buildBackup();
        buildOther();
        if (insideTabs) { // oxirgi qator pastki panel ostida qolmasin
            root.addView(new View(getParentActivity()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
        }
        if (search != null && search.length() > 0) {
            filter(search.getText().toString());
        }
    }

    /** Eng ko'p kerak bo'ladiganlar — sahifa boshida: 4 ta katta tugma va «Asosiy» karta. */
    private void buildQuick() {
        LinearLayout grid = new LinearLayout(getParentActivity());
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(getParentActivity()), r2 = new LinearLayout(getParentActivity());
        boolean ads = MilliyPro.hideAds(currentAccount);
        String district = MilliyData.getDistrictName(currentAccount);
        tile(r1, R.string.MilliyHideAds, LocaleController.getString(ads ? R.string.MilliyOn : R.string.MilliyOff), () -> {
            MilliyPro.setHideAds(currentAccount, !MilliyPro.hideAds(currentAccount));
            build();
        });
        tile(r1, R.string.MilliyUiScript, MilliyTranslit.uiScriptName(), () -> MilliyTranslit.chooseLanguage(this));
        tile(r2, R.string.MilliyMyRegion, district != null ? MilliyTranslit.ui(district) : LocaleController.getString(R.string.MilliyChooseRegion),
                () -> presentFragment(new MilliyRegionActivity(false)));
        if (!MilliyLogin.isBot(currentAccount)) { // bot akkauntda 2FA/seans so'rovlari ishlamaydi
            tile(r2, R.string.MilliySecurity, LocaleController.getString(R.string.MilliySecOpen), () -> presentFragment(new MilliySecurityActivity()));
        } else {
            tile(r2, R.string.MilliyInfoBar, LocaleController.getString(MilliyInfo.enabled() ? R.string.MilliyOn : R.string.MilliyOff), () -> {
                MilliyPro.set("info_bar", !MilliyInfo.enabled());
                build();
            });
        }
        grid.addView(r1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        grid.addView(r2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        root.addView(grid, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 6, 12, 4));
        tilesView = grid;

        header(R.string.MilliyQuickHeader);
        check(R.string.MilliyInfoBar, R.string.MilliyInfoBarInfo, "info_bar", true, null);
        info(R.string.MilliyQuickHint);
    }

    private View tilesView;

    /** Katta tezkor tugma: rangli ikonka, nom va holat. */
    private void tile(LinearLayout row, int title, String value, Runnable click) {
        int[] ic = icons.get(LocaleController.getString(title));
        LinearLayout t = new LinearLayout(getParentActivity());
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(org.telegram.messenger.AndroidUtilities.dp(14), org.telegram.messenger.AndroidUtilities.dp(12), org.telegram.messenger.AndroidUtilities.dp(12), org.telegram.messenger.AndroidUtilities.dp(12));
        t.setBackground(Theme.createRoundRectDrawable(org.telegram.messenger.AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
        android.widget.FrameLayout iconBox = new android.widget.FrameLayout(getParentActivity());
        org.telegram.ui.SettingsActivity.SettingCell.Background bg = new org.telegram.ui.SettingsActivity.SettingCell.Background();
        bg.setColor(ic != null ? ic[1] : 0xFF4FA3F7, ic != null ? ic[2] : 0xFF2F7FE0);
        iconBox.setBackground(bg);
        android.widget.ImageView iv = new android.widget.ImageView(getParentActivity());
        if (ic != null) iv.setImageResource(ic[0]);
        iv.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
        iconBox.addView(iv, LayoutHelper.createFrame(22, 22, android.view.Gravity.CENTER));
        t.addView(iconBox, LayoutHelper.createLinear(34, 34));
        android.widget.TextView name = new android.widget.TextView(getParentActivity());
        name.setText(LocaleController.getString(title));
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setSingleLine();
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        android.widget.TextView val = new android.widget.TextView(getParentActivity());
        val.setText(value);
        val.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        val.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        val.setSingleLine();
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(val, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        t.setOnClickListener(v -> click.run());
        t.setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        row.addView(t, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, row.getChildCount() == 0 ? 0 : 10, 0, 0, 0));
    }

    /** Qator ikonkalari: sarlavha matni → {ikonka, rang yuqori, rang pastki}. */
    private final java.util.HashMap<String, int[]> icons = new java.util.HashMap<>();

    private void loadIcons() {
        icons.clear();
        icons.put(LocaleController.getString(R.string.MilliyHideAds), new int[]{R.drawable.msg_block, 0xFFFF6B6B, 0xFFE5484D});
        icons.put(LocaleController.getString(R.string.MilliyInfoBar), new int[]{R.drawable.settings_milliy_weather, 0xFF4DD0F2, 0xFF1DA7D3});
        icons.put(LocaleController.getString(R.string.MilliyUiScript), new int[]{R.drawable.msg_language, 0xFFF77BB5, 0xFFE0497F});
        icons.put(LocaleController.getString(R.string.MilliyMyRegion), new int[]{R.drawable.msg_location, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.MilliySecurity), new int[]{R.drawable.outline_shield_check, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyStoryAnon), new int[]{R.drawable.msg_stories_stealth, 0xFFA97BF5, 0xFF7E57C2});
        icons.put(LocaleController.getString(R.string.MilliyHideStories), new int[]{R.drawable.msg_stories_myhide, 0xFFA97BF5, 0xFF7E57C2});
        icons.put(LocaleController.getString(R.string.MilliyStoryDownload), new int[]{R.drawable.msg_stories_save, 0xFFA97BF5, 0xFF7E57C2});
        icons.put(LocaleController.getString(R.string.MilliyKeepDeleted), new int[]{R.drawable.msg_delete, 0xFFFF6B6B, 0xFFE5484D});
        icons.put(LocaleController.getString(R.string.MilliyKeepEdits), new int[]{R.drawable.msg_edit, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.MilliyConfirmGift), new int[]{R.drawable.msg_gift_premium, 0xFFF77BB5, 0xFFE0497F});
        icons.put(LocaleController.getString(R.string.MilliyOneTimeVoice), new int[]{R.drawable.msg_voicechat, 0xFF3FD3C4, 0xFF16A99A});
        icons.put(LocaleController.getString(R.string.MilliyTimeSeconds), new int[]{R.drawable.outline_message_time_24, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyAutoReply), new int[]{R.drawable.menu_quickreply, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyAutoReplyText), new int[]{R.drawable.msg_message, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyAutoReplyEvery), new int[]{R.drawable.msg_autodelete, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyGhost), new int[]{R.drawable.msg_stories_stealth2, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyGhostOnline), new int[]{R.drawable.msg_contacts_time, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyGhostTyping), new int[]{R.drawable.msg_edit, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyGhostRead), new int[]{R.drawable.msg_folders_read, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyGhostStory), new int[]{R.drawable.msg_stories_views, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyGhostButton), new int[]{R.drawable.menu_sidebar_top, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyLockPinSet), new int[]{R.drawable.msg_pin_code, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyLockPinChange), new int[]{R.drawable.msg_pin_code, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyLockedChats), new int[]{R.drawable.msg_secret, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyVault), new int[]{R.drawable.msg_archive_hide, 0xFF9AA6B2, 0xFF6F7C88});
        icons.put(LocaleController.getString(R.string.MilliyPinDigits), new int[]{R.drawable.msg_pin_code, 0xFF9AA6B2, 0xFF6F7C88});
        icons.put(LocaleController.getString(R.string.MilliyFingerprint), new int[]{R.drawable.filled_access_fingerprint, 0xFF3FD3C4, 0xFF16A99A});
        icons.put(LocaleController.getString(R.string.MilliyStrangers), new int[]{R.drawable.msg_user_remove, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.MilliyStrangersTitle), new int[]{R.drawable.msg_contacts, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.MilliyConfirmStickerTitle), new int[]{R.drawable.msg_sticker, 0xFFF77BB5, 0xFFE0497F});
        icons.put(LocaleController.getString(R.string.MilliyConfirmGifTitle), new int[]{R.drawable.msg_gif, 0xFFF77BB5, 0xFFE0497F});
        icons.put(LocaleController.getString(R.string.MilliyConfirmVoiceTitle), new int[]{R.drawable.msg_voicechat2, 0xFFF77BB5, 0xFFE0497F});
        icons.put(LocaleController.getString(R.string.MilliyAutoDl), new int[]{R.drawable.msg_download_settings, 0xFF4DD0F2, 0xFF1DA7D3});
        icons.put(LocaleController.getString(R.string.MilliyRoundBack), new int[]{R.drawable.msg_video, 0xFF4DD0F2, 0xFF1DA7D3});
        icons.put(LocaleController.getString(R.string.MilliyHideTabs), new int[]{R.drawable.msg_folders, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyAvatarProfile), new int[]{R.drawable.msg_premium_avatar, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyAvatarShape), new int[]{R.drawable.msg_addphoto, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyThemeEditor), new int[]{R.drawable.msg_palette, 0xFFA97BF5, 0xFF7E57C2});
        icons.put(LocaleController.getString(R.string.MilliyLite), new int[]{R.drawable.settings_power, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.SettingsFolders), new int[]{R.drawable.settings_folders, 0xFF4FA3F7, 0xFF2F7FE0});
        icons.put(LocaleController.getString(R.string.MilliyQuickBar), new int[]{R.drawable.menu_sidebar_bottom, 0xFF3FD3C4, 0xFF16A99A});
        icons.put(LocaleController.getString(R.string.MilliyCategories), new int[]{R.drawable.menu_tag_filter, 0xFF3FD3C4, 0xFF16A99A});
        icons.put(LocaleController.getString(R.string.MilliyDlManager), new int[]{R.drawable.msg_download, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyAutoTranslate), new int[]{R.drawable.msg_translate, 0xFF7C8CF8, 0xFF5466E0});
        icons.put(LocaleController.getString(R.string.MilliyDictationLang), new int[]{R.drawable.msg_voice_unmuted, 0xFFFF6B6B, 0xFFE5484D});
        icons.put(LocaleController.getString(R.string.MilliyVoiceFx), new int[]{R.drawable.msg_voice_headphones, 0xFFFF6B6B, 0xFFE5484D});
        icons.put(LocaleController.getString(R.string.MilliyStatus), new int[]{R.drawable.msg_smile_status, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.MilliyContactLogTitle), new int[]{R.drawable.msg_contacts_name, 0xFF3FD3C4, 0xFF16A99A});
        icons.put(LocaleController.getString(R.string.MilliyContactLog), new int[]{R.drawable.msg_contacts_time, 0xFF3FD3C4, 0xFF16A99A});
        icons.put(LocaleController.getString(R.string.MilliySpecialList), new int[]{R.drawable.msg_premium_liststar, 0xFFFFAA44, 0xFFF07F1A});
        icons.put(LocaleController.getString(R.string.MilliyBackupExport), new int[]{R.drawable.msg_saved, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyBackupRestore), new int[]{R.drawable.msg_unarchive, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliyShowId), new int[]{R.drawable.filled_username, 0xFF9AA6B2, 0xFF6F7C88});
        icons.put(LocaleController.getString(R.string.MilliyApkShield), new int[]{R.drawable.outline_shield_plain_24, 0xFF5BD06A, 0xFF34A853});
        icons.put(LocaleController.getString(R.string.MilliySecretCodes), new int[]{R.drawable.menu_feature_code, 0xFF9AA6B2, 0xFF6F7C88});
        icons.put(LocaleController.getString(R.string.MilliyCopySettingsLink), new int[]{R.drawable.msg_link, 0xFF9AA6B2, 0xFF6F7C88});
    }

    // ---------- Qidiruv ----------

    private org.telegram.ui.Components.EditTextBoldCursor search;

    private static class Section {
        View header, card, info;
        final java.util.ArrayList<View> rows = new java.util.ArrayList<>();
        final java.util.ArrayList<String> texts = new java.util.ArrayList<>();
    }

    private final java.util.ArrayList<Section> sections = new java.util.ArrayList<>();

    private Section current() {
        if (sections.isEmpty()) {
            header(0);
        }
        return sections.get(sections.size() - 1);
    }

    /** Qatorni joriy kartaga qo'shadi (oldingi qatorga ajratuvchi chiziq). */
    private void addRow(MilliyRow row, String text) {
        Section sec = current();
        if (!sec.rows.isEmpty()) {
            ((MilliyRow) sec.rows.get(sec.rows.size() - 1)).setDivider(true);
        }
        ((LinearLayout) sec.card).addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        sec.rows.add(row);
        sec.texts.add(text == null ? "" : text.toLowerCase());
    }

    private MilliyRow newRow(String title) {
        int[] ic = icons.get(title);
        return new MilliyRow(getParentActivity(), ic != null ? ic[0] : R.drawable.msg_info, ic != null ? ic[1] : 0xFF9AA6B2, ic != null ? ic[2] : 0xFF6F7C88);
    }

    private void filter(String query) {
        String q = query.trim().toLowerCase();
        String q2 = MilliyTranslit.isMostlyCyrillic(q) ? MilliyTranslit.toLatin(q).toLowerCase() : MilliyTranslit.toCyrillic(q).toLowerCase();
        if (tilesView != null) {
            tilesView.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
        }
        for (Section sec : sections) {
            boolean any = false;
            for (int i = 0; i < sec.rows.size(); i++) {
                String t = sec.texts.get(i);
                boolean show = q.isEmpty() || t.contains(q) || t.contains(q2);
                sec.rows.get(i).setVisibility(show ? View.VISIBLE : View.GONE);
                any |= show;
            }
            if (sec.header != null) {
                sec.header.setVisibility(any ? View.VISIBLE : View.GONE);
            }
            sec.card.setVisibility(any ? View.VISIBLE : View.GONE);
            if (sec.info != null) {
                sec.info.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void customCheck(String title, String subtitle, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        MilliyRow c = newRow(title).setTexts(title, subtitle).setSwitch(getter.getAsBoolean());
        c.setOnClickListener(v -> {
            boolean nv = !getter.getAsBoolean();
            setter.accept(nv);
            c.setChecked(nv);
        });
        addRow(c, title + " " + subtitle);
    }

    private void buildStatus() {
        // Mening holatim
        header(R.string.MilliyStatus);
        String status = MilliyStatus.current(currentAccount);
        action(LocaleController.getString(R.string.MilliyStatus), status != null ? status : LocaleController.getString(R.string.MilliyNotSet),
                () -> MilliyStatus.show(this));
        info(R.string.MilliyStatusInfo);
    }

    private void buildGhost() {
        // Ghost
        header(R.string.MilliyGhost);
        check(R.string.MilliyGhost, R.string.MilliyGhostInfo, "ghost", false, this::build);
        if (MilliyPro.ghost()) {
            check(R.string.MilliyGhostOnline, 0, "ghost_online", true, null);
            check(R.string.MilliyGhostTyping, 0, "ghost_typing", true, null);
            check(R.string.MilliyGhostRead, 0, "ghost_read", true, null);
            check(R.string.MilliyGhostStory, 0, "ghost_story", true, null);
        }
        check(R.string.MilliyGhostButton, R.string.MilliyGhostButtonInfo, "ghost_button", true, null);
        info(R.string.MilliyGhostHint);
    }

    private void buildMessages() {
        // Xabarlar
        header(R.string.MilliyMessages);
        String[] modes = {LocaleController.getString(R.string.MilliyKeepOff), LocaleController.getString(R.string.MilliyKeepOthers), LocaleController.getString(R.string.MilliyKeepAll)};
        action(LocaleController.getString(R.string.MilliyKeepDeleted), modes[MilliyPro.keepDeletedMode()], () -> {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliyKeepDeleted));
            b.setItems(modes, (d, w) -> {
                MilliyPro.prefs().edit().putInt("keep_deleted", w).apply();
                build();
            });
            showDialog(b.create());
        });
        check(R.string.MilliyKeepEdits, R.string.MilliyKeepEditsInfo, "keep_edits", false, null);
        check(R.string.MilliyConfirmGift, 0, "confirm_gift", true, null);
        check(R.string.MilliyOneTimeVoice, R.string.MilliyOneTimeVoiceInfo, "one_time_voice", false, null);
        check(R.string.MilliyTimeSeconds, R.string.MilliyTimeSecondsInfo, "time_seconds", false,
                () -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, 0));
        info(R.string.MilliyKeepHint);
    }

    private void buildTranslate() {
        // Tarjima
        header(R.string.MilliyTranslate);
        check(R.string.MilliyAutoTranslate, R.string.MilliyAutoTranslateInfo, "auto_translate", false, null);
        info(R.string.MilliyAutoTranslateHint);
    }

    private void buildView() {
        // Ko'rinish
        header(R.string.MilliyView);
        check(R.string.MilliyHideTabs, R.string.MilliyHideTabsInfo, "hide_tabs", false,
                () -> getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated));
        check(R.string.MilliyAvatarProfile, R.string.MilliyAvatarProfileInfo, "avatar_profile", false, null);
        String[] shapes = {LocaleController.getString(R.string.MilliyAvatarCircle), LocaleController.getString(R.string.MilliyAvatarRounded), LocaleController.getString(R.string.MilliyAvatarSquare)};
        action(LocaleController.getString(R.string.MilliyAvatarShape), shapes[Math.min(2, MilliyPro.prefs().getInt("avatar_shape", 0))], () -> {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliyAvatarShape));
            b.setItems(shapes, (d, w) -> {
                MilliyPro.prefs().edit().putInt("avatar_shape", w).apply();
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_AVATAR);
                build();
            });
            showDialog(b.create());
        });
        action(LocaleController.getString(R.string.MilliyThemeEditor), "", () -> presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC)));
        customCheck(LocaleController.getString(R.string.MilliyLite), LocaleController.getString(R.string.MilliyLiteInfo), MilliyPro::lite, MilliyPro::setLite);
        info(R.string.MilliyViewHint);
    }

    private void buildChats() {
        // Chatlar: tezkor panel, kategoriyalar, yuklab olish menejeri
        header(R.string.MilliyChatsHeader);
        check(R.string.MilliyQuickBar, R.string.MilliyQuickBarInfo, "quick_bar", false, null);
        action(LocaleController.getString(R.string.MilliyCategories), String.valueOf(MilliyAdminFolders.categories(currentAccount).size()), this::manageCategories);
        action(LocaleController.getString(R.string.MilliyDlManager), "", () -> MilliyDownloads.open(this));
        info(R.string.MilliyChatsHint);
    }

    private void buildConfirm() {
        // Yuborishdan oldin so'rash
        header(R.string.MilliyConfirmHeader);
        check(R.string.MilliyConfirmStickerTitle, R.string.MilliyConfirmStickerInfo, "confirm_sticker", false, null);
        check(R.string.MilliyConfirmGifTitle, R.string.MilliyConfirmGifInfo, "confirm_gif", false, null);
        check(R.string.MilliyConfirmVoiceTitle, R.string.MilliyConfirmVoiceInfo, "confirm_voice", false, null);
        info(R.string.MilliyConfirmHint);
    }

    private void buildVoice() {
        // Ovoz va tarjima
        header(R.string.MilliyDictation);
        action(LocaleController.getString(R.string.MilliyDictationLang), MilliyDictation.languageName(), () -> MilliyDictation.chooseLanguage(this, this::build));
        String[] fx = {LocaleController.getString(R.string.MilliyOff), LocaleController.getString(R.string.MilliyFxChild), LocaleController.getString(R.string.MilliyFxDeep),
                LocaleController.getString(R.string.MilliyFxRobot), LocaleController.getString(R.string.MilliyFxEcho)};
        action(LocaleController.getString(R.string.MilliyVoiceFx), fx[Math.min(fx.length - 1, MilliyVoiceFx.effect())], () -> {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliyVoiceFx));
            b.setItems(fx, (d, w) -> {
                MilliyPro.prefs().edit().putInt("voice_fx", w).apply();
                build();
            });
            showDialog(b.create());
        });
        info(R.string.MilliyDictationHint);
    }

    private void buildAi() {
        // AI yordamchi
        header(R.string.MilliyAi);
        info(R.string.MilliyAiHint);
    }

    private void buildContacts() {
        // Kontaktlar
        header(R.string.MilliyContactsHeader);
        check(R.string.MilliyContactLogTitle, R.string.MilliyContactLogInfo, "contact_log", true, null);
        action(LocaleController.getString(R.string.MilliyContactLog), "", () -> presentFragment(new MilliyContacts.LogActivity()));
        action(LocaleController.getString(R.string.MilliySpecialList), String.valueOf(MilliyPro.getSet(currentAccount, "special").size()), null);
        info(R.string.MilliyContactsHint);
    }

    private void buildBackup() {
        // Zaxira
        header(R.string.MilliyBackup);
        action(LocaleController.getString(R.string.MilliyBackupExport), null, () -> MilliyBackup.export(this));
        action(LocaleController.getString(R.string.MilliyBackupRestore), null, () -> MilliyBackup.pick(this));
        info(R.string.MilliyBackupHint);
    }

    private void buildLock() {
        // Qulf va seyf
        header(R.string.MilliyPrivacyHeader);
        action(LocaleController.getString(MilliyLock.hasPin(MilliyLock.LOCK) ? R.string.MilliyLockPinChange : R.string.MilliyLockPinSet), null,
                () -> MilliyLock.setupPin(this, MilliyLock.LOCK, this::build));
        int locked = MilliyPro.getSet(currentAccount, "locked").size();
        action(LocaleController.getString(R.string.MilliyLockedChats), String.valueOf(locked), null);
        action(LocaleController.getString(R.string.MilliyVault), null, () -> MilliyLock.openVault(this));
        check(R.string.MilliyPinDigits, 0, "pin_digits", true, null);
        check(R.string.MilliyFingerprint, 0, "lock_fingerprint", true, null);
        info(R.string.MilliyLockHint);
    }

    private void buildStrangers() {
        // Begonalar
        header(R.string.MilliyStrangers);
        check(R.string.MilliyStrangers, R.string.MilliyStrangersInfo, "strangers", false, () -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            build();
        });
        if (MilliyPro.strangers()) {
            action(LocaleController.getString(R.string.MilliyStrangersTitle), String.valueOf(MilliyPro.strangerDialogs(currentAccount).size()),
                    () -> presentFragment(new MilliyChatsActivity(MilliyChatsActivity.STRANGERS)));
        }
        info(R.string.MilliyStrangersHint);
    }

    private void buildStories() {
        // Hikoyalar
        header(R.string.MilliyStories);
        check(R.string.MilliyStoryAnon, R.string.MilliyStoryAnonInfo, "story_anon", false, null);
        check(R.string.MilliyHideStories, R.string.MilliyHideStoriesInfo, "hide_stories", false, () -> getNotificationCenter().postNotificationName(NotificationCenter.storiesUpdated));
        check(R.string.MilliyStoryDownload, R.string.MilliyStoryDownloadInfo, "story_download", true, null);
        info(R.string.MilliyStoriesHint);
    }

    private void buildAdmins() {
        header(R.string.MilliyForAdmins);
        action(LocaleController.getString(R.string.SettingsFolders), "",
                () -> presentFragment(new org.telegram.ui.FiltersSetupActivity()));
        info(R.string.MilliyAdminsHint);
    }

    private void buildMedia() {
        // Media va trafik
        header(R.string.MilliyMedia);
        action(LocaleController.getString(R.string.MilliyAutoDl), LocaleController.getString(MilliyTools.autoDownloadOn(currentAccount) ? R.string.MilliyOn : R.string.MilliyOff), () -> {
            MilliyTools.toggleAutoDownload(this);
            build();
        });
        check(R.string.MilliyRoundBack, 0, "round_back", false, null);
        info(R.string.MilliyMediaHint);
    }

    private void buildAutoreply() {
        // Avto-javob
        header(R.string.MilliyAutoReply);
        check(R.string.MilliyAutoReply, R.string.MilliyAutoReplyInfo, "auto_reply", false, null);
        String text = MilliyAutoReply.text();
        action(LocaleController.getString(R.string.MilliyAutoReplyText), text.isEmpty() ? LocaleController.getString(R.string.MilliyNotSet) : (text.length() > 24 ? text.substring(0, 24) + "…" : text), this::editAutoReply);
        int[] hours = {1, 3, 6, 12, 24};
        action(LocaleController.getString(R.string.MilliyAutoReplyEvery), LocaleController.formatString(R.string.MilliyHoursN, MilliyAutoReply.hours()), () -> {
            CharSequence[] items = new CharSequence[hours.length];
            for (int i = 0; i < hours.length; i++) {
                items[i] = LocaleController.formatString(R.string.MilliyHoursN, hours[i]);
            }
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliyAutoReplyEvery));
            b.setItems(items, (d, w) -> {
                MilliyPro.prefs().edit().putInt("auto_reply_hours", hours[w]).apply();
                build();
            });
            showDialog(b.create());
        });
        info(R.string.MilliyAutoReplyHint);
    }

    private void buildOther() {
        // Boshqa
        header(R.string.MilliyOther);
        check(R.string.MilliyShowId, 0, "show_id", true, null);
        action(LocaleController.getString(R.string.MilliyApkShield), LocaleController.getString(R.string.MilliyOn), null);
        action(LocaleController.getString(R.string.MilliySecretCodes), ".heart · .typer", null);
        action(LocaleController.getString(R.string.MilliyCopySettingsLink), "tg://settings/milliy", () -> {
            org.telegram.messenger.AndroidUtilities.addToClipboard("tg://settings/milliy");
            org.telegram.ui.Components.BulletinFactory.of(this).createCopyLinkBulletin().show();
        });
        info(R.string.MilliyOtherHint);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == MilliyBackup.REQUEST_CODE) {
            MilliyBackup.onPicked(this, resultCode, data);
            build();
        }
    }

    private void manageCategories() {
        java.util.ArrayList<MilliyAdminFolders.Category> list = MilliyAdminFolders.categories(currentAccount);
        CharSequence[] items = new CharSequence[list.size() + 1];
        for (int i = 0; i < list.size(); i++) {
            items[i] = list.get(i).name + " (" + list.get(i).chats.size() + ")";
        }
        items[list.size()] = "➕ " + LocaleController.getString(R.string.MilliyCategoryNew);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyCategories));
        b.setItems(items, (d, w) -> {
            if (w == list.size()) {
                MilliyAdminFolders.askName(this, null, name -> {
                    MilliyAdminFolders.createCategory(currentAccount, name);
                    MilliyAdminFolders.saveCategories(currentAccount);
                    build();
                });
                return;
            }
            MilliyAdminFolders.Category c = list.get(w);
            AlertDialog.Builder b2 = new AlertDialog.Builder(getParentActivity());
            b2.setTitle(c.name);
            b2.setItems(new CharSequence[]{LocaleController.getString(R.string.MilliyCategoryRename), LocaleController.getString(R.string.MilliyCategoryDelete)}, (d2, w2) -> {
                if (w2 == 0) {
                    MilliyAdminFolders.askName(this, c.name, name -> {
                        c.name = name;
                        MilliyAdminFolders.saveCategories(currentAccount);
                        build();
                    });
                } else {
                    MilliyAdminFolders.deleteCategory(currentAccount, c);
                    build();
                }
            });
            showDialog(b2.create());
        });
        showDialog(b.create());
    }

    private void editAutoReply() {
        android.widget.EditText edit = new android.widget.EditText(getParentActivity());
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        edit.setHint(LocaleController.getString(R.string.MilliyAutoReplyTextHint));
        edit.setText(MilliyAutoReply.text());
        edit.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        edit.setMaxLines(6);
        LinearLayout box = new LinearLayout(getParentActivity());
        box.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyAutoReplyText));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            MilliyPro.prefs().edit().putString("auto_reply_text", edit.getText().toString().trim()).apply();
            build();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void header(int text) {
        Section sec = new Section();
        if (text != 0) {
            android.widget.TextView h = new android.widget.TextView(getParentActivity());
            h.setText(LocaleController.getString(text));
            h.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
            h.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            h.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            root.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 18, 28, 8));
            sec.header = h;
        }
        LinearLayout card = new LinearLayout(getParentActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(org.telegram.messenger.AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), org.telegram.messenger.AndroidUtilities.dp(16));
            }
        });
        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));
        sec.card = card;
        sections.add(sec);
    }

    private void info(int text) {
        android.widget.TextView c = new android.widget.TextView(getParentActivity());
        c.setText(LocaleController.getString(text));
        c.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        c.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 8, 28, 4));
        current().info = c;
    }

    private void check(int title, int subtitle, String key, boolean def, Runnable after) {
        String t = LocaleController.getString(title);
        String sub = subtitle != 0 ? LocaleController.getString(subtitle) : null;
        MilliyRow c = newRow(t).setTexts(t, sub).setSwitch(MilliyPro.get(key, def));
        c.setOnClickListener(v -> {
            boolean nv = !MilliyPro.get(key, def);
            MilliyPro.set(key, nv);
            c.setChecked(nv);
            if (after != null) {
                after.run();
            }
        });
        addRow(c, t + (sub != null ? " " + sub : ""));
    }

    private void action(String title, String value, Runnable click) {
        MilliyRow c = newRow(title).setTexts(title, null).setValue(value, click != null);
        if (click != null) {
            c.setOnClickListener(v -> click.run());
        } else {
            c.setBackground(null);
        }
        addRow(c, title);
    }
}
