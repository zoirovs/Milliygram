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
        info(R.string.MilliyKeepHint);

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

        // Boshqa
        header(R.string.MilliyOther);
        check(R.string.MilliyShowId, 0, "show_id", true, null);
        info(R.string.MilliyOtherHint);
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
