package sushi.hardcore.aira.adapters

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.aira.ChatItem
import sushi.hardcore.aira.R

class ChatAdapter(
    private val context: Context,
    private val onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val CONTAINER_MARGIN = 70
        const val BUBBLE_HORIZONTAL_PADDING = 40
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val chatItems = mutableListOf<ChatItem>()

    fun newMessage(chatItem: ChatItem) {
        chatItems.add(chatItem)
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
        protected fun setPadding(outgoing: Boolean) {
            if (outgoing) {
                itemView.updatePadding(right = BUBBLE_HORIZONTAL_PADDING)
            } else {
                itemView.updatePadding(left = BUBBLE_HORIZONTAL_PADDING)
            }
        }
        protected fun configureBubble(context: Context, view: View, outgoing: Boolean) {
            view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (outgoing) {
                    marginStart = CONTAINER_MARGIN
                    Gravity.END
                } else {
                    marginEnd = CONTAINER_MARGIN
                    Gravity.START
                }
            }
            if (!outgoing) {
                view.background.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(context, R.color.incomingBubbleBackground),
                    PorterDuff.Mode.SRC
                )
            }
        }
    }

    internal open class MessageViewHolder(itemView: View): BubbleViewHolder(itemView) {
        protected fun bindMessage(chatItem: ChatItem): TextView {
            itemView.findViewById<TextView>(R.id.text_message).apply {
                text = chatItem.data.sliceArray(1 until chatItem.data.size).decodeToString()
                return this
            }
        }
    }

    internal class OutgoingMessageViewHolder(private val context: Context, itemView: View): MessageViewHolder(itemView) {
        fun bind(chatItem: ChatItem) {
            configureBubble(context, bindMessage(chatItem), true)
            setPadding(true)
        }
    }

    internal class IncomingMessageViewHolder(private val context: Context, itemView: View): MessageViewHolder(itemView) {
        fun bind(chatItem: ChatItem) {
            configureBubble(context, bindMessage(chatItem), false)
            setPadding(false)
        }
    }

    internal open class FileViewHolder(itemView: View, private val onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): BubbleViewHolder(itemView) {
        protected fun bindFile(chatItem: ChatItem): LinearLayout {
            val filename = chatItem.data.sliceArray(17 until chatItem.data.size).decodeToString()
            itemView.findViewById<TextView>(R.id.text_filename).text = filename
            itemView.findViewById<ImageButton>(R.id.button_save).setOnClickListener {
                onSavingFile(filename, chatItem.data.sliceArray(1 until 17))
            }
            return itemView.findViewById(R.id.bubble_content)
        }
    }

    internal class OutgoingFileViewHolder(private val context: Context, itemView: View, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): FileViewHolder(itemView, onSavingFile) {
        fun bind(chatItem: ChatItem) {
            configureBubble(context, bindFile(chatItem), true)
            setPadding(true)
        }
    }

    internal class IncomingFileViewHolder(private val context: Context, itemView: View, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit): FileViewHolder(itemView, onSavingFile) {
        fun bind(chatItem: ChatItem) {
            configureBubble(context, bindFile(chatItem), false)
            setPadding(false)
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
        when (chatItem.itemType) {
            ChatItem.OUTGOING_MESSAGE -> (holder as OutgoingMessageViewHolder).bind(chatItem)
            ChatItem.INCOMING_MESSAGE -> (holder as IncomingMessageViewHolder).bind(chatItem)
            ChatItem.OUTGOING_FILE -> (holder as OutgoingFileViewHolder).bind(chatItem)
            ChatItem.INCOMING_FILE -> (holder as IncomingFileViewHolder).bind(chatItem)
        }
    }

    override fun getItemCount(): Int {
        return chatItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return chatItems[position].itemType
    }
}