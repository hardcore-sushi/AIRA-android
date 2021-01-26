package sushi.hardcore.aira

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import sushi.hardcore.aira.databinding.FragmentLoginBinding
import sushi.hardcore.aira.widgets.TextAvatar

class LoginFragment : Fragment() {
    companion object {
        private const val NAME_ARG = "identityName"
        fun newInstance(name: String): LoginFragment {
            return LoginFragment().apply {
                arguments = Bundle().apply { putString(NAME_ARG, name) }
            }
        }
    }

    private lateinit var binding: FragmentLoginBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let { bundle ->
            bundle.getString(NAME_ARG)?.let { name ->
                view.findViewById<TextAvatar>(R.id.text_avatar).setLetterFrom(name)
                view.findViewById<TextView>(R.id.text_identity_name).text = name
                binding.buttonLogin.setOnClickListener {
                    if (AIRADatabase.loadIdentity(Constants.getDatabaseFolder(requireContext()), binding.editPassword.text.toString().toByteArray())) {
                        AIRADatabase.clearTemporaryFiles()
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra("identityName", name)
                        startActivity(intent)
                        activity?.finish()
                    } else {
                        Toast.makeText(activity, R.string.identity_load_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}