package sushi.hardcore.aira.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import sushi.hardcore.aira.Constants
import sushi.hardcore.aira.R
import java.io.ByteArrayOutputStream

class AvatarPicker(
    private val activity: AppCompatActivity,
    private val onAvatarPicked: (AvatarPicker, RequestBuilder<Drawable>) -> Unit,
) {
    private lateinit var picker: ActivityResultLauncher<String>
    private lateinit var avatar: RequestBuilder<Drawable>
    fun register() {
        picker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                activity.contentResolver.openInputStream(uri)?.let { stream ->
                    val image = stream.readBytes()
                    stream.close()
                    if (image.size > Constants.MAX_AVATAR_SIZE) {
                        Toast.makeText(activity, R.string.avatar_too_large, Toast.LENGTH_SHORT).show()
                    } else {
                        avatar = Glide.with(activity).load(image).centerCrop()
                        onAvatarPicked(this, avatar)
                    }
                }
            }
        }
    }

    fun setOnAvatarCompressed(onCompressed: (ByteArray) -> Unit) {
        avatar.into(object: CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                val avatar = ByteArrayOutputStream()
                if (resource.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, avatar)) {
                    onCompressed(avatar.toByteArray())
                }
            }
            override fun onLoadCleared(placeholder: Drawable?) {}
        })
    }

    fun launch() {
        picker.launch("image/*")
    }
}