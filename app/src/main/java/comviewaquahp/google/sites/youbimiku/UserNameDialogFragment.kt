package comviewaquahp.google.sites.youbimiku

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import comviewaquahp.google.sites.youbimiku.databinding.UserNameDialogBinding
import kotlinx.android.synthetic.main.user_name_dialog.view.*
import java.util.*

interface DialogListener : EventListener {
    fun doPositiveClick()
}

class UserNameDialogFragment : DialogFragment() {

    private lateinit var binding: UserNameDialogBinding
    private var listener: DialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        binding = UserNameDialogBinding.inflate(requireActivity().layoutInflater)
        val view = binding.root

        view.editText.setText(getUserName(requireContext()))

        val builder = AlertDialog.Builder(requireContext())
        builder.setPositiveButton(getString(R.string.setting_dialog_accept)) { _, _ -> }
        if (arguments?.getBoolean(Constants.ARGUMENT_CANCELABLE) == true) {
            builder.setNegativeButton(getString(R.string.setting_dialog_cancel)) { _, _ -> }
        } else {
            this.isCancelable = false
        }
        val dialog = builder.setTitle(R.string.setting_name_title)
            .setView(view)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = view.editText?.text.toString()
            if (name.isEmpty()) {
                view.editText?.error = getString(R.string.setting_user_name_empty)
            } else {
                SharedPreferenceManager.put(
                    requireContext(),
                    Key.USER_NAME.name,
                    name
                )
                listener?.doPositiveClick()
                dialog.dismiss()
                removeDialogListener()
            }
        }
        return dialog
    }

    fun setDialogListener(listener: DialogListener) {
        this.listener = listener
    }

    private fun removeDialogListener() {
        this.listener = null
    }
}