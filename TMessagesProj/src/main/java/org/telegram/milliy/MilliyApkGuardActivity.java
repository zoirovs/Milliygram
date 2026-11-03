package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** «Aqlli APK Qo'riqchi» ekrani: holat, to'liq himoyani yoqish, skaner natijalari, jurnal. */
public class MilliyApkGuardActivity extends BaseFragment {

    private static final int RED = 0xFFEF4444;
    private static final int AMBER = 0xFFF59E0B;
    private static final int GREEN = 0xFF10B981;
    private static final int BLUE = 0xFF3B82F6;
    private static final int GRAY = 0xFF6B7280;

    private LinearLayout root;
    private TextView progressText;
    private boolean updatingRules;
    private boolean rescanOnResume;
    private MilliyApkGuard.ScanResult result;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MilliyApkGuardTitle));
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
        scroll.setFillViewport(true);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(30));
        scroll.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        fragmentView = scroll;
        result = MilliyApkGuard.getLastResult();
        build();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Tizim sozlamalari yoki o'chirish oynasidan qaytganda holat o'zgargan bo'lishi mumkin
        MilliyApkWatcher.refresh();
        if (rescanOnResume && !MilliyApkGuard.isScanning() && root != null) {
            rescanOnResume = false;
            startScan();
        } else if (root != null) {
            build();
        }
    }

    @Override
    public void onFragmentDestroy() {
        root = null;
        progressText = null;
        super.onFragmentDestroy();
    }

    private void build() {
        if (root == null || getParentActivity() == null) {
            return;
        }
        root.removeAllViews();
        progressText = null;
        Context context = getParentActivity();
        boolean enabled = MilliyApkGuard.isEnabled();

        root.addView(statusCard(context, enabled));
        if (result != null && enabled) {
            root.addView(metrics(context));
        }

        LinearLayout mainCard = card(context);
        TextCheckCell main = new TextCheckCell(context);
        main.setBackground(Theme.getSelectorDrawable(true));
        main.setTextAndValueAndCheck(LocaleController.getString(R.string.MilliyApkGuardEnable), LocaleController.getString(R.string.MilliyApkGuardDesc), enabled, true, false);
        main.setOnClickListener(v -> {
            MilliyApkGuard.setEnabled(!MilliyApkGuard.isEnabled());
            build();
        });
        mainCard.addView(main);
        root.addView(mainCard, cardParams());
        if (!enabled) {
            return;
        }

        header(LocaleController.getString(R.string.MilliyGuardFullTitle));
        root.addView(protectionCard(context), cardParams());
        info(context, LocaleController.getString(R.string.MilliyGuardPrivacyInfo));

        root.addView(scanCard(context), cardParams());
        if (result != null) {
            addResults(context);
        }
        addJournal(context);

        header(LocaleController.getString(R.string.MilliyGuardBaseTitle));
        LinearLayout base = card(context);
        TextSettingsCell update = new TextSettingsCell(context);
        update.setBackground(Theme.getSelectorDrawable(true));
        long last = MilliyApkGuard.getLastSyncTime();
        String lastText = last <= 0 ? LocaleController.getString(R.string.MilliyGuardBaseBuiltIn) : new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(last));
        update.setTextAndValue(LocaleController.getString(R.string.MilliyApkGuardRulesUpdateBtn), updatingRules ? LocaleController.getString(R.string.MilliyApkGuardRulesUpdating) : lastText, false);
        update.setOnClickListener(v -> {
            if (updatingRules) {
                return;
            }
            updatingRules = true;
            build();
            MilliyApkGuard.syncRules(true, () -> {
                updatingRules = false;
                build();
                if (root != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_bot, LocaleController.getString(R.string.MilliyApkGuardRulesUpdated)).show();
                }
            });
        });
        base.addView(update);
        root.addView(base, cardParams());
    }

    // ---------- Holat ----------

    private View statusCard(Context context, boolean enabled) {
        int color;
        String title, sub;
        boolean scanning = MilliyApkGuard.isScanning();
        boolean full = MilliyApkGuard.isAccessibilityOn() && MilliyApkGuard.isBlockInstalls();
        if (!enabled) {
            color = GRAY;
            title = LocaleController.getString(R.string.MilliyApkGuardShieldInactive);
            sub = LocaleController.getString(R.string.MilliyApkGuardShieldInactiveSub);
        } else if (scanning) {
            color = BLUE;
            title = LocaleController.getString(R.string.MilliyGuardScanning);
            sub = "";
        } else if (result != null && count(MilliyApkGuard.STATUS_TROJAN) > 0) {
            color = RED;
            title = LocaleController.formatString(R.string.MilliyGuardDangerTitle, count(MilliyApkGuard.STATUS_TROJAN));
            sub = LocaleController.getString(R.string.MilliyGuardDangerDesc);
        } else if (!full) {
            color = AMBER;
            title = LocaleController.getString(R.string.MilliyGuardPartialTitle);
            sub = LocaleController.getString(R.string.MilliyGuardPartialDesc);
        } else {
            color = GREEN;
            title = LocaleController.getString(R.string.MilliyApkGuardShieldActive);
            sub = LocaleController.getString(R.string.MilliyApkGuardShieldActiveSub);
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setStroke(dp(1.5f), color);
        box.setBackground(bg);
        if (enabled && result != null && !scanning) {
            TextView score = text(context, MilliyApkGuard.securityScore(result) + "%", 30, color, true);
            box.addView(score, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 2));
            box.addView(text(context, LocaleController.getString(R.string.MilliyGuardScore), 12, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        }
        TextView titleView = text(context, title, 16, color, true);
        titleView.setGravity(Gravity.CENTER);
        box.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        if (!TextUtils.isEmpty(sub)) {
            TextView subView = text(context, sub, 13, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
            subView.setGravity(Gravity.CENTER);
            box.addView(subView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
        }
        box.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 14, 14, 10));
        return box;
    }

    private int count(int status) {
        int c = 0;
        if (result != null) {
            for (MilliyApkGuard.ApkCheckResult r : result.apps) {
                if (r.status == status) {
                    c++;
                }
            }
            for (MilliyApkGuard.ScannedApk f : result.files) {
                if (!f.deleted && f.result.status == status) {
                    c++;
                }
            }
        }
        return c;
    }

    private View metrics(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(metric(context, count(MilliyApkGuard.STATUS_TROJAN), R.string.MilliyGuardDangerous, RED), metricParams(0));
        row.addView(metric(context, count(MilliyApkGuard.STATUS_SUSPICIOUS), R.string.MilliyGuardSuspicious, AMBER), metricParams(8));
        row.addView(metric(context, count(MilliyApkGuard.STATUS_UNKNOWN), R.string.MilliyGuardUnknown, GRAY), metricParams(8));
        row.addView(metric(context, count(MilliyApkGuard.STATUS_SAFE), R.string.MilliyGuardSafe, GREEN), metricParams(8));
        row.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 12));
        return row;
    }

    private LinearLayout.LayoutParams metricParams(int leftDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(leftDp);
        return lp;
    }

    private View metric(Context context, int value, int label, int color) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(12), dp(4), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        box.setBackground(bg);
        box.addView(text(context, String.valueOf(value), 18, color, true));
        TextView name = text(context, LocaleController.getString(label), 11, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(name);
        return box;
    }

    // ---------- To'liq himoya ----------

    private View protectionCard(Context context) {
        LinearLayout box = card(context);

        boolean a11y = MilliyApkGuard.isAccessibilityOn();
        TextSettingsCell a11yCell = new TextSettingsCell(context);
        a11yCell.setBackground(Theme.getSelectorDrawable(true));
        a11yCell.setTextAndValue(LocaleController.getString(R.string.MilliyGuardA11yRow), LocaleController.getString(a11y ? R.string.MilliyApkGuardOn : R.string.MilliyApkGuardOff), true);
        a11yCell.setOnClickListener(v -> showA11yGuide());
        box.addView(a11yCell);

        TextCheckCell block = new TextCheckCell(context);
        block.setBackground(Theme.getSelectorDrawable(true));
        block.setTextAndCheck(LocaleController.getString(R.string.MilliyGuardBlockInstalls), MilliyApkGuard.isBlockInstalls(), true);
        block.setOnClickListener(v -> {
            boolean value = !MilliyApkGuard.isBlockInstalls();
            MilliyApkGuard.setBlockInstalls(value);
            block.setChecked(value);
        });
        box.addView(block);

        boolean files = MilliyApkGuard.hasAllFilesAccess();
        TextSettingsCell filesCell = new TextSettingsCell(context);
        filesCell.setBackground(Theme.getSelectorDrawable(true));
        filesCell.setTextAndValue(LocaleController.getString(R.string.MilliyGuardFilesRow), LocaleController.getString(files ? R.string.MilliyApkGuardOn : R.string.MilliyApkGuardOff), true);
        filesCell.setOnClickListener(v -> openFilesSettings());
        box.addView(filesCell);

        TextCheckCell autoDelete = new TextCheckCell(context);
        autoDelete.setBackground(Theme.getSelectorDrawable(true));
        autoDelete.setTextAndCheck(LocaleController.getString(R.string.MilliyGuardAutoDelete), MilliyApkGuard.isAutoDelete(), false);
        autoDelete.setOnClickListener(v -> {
            if (MilliyApkGuard.isAutoDelete()) {
                MilliyApkGuard.setAutoDelete(false);
                autoDelete.setChecked(false);
            } else {
                confirmAutoDelete(autoDelete);
            }
        });
        box.addView(autoDelete);
        return box;
    }

    private void confirmAutoDelete(TextCheckCell cell) {
        if (!MilliyApkGuard.hasAllFilesAccess()) {
            openFilesSettings();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyGuardAutoDelete));
        b.setMessage(LocaleController.getString(R.string.MilliyGuardAutoDeleteWarn));
        b.setPositiveButton(LocaleController.getString(R.string.MilliyGuardAutoDeleteYes), (d, w) -> {
            MilliyApkGuard.setAutoDelete(true);
            cell.setChecked(true);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showA11yGuide() {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyGuardA11yRow));
        String text = LocaleController.getString(R.string.MilliyGuardA11yGuide);
        if (Build.VERSION.SDK_INT >= 33) {
            text += "\n\n" + LocaleController.getString(R.string.MilliyGuardA11yRestricted);
            b.setNeutralButton(LocaleController.getString(R.string.MilliyGuardOpenAppInfo), (d, w) -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getParentActivity().getPackageName()))));
        }
        b.setMessage(text);
        b.setPositiveButton(LocaleController.getString(R.string.MilliyGuardOpenSettings), (d, w) -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void openFilesSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getParentActivity().getPackageName()));
            if (!open(intent)) {
                open(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            getParentActivity().requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
        }
    }

    private boolean open(Intent intent) {
        try {
            getParentActivity().startActivity(intent);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    // ---------- Skaner ----------

    private View scanCard(Context context) {
        LinearLayout box = card(context);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.addView(text(context, LocaleController.getString(R.string.MilliyApkScanCardTitle), 15, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true));
        TextView desc = text(context, LocaleController.getString(R.string.MilliyApkScanCardDesc), 13, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
        box.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));
        boolean scanning = MilliyApkGuard.isScanning();
        if (scanning) {
            ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(true);
            box.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8, 0, 0, 0, 8));
            progressText = text(context, LocaleController.getString(R.string.MilliyGuardScanning), 12, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
            progressText.setSingleLine(true);
            progressText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            box.addView(progressText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
        TextView button = text(context, LocaleController.getString(scanning ? R.string.MilliyApkScanBtnStop : R.string.MilliyApkScanBtn), 14, 0xFFFFFFFF, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(scanning ? GRAY : GREEN);
        button.setBackground(bg);
        button.setOnClickListener(v -> {
            if (MilliyApkGuard.isScanning()) {
                MilliyApkGuard.stopScan();
            } else {
                startScan();
            }
        });
        box.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return box;
    }

    private void startScan() {
        MilliyApkGuard.startScan(new MilliyApkGuard.ScanListener() {
            @Override
            public void onProgress(int filesSeen, int apksFound, String current) {
                if (progressText != null) {
                    progressText.setText(LocaleController.formatString(R.string.MilliyGuardScanProgress, filesSeen, apksFound, current));
                }
            }

            @Override
            public void onComplete(MilliyApkGuard.ScanResult scan) {
                result = scan;
                build();
            }
        });
        build();
    }

    private void addResults(Context context) {
        boolean anyApp = false;
        for (MilliyApkGuard.ApkCheckResult r : result.apps) {
            if (r.status == MilliyApkGuard.STATUS_TROJAN || r.status == MilliyApkGuard.STATUS_SUSPICIOUS || r.status == MilliyApkGuard.STATUS_UNKNOWN) {
                if (!anyApp) {
                    header(LocaleController.getString(R.string.MilliyGuardAppsTitle));
                    anyApp = true;
                }
                root.addView(appCard(context, r), cardParams(8));
            }
        }
        if (!result.hasStorageAccess) {
            info(context, LocaleController.getString(R.string.MilliyGuardNoStorageInfo));
        }
        if (!result.files.isEmpty()) {
            header(LocaleController.formatString(R.string.MilliyApkResultsTitle, result.files.size()));
            for (MilliyApkGuard.ScannedApk item : result.files) {
                root.addView(fileCard(context, item), cardParams(8));
            }
        }
    }

    private View appCard(Context context, MilliyApkGuard.ApkCheckResult r) {
        return itemCard(context, r, r.appName, r.packageName, LocaleController.getString(R.string.MilliyGuardUninstall), v -> {
            rescanOnResume = true;
            open(MilliyApkGuard.uninstallIntent(r.packageName));
        });
    }

    private View fileCard(Context context, MilliyApkGuard.ScannedApk item) {
        String name = !TextUtils.isEmpty(item.result.appName) ? item.result.appName : item.file.getName();
        long size = item.file.length();
        String sizeText = size >= 1024 * 1024 ? size / (1024 * 1024) + " MB" : size / 1024 + " KB";
        String sub = sizeText + " • " + item.file.getAbsolutePath();
        return itemCard(context, item.result, name, sub, item.deleted ? null : LocaleController.getString(R.string.MilliyApkDeleteBtn), v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliyApkDeleteBtn));
            b.setMessage(LocaleController.formatString(R.string.MilliyGuardDeleteConfirm, item.file.getName()));
            b.setPositiveButton(LocaleController.getString(R.string.MilliyApkDeleteBtn), (d, w) -> {
                MilliyApkGuard.deleteScanned(item);
                build();
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        });
    }

    private View itemCard(Context context, MilliyApkGuard.ApkCheckResult r, String name, String sub, String action, View.OnClickListener click) {
        LinearLayout box = card(context);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        if (r.icon != null) {
            icon.setImageDrawable(r.icon);
        } else {
            icon.setImageResource(R.drawable.files_storage);
            icon.setColorFilter(statusColor(r.status));
        }
        row.addView(icon, LayoutHelper.createLinear(42, 42, 0, 0, 12, 0));
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = text(context, name, 14, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nameView);
        TextView subView = text(context, sub != null ? sub : "", 11, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
        subView.setSingleLine(true);
        subView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        info.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 6));
        TextView badge = text(context, statusLabel(r.status), 10, statusColor(r.status), true);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(6));
        badgeBg.setColor((statusColor(r.status) & 0x00FFFFFF) | 0x22000000);
        badge.setBackground(badgeBg);
        info.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        row.addView(info, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (action != null) {
            TextView button = text(context, action, 12, RED, true);
            button.setPadding(dp(10), dp(8), dp(10), dp(8));
            GradientDrawable buttonBg = new GradientDrawable();
            buttonBg.setCornerRadius(dp(8));
            buttonBg.setColor(0x18EF4444);
            button.setBackground(buttonBg);
            button.setOnClickListener(click);
            row.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        }
        box.addView(row);
        if (!r.reasons.isEmpty()) {
            TextView reasons = text(context, "• " + TextUtils.join("\n• ", r.reasons), 12, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
            box.addView(reasons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        }
        return box;
    }

    private int statusColor(int status) {
        return status == MilliyApkGuard.STATUS_TROJAN ? RED : status == MilliyApkGuard.STATUS_SUSPICIOUS ? AMBER : status == MilliyApkGuard.STATUS_SAFE ? GREEN : GRAY;
    }

    private String statusLabel(int status) {
        return LocaleController.getString(status == MilliyApkGuard.STATUS_TROJAN ? R.string.MilliyGuardDangerous : status == MilliyApkGuard.STATUS_SUSPICIOUS ? R.string.MilliyGuardSuspicious : status == MilliyApkGuard.STATUS_SAFE ? R.string.MilliyGuardSafe : R.string.MilliyGuardUnknown);
    }

    // ---------- Jurnal ----------

    private void addJournal(Context context) {
        JSONArray journal = MilliyApkGuard.getDeletedJournal();
        int blocked = MilliyApkGuard.getBlockedCount();
        if (journal.length() == 0 && blocked == 0) {
            return;
        }
        header(LocaleController.getString(R.string.MilliyGuardJournalTitle));
        LinearLayout box = card(context);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (blocked > 0) {
            box.addView(text(context, LocaleController.formatString(R.string.MilliyGuardBlockedCount, blocked), 13, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }
        SimpleDateFormat format = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
        for (int i = 0; i < journal.length() && i < 20; i++) {
            JSONObject item = journal.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String line = format.format(new Date(item.optLong("t"))) + " • " + item.optString("n");
            TextView view = text(context, line, 12, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
            view.setSingleLine(true);
            view.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            box.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        }
        root.addView(box, cardParams());
    }

    // ---------- Yordamchilar ----------

    private TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private LinearLayout card(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        box.setBackground(bg);
        box.setClipToOutline(true);
        return box;
    }

    private LinearLayout.LayoutParams cardParams() {
        return cardParams(12);
    }

    private LinearLayout.LayoutParams cardParams(int bottom) {
        return LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, bottom);
    }

    private void header(String value) {
        HeaderCell cell = new HeaderCell(getParentActivity());
        cell.setText(value);
        root.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void info(Context context, String value) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
        cell.setText(value);
        root.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
    }
}
