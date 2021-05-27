package sushi.hardcore.aira.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import sushi.hardcore.aira.R
import sushi.hardcore.aira.databinding.AvatarBinding

class Avatar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RelativeLayout(context, attrs, defStyle) {

    private val binding = AvatarBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.Avatar)
            for (i in 0..typedArray.indexCount) {
                val attr = typedArray.getIndex(i)
                if (attr == R.styleable.Avatar_textSize) {
                    val textSize = typedArray.getDimension(attr, -1F)
                    if (textSize != -1F) {
                        binding.textLetter.textSize = textSize
                    }
                }
            }
            typedArray.recycle()
        }
    }

    fun setTextAvatar(name: String) {
        if (name.isNotEmpty()) {
            binding.textLetter.text = name[0].toString()
            binding.imageAvatar.visibility = View.GONE
            binding.textAvatar.visibility = View.VISIBLE
        }
    }

    fun setImageAvatar(avatar: ByteArray) {
        Glide.with(this).load(avatar).circleCrop().into(binding.imageAvatar)
        binding.textAvatar.visibility = View.GONE
        binding.imageAvatar.visibility = View.VISIBLE
    }
}