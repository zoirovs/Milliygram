package org.telegram.milliy;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SearchDownloadsContainer;
import org.telegram.ui.FilteredSearchView;

import java.util.ArrayList;

/**
 * Yuklab olish menejeri: chatdagi barcha fayllarni (turi bo'yicha) navbatga qo'yish, yuklanmalar ro'yxati,
 * hammasini to'xtatish va davom ettirish. Telegram'ning ichki yuklash navbati ishlatiladi.
 */
public class MilliyDownloads {

    private static final int MAX_ITEMS = 500;
    private static final int PAGE = 100;

    public static void showDownloadAll(ChatActivity chat) {
        if (chat.getParentActivity() == null) {
            return;
        }
        CharSequence[] items = {
                LocaleController.getString(R.string.MilliyDlPhotoVideo),
                LocaleController.getString(R.string.MilliyDlFiles),
                LocaleController.getString(R.string.MilliyDlMusic),
                LocaleController.getString(R.string.MilliyDlVoice),
        };
        AlertDialog.Builder b = new AlertDialog.Builder(chat.getParentActivity(), chat.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyDownloadAll));
        b.setItems(items, (d, which) -> {
            TLRPC.MessagesFilter filter;
            switch (which) {
                case 0:
                    filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
                    break;
                case 1:
                    filter = new TLRPC.TL_inputMessagesFilterDocument();
                    break;
                case 2:
                    filter = new TLRPC.TL_inputMessagesFilterMusic();
                    break;
                default:
                    filter = new TLRPC.TL_inputMessagesFilterVoice();
                    break;
            }
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.ic_download, LocaleController.getString(R.string.MilliyDlSearching)).show();
            fetch(chat, chat.getCurrentAccount(), chat.getDialogId(), filter, 0, 0);
        });
        chat.showDialog(b.create());
    }

    private static void fetch(BaseFragment fragment, int account, long dialogId, TLRPC.MessagesFilter filter, int offsetId, int total) {
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        req.q = "";
        req.filter = filter;
        req.limit = PAGE;
        req.offset_id = offsetId;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.messages_Messages)) {
                done(fragment, total);
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            MessagesController mc = MessagesController.getInstance(account);
            mc.putUsers(res.users, false);
            mc.putChats(res.chats, false);
            int count = total;
            int minId = 0;
            for (TLRPC.Message msg : res.messages) {
                minId = minId == 0 ? msg.id : Math.min(minId, msg.id);
                if (count >= MAX_ITEMS) {
                    break;
                }
                if (queue(account, new MessageObject(account, msg, false, false))) {
                    count++;
                }
            }
            if (res.messages.size() >= PAGE && count < MAX_ITEMS && minId > 0) {
                fetch(fragment, account, dialogId, filter, minId, count);
            } else {
                done(fragment, count);
            }
        }));
    }

    /** Bitta xabar mediasini navbatga qo'yadi. true — yangi yuklash boshlandi. */
    private static boolean queue(int account, MessageObject mo) {
        TLRPC.Document doc = mo.getDocument();
        FileLoader loader = FileLoader.getInstance(account);
        if (doc != null) {
            if (loader.getPathToMessage(mo.messageOwner).exists()) {
                return false;
            }
            loader.loadFile(doc, mo, FileLoader.PRIORITY_LOW, 0);
            DownloadController.getInstance(account).startDownloadFile(doc, mo);
            return true;
        }
        TLRPC.MessageMedia media = MessageObject.getMedia(mo.messageOwner);
        if (media != null && media.photo != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
            if (size == null || loader.getPathToAttach(size, true).exists()) {
                return false;
            }
            loader.loadFile(ImageLocation.getForObject(size, media.photo), mo, "jpg", FileLoader.PRIORITY_LOW, 0);
            return true;
        }
        return false;
    }

    private static void done(BaseFragment fragment, int count) {
        BaseFragment f = org.telegram.ui.LaunchActivity.getLastFragment();
        if (f == null) {
            return;
        }
        if (count == 0) {
            BulletinFactory.of(f).createSimpleBulletin(R.raw.ic_download, LocaleController.getString(R.string.MilliyDlNothing)).show();
            return;
        }
        BulletinFactory.of(f).createSimpleBulletin(R.raw.ic_download, LocaleController.formatString(R.string.MilliyDlQueued, count),
                LocaleController.getString(R.string.MilliyDlOpen), () -> open(f)).show();
    }

    public static void open(BaseFragment from) {
        if (from != null) {
            from.presentFragment(new ManagerActivity());
        }
    }

    /** Yuklanayotgan barcha hujjatlarni to'xtatadi. */
    public static void pauseAll(int account) {
        DownloadController dc = DownloadController.getInstance(account);
        for (MessageObject mo : new ArrayList<>(dc.downloadingFiles)) {
            if (mo.getDocument() != null) {
                FileLoader.getInstance(account).cancelLoadFile(mo.getDocument());
            }
        }
    }

    /** To'xtatilgan yuklashlarni davom ettiradi. */
    public static void resumeAll(int account) {
        DownloadController dc = DownloadController.getInstance(account);
        for (MessageObject mo : new ArrayList<>(dc.downloadingFiles)) {
            TLRPC.Document doc = mo.getDocument();
            if (doc != null && !FileLoader.getInstance(account).getPathToMessage(mo.messageOwner).exists()) {
                FileLoader.getInstance(account).loadFile(doc, mo, FileLoader.PRIORITY_LOW, 0);
            }
        }
    }

    /** «Yuklab olish menejeri» sahifasi: Telegram'ning yuklanmalar ro'yxati + hammasini to'xtatish/davom ettirish. */
    public static class ManagerActivity extends BaseFragment implements FilteredSearchView.UiCallback {

        private SearchDownloadsContainer container;

        @Override
        public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(LocaleController.getString(R.string.MilliyDlManager));
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(1, R.drawable.ic_action_pause);
            menu.addItem(2, R.drawable.msg_download);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == 1) {
                        pauseAll(currentAccount);
                        BulletinFactory.of(ManagerActivity.this).createSimpleBulletin(R.raw.ic_download, LocaleController.getString(R.string.MilliyDlPaused)).show();
                        container.update(true);
                    } else if (id == 2) {
                        resumeAll(currentAccount);
                        BulletinFactory.of(ManagerActivity.this).createSimpleBulletin(R.raw.ic_download, LocaleController.getString(R.string.MilliyDlResumed)).show();
                        container.update(true);
                    }
                }
            });
            FrameLayout frame = new FrameLayout(context);
            frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            container = new SearchDownloadsContainer(this, currentAccount);
            container.setUiCallback(this);
            frame.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            fragmentView = frame;
            return frame;
        }

        @Override
        public void onResume() {
            super.onResume();
            if (container != null) {
                container.update(false);
            }
        }

        @Override
        public void goToMessage(MessageObject messageObject) {
            Bundle args = new Bundle();
            long did = messageObject.getDialogId();
            if (did > 0) {
                args.putLong("user_id", did);
            } else {
                args.putLong("chat_id", -did);
            }
            args.putInt("message_id", messageObject.getId());
            presentFragment(new ChatActivity(args));
        }

        @Override
        public boolean actionModeShowing() {
            return false;
        }

        @Override
        public void toggleItemSelection(MessageObject item, View view, int a) {
        }

        @Override
        public boolean isSelected(FilteredSearchView.MessageHashId messageHashId) {
            return false;
        }

        @Override
        public void showActionMode() {
        }

        @Override
        public int getFolderId() {
            return 0;
        }
    }
}
