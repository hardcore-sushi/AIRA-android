package sushi.hardcore.aira

import android.content.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.databinding.ActivitySettingsBinding
import sushi.hardcore.aira.databinding.ChangeAvatarDialogBinding
import sushi.hardcore.aira.databinding.DialogEditTextBinding
import sushi.hardcore.aira.utils.AvatarPicker
import sushi.hardcore.aira.utils.StringUtils

class SettingsActivity: AppCompatActivity() {
    class MySettingsFragment(private val activity: AppCompatActivity): PreferenceFragmentCompat() {
        private lateinit var airaService: AIRAService
        private val avatarPicker = AvatarPicker(activity) { picker, avatar ->
            if (::airaService.isInitialized) {
                picker.setOnAvatarCompressed { compressedAvatar ->
                    airaService.changeAvatar(compressedAvatar)
                }
            }
            displayAvatar(avatar)
        }
        private lateinit var identityAvatarPreference: Preference

        override fun onAttach(context: Context) {
            super.onAttach(context)
            avatarPicker.register()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>("identityAvatar")?.let { identityAvatarPreference = it }
            val paddingPreference = findPreference<SwitchPreferenceCompat>("psecPadding")
            paddingPreference?.isPersistent = false
            AIRADatabase.getIdentityAvatar(Constants.getDatabaseFolder(activity))?.let { avatar ->
                displayAvatar(avatar)
            }
            Intent(activity, AIRAService::class.java).also { serviceIntent ->
                activity.bindService(serviceIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                        val binder = service as AIRAService.AIRABinder
                        airaService = binder.getService()
                        paddingPreference?.isChecked = airaService.usePadding
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                }, Context.BIND_AUTO_CREATE)
            }
            identityAvatarPreference.setOnPreferenceClickListener {
                val dialogBuilder = AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                    .setTitle(R.string.your_avatar)
                    .setPositiveButton(R.string.set_a_new_one) { _, _ ->
                        avatarPicker.launch()
                    }
                val dialogBinding = ChangeAvatarDialogBinding.inflate(layoutInflater)
                val avatar = AIRADatabase.getIdentityAvatar(Constants.getDatabaseFolder(activity))
                if (avatar == null) {
                    dialogBinding.avatar.setTextAvatar(airaService.identityName!!)
                } else {
                    dialogBinding.avatar.setImageAvatar(avatar)
                    dialogBuilder.setNegativeButton(R.string.remove) { _, _ ->
                        displayAvatar(null)
                        airaService.changeAvatar(null)
                    }
                }
                dialogBuilder.setView(dialogBinding.root).show()
                false
            }
            findPreference<Preference>("identityName")?.setOnPreferenceClickListener {
                val dialogBinding = DialogEditTextBinding.inflate(layoutInflater)
                dialogBinding.editText.setText(airaService.identityName)
                AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                    .setTitle(it.title)
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        airaService.changeName(dialogBinding.editText.text.toString())
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                false
            }
            findPreference<Preference>("deleteIdentity")?.setOnPreferenceClickListener {
                AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                    .setMessage(R.string.confirm_delete)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        if (Constants.getDatabasePath(activity).delete()) {
                            airaService.logOut()
                            startActivity(Intent(activity, LoginActivity::class.java))
                            activity.finish()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                false
            }
            findPreference<Preference>("identityPassword")?.setOnPreferenceClickListener {
                val dialogView = layoutInflater.inflate(R.layout.dialog_password, null)
                val oldPasswordEditText = dialogView.findViewById<EditText>(R.id.old_password)
                val isIdentityProtected = AIRADatabase.isIdentityProtected(Constants.getDatabaseFolder(activity))
                if (!isIdentityProtected) {
                    oldPasswordEditText.visibility = View.GONE
                }
                val newPasswordEditText = dialogView.findViewById<EditText>(R.id.new_password)
                val newPasswordConfirmEditText = dialogView.findViewById<EditText>(R.id.new_password_confirm)
                AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                    .setView(dialogView)
                    .setTitle(R.string.change_password)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val newPassword = newPasswordEditText.text.toString().toByteArray()
                        if (newPassword.isEmpty()) {
                            if (isIdentityProtected) { //don't change password if identity is not protected and new password is blank
                                changePassword(isIdentityProtected, oldPasswordEditText, null)
                            }
                        } else {
                            val newPasswordConfirm = newPasswordConfirmEditText.text.toString().toByteArray()
                            if (newPassword.contentEquals(newPasswordConfirm)) {
                                changePassword(isIdentityProtected, oldPasswordEditText, newPassword)
                            } else {
                                AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                                    .setMessage(R.string.password_mismatch)
                                    .setTitle(R.string.error)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                            newPassword.fill(0)
                            newPasswordConfirm.fill(0)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                false
            }
            findPreference<Preference>("fingerprint")?.let { fingerprintPreference ->
                val fingerprint = StringUtils.beautifyFingerprint(AIRADatabase.getIdentityFingerprint())
                fingerprintPreference.summary = fingerprint
                fingerprintPreference.setOnPreferenceClickListener {
                    activity.getSystemService(CLIPBOARD_SERVICE)?.let { service ->
                        val clipboardManager = service as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", fingerprint))
                    }
                    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
                    false
                }
            }
            paddingPreference?.setOnPreferenceChangeListener { _, checked ->
                airaService.usePadding = checked as Boolean
                AIRADatabase.setUsePadding(checked)
                true
            }
        }

        private fun displayAvatar(avatar: ByteArray?) {
            if (avatar == null) {
                identityAvatarPreference.setIcon(R.drawable.ic_face)
            } else {
                displayAvatar(Glide.with(this).load(avatar))
            }
        }

        private fun displayAvatar(glideBuilder: RequestBuilder<Drawable>) {
            glideBuilder.apply(RequestOptions().override(90)).circleCrop().into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    identityAvatarPreference.icon = resource
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
        }

        private fun changePassword(isIdentityProtected: Boolean, oldPasswordEditText: EditText, newPassword: ByteArray?) {
            val oldPassword = if (isIdentityProtected) {
                oldPasswordEditText.text.toString().toByteArray()
            } else {
                null
            }
            if (!AIRADatabase.changePassword(Constants.getDatabaseFolder(activity), oldPassword, newPassword)) {
                AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                    .setMessage(R.string.change_password_failed)
                    .setTitle(R.string.error)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
            oldPassword?.fill(0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, MySettingsFragment(this))
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}