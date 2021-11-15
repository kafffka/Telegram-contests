package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class ChatSendersCell extends FrameLayout {

    private TextView titleView;
    private RecyclerListView recyclerListView;
    private int availableHeight;
    private ImageView shadowView;

    public interface ChatSendersCellDelegate {
        void didSelectPeer(TLRPC.Peer peer);
    }

    @SuppressLint("NotifyDataSetChanged")
    public ChatSendersCell(@NonNull Context context, int currentAccount, TLRPC.ChatFull chatFull, List<TLRPC.Peer> peers, int availableHeight, ChatSendersCellDelegate chatSendersCellDelegate) {
        super(context);
        this.availableHeight = availableHeight;

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setLines(1);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        titleView.setText(LocaleController.getString("SendMessageAs", R.string.SendMessageAs));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 12, 16, 16));

        shadowView = new ImageView(context);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.header_shadow);
        shadowView.setAlpha(0f);
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 6, Gravity.TOP, 0, 40, 0, 0));

        int selectedPosition = 0;
        for (int i = 0; i < peers.size(); i++) {
            TLRPC.Peer peer = peers.get(i);
            if (peer instanceof TLRPC.TL_peerChat) {
                if (peer.chat_id == chatFull.default_send_as.chat_id) {
                    selectedPosition = i;
                    break;
                }
            } else if (peer instanceof TLRPC.TL_peerChannel) {
                if (peer.channel_id == chatFull.default_send_as.channel_id) {
                    selectedPosition = i;
                    break;
                }
            } else if (peer instanceof TLRPC.TL_peerUser) {
                if (peer.user_id == chatFull.default_send_as.user_id) {
                    selectedPosition = i;
                    break;
                }
            }
        }

        recyclerListView = new RecyclerListView(getContext());
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof GroupCreateUserCell) {
                chatSendersCellDelegate.didSelectPeer(peers.get(position));
            }
        });
        int finalSelectedPosition = selectedPosition;
        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                GroupCreateUserCell peerCell = new GroupCreateUserCell(parent.getContext(), 2, 0, false, false, true);
                peerCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(peerCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                TLRPC.Peer peer = peers.get(position);
                if (peer instanceof TLRPC.TL_peerChat) {
                    TLRPC.TL_peerChat peerChat = (TLRPC.TL_peerChat) peer;
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(peerChat.chat_id);
                    cell.setObject(chat, null, null);
                } else if (peer instanceof TLRPC.TL_peerChannel) {
                    TLRPC.TL_peerChannel peerChannel = (TLRPC.TL_peerChannel) peer;
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(peerChannel.channel_id);
                    cell.setObject(chat, null, null);
                } else if (peer instanceof TLRPC.TL_peerUser) {
                    TLRPC.TL_peerUser peerUser = (TLRPC.TL_peerUser) peer;
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerUser.user_id);
                    cell.setObject(user, null, LocaleController.getString("SelectSenderPersonalAccount", R.string.SelectSenderPersonalAccount));
                }
                cell.setChecked(position == finalSelectedPosition, false);

            }

            @Override
            public int getItemCount() {
                return peers.size();
            }

        });
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int offset = recyclerView.computeVerticalScrollOffset();
                float alpha;
                if (offset == 0) {
                    alpha = 0;
                } else if (offset > 100) {
                    alpha = 1;
                } else {
                    alpha = offset / 100f;
                }
                shadowView.setAlpha(alpha);
            }
        });
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 40, 0, 0));
        Drawable shadowDrawable = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        setBackground(shadowDrawable);
        recyclerListView.scrollToPosition(finalSelectedPosition);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(availableHeight, AndroidUtilities.dp(416)), MeasureSpec.AT_MOST));
    }

}
