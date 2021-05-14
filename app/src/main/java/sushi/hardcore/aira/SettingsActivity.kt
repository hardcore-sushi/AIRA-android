package sushi.hardcore.aira

import android.content.*
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
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.databinding.ActivitySettingsBinding
import sushi.hardcore.aira.utils.StringUtils

class SettingsActivity: AppCompatActivity() {
    class MySettingsFragment : PreferenceFragmentCompat() {
        private lateinit var airaService: AIRAService
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val identityNamePreference = findPreference<EditTextPreference>("identityName")
            val paddingPreference = findPreference<SwitchPreferenceCompat>("psecPadding")
            identityNamePreference?.isPersistent = false
            paddingPreference?.isPersistent = false
            Intent(activity, AIRAService::class.java).also { serviceIntent ->
                activity?.bindService(serviceIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                        val binder = service as AIRAService.AIRABinder
                        airaService = binder.getService()
                        identityNamePreference?.text = airaService.identityName
                        paddingPreference?.isChecked = airaService.usePadding
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                }, Context.BIND_AUTO_CREATE)
            }
            identityNamePreference?.setOnPreferenceChangeListener { _, newValue ->
                if (airaService.changeName(newValue as String)) {
                    identityNamePreference.text = newValue
                }
                false
            }
            findPreference<Preference>("deleteIdentity")?.setOnPreferenceClickListener {
                activity?.let { activity ->
                    AlertDialog.Builder(activity)
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
                }
                false
            }
            findPreference<Preference>("identityPassword")?.setOnPreferenceClickListener {
                activity?.let { activity ->
                    val dialogView = layoutInflater.inflate(R.layout.dialog_password, null)
                    val oldPasswordEditText = dialogView.findViewById<EditText>(R.id.old_password)
                    val isIdentityProtected = AIRADatabase.isIdentityProtected(Constants.getDatabaseFolder(activity))
                    if (!isIdentityProtected) {
                        oldPasswordEditText.visibility = View.GONE
                    }
                    val newPasswordEditText = dialogView.findViewById<EditText>(R.id.new_password)
                    val newPasswordConfirmEditText = dialogView.findViewById<EditText>(R.id.new_password_confirm)
                    AlertDialog.Builder(activity)
                        .setView(dialogView)
                        .setTitle(R.string.change_password)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val newPassword = newPasswordEditText.text.toString().toByteArray()
                            if (newPassword.isEmpty()) {
                                if (isIdentityProtected) { //don't change password if identity is not protected and new password is blank
                                    changePassword(activity, isIdentityProtected, oldPasswordEditText, null)
                                }
                            } else {
                                val newPasswordConfirm = newPasswordConfirmEditText.text.toString().toByteArray()
                                if (newPassword.contentEquals(newPasswordConfirm)) {
                                    changePassword(activity, isIdentityProtected, oldPasswordEditText, newPassword)
                                } else {
                                    AlertDialog.Builder(activity)
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
                }
                false
            }
            findPreference<Preference>("fingerprint")?.let { fingerprintPreference ->
                val fingerprint = StringUtils.beautifyFingerprint(AIRADatabase.getIdentityFingerprint())
                fingerprintPreference.summary = fingerprint
                fingerprintPreference.setOnPreferenceClickListener {
                    activity?.getSystemService(CLIPBOARD_SERVICE)?.let { service ->
                        val clipboardManager = service as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", fingerprint))
                    }
                    Toast.makeText(activity, R.string.fingerprint_copied, Toast.LENGTH_SHORT).show()
                    false
                }
            }
            paddingPreference?.setOnPreferenceChangeListener { _, checked ->
                airaService.usePadding = checked as Boolean
                AIRADatabase.setUsePadding(checked)
                true
            }
        }

        private fun changePassword(context: Context, isIdentityProtected: Boolean, oldPasswordEditText: EditText, newPassword: ByteArray?) {
            val oldPassword = if (isIdentityProtected) {
                oldPasswordEditText.text.toString().toByteArray()
            } else {
                null
            }
            if (!AIRADatabase.changePassword(Constants.getDatabaseFolder(context), oldPassword, newPassword)) {
                AlertDialog.Builder(context)
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
            .replace(R.id.settings_container, MySettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}