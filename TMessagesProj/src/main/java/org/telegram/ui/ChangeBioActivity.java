/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CodepointsLengthInputFilter;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;

import java.util.ArrayList;

public class ChangeBioActivity extends BaseFragment {

    private EditTextBoldCursor bioField;
    private View doneButton;
    private NumberTextView checkTextView;
    private TextView helpTextView;

    private final static int done_button = 1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("UserBio", R.string.UserBio));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    saveBio();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        doneButton.setContentDescription(LocaleController.getString("Done", R.string.Done));

        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.setOnTouchListener((v, event) -> true);

        FrameLayout fieldContainer = new FrameLayout(context);
        linearLayout.addView(fieldContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 24, 20, 0));

        bioField = new EditTextBoldCursor(context) {
            @Override
            public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                InputConnection conn = super.onCreateInputConnection(outAttrs);
                outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                return conn;
            }
        };
        bioField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        bioField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        bioField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bioField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        bioField.setMaxLines(4);
        bioField.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 24 : 0), 0, AndroidUtilities.dp(LocaleController.isRTL ? 0 : 24), AndroidUtilities.dp(6));
        bioField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        bioField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new CodepointsLengthInputFilter(70) {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (source != null && TextUtils.indexOf(source, '\n') != -1) {
                    doneButton.performClick();
                    return "";
                }
                CharSequence result = super.filter(source, start, end, dest, dstart, dend);
                if (result != null && source != null && result.length() != source.length()) {
                    Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        v.vibrate(200);
                    }
                    AndroidUtilities.shakeView(checkTextView, 2, 0);
                }
                return result;
            }
        };
        bioField.setFilters(inputFilters);
        bioField.setMinHeight(AndroidUtilities.dp(36));
        bioField.setHint(LocaleController.getString("UserBio", R.string.UserBio));
        bioField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bioField.setCursorSize(AndroidUtilities.dp(20));
        bioField.setCursorWidth(1.5f);
        bioField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                doneButton.performClick();
                return true;
            }
            return false;
        });
        bioField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                checkTextView.setNumber(70 -  Character.codePointCount(s, 0, s.length()), true);
            }
        });

        fieldContainer.addView(bioField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 4, 0));

        checkTextView = new NumberTextView(context);
        checkTextView.setCenterAlign(true);
        checkTextView.setTextSize(15);
        checkTextView.setNumber(70, false);
        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        checkTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        fieldContainer.addView(checkTextView, LayoutHelper.createFrame(20, 20, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT, 0, 4, 4, 0));

        helpTextView = new TextView(context);
        helpTextView.setFocusable(true);
        helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        helpTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
        helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        helpTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("UserBioInfo", R.string.UserBioInfo)));
        linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
        if (userFull != null && userFull.about != null) {
            bioField.setText(userFull.about);
            bioField.setSelection(bioField.length());
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            bioField.requestFocus();
            AndroidUtilities.showKeyboard(bioField);
        }
    }

    private void saveBio() {
        final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
        if (getParentActivity() == null || userFull == null) {
            return;
        }
        String currentBio = userFull.about;
        if (currentBio == null) {
            currentBio = "";
        }
        final String newBio = bioField.getText().toString().replace("\n", "");
        if (currentBio.equals(newBio)) {
            finishFragment();
            return;
        }

        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);

        final TLRPC.TL_account_updateProfile req = new TLRPC.TL_account_updateProfile();
        req.about = newBio;
        req.flags |= 4;

        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                final TLRPC.User user = (TLRPC.User)response;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    userFull.about = newBio;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, user.id, userFull);
                    finishFragment();
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AlertsCreator.processError(currentAccount, error, ChangeBioActivity.this, req);
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);

        progressDialog.setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true));
        progressDialog.show();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            bioField.requestFocus();
            AndroidUtilities.showKeyboard(bioField);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(bioField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(bioField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(bioField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(bioField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(helpTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));

        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }
}
