package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.VideoShape;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.VideoShapeDrawer;

/**
 * Premium sheet header for video message shapes: the user's own picture as a round video that morphs through
 * every shape with the recording ring running around it. Drawn live, so it needs no promo video from the server.
 */
public class VideoShapesPreviewView extends View implements PagerHeaderView, NotificationCenter.NotificationCenterDelegate {

    private static final long SHAPE_DURATION = 1600;
    private static final long PROGRESS_DURATION = 6000;

    private final VideoShapeDrawer shapeDrawer = new VideoShapeDrawer(this::invalidate);
    private final ImageReceiver imageReceiver = new ImageReceiver(this);
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final boolean hasPhoto;
    private final int currentAccount;
    private final TLRPC.User user;
    private boolean fullUserRequested;
    private final StarParticlesView.Drawable particles = new StarParticlesView.Drawable(30);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final long startTime = SystemClock.elapsedRealtime();
    private int index;
    private float offset;

    public VideoShapesPreviewView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        shapeDrawer.setLive(true);
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        avatarDrawable.setInfo(currentAccount, user);
        // Square: the shape clip does the rounding (AvatarDrawable draws a circle for radius 0).
        avatarDrawable.setRoundRadius(1);
        hasPhoto = user != null && user.photo != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty);
        this.currentAccount = currentAccount;
        this.user = user;
        loadImage();

        particles.size1 = 14;
        particles.size2 = 10;
        particles.size3 = 8;
        particles.resourcesProvider = resourcesProvider;
        particles.colorKey = Theme.key_premiumStartSmallStarsColor2;
        particles.init();

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeJoin(Paint.Join.ROUND);
        trackPaint.setStrokeWidth(dp(3));
        trackPaint.setColor(0x40ffffff);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeJoin(Paint.Join.ROUND);
        progressPaint.setStrokeWidth(dp(3));
        progressPaint.setColor(0xffffffff);
    }

    /**
     * Same sources as the expanded profile: the largest size of the full profile photo (up to 1280px) and its video
     * if the avatar is a video, the 640px photo while the full profile isn't loaded; decoded at the size shown.
     */
    private void loadImage() {
        if (!hasPhoto) {
            return;
        }
        TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
        if (userFull == null && !fullUserRequested) {
            fullUserRequested = true;
            MessagesController.getInstance(currentAccount).loadFullUser(user, 0, true);
        }
        TLRPC.Photo photo = userFull == null ? null : user.photo.personal ? userFull.personal_photo : userFull.profile_photo;
        ImageLocation imageLocation = null;
        ImageLocation videoLocation = null;
        if (photo != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 1280);
            if (photoSize != null) {
                imageLocation = ImageLocation.getForPhoto(photoSize, photo);
            }
            // Straight from the photo: ImageLocation's TYPE_VIDEO_BIG only returns videos of premium users, and
            // this sheet is shown to people who aren't. Emoji/sticker markup avatars stay a still image.
            TLRPC.VideoSize best = null;
            if (photo.video_sizes != null) {
                for (TLRPC.VideoSize videoSize : photo.video_sizes) {
                    if (videoSize instanceof TLRPC.TL_videoSizeEmojiMarkup || videoSize instanceof TLRPC.TL_videoSizeStickerMarkup) {
                        continue;
                    }
                    if (best == null || videoSize.w > best.w) {
                        best = videoSize;
                    }
                }
            }
            if (best != null) {
                videoLocation = ImageLocation.getForPhoto(best, photo);
            }
        }
        if (imageLocation == null) {
            imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG);
        }
        imageReceiver.setRoundRadius(0);
        imageReceiver.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, imageLocation, "400_400", ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", null, 0, null, user, 1);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad && user != null && args.length > 0 && args[0] instanceof Long && (Long) args[0] == user.id) {
            loadImage();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
    }

    @Override
    public void setOffset(float translationX) {
        offset = getMeasuredWidth() <= 0 ? 0 : Math.min(1f, Math.abs(translationX / getMeasuredWidth()));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final long time = SystemClock.elapsedRealtime() - startTime;
        final int wanted = (int) ((time / SHAPE_DURATION) % VideoShape.ORDER.length);
        if (wanted != index) {
            index = wanted;
            shapeDrawer.setShape(VideoShape.ORDER[index], true);
        }

        final float size = Math.min(getWidth(), getHeight()) * 0.56f;
        final float cx = getWidth() / 2f, cy = getHeight() / 2f + dp(24);
        bounds.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);

        particles.rect.set(0, 0, getWidth(), getHeight());
        particles.excludeRect.set(bounds);
        particles.excludeRect.inset(-dp(10), -dp(10));
        particles.onDraw(canvas);

        canvas.save();
        final float scale = 1f + offset * 0.6f;
        canvas.scale(scale, scale, cx, cy);
        // Fading while paging needs a layer; it only has to cover the shape and its ring.
        if (offset > 0) {
            final float outset = dp(16);
            canvas.saveLayerAlpha(bounds.left - outset, bounds.top - outset, bounds.right + outset, bounds.bottom + outset, (int) (255 * (1f - offset)), Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }

        canvas.save();
        shapeDrawer.clip(canvas, bounds);
        // Initials on the peer colour gradient, as a square fill under the photo (or instead of it).
        avatarDrawable.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
        avatarDrawable.draw(canvas);
        if (hasPhoto) {
            imageReceiver.setImageCoords(bounds.left, bounds.top, bounds.width(), bounds.height());
            imageReceiver.draw(canvas);
        }
        canvas.restore();

        final float progress = (time % PROGRESS_DURATION) / (float) PROGRESS_DURATION;
        shapeDrawer.drawTrack(canvas, bounds, dp(9), trackPaint);
        shapeDrawer.drawProgress(canvas, bounds, dp(9), progress, progressPaint);

        canvas.restore();
        canvas.restore();
        invalidate();
    }
}
