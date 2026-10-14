package com.jianji.note.ui

import android.os.Bundle
import com.jianji.note.R
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityPasscodeBinding
import com.jianji.note.util.applySystemBarInsets
import com.jianji.note.widget.passcode.PasscodeView

/** 应用密码：MODE_SET 两次输入设置新密码，MODE_CHECK 校验已有密码。 */
class PasscodeActivity : BaseActivity() {

    /** 密码锁页固定用深色底（见 activity_passcode.xml 的 passcode_bg），系统栏图标要用白的。 */
    override fun isDarkSurface(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPasscodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        val mode = intent.getIntExtra(EXTRA_MODE, MODE_CHECK)
        val view = binding.passcodeView
        view.setPasscodeLength(4)

        if (mode == MODE_SET) {
            view.setFirstInputTip(getString(R.string.passcode_tip_first))
            view.setSecondInputTip(getString(R.string.passcode_tip_second))
            view.setWrongLengthTip(getString(R.string.passcode_wrong_length))
            view.setWrongInputTip(getString(R.string.passcode_not_match))
            view.setCorrectInputTip(getString(R.string.passcode_correct))
        } else {
            val saved = Prefs.passcode
            if (saved.isEmpty()) {
                finish()
                return
            }
            view.setFirstInputTip(getString(R.string.passcode_check_tip))
            view.setWrongLengthTip(getString(R.string.passcode_wrong_length))
            view.setWrongInputTip(getString(R.string.passcode_wrong))
            view.setCorrectInputTip(getString(R.string.passcode_correct))
            // setLocalPasscode 会同时把控件切换成校验模式
            view.setLocalPasscode(saved)
        }

        view.setListener(object : PasscodeView.PasscodeViewListener {
            override fun onFail(wrongNumber: String) {
                if (mode == MODE_CHECK) {
                    view.postDelayed({ view.resetInput() }, 250)
                }
            }

            override fun onSuccess(number: String) {
                if (mode == MODE_SET) {
                    Prefs.passcode = number
                }
                Prefs.unlocked = true
                setResult(RESULT_OK)
                finish()
            }
        })
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SET = 0
        const val MODE_CHECK = 1
    }
}
