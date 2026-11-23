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

    /** Eng ko'p kerak bo'ladiganlar — sahifa boshida. */
    private void buildQuick() {
        header(R.string.MilliyQuickHeader);
        customCheck(LocaleController.getString(R.string.MilliyHideAds), LocaleController.getString(MilliyPro.isUzPhone(currentAccount) ? R.string.MilliyHideAdsInfoUz : R.string.MilliyHideAdsInfo),
                () -> MilliyPro.hideAds(currentAccount), v -> MilliyPro.setHideAds(currentAccount, v));
        check(R.string.MilliyInfoBar, R.string.MilliyInfoBarInfo, "info_bar", true, null);
        action(LocaleController.getString(R.string.MilliyUiScript), MilliyTranslit.uiScriptName(), () -> MilliyTranslit.chooseLanguage(this));
        String district = MilliyData.getDistrictName(currentAccount);
        action(LocaleController.getString(R.string.MilliyMyRegion), district != null ? MilliyTranslit.ui(district) : LocaleController.getString(R.string.MilliyChooseRegion),
                () -> presentFragment(new MilliyRegionActivity(false)));
        if (!MilliyLogin.isBot(currentAccount)) { // bot akkauntda 2FA/seans so'rovlari ishlamaydi
            action(LocaleController.getString(R.string.MilliySecurity), "", () -> presentFragment(new MilliySecurityActivity()));
        }
        info(R.string.MilliyQuickHint);
    }

    // ---------- Qidiruv ----------

    private org.telegram.ui.Components.EditTextBoldCursor search;

    private static class Section {
        View header, info;
        final java.util.ArrayList<View> rows = new java.util.ArrayList<>();
        final java.util.ArrayList<String> texts = new java.util.ArrayList<>();
    }

    private final java.util.ArrayList<Section> sections = new java.util.ArrayList<>();

    private Section current() {
        if (sections.isEmpty()) {
            sections.add(new Section());
        }
        return sections.get(sections.size() - 1);
    }

    private void addRow(View row, String text) {
        current().rows.add(row);
        current().texts.add(text == null ? "" : text.toLowerCase());
    }

    private void filter(String query) {
        String q = query.trim().toLowerCase();
        String q2 = MilliyTranslit.isMostlyCyrillic(q) ? MilliyTranslit.toLatin(q).toLowerCase() : MilliyTranslit.toCyrillic(q).toLowerCase();
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
            if (sec.info != null) {
                sec.info.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void customCheck(String title, String subtitle, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        TextCheckCell c = new TextCheckCell(getParentActivity());
        c.setBackground(Theme.getSelectorDrawable(true));
        c.setTextAndValueAndCheck(title, subtitle, getter.getAsBoolean(), true, true);
        c.setOnClickListener(v -> {
            boolean nv = !getter.getAsBoolean();
            setter.accept(nv);
            c.setChecked(nv);
        });
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
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
        TextCheckCell lite = new TextCheckCell(getParentActivity());
        lite.setBackground(Theme.getSelectorDrawable(true));
        lite.setTextAndValueAndCheck(LocaleController.getString(R.string.MilliyLite), LocaleController.getString(R.string.MilliyLiteInfo), MilliyPro.lite(), true, true);
        lite.setOnClickListener(v -> {
            MilliyPro.setLite(!MilliyPro.lite());
            lite.setChecked(MilliyPro.lite());
        });
        root.addView(lite, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addRow(lite, LocaleController.getString(R.string.MilliyLite) + " " + LocaleController.getString(R.string.MilliyLiteInfo));
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
        HeaderCell h = new HeaderCell(getParentActivity());
        h.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        h.setText(LocaleController.getString(text));
        root.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        Section sec = new Section();
        sec.header = h;
        sections.add(sec);
    }

    private void info(int text) {
        TextInfoPrivacyCell c = new TextInfoPrivacyCell(getParentActivity());
        c.setText(LocaleController.getString(text));
        c.setBackground(Theme.getThemedDrawableByKey(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        current().info = c;
    }

    private void check(int title, int subtitle, String key, boolean def, Runnable after) {
        TextCheckCell c = new TextCheckCell(getParentActivity());
        c.setBackground(Theme.getSelectorDrawable(true));
        boolean on = MilliyPro.get(key, def);
        if (subtitle != 0) {
            c.setTextAndValueAndCheck(LocaleController.getString(title), LocaleController.getString(subtitle), on, true, true);
        } else {
            c.setTextAndCheck(LocaleController.getString(title), on, true);
        }
        c.setOnClickListener(v -> {
            boolean nv = !MilliyPro.get(key, def);
            MilliyPro.set(key, nv);
            c.setChecked(nv);
            if (after != null) {
                after.run();
            }
        });
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addRow(c, LocaleController.getString(title) + (subtitle != 0 ? " " + LocaleController.getString(subtitle) : ""));
    }

    private void action(String title, String value, Runnable click) {
        TextSettingsCell c = new TextSettingsCell(getParentActivity());
        c.setBackground(Theme.getSelectorDrawable(true));
        if (value != null) {
            c.setTextAndValue(title, value, true);
        } else {
            c.setText(title, true);
        }
        if (click != null) {
            c.setOnClickListener(v -> click.run());
        }
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addRow(c, title);
    }
}
