package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** Asosiy ro'yxatdan yashirilgan chatlar: maxfiy seyf yoki begona suhbatlar. */
public class MilliyChatsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int VAULT = 0, STRANGERS = 1;

    private final int mode;
    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private RecyclerListView listView;
    private TextView emptyView;

    public MilliyChatsActivity(int mode) {
        this.mode = mode;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(mode == VAULT ? R.string.MilliyVault : R.string.MilliyStrangersTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                DialogCell cell = new DialogCell(null, context, false, true);
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((DialogCell) holder.itemView).setDialog(dialogs.get(position), 0, 0);
            }

            @Override
            public int getItemCount() {
                return dialogs.size();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= dialogs.size()) {
                return;
            }
            long did = dialogs.get(position).id;
            Bundle args = new Bundle();
            if (DialogObject.isEncryptedDialog(did)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
            } else if (DialogObject.isUserDialog(did)) {
                args.putLong("user_id", did);
            } else {
                args.putLong("chat_id", -did);
            }
            presentFragment(new ChatActivity(args));
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (mode != VAULT || position < 0 || position >= dialogs.size()) {
                return false;
            }
            long did = dialogs.get(position).id;
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setItems(new CharSequence[]{LocaleController.getString(R.string.MilliyVaultRemove)}, (d, w) -> {
                MilliyLock.toggleVault(this, did);
                reload();
            });
            showDialog(b.create());
            return true;
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), 0, dp(32), 0);
        emptyView.setText(LocaleController.getString(mode == VAULT ? R.string.MilliyVaultEmpty : R.string.MilliyStrangersEmpty));
        frame.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = frame;
        reload();
        return frame;
    }

    private void reload() {
        dialogs.clear();
        if (mode == VAULT) {
            for (Long id : MilliyPro.getSet(currentAccount, "vault")) {
                TLRPC.Dialog d = getMessagesController().dialogs_dict.get(id);
                if (d != null) {
                    dialogs.add(d);
                }
            }
            java.util.Collections.sort(dialogs, (a, b) -> Integer.compare(b.last_message_date, a.last_message_date));
        } else {
            dialogs.addAll(MilliyPro.strangerDialogs(currentAccount));
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(dialogs.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        reload();
    }
}
