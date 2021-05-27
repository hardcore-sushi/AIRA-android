package sushi.hardcore.aira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.databinding.FragmentCreateIdentityBinding
import sushi.hardcore.aira.utils.AvatarPicker

class CreateIdentityFragment(private val activity: AppCompatActivity) : Fragment() {
    private external fun createNewIdentity(databaseFolder: String, name: String, password: ByteArray?): Boolean

    companion object {
        fun newInstance(activity: AppCompatActivity): CreateIdentityFragment {
            return CreateIdentityFragment(activity)
        }
    }

    private val avatarPicker = AvatarPicker(activity) { picker, avatar ->
        picker.setOnAvatarCompressed { compressedAvatar ->
            AIRADatabase.setIdentityAvatar(Constants.getDatabaseFolder(activity), compressedAvatar)
        }
        avatar.circleCrop().into(binding.avatar)
    }
    private lateinit var binding: FragmentCreateIdentityBinding

    override fun onAttach(context: Context) {
        super.onAttach(context)
        avatarPicker.register()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCreateIdentityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonSetAvatar.setOnClickListener {
            avatarPicker.launch()
        }
        binding.checkboxEnablePassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.editPassword.visibility = View.VISIBLE
                binding.editPasswordConfirm.visibility = View.VISIBLE
            } else {
                binding.editPassword.visibility = View.GONE
                binding.editPasswordConfirm.visibility = View.GONE
            }
        }
        binding.buttonCreate.setOnClickListener {
            val identityName = binding.editName.text.toString()
            val password = binding.editPassword.text.toString().toByteArray()
            if (password.isEmpty()) {
                createIdentity(identityName, null)
            } else {
                val passwordConfirm = binding.editPasswordConfirm.text.toString().toByteArray()
                if (password.contentEquals(passwordConfirm)) {
                    createIdentity(identityName, password)
                } else {
                    Toast.makeText(activity, R.string.password_mismatch, Toast.LENGTH_SHORT).show()
                }
                passwordConfirm.fill(0)
                password.fill(0)
            }
        }
    }

    private fun createIdentity(identityName: String, password: ByteArray?) {
        val databaseFolder = Constants.getDatabaseFolder(requireContext())
        if (createNewIdentity(
                databaseFolder,
                identityName,
                password
        )) {
            val intent = Intent(activity, MainActivity::class.java)
            intent.putExtra("identityName", identityName)
            startActivity(intent)
            activity.finish()
        } else {
            Toast.makeText(activity, R.string.identity_create_failed, Toast.LENGTH_SHORT).show()
        }
    }
}