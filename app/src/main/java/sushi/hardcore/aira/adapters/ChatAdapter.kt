package sushi.hardcore.aira.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.aira.AIRADatabase
import sushi.hardcore.aira.ChatItem
import sushi.hardcore.aira.R
import sushi.hardcore.aira.background_service.Protocol
import sushi.hardcore.aira.utils.StringUtils
import sushi.hardcore.aira.utils.TimeUtils
import java.text.DateFormat
import java.util.*

class ChatAdapter(
    private val context: Context,
    private val onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val BUBBLE_MARGIN = 150
        const val CONTAINER_PADDING = 40
        const val BUBBLE_VERTICAL_MARGIN = 30
        const val BUBBLE_CORNER_NORMAL = 50f
        const val BUBBLE_CORNER_ARROW = 20f
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val chatItems = mutableListOf<ChatItem>()

    fun newMessage(chatItem: ChatItem) {
        chatItems.add(chatItem)
        notifyItemChanged(chatItems.size-2)
        notifyItemInserted(chatItems.size-1)
    }

    fun newLoadedMessage(chatItem: ChatItem) {
        chatItems.add(0, chatItem)
        notifyItemInserted(0)
        notifyItemChanged(1)
    }

    fun clear() {
        chatItems.clear()
        notifyDataSetChanged()
    }

    internal open class BubbleViewHolder(private val context: Context, itemView: View): RecyclerView.ViewHolder(itemView) {
        private fun generateCorners(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): FloatArray {
            return floatArrayOf(topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft)
        }
        protected fun setBubbleContent(layoutResource: Int) {
            //if the view was recycled bubble_content will be null and we don't need to inflate a layout
            itemView.findViewById<View>(R.id.bubble_content)?.let { placeHolder ->
                val parent = placeHolder.parent as ViewGroup
                val index = parent.indexOfChild(placeHolder)
                parent.removeView(placeHolder)
                val bubbleContent = LayoutInflater.from(context).inflate(layoutResource, parent, false)
                parent.addView(bubbleContent, index)
            }
        }
        protected fun configureContainer(outgoing: Boolean, previousOutgoing: Boolean?, isLast: Boolean) {
            val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (previousOutgoing != null && previousOutgoing != outgoing) {
                layoutParams.updateMargins(top = BUBBLE_VERTICAL_MARGIN)
            }
            if (isLast) {
                layoutParams.updateMargins(bottom = BUBBLE_VERTICAL_MARGIN)
            }
            itemView.layoutParams = layoutParams //set layoutParams anyway to reset margins if the view was recycled
            if (outgoing) {
                itemView.updatePadding(right = CONTAINER_PADDING)
            } else {
                itemView.updatePadding(left = CONTAINER_PADDING)
            }
        }
        protected fun configureBubble(chatItem: ChatItem, previousChatItem: ChatItem?, nextChatItem: ChatItem?) {
            val bubble = itemView.findViewById<LinearLayout>(R.id.bubble)
            bubble.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (chatItem.outgoing) {
                    marginStart = BUBBLE_MARGIN
                    Gravity.END
                } else {
                    marginEnd = BUBBLE_MARGIN
                    Gravity.START
                }
            }
            val backgroundDrawable = GradientDrawable()
            backgroundDrawable.setColor(ContextCompat.getColor(context, if (chatItem.outgoing) {
                R.color.bubbleBackground
            } else {
                R.color.incomingBubbleBackground
            }))
            var topLeft = BUBBLE_CORNER_NORMAL
            var topRight = BUBBLE_CORNER_NORMAL
            var bottomRight = BUBBLE_CORNER_NORMAL
            var bottomLeft = BUBBLE_CORNER_NORMAL
            if (previousChatItem?.outgoing == chatItem.outgoing && TimeUtils.isInTheSameDay(chatItem, previousChatItem)) {
                if (chatItem.outgoing) {
                    topRight = BUBBLE_CORNER_ARROW
                } else {
                    topLeft = BUBBLE_CORNER_ARROW
                }
            }
            if (nextChatItem?.outgoing == chatItem.outgoing && TimeUtils.isInTheSameDay(chatItem, nextChatItem)) {
                if (chatItem.outgoing) {
                    bottomRight = BUBBLE_CORNER_ARROW
                } else {
                    bottomLeft = BUBBLE_CORNER_ARROW
                }
            }
            backgroundDrawable.cornerRadii = generateCorners(topLeft, topRight, bottomRight, bottomLeft)
            bubble.background = backgroundDrawable
        }
        protected fun showDateAndTime(chatItem: ChatItem, previousChatItem: ChatItem?) {
            var showTextPendingMsg = false
            val textPendingMsg = itemView.findViewById<TextView>(R.id.text_pending_msg)
            val textDate = itemView.findViewById<TextView>(R.id.text_date)
            val textHour = itemView.findViewById<TextView>(R.id.text_hour)
            val showDate = if (chatItem.timestamp == 0L) {
                if (previousChatItem == null || previousChatItem.timestamp != 0L) {
                    showTextPendingMsg = true
                }
                textHour.visibility = View.GONE
                false
            } else {
                textHour.apply {
                    visibility = View.VISIBLE
                    @SuppressLint("SetTextI18n")
                    text = StringUtils.toTwoDigits(chatItem.calendar.get(Calendar.HOUR_OF_DAY))+":"+StringUtils.toTwoDigits(chatItem.calendar.get(Calendar.MINUTE))
                    setTextColor(ContextCompat.getColor(context, if (chatItem.outgoing) {
                        R.color.outgoingTimestamp
                    } else {
                        R.color.incomingTimestamp
                    }))
                }
                if (previousChatItem == null) {
                    true
                } else {
                    !TimeUtils.isInTheSameDay(chatItem, previousChatItem)
                }
            }
            textDate.visibility = if (showDate) {
                textDate.text = DateFormat.getDateInstance().format(chatItem.calendar.time)
                View.VISIBLE
            } else {
                View.GONE
            }
            textPendingMsg.visibility = if (showTextPendingMsg) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    internal open class MessageViewHolder(context: Context, itemView: View): BubbleViewHolder(context, itemView) {
        protected fun bindMessage(chatItem: ChatItem, outgoing: Boolean): TextView {
            setBubbleContent(R.layout.message_bubble_content)
            itemView.findViewById<TextView>(R.id.text_message).apply {
                text = chatItem.data.sliceArray(1 until chatItem.data.size).decodeToString()
                if (!outgoing) {
                    highlightColor = ContextCompat.getColor(context, R.color.incomingHighlight)
                }
                return this
            }
        }
    }

    internal class OutgoingMessageViewHolder(context: Context, itemView: View): MessageViewHolder(context, itemView) {
        fun bind(chatItem: ChatItem, previousChatItem: ChatItem?, nextChatItem: ChatItem?) {
            bindMessage(chatItem, true).apply {
                setLinkTextColor(ContextCompat.getColor(context, R.color.outgoingTextLink))
            }
            showDateAndTime(chatItem, previousChatItem)
            configureBubble(chatItem, previousChatItem, nextChatItem)
            configureContainer(true, previousChatItem?.outgoing, nextChatItem == null)
        }
    }

    internal class IncomingMessageViewHolder(context: Context, itemView: View): MessageViewHolder(context, itemView) {
        fun bind(chatItem: ChatItem, previousChatItem: ChatItem?, nextChatItem: ChatItem?) {
            bindMessage(chatItem, false).apply {
                setLinkTextColor(ContextCompat.getColor(context, R.color.incomingTextLink))
            }
            showDateAndTime(chatItem, previousChatItem)
            configureBubble(chatItem, previousChatItem, nextChatItem)
            configureContainer(false, previousChatItem?.outgoing, nextChatItem == null)
        }
    }

    internal open class FileViewHolder(context: Context, itemView: View, private val onSavingFile: (fileName: String, fileContent: ByteArray) -> Unit): BubbleViewHolder(context, itemView) {
        protected fun bindFile(chatItem: ChatItem, outgoing: Boolean) {
            setBubbleContent(R.layout.file_bubble_content)
            val buttonSave = itemView.findViewById<ImageButton>(R.id.button_save)
            val fileName: String
            if (chatItem.timestamp == 0L) { //pending
                val file = Protocol.parseSmallFile(chatItem.data)!!
                fileName = file.rawFileName.decodeToString()
                buttonSave.setOnClickListener {
                    onSavingFile(fileName, file.fileContent)
                }
            } else {
                fileName = chatItem.data.sliceArray(17 until chatItem.data.size).decodeToString()
                buttonSave.setOnClickListener {
                    AIRADatabase.loadFile(chatItem.data.sliceArray(1 until 17))?.let {
                        onSavingFile(fileName, it)
                    }
                }
            }
            itemView.findViewById<TextView>(R.id.text_filename).apply {
                text = fileName
                if (!outgoing) {
                    highlightColor = ContextCompat.getColor(context, R.color.incomingHighlight)
                }
            }
        }
    }

    internal class OutgoingFileViewHolder(context: Context, itemView: View, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): FileViewHolder(context, itemView, onSavingFile) {
        fun bind(chatItem: ChatItem, previousChatItem: ChatItem?, nextChatItem: ChatItem?) {
            bindFile(chatItem, true)
            showDateAndTime(chatItem, previousChatItem)
            configureBubble(chatItem, previousChatItem, nextChatItem)
            configureContainer(true, previousChatItem?.outgoing, nextChatItem == null)
        }
    }

    internal class IncomingFileViewHolder(context: Context, itemView: View, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): FileViewHolder(context, itemView, onSavingFile) {
        fun bind(chatItem: ChatItem, previousChatItem: ChatItem?, nextChatItem: ChatItem?) {
            bindFile(chatItem, false)
            showDateAndTime(chatItem, previousChatItem)
            configureBubble(chatItem, previousChatItem, nextChatItem)
            configureContainer(false, previousChatItem?.outgoing, nextChatItem == null)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = inflater.inflate(R.layout.adapter_chat_item, parent, false)
        return if (viewType == ChatItem.OUTGOING_MESSAGE || viewType == ChatItem.INCOMING_MESSAGE) {
            if (viewType == ChatItem.OUTGOING_MESSAGE) {
                OutgoingMessageViewHolder(context, view)
            } else {
                IncomingMessageViewHolder(context, view)
            }
        } else {
            if (viewType == ChatItem.OUTGOING_FILE) {
                OutgoingFileViewHolder(context, view, onSavingFile)
            } else {
                IncomingFileViewHolder(context, view, onSavingFile)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = chatItems[position]
        val previousChatItem = if (position == 0) {
            null
        } else {
            chatItems[position-1]
        }
        val nextChatItem = if (position == chatItems.size - 1) {
            null
        } else {
            chatItems[position+1]
        }
        when (chatItem.itemType) {
            ChatItem.OUTGOING_MESSAGE -> (holder as OutgoingMessageViewHolder).bind(chatItem, previousChatItem, nextChatItem)
            ChatItem.INCOMING_MESSAGE -> (holder as IncomingMessageViewHolder).bind(chatItem, previousChatItem, nextChatItem)
            ChatItem.OUTGOING_FILE -> (holder as OutgoingFileViewHolder).bind(chatItem, previousChatItem, nextChatItem)
            ChatItem.INCOMING_FILE -> (holder as IncomingFileViewHolder).bind(chatItem, previousChatItem, nextChatItem)
        }
    }

    override fun getItemCount(): Int {
        return chatItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return chatItems[position].itemType
    }
}