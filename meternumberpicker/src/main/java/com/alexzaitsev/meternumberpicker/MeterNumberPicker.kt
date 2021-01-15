package com.alexzaitsev.meternumberpicker

import android.annotation.TargetApi
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.annotation.*
import java.util.Locale
import kotlin.math.abs

class MeterNumberPicker : View {
    private var minHeight = DEFAULT_MIN_HEIGHT_DP
    private var minWidth = DEFAULT_MIN_WIDTH_DP
    private var minValue = DEFAULT_MIN_VALUE
    private var maxValue = DEFAULT_MAX_VALUE
    private var value = DEFAULT_VALUE
    private lateinit var textPaint: Paint
    private var textColor = DEFAULT_TEXT_COLOR
    private var textSize = DEFAULT_TEXT_SIZE_SP
    private var order = DEFAULT_ORDER
    private var typeface: Typeface? = null
    private var wraparoundEventListener: WraparoundEventListener? = null

    /**
     * Current Y scroll offset
     */
    private var currentScrollOffset = 0

    /**
     * Current value offset
     */
    private var currentValueOffset = 0

    /**
     * The height of the text itself excluding paddings
     */
    private var textHeight = 0

    /**
     * Internal horizontal (left and right) padding
     */
    private var paddingHorizontal = DEFAULT_PADDING

    /**
     * Internal vertical (top and bottom) padding
     */
    private var paddingVertical = DEFAULT_PADDING

    /**
     * The Y position of the last down event
     */
    private var lastDownEventY = 0f

    /**
     * The Y position of the last down or move event
     */
    private var lastDownOrMoveEventY = 0f

    /**
     * The [Scroller] responsible for adjusting the selector
     */
    private var adjustScroller: Scroller? = null

    /**
     * The [Scroller] responsible for flinging the selector
     */
    private var flingScroller: Scroller? = null

    /**
     * The last Y position of adjustment scroller
     */
    private var scrollerLastY = 0

    /**
     * Determines speed during touch scrolling
     */
    private var velocityTracker: VelocityTracker? = null

    /**
     * @see ViewConfiguration.getScaledMinimumFlingVelocity
     */
    private var minimumFlingVelocity = 0

    /**
     * @see ViewConfiguration.getScaledMaximumFlingVelocity
     */
    private var maximumFlingVelocity = 0

    constructor(context: Context) : super(context) {
        initWithAttrs(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initWithAttrs(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initWithAttrs(context, attrs, defStyleAttr, 0)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initWithAttrs(context, attrs, defStyleAttr, defStyleRes)
    }

    constructor(context: Context, @StyleRes styleId: Int) : super(context) {
        initWithStyle(context, styleId)
    }

    private fun initWithAttrs(context: Context, attrs: AttributeSet?, defStyleAttrs: Int, defStyleRes: Int) {
        val attributesArray = context.obtainStyledAttributes(attrs, R.styleable.MeterNumberPicker, defStyleAttrs, defStyleRes)
        init(context, attributesArray)
        attributesArray.recycle()
    }

    private fun initWithStyle(context: Context, @StyleRes styleId: Int) {
        val styleTypedArray = context.obtainStyledAttributes(styleId, R.styleable.MeterNumberPicker)
        init(context, styleTypedArray)
        styleTypedArray.recycle()
    }

    private fun init(context: Context, attributesArray: TypedArray?) {
        if (attributesArray == null) {
            textSize = spToPx(textSize)
            minWidth = dpToPx(minWidth.toFloat()).toInt()
            minHeight = dpToPx(minHeight.toFloat()).toInt()
            paddingHorizontal = dpToPx(paddingHorizontal.toFloat()).toInt()
            paddingVertical = dpToPx(paddingVertical.toFloat()).toInt()
        } else {
            minValue = attributesArray.getInt(R.styleable.MeterNumberPicker_mnp_min, minValue)
            maxValue = attributesArray.getInt(R.styleable.MeterNumberPicker_mnp_max, maxValue)
            value = attributesArray.getInt(R.styleable.MeterNumberPicker_mnp_value, value)
            textColor = attributesArray.getColor(R.styleable.MeterNumberPicker_mnp_textColor, textColor)
            textSize = attributesArray.getDimensionPixelSize(R.styleable.MeterNumberPicker_mnp_textSize, spToPx(textSize).toInt()).toFloat()
            order = attributesArray.getInt(R.styleable.MeterNumberPicker_mnp_order, order)
            typeface = Typeface.create(attributesArray.getString(R.styleable.MeterNumberPicker_mnp_typeface), Typeface.NORMAL)
            minWidth = attributesArray.getDimensionPixelSize(R.styleable.MeterNumberPicker_mnp_minWidth, dpToPx(minWidth.toFloat()).toInt())
            minHeight = attributesArray.getDimensionPixelSize(R.styleable.MeterNumberPicker_mnp_minHeight, dpToPx(minHeight.toFloat()).toInt())
            paddingHorizontal = attributesArray.getDimensionPixelSize(R.styleable.MeterNumberPicker_mnp_paddingHorizontal,
                dpToPx(paddingHorizontal.toFloat()).toInt())
            paddingVertical = attributesArray.getDimensionPixelSize(R.styleable.MeterNumberPicker_mnp_paddingVertical,
                dpToPx(paddingVertical.toFloat()).toInt())
        }
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        textPaint = paint
        setTextColorInt(textColor)
        setTextSizePx(textSize)
        setTypeface(typeface)
        setValue(value)
        setMaxValue(maxValue)
        setMinValue(minValue)
        val configuration = ViewConfiguration.get(context)
        minimumFlingVelocity = configuration.scaledMinimumFlingVelocity
        maximumFlingVelocity = configuration.scaledMaximumFlingVelocity / MAX_FLING_VELOCITY_ADJUSTMENT
        flingScroller = Scroller(context, null, true)
        adjustScroller = Scroller(context, DecelerateInterpolator(2.5f))
    }

    // =============================================================================================
    // -------------------------------------- MEASURING --------------------------------------------
    // =============================================================================================
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = measureWidth(widthMeasureSpec)
        val heightSize = measureHeight(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)
    }

    private fun measureWidth(widthMeasureSpec: Int): Int {
        val specMode = MeasureSpec.getMode(widthMeasureSpec)
        val specSize = MeasureSpec.getSize(widthMeasureSpec)
        return if (specMode == MeasureSpec.EXACTLY) specSize
        else minWidth.coerceAtLeast(calculateTextWidthWithInternalPadding()) + paddingLeft + paddingRight
    }

    private fun measureHeight(heightMeasureSpec: Int): Int {
        val specMode = MeasureSpec.getMode(heightMeasureSpec)
        val specSize = MeasureSpec.getSize(heightMeasureSpec)
        return if (specMode == MeasureSpec.EXACTLY) specSize
        else minHeight.coerceAtLeast(calculateTextHeightWithInternalPadding()) + paddingTop + paddingBottom
    }

    private fun calculateTextWidthWithInternalPadding() =
        calculateTextWidth() + paddingHorizontal * 2

    private fun calculateTextHeightWithInternalPadding() =
        calculateTextHeight() + paddingVertical * 2

    private fun calculateTextWidth(): Int {
        var maxDigitWidth = 0f
        for (i in 0..9) {
            val digitWidth = textPaint.measureText(formatNumberWithLocale(i))
            if (digitWidth > maxDigitWidth) {
                maxDigitWidth = digitWidth
            }
        }
        var numberOfDigits = 0
        var current = maxValue
        while (current > 0) {
            numberOfDigits++
            current /= 10
        }
        return (numberOfDigits * maxDigitWidth).toInt()
    }

    private fun calculateTextHeight(): Int {
        val bounds = Rect()
        textPaint.getTextBounds("0", 0, 1, bounds)
        return bounds.height().also { textHeight = it }
    }

    // =============================================================================================
    // -------------------------------------- DRAWING ----------------------------------------------
    // =============================================================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val measuredHeight = measuredHeight
        val x = ((right - left) / 2).toFloat()
        val y = ((bottom - top) / 2 + textHeight / 2).toFloat()
        val currentValueStart = (y + currentScrollOffset).toInt()
        val prevValueStart = currentValueStart - measuredHeight
        val nextValueStart = currentValueStart + measuredHeight
        canvas.drawText(getValue(currentValueOffset + 1).toString(), x, prevValueStart.toFloat(), textPaint)
        canvas.drawText(getValue(currentValueOffset).toString(), x, currentValueStart.toFloat(), textPaint)
        canvas.drawText(getValue(currentValueOffset - 1).toString(), x, nextValueStart.toFloat(), textPaint)
    }

    // =============================================================================================
    // ----------------------------------- TOUCH & SCROLL ------------------------------------------
    // =============================================================================================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("TAG", "event: downtime: ${event.downTime}, event time: ${event.eventTime}, action: ${event.action}, metastate: ${event.metaState}")

        if (!isEnabled) return false

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()

        velocityTracker!!.addMovement(event)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (!flingScroller!!.isFinished) flingScroller!!.forceFinished(true)
                if (!adjustScroller!!.isFinished) adjustScroller!!.forceFinished(true)
                lastDownEventY = event.y

                // Disallow ScrollView to intercept touch events.
                this.parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                lastDownOrMoveEventY = event.y
                val rawScrollOffset = (lastDownOrMoveEventY - lastDownEventY).toInt()
                calculateCurrentOffsets(rawScrollOffset, measuredHeight)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker!!.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                val initialVelocity = velocityTracker!!.yVelocity.toInt()
                if (abs(initialVelocity) > minimumFlingVelocity) fling(initialVelocity) else {
                    val rawScrollOffset = (lastDownOrMoveEventY - lastDownEventY).toInt()
                    val adjustedValueOffset = calculateAdjustedValueOffset(rawScrollOffset, measuredHeight)
                    calculateCurrentOffsets(rawScrollOffset, measuredHeight)
                    value = getValue(adjustedValueOffset)
                    adjust(measuredHeight, adjustedValueOffset)
                }
                invalidate()
                velocityTracker!!.recycle()
                velocityTracker = null

                // Allow ScrollView to intercept touch events.
                this.parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun computeScroll() {
        val scroller = when {
            flingScroller?.isFinished == false -> flingScroller!!
            adjustScroller?.isFinished == false -> adjustScroller!!
            else -> return
        }

        scroller.computeScrollOffset()
        val currentScrollerY = scroller.currY
        val diffScrollY = scrollerLastY - currentScrollerY
        currentScrollOffset -= diffScrollY
        scrollerLastY = currentScrollerY

        if (adjustScroller!!.isFinished) {
            if (flingScroller!!.isFinished) {
                if (currentScrollOffset != 0) {
                    val measuredHeight = measuredHeight
                    val adjustedValueOffset = calculateAdjustedValueOffset(measuredHeight)
                    value = getValue(adjustedValueOffset)
                    adjust(measuredHeight, adjustedValueOffset)
                }
            } else {
                val newScrollOffset = currentScrollOffset % measuredHeight
                if (newScrollOffset != currentScrollOffset) {
                    val numberOfValuesScrolled = (currentScrollOffset - newScrollOffset) / measuredHeight
                    currentValueOffset += numberOfValuesScrolled
                    currentScrollOffset = newScrollOffset
                }
            }
        }
        invalidate()
    }

    private fun calculateCurrentOffsets(rawScrollOffset: Int, measuredHeight: Int) {
        currentValueOffset = rawScrollOffset / measuredHeight
        currentScrollOffset = abs(rawScrollOffset) - abs(currentValueOffset) * measuredHeight
        currentScrollOffset *= if (rawScrollOffset < 0) -1 else 1
    }

    private fun calculateAdjustedValueOffset(rawScrollOffset: Int, measuredHeight: Int): Int {
        val currentValueOffset = rawScrollOffset.toDouble() / measuredHeight.toDouble()
        return (currentValueOffset + 0.5 * if (currentValueOffset < 0) -1.0 else 1.0).toInt()
    }

    /**
     * Calculating adjusted value offset based only on the current scroll offset
     *
     * @return currentValueOffset if no changes should be applied, currentValueOffset + 1 or currentValueOffset - 1
     */
    private fun calculateAdjustedValueOffset(measuredHeight: Int): Int {
        return if (abs(currentScrollOffset) < measuredHeight / 2) currentValueOffset else currentValueOffset + if (currentScrollOffset < 0) -1 else 1
    }

    private fun adjust(measuredHeight: Int, adjustedValueOffset: Int) {
        if (adjustedValueOffset != currentValueOffset) {
            if (currentScrollOffset < 0) {
                currentScrollOffset += measuredHeight
            } else {
                currentScrollOffset -= measuredHeight
            }
        }
        scrollerLastY = currentScrollOffset
        currentValueOffset = 0
        adjustScroller!!.startScroll(0, currentScrollOffset, 0, -currentScrollOffset, ADJUSTMENT_DURATION_MILLIS)
    }

    private fun fling(velocity: Int) {
        val startY = if (velocity > 0) 0 else Int.MAX_VALUE
        flingScroller!!.fling(0, startY, 0, velocity, 0, 0, 0, Int.MAX_VALUE)
        scrollerLastY = startY
    }

    // =============================================================================================
    // -------------------------------------- UTILS ------------------------------------------------
    // =============================================================================================
    private fun getValue(offset: Int): Int {
        val distance = maxValue - minValue + 1
        val result = getResult(offset, distance)
        return when {
            result < minValue -> result + distance
            result > maxValue -> result - distance
            else -> result
        }
    }

    private fun getResult(offset: Int, distance: Int): Int {
        val i = offset % distance
        return if (order == 0) value - i else value + i
    }

    private fun formatNumberWithLocale(value: Int) =
        String.format(Locale.getDefault(), "%d", value)

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density

    private fun spToPx(sp: Float) = sp * resources.displayMetrics.scaledDensity

    // =============================================================================================
    // --------------------------------- GETTERS & SETTERS -----------------------------------------
    // =============================================================================================
    fun setMinValue(minValue: Int) {
        require(minValue >= 0) { "minValue must be >= 0" }
        this.minValue = minValue
        if (value < minValue) {
            value = minValue
        }
        invalidate()
    }

    fun setMaxValue(maxValue: Int) {
        require(maxValue >= 0) { "maxValue must be >= 0" }
        this.maxValue = maxValue
        if (value > maxValue) {
            value = maxValue
        }
        invalidate()
    }

    fun setValue(value: Int) {
        require(value >= minValue) { "value must be >= $minValue but was $value" }
        require(value <= maxValue) { "value must be <= $maxValue but was $value" }
        this.value = value
        invalidate()
    }

    fun setTextColorInt(@ColorInt color: Int) {
        textPaint.color = color.also { textColor = it }
        invalidate()
    }

    fun setTextColorRes(@ColorRes colorRes: Int) {
        setTextColorInt(resources.getColor(colorRes))
    }

    fun setTextSizePx(size: Float) {
        textPaint.textSize = size.also { textSize = it }
        invalidate()
    }

    fun setTextSizeRes(@DimenRes textSizeRes: Int) {
        setTextSizePx(resources.getDimensionPixelSize(textSizeRes).toFloat())
    }

    fun setTypeface(typeface: Typeface?) {
        this.typeface = typeface ?: Typeface.DEFAULT
        textPaint.typeface = this.typeface
    }

    fun setTypeface(string: String?, style: Int) {
        if (TextUtils.isEmpty(string)) {
            return
        }
        setTypeface(Typeface.create(string, style))
    }

    fun setTypeface(string: String?) {
        setTypeface(string, Typeface.NORMAL)
    }

    fun setTypeface(@StringRes stringId: Int, style: Int) {
        setTypeface(resources.getString(stringId), style)
    }

    fun setTypeface(@StringRes stringId: Int) {
        setTypeface(stringId, Typeface.NORMAL)
    }

    fun setMinWidthPx(width: Int) {
        minWidth = width
        requestLayout()
    }

    fun setMinWidthRes(@DimenRes width: Int) {
        setMinWidthPx(resources.getDimensionPixelSize(width))
    }

    fun setMinHeightPx(height: Int) {
        minHeight = height
        requestLayout()
    }

    fun setMinHeightRes(@DimenRes height: Int) {
        setMinHeightPx(resources.getDimensionPixelSize(height))
    }

    fun setVerticalPaddingPx(padding: Int) {
        paddingVertical = padding
        requestLayout()
    }

    fun setVerticalPaddingRes(padding: Int) {
        setVerticalPaddingPx(resources.getDimensionPixelSize(padding))
    }

    fun setHorizontalPaddingPx(padding: Int) {
        paddingHorizontal = padding
        requestLayout()
    }

    fun setHorizontalPaddingRes(padding: Int) {
        setHorizontalPaddingPx(resources.getDimensionPixelSize(padding))
    }

    fun getMinValue(): Int {
        return minValue
    }

    fun getMaxValue(): Int {
        return maxValue
    }

    fun getValue(): Int {
        return value
    }

    fun getTypeface(): Typeface? {
        return typeface
    }

    fun attachWraparoundEventListener(listener: WraparoundEventListener) {
        wraparoundEventListener = listener
    }

    companion object {
        private const val DEFAULT_MIN_HEIGHT_DP = 20
        private const val DEFAULT_MIN_WIDTH_DP = 14
        private const val DEFAULT_MAX_VALUE = 9
        private const val DEFAULT_MIN_VALUE = 0
        private const val DEFAULT_VALUE = 0
        private const val DEFAULT_TEXT_COLOR = -0x1000000
        private const val DEFAULT_TEXT_SIZE_SP = 25f
        private const val DEFAULT_ORDER = 1

        /**
         * The default internal padding for the text (do not mix up with view paddings -
         * this is separate thing)
         */
        private const val DEFAULT_PADDING = 2
        private const val ADJUSTMENT_DURATION_MILLIS = 800

        /**
         * The coefficient by which to adjust (divide) the max fling velocity.
         */
        private const val MAX_FLING_VELOCITY_ADJUSTMENT = 6
    }
}

enum class WraparoundEvent {
    MAX_TO_MIN,
    MIN_TO_MAX
}

fun interface WraparoundEventListener {
    fun onWraparoundEvent(event: WraparoundEvent)
}
