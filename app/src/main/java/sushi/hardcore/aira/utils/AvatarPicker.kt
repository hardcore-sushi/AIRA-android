package sushi.hardcore.aira.utils

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.Constants
import sushi.hardcore.aira.R

class AvatarPicker(
    private val activity: AppCompatActivity,
    private val onAvatarPicked: (ByteArray) -> Unit,
) {
    private lateinit var picker: ActivityResultLauncher<String>
    fun register() {
        picker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                activity.contentResolver.openInputStream(uri)?.let { stream ->
                    val image = stream.readBytes()
                    stream.close()
                    if (image.size > Constants.MAX_AVATAR_SIZE) {
                        Toast.makeText(activity, R.string.avatar_too_large, Toast.LENGTH_SHORT).show()
                    } else {
                        onAvatarPicked(image)
                    }
                }
            }
        }
    }

    fun launch() {
        picker.launch("image/*")
    }
}