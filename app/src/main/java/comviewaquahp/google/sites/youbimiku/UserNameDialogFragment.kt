package comviewaquahp.google.sites.youbimiku

import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.user_name_dialog.view.*
import java.util.EventListener

interface DialogListener: EventListener {
    fun doPositiveClick()
}
class UserNameDialogFragment : DialogFragment() {

    private var listener: DialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        val inflater = activity?.layoutInflater
        val nullParent: ViewGroup? = null
        val view = inflater?.inflate(R.layout.user_name_dialog, nullParent)

        view?.editText?.setText(getUserName(activity))

        val builder = AlertDialog.Builder(activity)
        builder.setPositiveButton(getString(R.string.setting_dialog_accept)) { _, _ ->}
        if (arguments.getBoolean(Constants.ARGUMENT_CANCELABLE)){
            builder.setNegativeButton(getString(R.string.setting_dialog_cancel)){ _, _ ->}
        } else{
            this.isCancelable = false
        }
        val dialog = builder.setTitle(R.string.setting_name_title)
                .setView(view)
                .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = view?.editText?.text.toString()
            if (name.isEmpty()) {
                view?.editText?.error = getString(R.string.setting_user_name_empty)
            } else {
                SharedPreferenceManager.put(
                        activity,
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