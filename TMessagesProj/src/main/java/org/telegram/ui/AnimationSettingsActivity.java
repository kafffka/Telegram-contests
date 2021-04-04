package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.time.FastDateFormat;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Locale;

public class AnimationSettingsActivity extends BaseFragment {

    private class ViewPage extends FrameLayout {
        private RecyclerListView listView;
        private ListAdapter listAdapter;
        private LinearLayoutManager layoutManager;
        private int selectedType;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private ViewPage[] viewPages = new ViewPage[9];
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;
    private int maximumVelocity;
    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    private Paint backgroundPaint = new Paint();
    private ListAdapter backgroundAdapter;
    private ListAdapter shortTextAdapter;
    private ListAdapter longTextAdapter;
    private ListAdapter linkAdapter;
    private ListAdapter emojiAdapter;
    private ListAdapter stickerAdapter;
    private ListAdapter voiceAdapter;
    private ListAdapter videoAdapter;
    private ListAdapter photosAdapter;


    private ActionBarPopupWindow scrimPopupWindow;
    private ActionBarMenuItem otherItem;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Animation Settings");
        actionBar.setAllowOverlayTitle(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 0) {
                    shareParameters();
                } else if (id == 1) {
                    importParameters();
                } else if (id == 2) {
                    restoreSettings();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        otherItem = menu.addItem(10, R.drawable.ic_ab_other);
        otherItem.addSubItem(0, "Share Parameters");
        otherItem.addSubItem(1, "Import Parameters");
        otherItem.addSubItem(2, "Restore to Defaults").setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));

        hasOwnBackground = true;

        backgroundAdapter = new ListAdapter(context, 0);
        shortTextAdapter = new ListAdapter(context, 1);
        longTextAdapter = new ListAdapter(context, 2);
        linkAdapter = new ListAdapter(context, 3);
        emojiAdapter = new ListAdapter(context, 4);
        stickerAdapter = new ListAdapter(context, 5);
        voiceAdapter = new ListAdapter(context, 6);
        videoAdapter = new ListAdapter(context, 7);
        photosAdapter = new ListAdapter(context, 8);


        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));

        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (viewPages[0].selectedType == id) {
                    return;
                }
                viewPages[1].selectedType = id;
                viewPages[1].setVisibility(View.VISIBLE);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1 && viewPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
                }
                if (progress == 1) {
                    ViewPage tempPage = viewPages[0];
                    viewPages[0] = viewPages[1];
                    viewPages[1] = tempPage;
                    viewPages[1].setVisibility(View.GONE);
                }
            }
        });
        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context) {

            private int startedTrackingPointerId;
            private boolean startedTracking;
            private boolean maybeStartTracking;
            private int startedTrackingX;
            private int startedTrackingY;
            private VelocityTracker velocityTracker;
            private boolean globalIgnoreLayout;

            private boolean prepareForMoving(MotionEvent ev, boolean forward) {
                int id = scrollSlidingTextTabStrip.getNextPageId(forward);
                if (id < 0) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                maybeStartTracking = false;
                startedTracking = true;
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                scrollSlidingTextTabStrip.setEnabled(false);
                viewPages[1].selectedType = id;
                viewPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                switchToCurrentSelectedMode(true);
                if (forward) {
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
                }
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                globalIgnoreLayout = true;
                for (int a = 0; a < viewPages.length; a++) {
                    if (viewPages[a] == null) {
                        continue;
                    }
                    if (viewPages[a].listView != null) {
                        viewPages[a].listView.setPadding(0, actionBarHeight, 0, AndroidUtilities.dp(4));
                    }
                }
                globalIgnoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
                }
            }

            @Override
            public void requestLayout() {
                if (globalIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            public boolean checkTabsAnimationInProgress() {
                if (tabsAnimationInProgress) {
                    boolean cancel = false;
                    if (backAnimation) {
                        if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                            viewPages[0].setTranslationX(0);
                            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                            cancel = true;
                        }
                    } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                        viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                        viewPages[1].setTranslationX(0);
                        cancel = true;
                    }
                    if (cancel) {
                        if (tabsAnimation != null) {
                            tabsAnimation.cancel();
                            tabsAnimation = null;
                        }
                        tabsAnimationInProgress = false;
                    }
                    return tabsAnimationInProgress;
                }
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip.isAnimatingIndicator() || onTouchEvent(ev);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                canvas.drawRect(0, actionBar.getMeasuredHeight() + actionBar.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!parentLayout.checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
                    if (ev != null) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.addMovement(ev);
                    }
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        velocityTracker.clear();
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                                viewPages[0].setTranslationX(0);
                                viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                                scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, 0);
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) > dy) {
                                prepareForMoving(ev, dx < 0);
                            }
                        } else if (startedTracking) {
                            if (animatingForward) {
                                viewPages[0].setTranslationX(dx);
                                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() + dx);
                            } else {
                                viewPages[0].setTranslationX(dx);
                                viewPages[1].setTranslationX(dx - viewPages[0].getMeasuredWidth());
                            }
                            float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                        float velX;
                        float velY;
                        if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                            velX = velocityTracker.getXVelocity();
                            velY = velocityTracker.getYVelocity();
                            if (!startedTracking) {
                                if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                    prepareForMoving(ev, velX < 0);
                                }
                            }
                        } else {
                            velX = 0;
                            velY = 0;
                        }
                        if (startedTracking) {
                            float x = viewPages[0].getX();
                            tabsAnimation = new AnimatorSet();
                            backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float dx;
                            if (backAnimation) {
                                dx = Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, viewPages[1].getMeasuredWidth())
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, -viewPages[1].getMeasuredWidth())
                                    );
                                }
                            } else {
                                dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, -viewPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, viewPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                    );
                                }
                            }
                            tabsAnimation.setInterpolator(interpolator);

                            int width = getMeasuredWidth();
                            int halfWidth = width / 2;
                            float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                            float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
                            velX = Math.abs(velX);
                            int duration;
                            if (velX > 0) {
                                duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                            } else {
                                float pageDelta = dx / getMeasuredWidth();
                                duration = (int) ((pageDelta + 1.0f) * 100.0f);
                            }
                            duration = Math.max(150, Math.min(duration, 600));

                            tabsAnimation.setDuration(duration);
                            tabsAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    tabsAnimation = null;
                                    if (backAnimation) {
                                        viewPages[1].setVisibility(View.GONE);
                                    } else {
                                        ViewPage tempPage = viewPages[0];
                                        viewPages[0] = viewPages[1];
                                        viewPages[1] = tempPage;
                                        viewPages[1].setVisibility(View.GONE);
                                        scrollSlidingTextTabStrip.selectTabWithId(viewPages[0].selectedType, 1.0f);
                                    }
                                    tabsAnimationInProgress = false;
                                    maybeStartTracking = false;
                                    startedTracking = false;
                                    actionBar.setEnabled(true);
                                    scrollSlidingTextTabStrip.setEnabled(true);
                                }
                            });
                            tabsAnimation.start();
                            tabsAnimationInProgress = true;
                            startedTracking = false;
                        } else {
                            maybeStartTracking = false;
                            actionBar.setEnabled(true);
                            scrollSlidingTextTabStrip.setEnabled(true);
                        }
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                    }
                    return startedTracking;
                }
                return false;
            }
        };
        frameLayout.setWillNotDraw(false);

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;

        for (int a = 0; a < viewPages.length; a++) {
            if (a == 0) {
                if (viewPages[a] != null && viewPages[a].layoutManager != null) {
                    scrollToPositionOnRecreate = viewPages[a].layoutManager.findFirstVisibleItemPosition();
                    if (scrollToPositionOnRecreate != viewPages[a].layoutManager.getItemCount() - 1) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) viewPages[a].listView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                        if (holder != null) {
                            scrollToOffsetOnRecreate = holder.itemView.getTop();
                        } else {
                            scrollToPositionOnRecreate = -1;
                        }
                    } else {
                        scrollToPositionOnRecreate = -1;
                    }
                }
            }
            final ViewPage ViewPage = new ViewPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress) {
                        if (viewPages[0] == this) {
                            float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    }
                }
            };
            frameLayout.addView(ViewPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[a] = ViewPage;

            final LinearLayoutManager layoutManager = viewPages[a].layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            };
            viewPages[a].listView = new RecyclerListView(context);
            viewPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            viewPages[a].listView.setItemAnimator(null);
            viewPages[a].listView.setClipToPadding(false);
            viewPages[a].listView.setSectionsType(2);
            viewPages[a].listView.setLayoutManager(layoutManager);
            viewPages[a].addView(viewPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[a].listView.setOnItemClickListener((view, position) -> {
                ListAdapter listAdapter = (ListAdapter) viewPages[0].listView.getAdapter();
                if ((listAdapter.currentType != 0 && position == listAdapter.durationRow) || position == listAdapter.sendMessageDurationPositionRow || position == listAdapter.openChatDurationPositionRow || position == listAdapter.jumpToMessageDurationPositionRow) {

                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        scrimPopupWindow.dismiss();
                        scrimPopupWindow = null;
                    }

                    Rect rect = new Rect();
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity());
                    popupLayout.setOnTouchListener(new View.OnTouchListener() {
                        private int[] pos = new int[2];

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                                    View contentView = scrimPopupWindow.getContentView();
                                    contentView.getLocationInWindow(pos);
                                    rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                                    if (!rect.contains((int) event.getX(), (int) event.getY())) {
                                        scrimPopupWindow.dismiss();
                                    }
                                }
                            } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                                if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                                    scrimPopupWindow.dismiss();
                                }
                            }
                            return false;
                        }
                    });
                    popupLayout.setDispatchKeyEventListener(keyEvent -> {
                        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                            scrimPopupWindow.dismiss();
                        }
                    });

                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    ScrollView scrollView;
                    if (Build.VERSION.SDK_INT >= 21) {
                        scrollView = new ScrollView(getParentActivity(), null, 0, R.style.scrollbarShapeStyle) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                                setMeasuredDimension(linearLayout.getMeasuredWidth(), getMeasuredHeight());
                            }
                        };
                    } else {
                        scrollView = new ScrollView(getParentActivity());
                    }
                    scrollView.setClipToPadding(false);
                    popupLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                    linearLayout.setOrientation(LinearLayout.VERTICAL);

                    ArrayList<Integer> items = new ArrayList<>();
                    items.add(200);
                    items.add(300);
                    items.add(400);
                    items.add(500);
                    items.add(600);
                    items.add(700);
                    items.add(800);
                    items.add(900);
                    items.add(1000);
                    items.add(1500);
                    items.add(2000);
                    items.add(3000);

                    for (int i = 0, N = items.size(); i < N; i++) {
                        ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), i == 0, i == N - 1);
                        cell.setText(items.get(i) + "ms");
                        linearLayout.addView(cell);
                        int finalI = i;
                        cell.setOnClickListener(v1 -> {
                            String durationKey = "duration_type_" + viewPages[0].selectedType;
                            int time = items.get(finalI);
                            MessagesController.getGlobalMessageAnimationSettings().edit().putInt(durationKey, time).commit();
                            viewPages[0].listView.getAdapter().notifyDataSetChanged();

                            if (scrimPopupWindow != null) {
                                scrimPopupWindow.dismiss();
                            }
                        });
                    }
                    scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

                    scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                        @Override
                        public void dismiss() {
                            super.dismiss();
                            if (scrimPopupWindow != this) {
                                return;
                            }
                            scrimPopupWindow = null;
                        }
                    };
                    scrimPopupWindow.setPauseNotifications(true);
                    scrimPopupWindow.setDismissAnimationDuration(220);
                    scrimPopupWindow.setOutsideTouchable(true);
                    scrimPopupWindow.setClippingEnabled(true);
                    scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                    scrimPopupWindow.setFocusable(true);
                    scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                    scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                    scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
                    scrimPopupWindow.showAsDropDown(view, view.getMeasuredWidth() - popupLayout.getMeasuredWidth() - AndroidUtilities.dp(16), 0);

                }
            });
            viewPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
                        int scrollY = (int) -actionBar.getTranslationY();
                        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
                        if (scrollY != 0 && scrollY != actionBarHeight) {
                            if (scrollY < actionBarHeight / 2) {
                                viewPages[0].listView.smoothScrollBy(0, -scrollY);
                            } else {
                                viewPages[0].listView.smoothScrollBy(0, actionBarHeight - scrollY);
                            }
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (recyclerView == viewPages[0].listView) {
                        float currentTranslation = actionBar.getTranslationY();
                        float newTranslation = currentTranslation - dy;
                        if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
                            newTranslation = -ActionBar.getCurrentActionBarHeight();
                        } else if (newTranslation > 0) {
                            newTranslation = 0;
                        }
                        if (newTranslation != currentTranslation) {
                            setScrollY(newTranslation);
                        }
                    }
                }
            });
            if (a == 0 && scrollToPositionOnRecreate != -1) {
                layoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            }
            if (a != 0) {
                viewPages[a].setVisibility(View.GONE);
            }
        }


        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        updateTabs();
        switchToCurrentSelectedMode(false);
        return fragmentView;
    }


    @Override
    public void onResume() {
        super.onResume();
        if (backgroundAdapter != null) {
            backgroundAdapter.notifyDataSetChanged();
        }
        if (shortTextAdapter != null) {
            shortTextAdapter.notifyDataSetChanged();
        }
        if (longTextAdapter != null) {
            longTextAdapter.notifyDataSetChanged();
        }
        if (linkAdapter != null) {
            linkAdapter.notifyDataSetChanged();
        }
        if (emojiAdapter != null) {
            emojiAdapter.notifyDataSetChanged();
        }
        if (stickerAdapter != null) {
            stickerAdapter.notifyDataSetChanged();
        }
        if (voiceAdapter != null) {
            voiceAdapter.notifyDataSetChanged();
        }
        if (videoAdapter != null) {
            videoAdapter.notifyDataSetChanged();
        }
        if (photosAdapter != null) {
            photosAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public void finishFragment() {
        super.finishFragment();
        if (scrimPopupWindow != null) {
            scrimPopupWindow.setPauseNotifications(false);
            scrimPopupWindow.dismiss();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (scrimPopupWindow != null) {
            scrimPopupWindow.setPauseNotifications(false);
            scrimPopupWindow.dismiss();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            return false;
        }
        return true;
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
//        scrollSlidingTextTabStrip.addTextTab(0, "Background");
        scrollSlidingTextTabStrip.addTextTab(1, "Short Text");
//        scrollSlidingTextTabStrip.addTextTab(2, "Long Text");
        scrollSlidingTextTabStrip.addTextTab(3, "Link");
        scrollSlidingTextTabStrip.addTextTab(4, "Emoji");
        scrollSlidingTextTabStrip.addTextTab(5, "Sticker");
        scrollSlidingTextTabStrip.addTextTab(6, "Voice");
        scrollSlidingTextTabStrip.addTextTab(7, "Video");
//        scrollSlidingTextTabStrip.addTextTab(8, "Photos");
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            viewPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        for (ViewPage viewPage : viewPages) {
            viewPage.listView.setPinnedSectionOffsetY((int) value);
        }
        fragmentView.invalidate();
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (ViewPage viewPage : viewPages) {
            viewPage.listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = viewPages[a].listView.getAdapter();
        viewPages[a].listView.setPinnedHeaderShadowDrawable(null);
        if (viewPages[a].selectedType == 0) {
            if (currentAdapter != backgroundAdapter) {
                viewPages[a].listView.setAdapter(backgroundAdapter);
            }
        } else if (viewPages[a].selectedType == 1) {
            if (currentAdapter != shortTextAdapter) {
                viewPages[a].listView.setAdapter(shortTextAdapter);
            }
        } else if (viewPages[a].selectedType == 2) {
            if (currentAdapter != longTextAdapter) {
                viewPages[a].listView.setAdapter(longTextAdapter);
            }
        } else if (viewPages[a].selectedType == 3) {
            if (currentAdapter != linkAdapter) {
                viewPages[a].listView.setAdapter(linkAdapter);
            }
        } else if (viewPages[a].selectedType == 4) {
            if (currentAdapter != emojiAdapter) {
                viewPages[a].listView.setAdapter(emojiAdapter);
            }
        } else if (viewPages[a].selectedType == 5) {
            if (currentAdapter != stickerAdapter) {
                viewPages[a].listView.setAdapter(stickerAdapter);
            }
        } else if (viewPages[a].selectedType == 6) {
            if (currentAdapter != voiceAdapter) {
                viewPages[a].listView.setAdapter(voiceAdapter);
            }
        } else if (viewPages[a].selectedType == 7) {
            if (currentAdapter != videoAdapter) {
                viewPages[a].listView.setAdapter(videoAdapter);
            }
        } else if (viewPages[a].selectedType == 8) {
            if (currentAdapter != photosAdapter) {
                viewPages[a].listView.setAdapter(photosAdapter);
            }
        }
        viewPages[a].listView.setVisibility(View.VISIBLE);

        if (actionBar.getTranslationY() != 0) {
            viewPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        }
    }

    private void shareParameters() {
        FastDateFormat dateFormat = FastDateFormat.getInstance("dd_MM_yyyy_HH_mm_ss", Locale.US);
        try {
            File sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            if (sdCard == null) {
                return;
            }
            File dir = new File(sdCard.getAbsolutePath() + "/animation");
            dir.mkdirs();
            File currentFile = new File(dir, dateFormat.format(System.currentTimeMillis()) + ".txt");

            currentFile.createNewFile();
            FileOutputStream stream = new FileOutputStream(currentFile);

            ArrayList<String> intKeys = new ArrayList();
            intKeys.add("duration_type_1");
            intKeys.add("start_time_type_1_row_xPosition");
            intKeys.add("end_time_type_1_row_xPosition");
            intKeys.add("start_time_type_1_row_yPosition");
            intKeys.add("end_time_type_1_row_yPosition");
            intKeys.add("start_time_type_1_row_bubbleShape");
            intKeys.add("end_time_type_1_row_bubbleShape");
            intKeys.add("start_time_type_1_row_textScale");
            intKeys.add("end_time_type_1_row_textScale");
            intKeys.add("start_time_type_1_row_colorChange");
            intKeys.add("end_time_type_1_row_colorChange");
            intKeys.add("start_time_type_1_row_timeAppears");
            intKeys.add("end_time_type_1_row_timeAppears");

            intKeys.add("duration_type_3");
            intKeys.add("start_time_type_3_row_xPosition");
            intKeys.add("end_time_type_3_row_xPosition");
            intKeys.add("start_time_type_3_row_yPosition");
            intKeys.add("end_time_type_3_row_yPosition");
            intKeys.add("start_time_type_3_row_bubbleShape");
            intKeys.add("end_time_type_3_row_bubbleShape");
            intKeys.add("start_time_type_3_row_textScale");
            intKeys.add("end_time_type_3_row_textScale");
            intKeys.add("start_time_type_3_row_colorChange");
            intKeys.add("end_time_type_3_row_colorChange");
            intKeys.add("start_time_type_3_row_timeAppears");
            intKeys.add("end_time_type_3_row_timeAppears");

            intKeys.add("duration_type_4");
            intKeys.add("start_time_type_4_row_xPosition");
            intKeys.add("end_time_type_4_row_xPosition");
            intKeys.add("start_time_type_4_row_yPosition");
            intKeys.add("end_time_type_4_row_yPosition");
            intKeys.add("start_time_type_4_row_emojiScale");
            intKeys.add("end_time_type_4_row_emojiScale");
            intKeys.add("start_time_type_4_row_timeAppears");
            intKeys.add("end_time_type_4_row_timeAppears");
            intKeys.add("start_time_type_4_row_colorChange");
            intKeys.add("end_time_type_4_row_colorChange");

            intKeys.add("duration_type_5");
            intKeys.add("start_time_type_5_row_xPosition");
            intKeys.add("end_time_type_5_row_xPosition");
            intKeys.add("start_time_type_5_row_yPosition");
            intKeys.add("end_time_type_5_row_yPosition");
            intKeys.add("start_time_type_5_row_stickerScale");
            intKeys.add("end_time_type_5_row_stickerScale");
            intKeys.add("start_time_type_5_row_timeAppears");
            intKeys.add("end_time_type_5_row_timeAppears");
            intKeys.add("start_time_type_5_row_colorChange");
            intKeys.add("end_time_type_5_row_colorChange");

            intKeys.add("duration_type_6");
            intKeys.add("start_time_type_6_row_xPosition");
            intKeys.add("end_time_type_6_row_xPosition");
            intKeys.add("start_time_type_6_row_yPosition");
            intKeys.add("end_time_type_6_row_yPosition");
            intKeys.add("start_time_type_6_row_bubbleShape");
            intKeys.add("end_time_type_6_row_bubbleShape");
            intKeys.add("start_time_type_6_row_textScale");
            intKeys.add("end_time_type_6_row_textScale");
            intKeys.add("start_time_type_6_row_colorChange");
            intKeys.add("end_time_type_6_row_colorChange");
            intKeys.add("start_time_type_6_row_timeAppears");
            intKeys.add("end_time_type_6_row_timeAppears");

            intKeys.add("duration_type_7");
            intKeys.add("start_time_type_7_row_xPosition");
            intKeys.add("end_time_type_7_row_xPosition");
            intKeys.add("start_time_type_7_row_yPosition");
            intKeys.add("end_time_type_7_row_yPosition");

            ArrayList<String> floatKeys = new ArrayList();
            floatKeys.add("start_progress_type_1_row_xPosition");
            floatKeys.add("end_progress_type_1_row_xPosition");
            floatKeys.add("start_progress_type_1_row_yPosition");
            floatKeys.add("end_progress_type_1_row_yPosition");
            floatKeys.add("start_progress_type_1_row_bubbleShape");
            floatKeys.add("end_progress_type_1_row_bubbleShape");
            floatKeys.add("start_progress_type_1_row_textScale");
            floatKeys.add("end_progress_type_1_row_textScale");
            floatKeys.add("start_progress_type_1_row_colorChange");
            floatKeys.add("end_progress_type_1_row_colorChange");
            floatKeys.add("start_progress_type_1_row_timeAppears");
            floatKeys.add("end_progress_type_1_row_timeAppears");

            floatKeys.add("start_progress_type_3_row_xPosition");
            floatKeys.add("end_progress_type_3_row_xPosition");
            floatKeys.add("start_progress_type_3_row_yPosition");
            floatKeys.add("end_progress_type_3_row_yPosition");
            floatKeys.add("start_progress_type_3_row_bubbleShape");
            floatKeys.add("end_progress_type_3_row_bubbleShape");
            floatKeys.add("start_progress_type_3_row_textScale");
            floatKeys.add("end_progress_type_3_row_textScale");
            floatKeys.add("start_progress_type_3_row_colorChange");
            floatKeys.add("end_progress_type_3_row_colorChange");
            floatKeys.add("start_progress_type_3_row_timeAppears");
            floatKeys.add("end_progress_type_3_row_timeAppears");

            floatKeys.add("start_progress_type_4_row_xPosition");
            floatKeys.add("end_progress_type_4_row_xPosition");
            floatKeys.add("start_progress_type_4_row_yPosition");
            floatKeys.add("end_progress_type_4_row_yPosition");
            floatKeys.add("start_progress_type_4_row_emojiScale");
            floatKeys.add("end_progress_type_4_row_emojiScale");
            floatKeys.add("start_progress_type_4_row_timeAppears");
            floatKeys.add("end_progress_type_4_row_timeAppears");
            floatKeys.add("start_progress_type_4_row_colorChange");
            floatKeys.add("end_progress_type_4_row_colorChange");

            floatKeys.add("start_progress_type_5_row_xPosition");
            floatKeys.add("end_progress_type_5_row_xPosition");
            floatKeys.add("start_progress_type_5_row_yPosition");
            floatKeys.add("end_progress_type_5_row_yPosition");
            floatKeys.add("start_progress_type_5_row_stickerScale");
            floatKeys.add("end_progress_type_5_row_stickerScale");
            floatKeys.add("start_progress_type_5_row_timeAppears");
            floatKeys.add("end_progress_type_5_row_timeAppears");
            floatKeys.add("start_progress_type_5_row_colorChange");
            floatKeys.add("end_progress_type_5_row_colorChange");

            floatKeys.add("start_progress_type_6_row_xPosition");
            floatKeys.add("end_progress_type_6_row_xPosition");
            floatKeys.add("start_progress_type_6_row_yPosition");
            floatKeys.add("end_progress_type_6_row_yPosition");
            floatKeys.add("start_progress_type_6_row_bubbleShape");
            floatKeys.add("end_progress_type_6_row_bubbleShape");
            floatKeys.add("start_progress_type_6_row_textScale");
            floatKeys.add("end_progress_type_6_row_textScale");
            floatKeys.add("start_progress_type_6_row_colorChange");
            floatKeys.add("end_progress_type_6_row_colorChange");
            floatKeys.add("start_progress_type_6_row_timeAppears");
            floatKeys.add("end_progress_type_6_row_timeAppears");

            floatKeys.add("start_progress_type_7_row_xPosition");
            floatKeys.add("end_progress_type_7_row_xPosition");
            floatKeys.add("start_progress_type_7_row_yPosition");
            floatKeys.add("end_progress_type_7_row_yPosition");

            OutputStreamWriter streamWriter = new OutputStreamWriter(stream);
            for (int i = 0; i < intKeys.size(); i++) {
                streamWriter.write("i " + intKeys.get(i) + " " + MessagesController.getGlobalMessageAnimationSettings().getInt(intKeys.get(i), -1) + "\n");
            }
            for (int i = 0; i < floatKeys.size(); i++) {
                streamWriter.write("f " + floatKeys.get(i) + " " + MessagesController.getGlobalMessageAnimationSettings().getFloat(floatKeys.get(i), -1) + "\n");
            }
            streamWriter.flush();

            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", currentFile);
            } else {
                uri = Uri.fromFile(currentFile);
            }

            Intent i = new Intent(Intent.ACTION_SEND);
            if (Build.VERSION.SDK_INT >= 24) {
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            i.setType("message/rfc822");
            i.putExtra(Intent.EXTRA_EMAIL, "");
            i.putExtra(Intent.EXTRA_SUBJECT, "Animation settings from " + LocaleController.getInstance().formatterStats.format(System.currentTimeMillis()));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (getParentActivity() != null) {
                try {
                    getParentActivity().startActivity(Intent.createChooser(i, "Select email application."));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void importParameters() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("text/plain");
        getParentActivity().startActivityForResult(Intent.createChooser(chooseFile, "Choose a file"), 42);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == 42) {
            Uri content_describer = data.getData();
            BufferedReader reader = null;
            try {
                InputStream in = getParentActivity().getContentResolver().openInputStream(content_describer);
                reader = new BufferedReader(new InputStreamReader(in));
                String line;

                SharedPreferences.Editor editor = MessagesController.getGlobalMessageAnimationSettings().edit();
                while ((line = reader.readLine()) != null) {
                    if (line.length() != 0) {
                        String[] typeKeyValue = line.split(" ");
                        if (typeKeyValue[0].equals("i")) {
                            editor.putInt(typeKeyValue[1], Integer.parseInt(typeKeyValue[2]));
                        } else if (typeKeyValue[0].equals("f")) {
                            editor.putFloat(typeKeyValue[1], Float.parseFloat(typeKeyValue[2]));
                        }
                    }
                }
                editor.commit();
                shortTextAdapter.notifyDataSetChanged();
                emojiAdapter.notifyDataSetChanged();
                stickerAdapter.notifyDataSetChanged();
                voiceAdapter.notifyDataSetChanged();
                videoAdapter.notifyDataSetChanged();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void restoreSettings() {
        MessagesController.resetGlobalMessageAnimationSettings();
        shortTextAdapter.notifyDataSetChanged();
        emojiAdapter.notifyDataSetChanged();
        stickerAdapter.notifyDataSetChanged();
        voiceAdapter.notifyDataSetChanged();
        videoAdapter.notifyDataSetChanged();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public int currentType;

        private int backgroundPreviewPositionSectionRow;
        private int backgroundPreviewRow;
        private int openBackgroundPreviewPositionRow;
        private int backgroundPreviewPositionSection2Row;

        private int backgroundColorsPositionSectionRow;
        private int backgroundColor1PositionRow;
        private int backgroundColor2PositionRow;
        private int backgroundColor3PositionRow;
        private int backgroundColor4PositionRow;
        private int backgroundColorsPositionSection2Row;

        private int sendMessagePositionSectionRow;
        private int sendMessageDurationPositionRow;
        private int sendMessageSettingsPositionRow;
        private int sendMessagePositionSection2Row;

        private int openChatPositionSectionRow;
        private int openChatDurationPositionRow;
        private int openChatSettingsPositionRow;
        private int openChatPositionSection2Row;

        private int jumpToMessagePositionSectionRow;
        private int jumpToMessageDurationPositionRow;
        private int jumpToMessageSettingsPositionRow;
        private int jumpToMessagePositionSection2Row;

        private int durationRow;
        private int durationSection2Row;

        private int xPositionSectionRow;
        private int xPositionRow;
        private int xPositionSection2Row;

        private int yPositionSectionRow;
        private int yPositionRow;
        private int yPositionSection2Row;

        private int emojiScalePositionSectionRow;
        private int emojiScalePositionRow;
        private int emojiScalePositionSection2Row;

        private int stickerScalePositionSectionRow;
        private int stickerScalePositionRow;
        private int stickerScalePositionSection2Row;

        private int bubbleShapePositionSectionRow;
        private int bubbleShapePositionRow;
        private int bubbleShapePositionSection2Row;

        private int textScalePositionSectionRow;
        private int textScalePositionRow;
        private int textScalePositionSection2Row;

        private int colorChangePositionSectionRow;
        private int colorChangePositionRow;
        private int colorChangePositionSection2Row;

        private int timeAppearsPositionSectionRow;
        private int timeAppearsPositionRow;
        private int timeAppearsPositionSection2Row;

        private int rowCount;

        public ListAdapter(Context context, int type) {
            mContext = context;
            currentType = type;

            rowCount = 0;

            if (currentType == 0) {
                backgroundPreviewPositionSectionRow = rowCount++;
                backgroundPreviewRow = rowCount++;
                openBackgroundPreviewPositionRow = rowCount++;
                backgroundPreviewPositionSection2Row = rowCount++;
                backgroundColorsPositionSectionRow = rowCount++;
                backgroundColor1PositionRow = rowCount++;
                backgroundColor2PositionRow = rowCount++;
                backgroundColor3PositionRow = rowCount++;
                backgroundColor4PositionRow = rowCount++;
                backgroundColorsPositionSection2Row = rowCount++;
                sendMessagePositionSectionRow = rowCount++;
                sendMessageDurationPositionRow = rowCount++;
                sendMessageSettingsPositionRow = rowCount++;
                sendMessagePositionSection2Row = rowCount++;
                openChatPositionSectionRow = rowCount++;
                openChatDurationPositionRow = rowCount++;
                openChatSettingsPositionRow = rowCount++;
                openChatPositionSection2Row = rowCount++;
                jumpToMessagePositionSectionRow = rowCount++;
                jumpToMessageDurationPositionRow = rowCount++;
                jumpToMessageSettingsPositionRow = rowCount++;
                jumpToMessagePositionSection2Row = rowCount++;
            }

            if (currentType == 1 || currentType == 2 || currentType == 3 || currentType == 4 || currentType == 5 || currentType == 6 || currentType == 7 || currentType == 8) {
                durationRow = rowCount++;
                durationSection2Row = rowCount++;
                xPositionSectionRow = rowCount++;
                xPositionRow = rowCount++;
                xPositionSection2Row = rowCount++;
                yPositionSectionRow = rowCount++;
                yPositionRow = rowCount++;
                yPositionSection2Row = rowCount++;
            }

            if (currentType == 1 || currentType == 2 || currentType == 3 || currentType == 4 || currentType == 5 || currentType == 6 ) {
                colorChangePositionSectionRow = rowCount++;
                colorChangePositionRow = rowCount++;
                colorChangePositionSection2Row = rowCount++;
            }

            if (currentType == 1 || currentType == 2 || currentType == 3 || currentType == 6 || currentType == 8) {
                bubbleShapePositionSectionRow = rowCount++;
                bubbleShapePositionRow = rowCount++;
                bubbleShapePositionSection2Row = rowCount++;
            }

            if (currentType == 1 || currentType == 2 || currentType == 3 || currentType == 6 || currentType == 8) {
                textScalePositionSectionRow = rowCount++;
                textScalePositionRow = rowCount++;
                textScalePositionSection2Row = rowCount++;
            }

            if (currentType == 4) {
                emojiScalePositionSectionRow = rowCount++;
                emojiScalePositionRow = rowCount++;
                emojiScalePositionSection2Row = rowCount++;
            }

            if (currentType == 5) {
                stickerScalePositionSectionRow = rowCount++;
                stickerScalePositionRow = rowCount++;
                stickerScalePositionSection2Row = rowCount++;
            }

            if (currentType == 1 || currentType == 3 || currentType == 2 || currentType == 4 || currentType == 5 || currentType == 6 || currentType == 8) {
                timeAppearsPositionSectionRow = rowCount++;
                timeAppearsPositionRow = rowCount++;
                timeAppearsPositionSection2Row = rowCount++;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return (currentType != 0 && position == durationRow) || position == openBackgroundPreviewPositionRow || position == sendMessageDurationPositionRow || position == openChatDurationPositionRow || position == jumpToMessageDurationPositionRow || position == backgroundColor1PositionRow || position == backgroundColor2PositionRow || position == backgroundColor3PositionRow || position == backgroundColor4PositionRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (currentType == 0 && position == backgroundPreviewPositionSectionRow) {
                return 1;
            }
            if (position == durationRow || position == sendMessageDurationPositionRow || position == openChatDurationPositionRow || position == jumpToMessageDurationPositionRow) {
                return 3;
            } else if (position == backgroundPreviewPositionSection2Row || position == backgroundColorsPositionSection2Row || position == sendMessagePositionSection2Row || position == openChatPositionSection2Row || position == jumpToMessagePositionSection2Row || position == durationSection2Row || position == xPositionSection2Row || position == yPositionSection2Row || position == bubbleShapePositionSection2Row || position == textScalePositionSection2Row || position == colorChangePositionSection2Row || position == stickerScalePositionSection2Row || position == timeAppearsPositionSection2Row || position == emojiScalePositionSection2Row) {
                return 0;
            } else if (position == backgroundColorsPositionSectionRow || position == sendMessagePositionSectionRow || position == openChatPositionSectionRow || position == jumpToMessagePositionSectionRow || position == xPositionSectionRow || position == yPositionSectionRow || position == bubbleShapePositionSectionRow || position == textScalePositionSectionRow || position == colorChangePositionSectionRow || position == emojiScalePositionSectionRow || position == stickerScalePositionSectionRow || position == timeAppearsPositionSectionRow) {
                return 1;
            } else if (position == sendMessageSettingsPositionRow || position == openChatSettingsPositionRow || position == jumpToMessageSettingsPositionRow || position == xPositionRow || position == yPositionRow || position == bubbleShapePositionRow || position == textScalePositionRow || position == colorChangePositionRow || position == emojiScalePositionRow || position == stickerScalePositionRow || position == timeAppearsPositionRow) {
                return 2;
            } else if (position == openBackgroundPreviewPositionRow) {
                return 4;
            } else if (position == backgroundPreviewRow) {
                return 5;
            } else {
                return 0;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new MessageAnimationSlider(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @SuppressLint("ApplySharedPref")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }

                case 1: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == backgroundPreviewPositionSectionRow) {
                        headerCell.setText("Background Preview");
                    } else if (position == backgroundColorsPositionSectionRow) {
                        headerCell.setText("Colors");
                    } else if (position == sendMessagePositionSectionRow) {
                        headerCell.setText("Send Message");
                    } else if (position == openChatPositionSectionRow) {
                        headerCell.setText("Open Chat");
                    } else if (position == jumpToMessagePositionSectionRow) {
                        headerCell.setText("Jump to Message");
                    } else if (position == xPositionSectionRow) {
                        headerCell.setText("X Position");
                    } else if (position == yPositionSectionRow) {
                        headerCell.setText("Y Position");
                    } else if (position == bubbleShapePositionSectionRow) {
                        headerCell.setText("Bubble shape");
                    } else if (position == textScalePositionSectionRow) {
                        headerCell.setText("Text scale");
                    } else if (position == colorChangePositionSectionRow) {
                        headerCell.setText("Color change");
                    } else if (position == timeAppearsPositionSectionRow) {
                        headerCell.setText("Time appears");
                    } else if (position == emojiScalePositionSectionRow) {
                        headerCell.setText("Emoji scale");
                    } else if (position == stickerScalePositionSectionRow) {
                        headerCell.setText("Sticker scale");
                    }
                    break;
                }
                case 2: {
                    String row = getRow(position);
                    String durationKey = "duration_type_" + currentType;
                    String startTimeKey = "start_time_type_" + currentType + "_row_" + row;
                    String endTimeKey = "end_time_type_" + currentType + "_row_" + row;
                    String startProgressKey = "start_progress_type_" + currentType + "_row_" + row;
                    String endProgressKey = "end_progress_type_" + currentType + "_row_" + row;

                    int duration = MessagesController.getGlobalMessageAnimationSettings().getInt(durationKey, 6000);
                    int startTime = MessagesController.getGlobalMessageAnimationSettings().getInt(startTimeKey, 0);
                    int endTime = MessagesController.getGlobalMessageAnimationSettings().getInt(endTimeKey, duration);
                    float startProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat(startProgressKey, 0);
                    float endProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat(endProgressKey, 0);

                    if (endTime > duration) {
                        endTime = duration;
                        MessagesController.getGlobalMessageAnimationSettings().edit().putInt(endTimeKey, endTime).commit();
                        startTime = 0;
                        MessagesController.getGlobalMessageAnimationSettings().edit().putInt(startTimeKey, startTime).commit();
                    }

                    MessageAnimationSlider messageAnimationSlider = (MessageAnimationSlider) holder.itemView;
                    messageAnimationSlider.setInitialValues(duration, startTime, endTime, startProgress, endProgress);

                    messageAnimationSlider.setDelegate(new MessageAnimationSlider.Delegate() {
                        @Override
                        public void onStartTimeChanged(int time) {
                            MessagesController.getGlobalMessageAnimationSettings().edit().putInt(startTimeKey, time).commit();
                        }

                        @Override
                        public void onEndTimeChanged(int time) {
                            MessagesController.getGlobalMessageAnimationSettings().edit().putInt(endTimeKey, time).commit();
                        }

                        @Override
                        public void onStartProgressChanged(float progress) {
                            MessagesController.getGlobalMessageAnimationSettings().edit().putFloat(startProgressKey, progress).commit();
                        }

                        @Override
                        public void onEndProgressChanged(float progress) {
                            MessagesController.getGlobalMessageAnimationSettings().edit().putFloat(endProgressKey, progress).commit();
                        }
                    });
                    break;
                }
                case 3: {
                    String durationKey = "duration_type_" + currentType;
                    int duration = MessagesController.getGlobalMessageAnimationSettings().getInt(durationKey, 200);
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    textSettingsCell.setTextAndValue("Duration", duration + "ms", false);
                    break;
                }
                case 4: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == openBackgroundPreviewPositionRow) {
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteBlueText);
                        textCell.setText("Open Full Screen", false);
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        private String getRow(int position) {
            String row;
            if (position == xPositionRow) {
                row = "xPosition";
            } else if (position == yPositionRow) {
                row = "yPosition";
            } else if (position == bubbleShapePositionRow) {
                row = "bubbleShape";
            } else if (position == textScalePositionRow) {
                row = "textScale";
            } else if (position == colorChangePositionRow) {
                row = "colorChange";
            } else if (position == timeAppearsPositionRow) {
                row = "timeAppears";
            } else if (position == emojiScalePositionRow) {
                row = "emojiScale";
            } else if (position == stickerScalePositionRow) {
                row = "stickerScale";
            } else {
                row = "default";
            }
            return row;
        }
    }

    static class MessageAnimationSlider extends View {

        interface Delegate {
            void onStartTimeChanged(int time);

            void onEndTimeChanged(int time);

            void onStartProgressChanged(float progress);

            void onEndProgressChanged(float progress);
        }

        private int duration;
        private int startTime;
        private int endTime;
        private float startProgress;
        private float endProgress;
        private String textStartTime;
        private String textEndTime;
        private String textEndProgress;
        private String textStartProgress;

        private float xStartTime;
        private float xEndTime;
        private float xStartProgress;
        private float xEndProgress;

        private float prevStartProgress;
        private float prevEndProgress;
        private float prevStartTime;
        private float prevEndTime;

        private float progressWidth;
        private float progressHeight;
        private float progressMargin;
        private float verticalSelectorWidth;
        private float accentDotPadding;
        private float progressRadius;
        private float timeRadius;
        private float verticalSelectorHeigth;
        private float clickPadding;

        private Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint selectedProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint defaultProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint colorProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint cubicProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private TextPaint timeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private TextPaint progressTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private RectF startTimeRect = new RectF();
        private RectF endTimeRect = new RectF();
        private Rect textBounds = new Rect();
        private Path cubicBezierPath = new Path();
        private Delegate delegate;

        private float sx, sy;
        private boolean capturedTopProgress;
        private boolean capturedBottomProgress;
        private boolean capturedLeftTime;
        private boolean capturedRightTime;
        private boolean dragging;

        public MessageAnimationSlider(Context context) {
            super(context);

            progressMargin = AndroidUtilities.dp(32f);
            verticalSelectorWidth = AndroidUtilities.dp(14f);
            verticalSelectorHeigth = AndroidUtilities.dp(32f);
            accentDotPadding = AndroidUtilities.dp(6f);
            progressHeight = AndroidUtilities.dp(150f);
            progressRadius = AndroidUtilities.dp(12f);
            clickPadding = AndroidUtilities.dp(24f);
            timeRadius = AndroidUtilities.dp(12f);


            circlePaint.setColor(0xffffffff);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setShadowLayer(AndroidUtilities.dp(6f), 0, 0, 0x33000000);
            setLayerType(LAYER_TYPE_SOFTWARE, circlePaint);

            selectedProgressPaint.setColor(0xff47A1E4);
            selectedProgressPaint.setStrokeWidth(AndroidUtilities.dp(3f));

            defaultProgressPaint.setColor(0xffEBEDF0);
            defaultProgressPaint.setStyle(Paint.Style.FILL);
            defaultProgressPaint.setStrokeWidth(AndroidUtilities.dp(3f));

            colorProgressPaint.setColor(0xffF8CD46);
            colorProgressPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            colorProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            colorProgressPaint.setStrokeWidth(AndroidUtilities.dp(2f));
            colorProgressPaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(1f), AndroidUtilities.dp(8)}, 0));

            timeTextPaint.setColor(0xffF8CD46);
            timeTextPaint.setTextSize(AndroidUtilities.dp(12));

            progressTextPaint.setColor(0xff47A1E4);
            progressTextPaint.setTextSize(AndroidUtilities.dp(12));
            progressTextPaint.getTextBounds("1", 0, 1, textBounds);

            cubicProgressPaint.setColor(0xffEBEDF0);
            cubicProgressPaint.setStyle(Paint.Style.STROKE);
            cubicProgressPaint.setStrokeWidth(AndroidUtilities.dp(3));
        }

        public void setInitialValues(int duration, int startTime, int endTime, float currentStartProgress, float currentEndProgress) {
            this.duration = duration;
            this.startTime = startTime;
            this.endTime = endTime;
            this.startProgress = currentStartProgress;
            this.prevStartTime = currentStartProgress;
            this.endProgress = currentEndProgress;
            this.prevEndProgress = currentEndProgress;
            invalidate();
        }

        public void setDelegate(Delegate delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return onTouch(event);
        }

        boolean onTouch(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                sx = ev.getX();
                sy = ev.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (dragging) {
                    if (capturedTopProgress) {
                        onTopProgressDrag((int) ev.getX(), true);
                    } else if (capturedBottomProgress) {
                        onBottomProgressDrag((int) ev.getX(), true);
                    } else if (capturedLeftTime) {
                        onLeftTimeDrag((int) ev.getX(), true);
                    } else if (capturedRightTime) {
                        onRightTimeDrag((int) ev.getX(), true);
                    }
                    capturedTopProgress = false;
                    capturedBottomProgress = false;
                    capturedLeftTime = false;
                    capturedRightTime = false;
                    dragging = false;
                    invalidate();
                    return true;
                } else if (ev.getAction() == MotionEvent.ACTION_UP && (Math.abs(ev.getX() - sx) < vc.getScaledTouchSlop())) {
                    if (ev.getY() > getTopOffset() - clickPadding && ev.getY() < getTopOffset() + clickPadding) {
                        onTopProgressDrag((int) ev.getX(), true);
                        invalidate();
                    } else if (ev.getY() > getTopOffset() + progressHeight - clickPadding && ev.getY() < getTopOffset() + progressHeight + clickPadding) {
                        onBottomProgressDrag((int) ev.getX(), true);
                        invalidate();
                    }
                    return true;
                }
            } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (!capturedTopProgress && !capturedBottomProgress && !capturedLeftTime && !capturedRightTime) {
                    if (Math.abs(ev.getY() - sy) > vc.getScaledTouchSlop()) {
                        return false;
                    }
                    if (Math.abs(ev.getX() - sx) > vc.getScaledTouchSlop()) {
                        if (isTopCoordinate((int) sx, (int) sy)) {
                            capturedTopProgress = true;
                            onTopProgressDrag((int) ev.getX(), false);
                        } else if (isBottomProgress((int) sx, (int) sy)) {
                            capturedBottomProgress = true;
                            onBottomProgressDrag((int) ev.getX(), false);
                        } else if (isLeftTime((int) sx, (int) sy)) {
                            capturedLeftTime = true;
                            onLeftTimeDrag((int) ev.getX(), false);
                        } else if (isRightTime((int) sx, (int) sy)) {
                            capturedRightTime = true;
                            onRightTimeDrag((int) ev.getX(), false);
                        } else {
                            return false;
                        }
                        dragging = true;
                        invalidate();
                    }
                    return true;
                } else {
                    if (dragging) {
                        if (capturedTopProgress) {
                            onTopProgressDrag((int) ev.getX(), false);
                        } else if (capturedBottomProgress) {
                            onBottomProgressDrag((int) ev.getX(), false);
                        } else if (capturedLeftTime) {
                            onLeftTimeDrag((int) ev.getX(), false);
                        } else if (capturedRightTime) {
                            onRightTimeDrag((int) ev.getX(), false);
                        }
                        invalidate();
                        return true;
                    }
                }
            }
            return false;
        }

        private void onTopProgressDrag(int x, boolean finish) {
            x -= progressMargin;
            if (x < xStartTime) {
                xEndProgress = xStartTime;
            } else if (x > xEndTime) {
                xEndProgress = xEndTime;
            } else {
                xEndProgress = x;
            }
            endProgress = 1 - ((xEndProgress - xStartTime) / (xEndTime - xStartTime));
            if (prevEndProgress != endProgress && finish && delegate != null) {
                prevEndProgress = endProgress;
                delegate.onEndProgressChanged(endProgress);
            }
        }

        private void onBottomProgressDrag(int x, boolean finish) {
            x -= progressMargin;
            if (x < xStartTime) {
                xStartProgress = xStartTime;
            } else if (x > xEndTime) {
                xStartProgress = xEndTime;
            } else {
                xStartProgress = x;
            }
            startProgress = (xStartProgress - xStartTime) / (xEndTime - xStartTime);
            if (prevStartProgress != startProgress && finish && delegate != null) {
                prevStartProgress = startProgress;
                delegate.onStartProgressChanged(prevStartProgress);
            }
        }

        private void onLeftTimeDrag(int x, boolean finish) {
            x -= progressMargin;
            if (x < 0) {
                xStartTime = 0;
            } else if (x > xEndTime - clickPadding * 2) {
                xStartTime = xEndTime - clickPadding * 2;
            } else {
                xStartTime = x;
            }
            if (xStartProgress < x) {
                xStartProgress = x;
            }
            if (xEndProgress < x) {
                xEndProgress = x;
            }
            startTime = (int) ((xStartTime * duration) / progressWidth);
            if (prevStartTime != startTime && finish && delegate != null) {
                prevStartTime = startTime;
                delegate.onStartTimeChanged(startTime);
            }
        }

        private void onRightTimeDrag(int x, boolean finish) {
            x -= progressMargin;
            if (x < xStartTime + clickPadding * 2) {
                xEndTime = xStartTime + clickPadding * 2;
            } else if (x > progressWidth) {
                xEndTime = progressWidth;
            } else {
                xEndTime = x;
            }
            if (xStartProgress > x) {
                xStartProgress = x;
            }
            if (xEndProgress > x) {
                xEndProgress = x;
            }
            endTime = (int) ((xEndTime * duration) / progressWidth);
            if (prevEndTime != endTime && finish && delegate != null) {
                prevEndTime = endTime;
                delegate.onEndTimeChanged(endTime);
            }
        }

        private boolean isTopCoordinate(int x, int y) {
            return x > progressMargin + xEndProgress - clickPadding && x < progressMargin + xEndProgress + clickPadding &&
                    y > getTopOffset() - clickPadding && y < getTopOffset() + clickPadding;
        }

        private boolean isBottomProgress(int x, int y) {
            return x > progressMargin + xStartProgress - clickPadding && x < progressMargin + xStartProgress + clickPadding &&
                    y > getTopOffset() + progressHeight - clickPadding && y < getTopOffset() + progressHeight + clickPadding;
        }

        private boolean isLeftTime(int x, int y) {
            return x > progressMargin + xStartTime - verticalSelectorWidth / 2 - clickPadding &&
                    x < progressMargin + xStartTime + verticalSelectorWidth / 2 + clickPadding &&
                    y > (getTopOffset() + progressHeight / 2f) - (verticalSelectorHeigth / 2) - clickPadding &&
                    y < (getTopOffset() + progressHeight / 2f) + (verticalSelectorHeigth / 2) + clickPadding;
        }

        private boolean isRightTime(int x, int y) {
            return x > progressMargin + xEndTime - verticalSelectorWidth / 2 - clickPadding &&
                    x < progressMargin + xEndTime + verticalSelectorWidth / 2 + clickPadding &&
                    y > (getTopOffset() + progressHeight / 2f) - (verticalSelectorHeigth / 2) - clickPadding &&
                    y < (getTopOffset() + progressHeight / 2f) + (verticalSelectorHeigth / 2) + clickPadding;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            progressWidth = MeasureSpec.getSize(widthMeasureSpec) - progressMargin * 2;
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec((int) (progressHeight + textBounds.height() * 2 + AndroidUtilities.dp(16) + (progressMargin * 2) + getPaddingTop() + getPaddingBottom()), View.MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updatePositions();
            updateTexts();

            cubicBezierPath.reset();
            cubicBezierPath.moveTo(progressMargin + xStartTime + accentDotPadding, getTopOffset() + progressHeight);
            cubicBezierPath.cubicTo(progressMargin + xStartProgress, getTopOffset() + progressHeight, progressMargin + xEndProgress, getTopOffset(), progressMargin + xEndTime - accentDotPadding, getTopOffset());
            canvas.drawPath(cubicBezierPath, cubicProgressPaint);
            canvas.drawLine(progressMargin + xStartTime, getTopOffset(), progressMargin + xStartTime, getTopOffset() + progressHeight, colorProgressPaint);
            canvas.drawLine(progressMargin + xEndTime, getTopOffset(), progressMargin + xEndTime, getTopOffset() + progressHeight, colorProgressPaint);
            canvas.drawCircle(progressMargin + xStartTime, getTopOffset(), AndroidUtilities.dp(3), colorProgressPaint);
            canvas.drawCircle(progressMargin + xStartTime, getTopOffset() + progressHeight, AndroidUtilities.dp(3), colorProgressPaint);
            canvas.drawCircle(progressMargin + xEndTime, getTopOffset(), AndroidUtilities.dp(3), colorProgressPaint);
            canvas.drawCircle(progressMargin + xEndTime, getTopOffset() + progressHeight, AndroidUtilities.dp(3), colorProgressPaint);
            // draw top progress
            if (startTime != 0) {
                canvas.drawLine(progressMargin, getTopOffset(), progressMargin + xStartTime - accentDotPadding, getTopOffset(), defaultProgressPaint);
            }
            canvas.drawLine(progressMargin + xStartTime + accentDotPadding, getTopOffset(), progressMargin + xEndProgress, getTopOffset(), defaultProgressPaint);
            canvas.drawLine(progressMargin + xEndProgress, getTopOffset(), progressMargin + xEndTime - accentDotPadding, getTopOffset(), selectedProgressPaint);
            if (endTime != duration) {
                canvas.drawLine(progressMargin + xEndTime + accentDotPadding, getTopOffset(), progressMargin + progressWidth, getTopOffset(), defaultProgressPaint);
            }
            canvas.drawCircle(progressMargin + xEndProgress, getTopOffset(), progressRadius, circlePaint);
            // draw bottom progress
            if (startTime != 0) {
                canvas.drawLine(progressMargin, getTopOffset() + progressHeight, progressMargin + xStartTime - accentDotPadding, getTopOffset() + progressHeight, defaultProgressPaint);
            }
            canvas.drawLine(progressMargin + xStartTime + accentDotPadding, getTopOffset() + progressHeight, progressMargin + xStartProgress, getTopOffset() + progressHeight, selectedProgressPaint);
            canvas.drawLine(progressMargin + xStartProgress, getTopOffset() + progressHeight, progressMargin + xEndTime - accentDotPadding, getTopOffset() + progressHeight, defaultProgressPaint);
            if (endTime != duration) {
                canvas.drawLine(progressMargin + xEndTime + accentDotPadding, getTopOffset() + progressHeight, progressMargin + progressWidth, getTopOffset() + progressHeight, defaultProgressPaint);
            }
            canvas.drawCircle(progressMargin + xStartProgress, getTopOffset() + progressHeight, progressRadius, circlePaint);
            startTimeRect.set(progressMargin + xStartTime - verticalSelectorWidth / 2, (getTopOffset() + progressHeight / 2f) - (verticalSelectorHeigth / 2), progressMargin + xStartTime + verticalSelectorWidth / 2, (getTopOffset() + progressHeight / 2f) + (verticalSelectorHeigth / 2));
            canvas.drawRoundRect(startTimeRect, timeRadius, timeRadius, circlePaint);
            endTimeRect.set(progressMargin + xEndTime - verticalSelectorWidth / 2, (getTopOffset() + progressHeight / 2f) - (verticalSelectorHeigth / 2), progressMargin + xEndTime + verticalSelectorWidth / 2, (getTopOffset() + progressHeight / 2f) + (verticalSelectorHeigth / 2));
            canvas.drawRoundRect(endTimeRect, timeRadius, timeRadius, circlePaint);
            timeTextPaint.getTextBounds(textStartTime, 0, textStartTime.length(), textBounds);
            if (startTime < duration / 2f) {
                canvas.drawText(textStartTime, progressMargin + xStartTime + verticalSelectorWidth / 2 + AndroidUtilities.dp(8), (getTopOffset() + progressHeight / 2f) + textBounds.height() / 2f - textBounds.bottom, timeTextPaint);
            } else {
                canvas.drawText(textStartTime, progressMargin + xStartTime - verticalSelectorWidth / 2 - textBounds.width() - AndroidUtilities.dp(8), (getTopOffset() + progressHeight / 2f) + textBounds.height() / 2f - textBounds.bottom, timeTextPaint);
            }
            timeTextPaint.getTextBounds(textEndTime, 0, textEndTime.length(), textBounds);
            if (endTime < duration / 2f) {
                canvas.drawText(textEndTime, progressMargin + xEndTime + verticalSelectorWidth / 2 + AndroidUtilities.dp(8), (getTopOffset() + progressHeight / 2f) + textBounds.height() / 2f - textBounds.bottom, timeTextPaint);
            } else {
                canvas.drawText(textEndTime, progressMargin + xEndTime - verticalSelectorWidth / 2 - textBounds.width() - AndroidUtilities.dp(8), (getTopOffset() + progressHeight / 2f) + textBounds.height() / 2f - textBounds.bottom, timeTextPaint);
            }

            progressTextPaint.getTextBounds(textStartProgress, 0, textStartProgress.length(), textBounds);
            canvas.drawText(textStartProgress, progressMargin + xStartProgress - textBounds.width() / 2f, getTopOffset() + progressHeight + progressRadius + textBounds.height() + AndroidUtilities.dp(8), progressTextPaint);
            progressTextPaint.getTextBounds(textEndProgress, 0, textEndProgress.length(), textBounds);
            canvas.drawText(textEndProgress, progressMargin + xEndProgress - textBounds.width() / 2f, getTopOffset() - progressRadius - AndroidUtilities.dp(8), progressTextPaint);
        }

        public float getTopOffset() {
            return progressMargin + textBounds.height() + getPaddingTop();
        }

        private void updatePositions() {
            xStartTime = (progressWidth * startTime) / duration;
            xEndTime = (progressWidth * endTime) / duration;
            xStartProgress = xStartTime + (xEndTime - xStartTime) * startProgress;
            xEndProgress = xStartTime + (xEndTime - xStartTime) * (1 - endProgress);
        }

        private void updateTexts() {
            textStartProgress = Math.round(startProgress * 100) + "%";
            textEndProgress = Math.round(endProgress * 100) + "%";
            textEndTime = endTime + "ms";
            textStartTime = startTime + "ms";
        }
    }


    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabActiveText));
        themeDescriptions.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabUnactiveText));
        themeDescriptions.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabLine));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, new Drawable[]{scrollSlidingTextTabStrip.getSelectorDrawable()}, null, Theme.key_actionBarTabSelector));

        for (int a = 0; a < viewPages.length; a++) {
            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
            themeDescriptions.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        }

        return themeDescriptions;
    }


}
