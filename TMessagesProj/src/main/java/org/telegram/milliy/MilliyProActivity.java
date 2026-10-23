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

/** Sozlamalar → «Qo'shimcha imkoniyatlar»: ghost, xabarlarni saqlash, qulf, seyf, begonalar va boshqalar. */
public class MilliyProActivity extends BaseFragment {

    private LinearLayout root;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MilliyPro));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        fragmentView = scroll;
        build();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (root != null) {
            build();
        }
    }

    private void build() {
        root.removeAllViews();

        // Mening holatim
        header(R.string.MilliyStatus);
        String status = MilliyStatus.current(currentAccount);
        action(LocaleController.getString(R.string.MilliyStatus), status != null ? status : LocaleController.getString(R.string.MilliyNotSet),
                () -> MilliyStatus.show(this));
        info(R.string.MilliyStatusInfo);

        // Ghost
        header(R.string.MilliyGhost);
        check(R.string.MilliyGhost, R.string.MilliyGhostInfo, "ghost", false, this::build);
        if (MilliyPro.ghost()) {
            check(R.string.MilliyGhostOnline, 0, "ghost_online", true, null);
            check(R.string.MilliyGhostTyping, 0, "ghost_typing", true, null);
            check(R.string.MilliyGhostRead, 0, "ghost_read", true, null);
            check(R.string.MilliyGhostStory, 0, "ghost_story", true, null);
        }
        check(R.string.MilliyGhostButton, 0, "ghost_button", true, null);
        info(R.string.MilliyGhostHint);

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
        check(R.string.MilliyKeepEdits, 0, "keep_edits", false, null);
        check(R.string.MilliyConfirmGift, 0, "confirm_gift", true, null);
        check(R.string.MilliyOneTimeVoice, R.string.MilliyOneTimeVoiceInfo, "one_time_voice", false, null);
        check(R.string.MilliyTimeSeconds, R.string.MilliyTimeSecondsInfo, "time_seconds", false,
                () -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, 0));
        info(R.string.MilliyKeepHint);

        // Tarjima
        header(R.string.MilliyTranslate);
        check(R.string.MilliyAutoTranslate, R.string.MilliyAutoTranslateInfo, "auto_translate", false, null);
        info(R.string.MilliyAutoTranslateHint);

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
        info(R.string.MilliyViewHint);

        // Chatlar: tezkor panel, kategoriyalar, yuklab olish menejeri
        header(R.string.MilliyChatsHeader);
        check(R.string.MilliyQuickBar, R.string.MilliyQuickBarInfo, "quick_bar", false, null);
        action(LocaleController.getString(R.string.MilliyCategories), String.valueOf(MilliyAdminFolders.categories(currentAccount).size()), this::manageCategories);
        action(LocaleController.getString(R.string.MilliyDlManager), "", () -> MilliyDownloads.open(this));
        info(R.string.MilliyChatsHint);

        // Yuborishdan oldin so'rash
        header(R.string.MilliyConfirmHeader);
        check(R.string.MilliyConfirmStickerTitle, 0, "confirm_sticker", false, null);
        check(R.string.MilliyConfirmGifTitle, 0, "confirm_gif", false, null);
        check(R.string.MilliyConfirmVoiceTitle, 0, "confirm_voice", false, null);
        info(R.string.MilliyConfirmHint);

        // Ovoz va tarjima
        header(R.string.MilliyDictation);
        action(LocaleController.getString(R.string.MilliyDictationLang), MilliyDictation.languageName(), () -> MilliyDictation.chooseLanguage(this, this::build));
        info(R.string.MilliyDictationHint);

        // Zaxira
        header(R.string.MilliyBackup);
        action(LocaleController.getString(R.string.MilliyBackupExport), null, () -> MilliyBackup.export(this));
        action(LocaleController.getString(R.string.MilliyBackupRestore), null, () -> MilliyBackup.pick(this));
        info(R.string.MilliyBackupHint);

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

        // Hikoyalar
        header(R.string.MilliyStories);
        check(R.string.MilliyStoryAnon, 0, "story_anon", false, null);
        check(R.string.MilliyHideStories, 0, "hide_stories", false, () -> getNotificationCenter().postNotificationName(NotificationCenter.storiesUpdated));
        check(R.string.MilliyStoryDownload, 0, "story_download", true, null);
        info(R.string.MilliyStoriesHint);

        // Adminlar uchun
        header(R.string.MilliyForAdmins);
        info(R.string.MilliyAdminsHint);

        // Jildlar — Sozlamalar → Chat jildlari (Milliygram bo'limida)
        action(LocaleController.getString(R.string.SettingsFolders), "",
                () -> presentFragment(new org.telegram.ui.FiltersSetupActivity()));

        // Media va trafik
        header(R.string.MilliyMedia);
        action(LocaleController.getString(R.string.MilliyAutoDl), LocaleController.getString(MilliyTools.autoDownloadOn(currentAccount) ? R.string.MilliyOn : R.string.MilliyOff), () -> {
            MilliyTools.toggleAutoDownload(this);
            build();
        });
        check(R.string.MilliyRoundBack, 0, "round_back", false, null);
        info(R.string.MilliyMediaHint);

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
    }

    private void info(int text) {
        TextInfoPrivacyCell c = new TextInfoPrivacyCell(getParentActivity());
        c.setText(LocaleController.getString(text));
        c.setBackground(Theme.getThemedDrawableByKey(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
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
    }
}
