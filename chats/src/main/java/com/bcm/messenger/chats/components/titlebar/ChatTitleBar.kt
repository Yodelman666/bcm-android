package com.bcm.messenger.chats.components.titlebar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMELogin
import kotlinx.android.synthetic.main.chats_titlebar_layout.view.*
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.*
import com.bcm.route.api.BcmRouter

/**
 * Created by bcm.social.01 on 2019/1/27.
 */
class ChatTitleBar : androidx.constraintlayout.widget.ConstraintLayout {

    interface OnChatTitleCallback {
        fun onLeft(multiSelect: Boolean)
        fun onRight(multiSelect: Boolean)
        fun onTitle(multiSelect: Boolean)
    }

    private var mShowDot: Boolean = false
    private var mMultiSelect: Boolean = false
    private var mInPrivateChat: Boolean = false
    private var mCallback: OnChatTitleCallback? = null
    private var address:Address? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        View.inflate(context, R.layout.chats_titlebar_layout, this)
        chat_status_fill.layoutParams = chat_status_fill.layoutParams.apply {
            height = context.getStatusBarHeight()
        }

        bar_back_text.setOnClickListener {
            mCallback?.onLeft(mMultiSelect)
        }
        bar_back_img.setOnClickListener {
            mCallback?.onLeft(mMultiSelect)
        }
        bar_recipient_photo.setOnClickListener {
            mCallback?.onRight(mMultiSelect)
        }
        bar_title_main.setOnClickListener {
            mCallback?.onTitle(mMultiSelect)
        }
        bar_title_whole.setOnClickListener {
            mCallback?.onTitle(mMultiSelect)
        }

        bar_add_friend.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.REQUEST_FRIEND).putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS,
                    address?:return@setOnClickListener).startBcmActivity(AMELogin.majorContext, context)
        }
    }

    fun setOnChatTitleCallback(callback: OnChatTitleCallback?) {
        mCallback = callback
    }

    fun setPrivateChat(recipient: Recipient?) {
        mInPrivateChat = true
        if (recipient == null) {
            bar_title_layout.visibility = View.GONE
            bar_title_whole.visibility = View.GONE
            return
        } else {
            address = recipient.address
            if (recipient.isFriend) {
                bar_title_layout.visibility = View.GONE
                bar_title_whole.visibility = View.VISIBLE
                bar_title_whole.text = recipient.name
                bar_add_friend.visibility = View.GONE
            } else {
                bar_title_layout.visibility = View.VISIBLE
                bar_title_whole.visibility = View.GONE
                bar_title_main.text = recipient.name
                bar_add_friend.visibility = View.VISIBLE
                bar_title_sub.text = context.getString(R.string.chats_recipient_stranger_role)
            }
            bar_recipient_photo.showPrivateAvatar(recipient)
        }
        setMultiSelectionMode(mMultiSelect)
    }

    fun setGroupChat(accountContext: AccountContext, gid: Long, name: String, memberCount: Int) {
        mInPrivateChat = false

        address = GroupUtil.addressFromGid(accountContext, gid)

        bar_title_layout.visibility = View.GONE
        bar_title_whole.visibility = View.VISIBLE
        bar_title_whole.text = "$name($memberCount)"
        bar_recipient_photo.showGroupAvatar(accountContext, gid)
        setMultiSelectionMode(mMultiSelect)
    }

    fun updateGroupName(name: String, memberCount: Int) {
        bar_title_whole.text = "$name($memberCount)"
    }

    fun updateGroupAvatar(accountContext: AccountContext, gid: Long, path: String) {
        bar_recipient_photo.showGroupAvatar(accountContext, gid, path = path)
    }

    fun setMultiSelectionMode(multiSelect: Boolean) {
        mMultiSelect = multiSelect
        showRight(!multiSelect)
        if (multiSelect) {
            bar_back_text.text = context.getString(R.string.chats_select_mode_cancel_btn)
            bar_back_text.visibility = View.VISIBLE
            bar_back_img.visibility = View.GONE
        } else {
            bar_back_text.visibility = View.INVISIBLE
            bar_back_img.visibility = View.VISIBLE
        }
    }

    fun showRight(show: Boolean) {
        bar_recipient_photo.visibility = if (show && !mMultiSelect) View.VISIBLE else View.GONE
        if (show && !mMultiSelect && mShowDot) {
            bar_right_layout.showDot()
        } else {
            bar_right_layout.hideBadge()
        }
    }

    fun showDot(show: Boolean) {
        mShowDot = show
        if (show && bar_recipient_photo.visibility == View.VISIBLE) {
            bar_right_layout.showDot()
        } else {
            bar_right_layout.hideBadge()
        }
    }
}