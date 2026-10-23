package org.telegram.milliy;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Tezkor panel: chatlar ro'yxati tepasida «Tanlanganlar» chatlari avatar bilan bir qatorda (oxirgi xabar bo'yicha).
 * Bosilsa — chat ochiladi.
 */
public class MilliyQuickBar extends HorizontalScrollView {

    private static final int MAX = 20;

    private final BaseFragment fragment;
    private final LinearLayout row;

    public MilliyQuickBar(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public static boolean enabled() {
        return MilliyPro.get("quick_bar", false);
    }

    /** Ro'yxatni yangilaydi. true — panel ko'rsatilishi kerak. */
    public boolean refresh() {
        row.removeAllViews();
        if (!enabled()) {
            return false;
        }
        final int account = fragment.getCurrentAccount();
        MessagesController mc = MessagesController.getInstance(account);
        ArrayList<TLRPC.Dialog> list = new ArrayList<>();
        for (TLRPC.Dialog d : mc.getAllDialogs()) {
            if (MilliyAdminFolders.isFavorite(account, d.id)) {
                list.add(d);
            }
        }
        Collections.sort(list, (a, b) -> Integer.compare(b.last_message_date, a.last_message_date));
        for (int i = 0; i < list.size() && i < MAX; i++) {
            long did = list.get(i).id;
            TLObject peer = did > 0 ? mc.getUser(did) : mc.getChat(-did);
            if (peer == null) {
                continue;
            }
            row.addView(item(account, did, peer), LayoutHelper.createLinear(64, LayoutHelper.WRAP_CONTENT));
        }
        return row.getChildCount() > 0;
    }

    private LinearLayout item(int account, long did, TLObject peer) {
        Context ctx = getContext();
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        BackupImageView img = new BackupImageView(ctx);
        img.setRoundRadius(MilliyPro.avatarRadius(AndroidUtilities.dp(23)));
        AvatarDrawable avatar = new AvatarDrawable();
        avatar.setInfo(account, peer);
        img.setForUserOrChat(peer, avatar);
        cell.addView(img, LayoutHelper.createLinear(46, 46, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 3));
        TextView name = new TextView(ctx);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setText(peer instanceof TLRPC.User ? UserObject.getFirstName((TLRPC.User) peer) : ((TLRPC.Chat) peer).title);
        cell.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 2, 0, 2, 2));
        cell.setOnClickListener(v -> {
            Bundle args = new Bundle();
            if (did > 0) {
                args.putLong("user_id", did);
            } else {
                args.putLong("chat_id", -did);
            }
            fragment.presentFragment(new ChatActivity(args));
        });
        return cell;
    }
}
