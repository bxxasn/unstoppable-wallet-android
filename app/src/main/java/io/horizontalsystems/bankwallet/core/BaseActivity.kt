package io.horizontalsystems.bankwallet.core

import android.content.Context
import android.os.Handler
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.core.CoreActivity
import io.horizontalsystems.views.AlertDialogFragment

abstract class BaseActivity : CoreActivity() {

    fun showCustomKeyboardAlert() {
        AlertDialogFragment.newInstance(
                titleString = getString(R.string.Alert_TitleWarning),
                descriptionString = getString(R.string.Alert_CustomKeyboardIsUsed),
                buttonText = R.string.Alert_Ok,
                cancelable = false,
                listener = object : AlertDialogFragment.Listener {
                    override fun onButtonClick() {
                        val imeManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imeManager.showInputMethodPicker()
                        hideSoftKeyboard()
                        Handler().postDelayed({
                            try {
                                onBackPressed()
                            } catch (e: NullPointerException) {
                                //do nothing
                            }
                        }, (1 * 750).toLong())
                    }

                    override fun onCancel() {}
                }).show(supportFragmentManager, "custom_keyboard_alert")
    }

    fun setTopMarginByStatusBarHeight(view: View) {
        val newLayoutParams = view.layoutParams as ConstraintLayout.LayoutParams
        newLayoutParams.topMargin = getStatusBarHeight()
        newLayoutParams.leftMargin = 0
        newLayoutParams.rightMargin = 0
        view.layoutParams = newLayoutParams
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    protected fun setTransparentStatusBar() {
        val oldFlags = window.decorView.systemUiVisibility
        window.decorView.systemUiVisibility = oldFlags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    protected fun hideSoftKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

}
