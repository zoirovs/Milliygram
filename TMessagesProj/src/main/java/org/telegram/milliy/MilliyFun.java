package org.telegram.milliy;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Yashirin kodlar: «.heart» — yurakcha animatsiyasi, «.typer matn» — matn harfma-harf yoziladi.
 * Xabar oddiy yuboriladi, keyin bir necha marta tahrirlanadi (tahrir limitiga tushmaslik uchun kadrlar kam).
 */
public class MilliyFun {

    private static final int FRAME_MS = 800;
    private static final int TYPER_MAX_FRAMES = 14;

    private static final String WHITE = "🤍", RED = "❤️";

    /** 7x6 yurak shakli: 1 — yurak katakchasi. */
    private static final int[][] SHAPE = {
            {0, 1, 1, 0, 1, 1, 0},
            {1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 0},
            {0, 0, 1, 1, 1, 0, 0},
            {0, 0, 0, 1, 0, 0, 0},
    };
    private static final String[] COLORS = {"❤️", "🧡", "💛", "💚", "💙", "💜", "🩷"};

    /** true — kod ushlandi va animatsiya boshlandi (oddiy yuborish kerak emas). */
    public static boolean trySend(int account, long dialogId, CharSequence text, MessageObject replyTo, MessageObject topMsg) {
        if (text == null || DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        String t = text.toString().trim();
        ArrayList<String> frames;
        String lower = t.toLowerCase(Locale.US);
        if (lower.equals(".heart")) {
            frames = heartFrames();
        } else if (lower.startsWith(".typer ") && t.length() > 7) {
            frames = typerFrames(t.substring(7).trim());
        } else {
            return false;
        }
        if (frames.size() < 2) {
            return false;
        }
        start(account, dialogId, frames, replyTo, topMsg);
        return true;
    }

    private static ArrayList<String> heartFrames() {
        ArrayList<String> out = new ArrayList<>();
        // yurak oq kataklardan to'ladi, keyin ranglar almashadi, oxirida bitta katta yurak
        int cells = 0;
        for (int[] row : SHAPE) {
            for (int c : row) {
                cells += c;
            }
        }
        int[] steps = {cells / 3, 2 * cells / 3, cells};
        for (int step : steps) {
            out.add(grid(step, RED));
        }
        for (String color : COLORS) {
            out.add(grid(cells, color));
        }
        out.add("❤️");
        return out;
    }

    private static String grid(int filled, String color) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int r = 0; r < SHAPE.length; r++) {
            for (int c = 0; c < SHAPE[r].length; c++) {
                if (SHAPE[r][c] == 1) {
                    sb.append(n++ < filled ? color : WHITE);
                } else {
                    sb.append(WHITE);
                }
            }
            if (r < SHAPE.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static ArrayList<String> typerFrames(String text) {
        ArrayList<String> out = new ArrayList<>();
        int len = text.codePointCount(0, text.length());
        if (len == 0) {
            return out;
        }
        int steps = Math.min(TYPER_MAX_FRAMES, len);
        for (int i = 1; i <= steps; i++) {
            int cps = (int) Math.ceil(len * (double) i / steps);
            int end = text.offsetByCodePoints(0, cps);
            out.add(i < steps ? text.substring(0, end) + "▒" : text);
        }
        if (out.size() == 1) {
            out.add(0, "▒");
        }
        return out;
    }

    private static void start(int account, long dialogId, ArrayList<String> frames, MessageObject replyTo, MessageObject topMsg) {
        final String first = frames.get(0);
        final NotificationCenter nc = NotificationCenter.getInstance(account);
        final NotificationCenter.NotificationCenterDelegate[] holder = new NotificationCenter.NotificationCenterDelegate[1];
        holder[0] = (id, acc, args) -> {
            if (id != NotificationCenter.messageReceivedByServer || args.length < 4) {
                return;
            }
            TLRPC.Message msg = args[2] instanceof TLRPC.Message ? (TLRPC.Message) args[2] : null;
            long did = args[3] instanceof Long ? (Long) args[3] : 0;
            if (msg == null || did != dialogId || !first.equals(msg.message)) {
                return;
            }
            nc.removeObserver(holder[0], NotificationCenter.messageReceivedByServer);
            animate(account, dialogId, msg.id, frames, 1);
        };
        nc.addObserver(holder[0], NotificationCenter.messageReceivedByServer);
        AndroidUtilities.runOnUIThread(() -> nc.removeObserver(holder[0], NotificationCenter.messageReceivedByServer), 60_000);
        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(first, dialogId, replyTo, topMsg,
                null, false, null, null, null, true, 0, 0, null, false));
    }

    private static void animate(int account, long dialogId, int mid, ArrayList<String> frames, int index) {
        if (index >= frames.size()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
            req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
            req.id = mid;
            req.message = frames.get(index);
            req.flags |= 2048;
            req.no_webpage = true;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    MessagesController.getInstance(account).processUpdates((TLRPC.Updates) response, false);
                }
                if (error == null || "MESSAGE_NOT_MODIFIED".equals(error.text)) {
                    animate(account, dialogId, mid, frames, index + 1);
                }
            });
        }, FRAME_MS);
    }
}
