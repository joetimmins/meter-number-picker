package com.alexzaitsev.meternumberpicker

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.core.view.children
import kotlin.math.pow

class MeterView : LinearLayout {
    private var numberOfFirst = DEFAULT_NUMBER_OF_BLACK
    private var numberOfSecond = DEFAULT_NUMBER_OF_RED
    private var firstColor = DEFAULT_BLACK_COLOR
    private var secondColor = DEFAULT_RED_COLOR
    private var enabled = DEFAULT_ENABLED
    private var pickerStyleId = -1

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        orientation = HORIZONTAL
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MeterView, 0, 0)
            numberOfFirst = typedArray.getInt(R.styleable.MeterView_mv_numberOfFirst, numberOfFirst)
            numberOfSecond = typedArray.getInt(R.styleable.MeterView_mv_numberOfSecond, numberOfSecond)
            firstColor = typedArray.getColor(R.styleable.MeterView_mv_firstColor, firstColor)
            secondColor = typedArray.getColor(R.styleable.MeterView_mv_secondColor, secondColor)
            pickerStyleId = typedArray.getResourceId(R.styleable.MeterView_mv_pickerStyle, pickerStyleId)
            enabled = typedArray.getBoolean(R.styleable.MeterView_mv_enabled, enabled)
            typedArray.recycle()
        }
        populate(context)
    }

    private fun populate(context: Context) {
        for (i in 0 until numberOfFirst + numberOfSecond) {
            val meterNumberPicker = createPicker(context)
            meterNumberPicker.setBackgroundColor(if (i < numberOfFirst) firstColor else secondColor)
            meterNumberPicker.isEnabled = isEnabled
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.weight = 1f
            addView(meterNumberPicker, lp)
        }
    }

    private fun createPicker(context: Context) =
        if (pickerStyleId == -1) MeterNumberPicker(context) else MeterNumberPicker(context, pickerStyleId)

    override fun isEnabled() = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) =
        !enabled || super.onInterceptTouchEvent(ev)
    /**
     * Returns current value of the widget. Works only if "mnp_max" is not bigger then 9.
     * For other cases you have to extend this view for now.
     */
    /**
     * Sets current value to the widget. Works only if "mnp_max" is not bigger then 9.
     * For other cases you have to extend this view for now.
     */
    var value: Int
        get() {
            var result = 0
            children.forEachIndexed { index, view ->
                val coefficient = childCount - index - 1
                val placeValue = 10.0.pow(coefficient)
                val value1 = (view as MeterNumberPicker).getValue() * placeValue
                result += value1.toInt()
            }
            return result
        }
        set(value) {
            var newValue = value
            children.forEachIndexed { index, view ->
                val coefficient = childCount - index - 1
                val placeValue = 10.0.pow(coefficient)
                val number = (newValue / placeValue).toInt()
                require(!(index == 0 && number > 9)) { "Number of digits cannot be greater then pickers number" }
                newValue -= (number * placeValue).toInt()
                (view as MeterNumberPicker).setValue(number)
            }
        }

    fun setNumbersOf(numberOfFirst: Int, numberOfSecond: Int) {
        this.numberOfFirst = numberOfFirst
        this.numberOfSecond = numberOfSecond
        removeAllViews()
        init(context, null)
    }

    companion object {
        private const val DEFAULT_NUMBER_OF_BLACK = 5
        private const val DEFAULT_NUMBER_OF_RED = 0
        private const val DEFAULT_BLACK_COLOR = -0x1000000
        private const val DEFAULT_RED_COLOR = -0x340000
        private const val DEFAULT_ENABLED = true
    }
}
