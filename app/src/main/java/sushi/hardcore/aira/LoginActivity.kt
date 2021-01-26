package sushi.hardcore.aira

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.background_service.AIRAService
import java.io.File

class LoginActivity : AppCompatActivity() {
    private external fun getIdentityName(databaseFolder: String): String?

    companion object {
        init {
            System.loadLibrary("aira")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val databaseFolder = Constants.getDatabaseFolder(this)
        val dbFile = File(databaseFolder)
        if (!dbFile.isDirectory) {
            if (!dbFile.mkdir()) {
                Toast.makeText(this, R.string.db_mkdir_failed, Toast.LENGTH_SHORT).show()
            }
        }
        val isProtected = AIRADatabase.isIdentityProtected(databaseFolder)
        val name = getIdentityName(databaseFolder)
        if (AIRAService.isServiceRunning) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else if (name != null && !isProtected) {
            if (AIRADatabase.loadIdentity(databaseFolder, null)) {
                AIRADatabase.clearTemporaryFiles()
                startMainActivity(name)
            } else {
                Toast.makeText(this, R.string.identity_load_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            supportFragmentManager.beginTransaction()
                .add(
                    R.id.fragment_container, if (name == null) {
                        CreateIdentityFragment.newInstance()
                    } else {
                        LoginFragment.newInstance(name)
                    }
                )
                .commit()
        }
    }

    private fun startMainActivity(identityName: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("identityName", identityName)
        startActivity(intent)
        finish()
    }
}