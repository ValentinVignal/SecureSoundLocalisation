package com.e.sslapp.customElements

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.e.sslapp.R
import kotlinx.android.synthetic.main.menu_switch.view.*

class MenuSwitch : LinearLayout {
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    )
            : super(context, attrs, defStyleAttr)

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    )
            : super(context, attrs, defStyleAttr, defStyleRes)

    fun init(attrs: AttributeSet?) {
        LayoutInflater.from(context)
            .inflate(R.layout.menu_switch, this, true)

        orientation = VERTICAL

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                it,
                R.styleable.menu_switch, 0, 0
            )
            val title = typedArray.getText(R.styleable.menu_switch_title)
            val state = typedArray.getText(R.styleable.menu_switch_state)

            mtitle.text = title
            mstate.text = state

            typedArray.recycle()
        }

        fun setTitle(title: String){
            mtitle.text = title
        }

        fun setState(state: String){
            mstate.text = state
        }

        fun setState(state: Boolean){
            if (state){
                mstate.text = "ON"
            } else {
                mstate.text = "OFF"
            }
        }
    }
}