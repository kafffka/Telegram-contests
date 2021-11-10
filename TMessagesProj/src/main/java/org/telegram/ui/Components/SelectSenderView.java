package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class SelectSenderView extends View {

    private long uid;
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable closeDrawable;
    private RectF rect = new RectF();
    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;
    private float progress;
    private boolean closing;
    private long lastUpdateTime;
    private int[] colors = new int[8];
    private int currentAccount;

    public SelectSenderView(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;

        closeDrawable = getResources().getDrawable(R.drawable.delete);
        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));

        imageReceiver = new ImageReceiver();
        imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
    }

    public void setPeer(TLRPC.Peer peer) {
        Object selectSenderObject = null;
        if (peer instanceof TLRPC.TL_peerUser) {
            selectSenderObject = MessagesController.getInstance(currentAccount).getUser(peer.user_id);
        } else if (peer instanceof TLRPC.TL_peerChat) {
            selectSenderObject = MessagesController.getInstance(currentAccount).getChat(peer.chat_id);
        } else if (peer instanceof TLRPC.TL_peerChannel) {
            selectSenderObject = MessagesController.getInstance(currentAccount).getChat(peer.channel_id);
        }
        setItem(selectSenderObject);
    }

    private void setItem(Object object) {
        ImageLocation imageLocation;
        Object imageParent;

        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            uid = user.id;
            avatarDrawable.setInfo(user);
            imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
            imageParent = user;
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            avatarDrawable.setInfo(chat);
            uid = -chat.id;
            imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            imageParent = chat;
        } else {
            imageLocation = null;
            imageParent = null;
        }

        imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, imageParent, 1);
        updateColors();
        invalidate();
    }

    public void updateColors() {
        int color = avatarDrawable.getColor();
        int back = Theme.getColor(Theme.key_groupcreate_spanBackground);
        int delete = Theme.getColor(Theme.key_groupcreate_spanDelete);
        colors[0] = Color.red(back);
        colors[1] = Color.red(color);
        colors[2] = Color.green(back);
        colors[3] = Color.green(color);
        colors[4] = Color.blue(back);
        colors[5] = Color.blue(color);
        colors[6] = Color.alpha(back);
        colors[7] = Color.alpha(color);
        closeDrawable.setColorFilter(new PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY));
        backPaint.setColor(back);
    }

    public boolean isClosing() {
        return closing;
    }

    public void startCloseAnimation() {
        if (closing) {
            return;
        }
        closing = true;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void cancelCloseAnimation() {
        if (!closing) {
            return;
        }
        closing = false;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public long getUid() {
        return uid;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (closing && progress != 1.0f || !closing && progress != 0.0f) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            if (closing) {
                progress += dt / 120.0f;
                if (progress >= 1.0f) {
                    progress = 1.0f;
                }
            } else {
                progress -= dt / 120.0f;
                if (progress < 0.0f) {
                    progress = 0.0f;
                }
            }
            invalidate();
        }
        canvas.save();
        rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(30));
        backPaint.setColor(Color.argb(colors[6] + (int) ((colors[7] - colors[6]) * progress), colors[0] + (int) ((colors[1] - colors[0]) * progress), colors[2] + (int) ((colors[3] - colors[2]) * progress), colors[4] + (int) ((colors[5] - colors[4]) * progress)));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(15), AndroidUtilities.dp(15), backPaint);
        imageReceiver.draw(canvas);
        if (progress != 0) {
            int color = avatarDrawable.getColor();
            float alpha = Color.alpha(color) / 255.0f;
            backPaint.setColor(color);
            backPaint.setAlpha((int) (255 * progress * alpha));
            canvas.drawCircle(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15), backPaint);
            canvas.save();
            canvas.rotate(45 * (1.0f - progress), AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            closeDrawable.setBounds(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
            closeDrawable.setAlpha((int) (255 * progress));
            closeDrawable.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

}
