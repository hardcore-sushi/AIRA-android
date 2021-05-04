package sushi.hardcore.aira.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.widget.TextView
import sushi.hardcore.aira.R

class TextAvatar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RelativeLayout(context, attrs, defStyle) {

    private val view = LayoutInflater.from(context).inflate(R.layout.text_avatar, this, true)

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.TextAvatar)
            for (i in 0..typedArray.indexCount) {
                val attr = typedArray.getIndex(i)
                if (attr == R.styleable.TextAvatar_textSize) {
                    val textSize = typedArray.getDimension(attr, -1F)
                    if (textSize != -1F) {
                        view.findViewById<TextView>(R.id.text_letter).textSize = textSize
                    }
                    break
                }
            }
            typedArray.recycle()
        }
    }

    fun setLetterFrom(name: String) {
        if (name.isNotEmpty()) {
            view.findViewById<TextView>(R.id.text_letter).text = name[0].toUpperCase().toString()
        }
    }
}