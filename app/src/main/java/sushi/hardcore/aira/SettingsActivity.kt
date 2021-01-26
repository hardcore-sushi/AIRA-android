package sushi.hardcore.aira

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.databinding.ActivityMainBinding
import sushi.hardcore.aira.databinding.ActivitySettingsBinding
import sushi.hardcore.aira.utils.StringUtils

class SettingsActivity: AppCompatActivity() {
    class MySettingsFragment : PreferenceFragmentCompat() {
        private lateinit var airaService: AIRAService
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val identityName = findPreference<EditTextPreference>("identityName")
            Intent(activity, AIRAService::class.java).also { serviceIntent ->
                activity?.bindService(serviceIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                        val binder = service as AIRAService.AIRABinder
                        airaService = binder.getService()
                        identityName?.text = airaService.identityName
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                }, Context.BIND_AUTO_CREATE)
            }
            identityName?.setOnPreferenceChangeListener { _, newValue ->
                if (airaService.changeName(newValue as String)) {
                    identityName.text = newValue
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
        binding.textFingerprint.text = StringUtils.beautifyFingerprint(AIRADatabase.getIdentityFingerprint())
    }
}