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
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.aira.ChatItem
import sushi.hardcore.aira.R

class ChatAdapter(
    private val context: Context,
    private val onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val BUBBLE_MARGIN = 70
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

    internal open class BubbleViewHolder(private val context: Context, itemView: View): RecyclerView.ViewHolder(itemView) {
        fun handleItemView(position: Int) {
            if (position == 0) {
                itemView.setPadding(itemView.paddingLeft, 50, itemView.paddingRight, itemView.paddingBottom)
            }
        }
        fun setBubbleColor(bubble: View, outgoing: Boolean) {
            if (outgoing) {
                bubble.background.clearColorFilter()
            } else {
                bubble.background.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.incomingBubbleBackground), PorterDuff.Mode.SRC)
            }
        }
    }

    internal class MessageViewHolder(context: Context, itemView: View): BubbleViewHolder(context, itemView) {
        fun bind(chatItem: ChatItem, position: Int) {
            itemView.findViewById<TextView>(R.id.text_message).apply {
                text = chatItem.data.sliceArray(1 until chatItem.data.size).decodeToString()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (chatItem.outgoing) {
                        gravity = Gravity.END
                        marginStart = BUBBLE_MARGIN
                    } else {
                        gravity = Gravity.START
                        marginEnd = BUBBLE_MARGIN
                    }
                }
                setBubbleColor(this, chatItem.outgoing)
            }
            handleItemView(position)
        }
    }

    internal class FileViewHolder(context: Context, itemView: View): BubbleViewHolder(context, itemView) {
        fun bind(chatItem: ChatItem, position: Int, onSavingFile: (filename: String, rawUuid: ByteArray) -> Unit) {
            val filename = chatItem.data.sliceArray(17 until chatItem.data.size).decodeToString()
            itemView.findViewById<TextView>(R.id.text_filename).text = filename
            itemView.findViewById<ImageButton>(R.id.button_save).setOnClickListener {
                onSavingFile(filename, chatItem.data.sliceArray(1 until 17))
            }
            itemView.findViewById<LinearLayout>(R.id.bubble_content).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (chatItem.outgoing) {
                        gravity = Gravity.END
                        marginStart = BUBBLE_MARGIN
                    } else {
                        gravity = Gravity.START
                        marginEnd = BUBBLE_MARGIN
                    }
                }
                setBubbleColor(this, chatItem.outgoing)
            }
            handleItemView(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ChatItem.MESSAGE -> {
                val view = inflater.inflate(R.layout.adapter_chat_message, parent, false)
                MessageViewHolder(context, view)
            }
            ChatItem.FILE -> {
                val view = inflater.inflate(R.layout.adapter_chat_file, parent, false)
                FileViewHolder(context, view)
            }
            else -> throw RuntimeException("Invalid chat item type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = chatItems[position]
        when (chatItem.itemType) {
            ChatItem.MESSAGE -> {
                (holder as MessageViewHolder).bind(chatItem, position)
            }
            ChatItem.FILE -> {
                (holder as FileViewHolder).bind(chatItem, position, onSavingFile)
            }
        }
    }

    override fun getItemCount(): Int {
        return chatItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return chatItems[position].itemType
    }
}