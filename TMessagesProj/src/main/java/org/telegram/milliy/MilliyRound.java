package org.telegram.milliy;

import android.content.Intent;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

/** Galereyadagi istalgan videoni dumaloq video xabar qilib yuborish (markazdan kvadrat kesiladi, 60 soniyagacha). */
public class MilliyRound {

    public static final int REQUEST_CODE = 7701;
    private static final int SIDE = 400;
    private static final long MAX_MS = 59_500;

    public static boolean backCamera() {
        return MilliyPro.get("round_back", false);
    }

    public static void pick(ChatActivity chat) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            chat.startActivityForResult(intent, REQUEST_CODE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onPicked(ChatActivity chat, Intent data) {
        if (data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        long dialogId = chat.getDialogId();
        BulletinFactory.of(chat).createSimpleBulletin(R.raw.forward, LocaleController.getString(R.string.MilliyRoundPreparing)).show();
        Utilities.globalQueue.postRunnable(() -> {
            String path = MediaController.copyFileToCache(uri, "mp4");
            VideoEditedInfo info = path != null ? SendMessagesHelper.milliyCompressionSettings(path) : null;
            if (info == null) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.MilliyRoundFailed)).show());
                return;
            }
            // Natija o'lchami: qisqa tomoni ~SIDE bo'lguncha kichraytiramiz
            int w = info.originalWidth, h = info.originalHeight;
            float scale = Math.min(1f, SIDE / (float) Math.min(w, h));
            info.resultWidth = Math.max(2, Math.round(w * scale / 2) * 2);
            info.resultHeight = Math.max(2, Math.round(h * scale / 2) * 2);
            boolean rotated = info.rotationValue == 90 || info.rotationValue == 270;
            int dispW = rotated ? info.resultHeight : info.resultWidth;
            int dispH = rotated ? info.resultWidth : info.resultHeight;
            int side = Math.min(dispW, dispH) / 16 * 16;
            MediaController.CropState crop = new MediaController.CropState();
            crop.cropPw = side / (float) dispW;
            crop.cropPh = side / (float) dispH;
            crop.transformWidth = side;
            crop.transformHeight = side;
            info.cropState = crop;
            info.roundVideo = true;
            info.muted = false;
            info.bitrate = 1_000_000;
            if (info.estimatedDuration > MAX_MS) {
                info.startTime = 0;
                info.endTime = MAX_MS * 1000;
                info.estimatedDuration = MAX_MS;
            }
            info.estimatedSize = Math.max(1, (long) (info.estimatedDuration / 1000f * (info.bitrate + 64_000) / 8));
            AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingVideo(chat.getAccountInstance(), path, info, null, null, dialogId,
                    null, null, null, null, null, 0, null, true, 0, 0, false, false, null, SendMessageChatArguments.EMPTY, 0, 0));
        });
    }
}
