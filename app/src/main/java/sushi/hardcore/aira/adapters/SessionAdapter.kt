package sushi.hardcore.aira.adapters

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import sushi.hardcore.aira.R
import sushi.hardcore.aira.widgets.TextAvatar

class SessionAdapter(val context: Context): BaseAdapter() {
    private val sessions = mutableListOf<Session>()
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    val selectedItems = mutableListOf<Int>()

    override fun getCount(): Int {
        return sessions.size
    }

    override fun getItem(position: Int): Session {
        return sessions[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_session, parent, false)
        val currentSession = getItem(position)
        view.findViewById<TextView>(R.id.text_name).apply {
            view.findViewById<TextAvatar>(R.id.text_avatar).setLetterFrom(if (currentSession.name == null) {
                text = currentSession.ip
                setTextColor(Color.RED)
                "?"
            } else {
                text = currentSession.name
                setTextColor(Color.WHITE)
                currentSession.name!!
            })
        }
        view.findViewById<ImageView>(R.id.image_trust_level).apply {
            if (currentSession.isVerified) {
                setImageResource(R.drawable.ic_verified)
            } else if (!currentSession.isContact) {
                setImageResource(R.drawable.ic_warning)
            } else {
                setImageDrawable(null)
            }
        }
        view.findViewById<ImageView>(R.id.image_seen).visibility = if (currentSession.seen) {
            View.GONE
        } else {
            View.VISIBLE
        }
        view.setBackgroundColor(ContextCompat.getColor(context, if (selectedItems.contains(position)) {
           R.color.itemSelected
        } else {
            R.color.sessionBackground
        }))
        return view
    }

    fun add(session: Session) {
        sessions.add(session)
        notifyDataSetChanged()
    }

    private fun getSessionById(sessionId: Int): Session? {
        for (session in sessions){
            if (session.sessionId == sessionId) {
                return session
            }
        }
        return null
    }

    fun remove(sessionId: Int): Session? {
        getSessionById(sessionId)?.let {
            sessions.remove(it)
            notifyDataSetChanged()
            return it
        }
        return null
    }

    fun setName(sessionId: Int, name: String) {
        getSessionById(sessionId)?.let {
            it.name = name
            notifyDataSetChanged()
        }
    }

    fun setSeen(sessionId: Int, seen: Boolean) {
        getSessionById(sessionId)?.let {
            it.seen = seen
            notifyDataSetChanged()
        }
    }

    fun reset() {
        sessions.clear()
        notifyDataSetChanged()
    }

    fun onSelectionChanged(position: Int) {
        if (!selectedItems.contains(position)) {
            selectedItems.add(position)
        } else {
            selectedItems.remove(position)
        }
        notifyDataSetChanged()
    }

    fun unSelectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedSessionIds(): List<Int> {
        return selectedItems.map { position -> sessions[position].sessionId }
    }
}