package org.telegram.milliy;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Qo'shilish so'rovlarini ommaviy qabul/rad qilish — har doim aniq tasdiq so'raladi. */
public class MilliyJoinRequests {

    public static void show(BaseFragment fragment, long chatId) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(R.string.MilliyJoinBulk));
        b.setItems(new CharSequence[]{
                LocaleController.getString(R.string.MilliyJoinApproveN),
                LocaleController.getString(R.string.MilliyJoinDeclineN)
        }, (d, which) -> askCount(fragment, chatId, which == 0));
        fragment.showDialog(b.create());
    }

    private static void askCount(BaseFragment fragment, long chatId, boolean approve) {
        Context context = fragment.getParentActivity();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        hint.setText(LocaleController.getString(R.string.MilliyJoinCountHint));
        box.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        EditText count = new EditText(context);
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        count.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        count.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        count.setHint(LocaleController.getString(R.string.MilliyJoinAll));
        count.setBackground(Theme.createEditTextDrawable(context, true));
        box.addView(count, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 24, 0, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(approve ? R.string.MilliyJoinApproveN : R.string.MilliyJoinDeclineN));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.MilliyNext), (d, w) -> {
            int n = 0;
            try {
                n = Integer.parseInt(count.getText().toString().trim());
            } catch (Exception ignore) {
            }
            confirm(fragment, chatId, approve, Math.max(0, n));
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    /** n == 0 — hammasi. */
    private static void confirm(BaseFragment fragment, long chatId, boolean approve, int n) {
        String what = n == 0 ? LocaleController.getString(R.string.MilliyJoinAll) : String.valueOf(n);
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyJoinSure));
        b.setMessage(LocaleController.formatString(approve ? R.string.MilliyJoinSureApprove : R.string.MilliyJoinSureDecline, what));
        b.setPositiveButton(LocaleController.getString(approve ? R.string.MilliyJoinYesApprove : R.string.MilliyJoinYesDecline), (d, w) -> run(fragment, chatId, approve, n));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        if (!approve) {
            android.view.View btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (btn instanceof TextView) {
                ((TextView) btn).setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }
    }

    private static void run(BaseFragment fragment, long chatId, boolean approve, int n) {
        int account = fragment.getCurrentAccount();
        MessagesController mc = MessagesController.getInstance(account);
        ConnectionsManager cm = ConnectionsManager.getInstance(account);
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        if (n == 0) {
            TLRPC.TL_messages_hideAllChatJoinRequests req = new TLRPC.TL_messages_hideAllChatJoinRequests();
            req.approved = approve;
            req.peer = mc.getInputPeer(-chatId);
            cm.sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.Updates) {
                    mc.processUpdates((TLRPC.Updates) res, false);
                }
                done(fragment, progress, err == null ? -1 : 0, approve);
            }));
            return;
        }
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.requested = true;
        req.peer = mc.getInputPeer(-chatId);
        req.offset_user = new TLRPC.TL_inputUserEmpty();
        req.limit = Math.min(n, 100);
        ArrayList<Long> users = new ArrayList<>();
        cm.sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_messages_chatInviteImporters) {
                TLRPC.TL_messages_chatInviteImporters r = (TLRPC.TL_messages_chatInviteImporters) res;
                mc.putUsers(r.users, false);
                for (TLRPC.TL_chatInviteImporter imp : r.importers) {
                    if (users.size() < n) {
                        users.add(imp.user_id);
                    }
                }
            }
            if (users.isEmpty()) {
                done(fragment, progress, 0, approve);
            } else {
                next(fragment, progress, chatId, approve, users, 0, 0, 0, n);
            }
        }));
    }

    /** So'rovlar bittadan yuboriladi (flood-limitga tushmaslik uchun oraliq bilan); 100 tadan keyin yangi sahifa olinadi. */
    private static void next(BaseFragment fragment, AlertDialog progress, long chatId, boolean approve, ArrayList<Long> users, int i, int ok, int pageOk, int n) {
        int account = fragment.getCurrentAccount();
        MessagesController mc = MessagesController.getInstance(account);
        if (i >= users.size()) {
            if (ok < n && users.size() >= 100 && pageOk > 0) {
                // yana sahifa bor bo'lishi mumkin
                TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
                req.requested = true;
                req.peer = mc.getInputPeer(-chatId);
                req.offset_user = new TLRPC.TL_inputUserEmpty();
                req.limit = Math.min(n - ok, 100);
                ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    ArrayList<Long> more = new ArrayList<>();
                    if (res instanceof TLRPC.TL_messages_chatInviteImporters) {
                        TLRPC.TL_messages_chatInviteImporters r = (TLRPC.TL_messages_chatInviteImporters) res;
                        mc.putUsers(r.users, false);
                        for (TLRPC.TL_chatInviteImporter imp : r.importers) {
                            more.add(imp.user_id);
                        }
                    }
                    if (more.isEmpty()) {
                        done(fragment, progress, ok, approve);
                    } else {
                        users.clear();
                        users.addAll(more);
                        next(fragment, progress, chatId, approve, users, 0, ok, 0, n);
                    }
                }));
                return;
            }
            done(fragment, progress, ok, approve);
            return;
        }
        TLRPC.User u = mc.getUser(users.get(i));
        TLRPC.TL_messages_hideChatJoinRequest req = new TLRPC.TL_messages_hideChatJoinRequest();
        req.approved = approve;
        req.peer = mc.getInputPeer(-chatId);
        req.user_id = mc.getInputUser(u);
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.Updates) {
                mc.processUpdates((TLRPC.Updates) res, false);
            }
            int nok = ok + (err == null ? 1 : 0), npage = pageOk + (err == null ? 1 : 0);
            if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT")) {
                done(fragment, progress, nok, approve);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> next(fragment, progress, chatId, approve, users, i + 1, nok, npage, n), 350);
        }));
    }

    private static void done(BaseFragment fragment, AlertDialog progress, int count, boolean approve) {
        try {
            progress.dismiss();
        } catch (Exception ignore) {
        }
        String text = count < 0 ? LocaleController.getString(approve ? R.string.MilliyJoinDoneAllApproved : R.string.MilliyJoinDoneAllDeclined)
                : LocaleController.formatString(approve ? R.string.MilliyJoinDoneApproved : R.string.MilliyJoinDoneDeclined, count);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, text).show();
        if (fragment instanceof org.telegram.ui.MemberRequestsActivity) {
            ((org.telegram.ui.MemberRequestsActivity) fragment).milliyReload();
        }
    }
}
