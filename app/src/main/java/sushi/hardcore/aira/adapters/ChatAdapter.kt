package sushi.hardcore.aira.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
import sushi.hardcore.aira.ChatItem
import sushi.hardcore.aira.R
import sushi.hardcore.aira.utils.StringUtils
import java.util.*

class ChatAdapter(
    private val context: Context,
    private val onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val BUBBLE_MARGIN = 150
        const val CONTAINER_PADDING = 40
        const val BUBBLE_VERTICAL_MARGIN = 40
        const val BUBBLE_CORNER_NORMAL = 50f
        const val BUBBLE_CORNER_ARROW = 20f
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val chatItems = mutableListOf<ChatItem>()

    fun newMessage(chatItem: ChatItem) {
        chatItems.add(chatItem)
        Handler(Looper.getMainLooper()).postDelayed({
            notifyItemChanged(chatItems.size-2)
        }, 100)
        notifyItemInserted(chatItems.size-1)
    }

    fun newLoadedMessage(chatItem: ChatItem) {
        chatItems.add(0, chatItem)
        notifyItemInserted(0)
    }

    fun clear() {
        chatItems.clear()
        notifyDataSetChanged()
    }

    internal open class BubbleViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private fun generateCorners(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): FloatArray {
            return floatArrayOf(topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft)
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
        protected fun configureBubble(context: Context, outgoing: Boolean, previousOutgoing: Boolean?, nextOutgoing: Boolean?) {
            val bubble = itemView.findViewById<LinearLayout>(R.id.bubble_content)
            bubble.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (outgoing) {
                    marginStart = BUBBLE_MARGIN
                    Gravity.END
                } else {
                    marginEnd = BUBBLE_MARGIN
                    Gravity.START
                }
            }
            val backgroundDrawable = GradientDrawable()
            backgroundDrawable.setColor(ContextCompat.getColor(context, if (outgoing) {
                R.color.bubbleBackground
            } else {
                R.color.incomingBubbleBackground
            }))
            var topLeft = BUBBLE_CORNER_NORMAL
            var topRight = BUBBLE_CORNER_NORMAL
            var bottomRight = BUBBLE_CORNER_NORMAL
            var bottomLeft = BUBBLE_CORNER_NORMAL
            if (nextOutgoing == outgoing) {
                if (outgoing) {
                    bottomRight = BUBBLE_CORNER_ARROW
                } else {
                    bottomLeft = BUBBLE_CORNER_ARROW
                }
            }
            if (previousOutgoing == outgoing) {
                if (outgoing) {
                    topRight = BUBBLE_CORNER_ARROW
                } else {
                    topLeft = BUBBLE_CORNER_ARROW
                }
            }
            backgroundDrawable.cornerRadii = generateCorners(topLeft, topRight, bottomRight, bottomLeft)
            bubble.background = backgroundDrawable
        }
        protected fun setTimestamp(chatItem: ChatItem): TextView {
            val calendar = Calendar.getInstance().apply {
                time = Date(chatItem.timestamp * 1000)
            }
            val textView = itemView.findViewById<TextView>(R.id.timestamp)
            @SuppressLint("SetTextI18n")
            textView.text = StringUtils.toTwoDigits(calendar.get(Calendar.HOUR_OF_DAY))+":"+StringUtils.toTwoDigits(calendar.get(Calendar.MINUTE))
            return textView
        }
    }

    internal open class MessageViewHolder(itemView: View): BubbleViewHolder(itemView) {
        protected fun bindMessage(chatItem: ChatItem, outgoing: Boolean): TextView {
            itemView.findViewById<TextView>(R.id.text_message).apply {
                text = chatItem.data.sliceArray(1 until chatItem.data.size).decodeToString()
                if (!outgoing) {
                    highlightColor = ContextCompat.getColor(context, R.color.incomingHighlight)
                }
                return this
            }
        }
    }

    internal class OutgoingMessageViewHolder(private val context: Context, itemView: View): MessageViewHolder(itemView) {
        fun bind(chatItem: ChatItem, previousOutgoing: Boolean?, nextOutgoing: Boolean?) {
            setTimestamp(chatItem).apply {
                setTextColor(ContextCompat.getColor(context, R.color.outgoingTimestamp))
            }
            configureBubble(context, true, previousOutgoing, nextOutgoing)
            bindMessage(chatItem, true).apply {
                setLinkTextColor(ContextCompat.getColor(context, R.color.outgoingTextLink))
            }
            configureContainer(true, previousOutgoing, nextOutgoing == null)
        }
    }

    internal class IncomingMessageViewHolder(private val context: Context, itemView: View): MessageViewHolder(itemView) {
        fun bind(chatItem: ChatItem, previousOutgoing: Boolean?, nextOutgoing: Boolean?) {
            setTimestamp(chatItem).apply {
                setTextColor(ContextCompat.getColor(context, R.color.incomingTimestamp))
            }
            configureBubble(context, false, previousOutgoing, nextOutgoing)
            bindMessage(chatItem, false).apply {
                setLinkTextColor(ContextCompat.getColor(context, R.color.incomingTextLink))
            }
            configureContainer(false, previousOutgoing, nextOutgoing == null)
        }
    }

    internal open class FileViewHolder(itemView: View, private val onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): BubbleViewHolder(itemView) {
        protected fun bindFile(chatItem: ChatItem, outgoing: Boolean) {
            val filename = chatItem.data.sliceArray(17 until chatItem.data.size).decodeToString()
            itemView.findViewById<TextView>(R.id.text_filename).apply {
                text = filename
                if (!outgoing) {
                    highlightColor = ContextCompat.getColor(context, R.color.incomingHighlight)
                }
            }
            itemView.findViewById<ImageButton>(R.id.button_save).setOnClickListener {
                onSavingFile(filename, chatItem.data.sliceArray(1 until 17))
            }
        }
    }

    internal class OutgoingFileViewHolder(private val context: Context, itemView: View, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): FileViewHolder(itemView, onSavingFile) {
        fun bind(chatItem: ChatItem, previousOutgoing: Boolean?, nextOutgoing: Boolean?) {
            setTimestamp(chatItem).apply {
                setTextColor(ContextCompat.getColor(context, R.color.outgoingTimestamp))
            }
            bindFile(chatItem, true)
            configureBubble(context, true, previousOutgoing, nextOutgoing)
            configureContainer(true, previousOutgoing, nextOutgoing == null)
        }
    }

    internal class IncomingFileViewHolder(private val context: Context, itemView: View, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): FileViewHolder(itemView, onSavingFile) {
        fun bind(chatItem: ChatItem, previousOutgoing: Boolean?, nextOutgoing: Boolean?) {
            setTimestamp(chatItem).apply {
                setTextColor(ContextCompat.getColor(context, R.color.incomingTimestamp))
            }
            bindFile(chatItem, false)
            configureBubble(context, false, previousOutgoing, nextOutgoing)
            configureContainer(false, previousOutgoing, nextOutgoing == null)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ChatItem.OUTGOING_MESSAGE || viewType == ChatItem.INCOMING_MESSAGE) {
            val view = inflater.inflate(R.layout.adapter_chat_message, parent, false)
            if (viewType == ChatItem.OUTGOING_MESSAGE) {
                OutgoingMessageViewHolder(context, view)
            } else {
                IncomingMessageViewHolder(context, view)
            }
        } else {
            val view = inflater.inflate(R.layout.adapter_chat_file, parent, false)
            if (viewType == ChatItem.OUTGOING_FILE) {
                OutgoingFileViewHolder(context, view, onSavingFile)
            } else {
                IncomingFileViewHolder(context, view, onSavingFile)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = chatItems[position]
        val previousOutgoing = if (position == 0) {
            null
        } else {
            chatItems[position-1].outgoing
        }
        val nextOutgoing = if (position == chatItems.size - 1) {
            null
        } else {
            chatItems[position+1].outgoing
        }
        when (chatItem.itemType) {
            ChatItem.OUTGOING_MESSAGE -> (holder as OutgoingMessageViewHolder).bind(chatItem, previousOutgoing, nextOutgoing)
            ChatItem.INCOMING_MESSAGE -> (holder as IncomingMessageViewHolder).bind(chatItem, previousOutgoing, nextOutgoing)
            ChatItem.OUTGOING_FILE -> (holder as OutgoingFileViewHolder).bind(chatItem, previousOutgoing, nextOutgoing)
            ChatItem.INCOMING_FILE -> (holder as IncomingFileViewHolder).bind(chatItem, previousOutgoing, nextOutgoing)
        }
    }

    override fun getItemCount(): Int {
        return chatItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return chatItems[position].itemType
    }
}