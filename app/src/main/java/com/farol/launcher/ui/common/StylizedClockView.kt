/*
 * Copyright (C) 2026 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.farol.launcher.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.preferences.Entry
import com.farol.launcher.ui.Measurements
import com.farol.launcher.utils.Utils
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import androidx.core.graphics.withSave

open class StylizedClockView : View, SensorEventListener {
    companion object {
        const val BACKGROUND_ALPHA_KEY = "com.stario.BACKGROUND_ALPHA"
        const val IMPERIAL_KEY = "com.stario.IMPERIAL"
        private const val MIN_SCALING_RATIO_THRESHOLD = 0.2f
        private const val LYING_ON_TABLE_THRESHOLD = 8.5f
        private const val TILT_DEADZONE = 1.2f
        private const val TEST_HOUR_SIZE = 100f
        private const val BASE_GAP = 5f
    }

    private lateinit var pillBackgroundRect: RectF
    private lateinit var amContainerRect: RectF
    private lateinit var pmContainerRect: RectF
    private lateinit var contentRect: RectF
    private lateinit var backgroundPaint: Paint
    private lateinit var outlinePaint: Paint
    private lateinit var minutePaint: Paint
    private lateinit var pillBgPaint: Paint
    private lateinit var pillFgPaint: Paint
    private lateinit var amPmPaint: Paint
    private lateinit var hourPaint: Paint
    private var backgroundColor = 0
    private var pillBgColor = 0
    private var containerRadius = 0f
    private var minuteDrawX = 0f
    private var minuteDrawY = 0f
    private var hourDrawX = 0f
    private var hourDrawY = 0f
    private var amTextX = 0f
    private var amTextY = 0f
    private var pmTextX = 0f
    private var pmTextY = 0f
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gravityAngle = 0f
    private lateinit var preferences: SharedPreferences
    private lateinit var calendar: Calendar
    private lateinit var farol: Farol

    constructor(context: Context) : super(context) { init(context, null) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(context, attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(context, attrs) }

    private fun init(context: Context, attrs: AttributeSet?) {
        farol = context.applicationContext as Farol
        preferences = farol.getSharedPreferences(Entry.CLOCK)
        calendar = Calendar.getInstance()
        contentRect = RectF()
        amContainerRect = RectF()
        pmContainerRect = RectF()
        pillBackgroundRect = RectF()
        var textColor = Color.rgb(239, 223, 219)
        var outlineColor = Color.rgb(55, 46, 44)
        var pillFgColor = Color.rgb(249, 183, 165)
        backgroundColor = Color.rgb(34, 26, 24)
        pillBgColor = Color.rgb(93, 64, 56)
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.StylizedClockView)
            try {
                textColor = array.getColor(R.styleable.StylizedClockView_clockTextColor, textColor)
                outlineColor = array.getColor(R.styleable.StylizedClockView_clockOutlineColor, outlineColor)
                pillFgColor = array.getColor(R.styleable.StylizedClockView_clockPillForegroundColor, pillFgColor)
                backgroundColor = array.getColor(R.styleable.StylizedClockView_clockBackgroundColor, backgroundColor)
                pillBgColor = array.getColor(R.styleable.StylizedClockView_clockPillBackgroundColor, pillBgColor)
            } finally {
                array.recycle()
            }
        }
        var clockfaceTypeface: Typeface?
        var dmSansTypeface: Typeface?
        try {
            clockfaceTypeface = ResourcesCompat.getFont(context, R.font.stario_clockface_variable)
            dmSansTypeface = ResourcesCompat.getFont(context, R.font.dm_sans_black)
        } catch (_: Exception) {
            clockfaceTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            dmSansTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = backgroundColor }
        outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = outlineColor }
        hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = clockfaceTypeface }
        minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = clockfaceTypeface }
        amPmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = dmSansTypeface; textAlign = Paint.Align.CENTER }
        pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor; style = Paint.Style.FILL }
        pillFgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillFgColor; style = Paint.Style.FILL }
        gravityAngle = 0f
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GRAVITY) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val flatAmount = min(1f, max(0f, abs(z) - LYING_ON_TABLE_THRESHOLD))
        @Suppress("DEPRECATION")
        val rotation = (farol.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        var adjustedX = x
        var adjustedY = y
        when (rotation) {
            Surface.ROTATION_0 -> { adjustedX = -x; adjustedY = y }
            Surface.ROTATION_90 -> { adjustedX = y; adjustedY = x }
            Surface.ROTATION_180 -> { adjustedX = x; adjustedY = -y }
            Surface.ROTATION_270 -> { adjustedX = -y; adjustedY = -x }
        }
        var rawAngle = (Math.toDegrees(Math.atan2(-adjustedX.toDouble(), adjustedY.toDouble())).toFloat() + 360f) % 360f
        if (z < 0) rawAngle += 180f
        if (sqrt(x * x + y * y) < TILT_DEADZONE) rawAngle = gravityAngle
        gravityAngle += 0.08f * ((getDegreeStrengthBias(rawAngle, flatAmount) - gravityAngle + 540f) % 360f - 180f)
        invalidate()
    }

    private fun getDegreeStrengthBias(angle: Float, factor: Float): Float {
        val delta = ((-angle + 540f) % 360f) - 180f
        return (angle + delta * factor + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)
        calculateLayout(width, height)
    }

    private fun calculateLayout(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val isWideMode = viewWidth >= viewHeight * 1.8f
        hourPaint.textScaleX = 1f
        minutePaint.textScaleX = 1f
        amPmPaint.textScaleX = 1f
        hourPaint.textSize = TEST_HOUR_SIZE
        hourPaint.fontVariationSettings = "'VTCL' 100"
        val hourBounds = Rect()
        hourPaint.getTextBounds("00", 0, 2, hourBounds)
        val hourHeight = hourBounds.height().toFloat()
        val hourWidth = hourBounds.width().toFloat()
        var testAmPmSize = TEST_HOUR_SIZE * 0.11f
        amPmPaint.textSize = testAmPmSize
        val amBounds = Rect()
        amPmPaint.getTextBounds("AM", 0, 2, amBounds)
        val minBounds = Rect()
        val containerHeight: Float
        val containerWidth: Float
        var testMinuteSize: Float
        val minWidth: Float
        val totalMaxWidth: Float
        val totalMaxHeight: Float
        if (isWideMode) {
            minutePaint.fontVariationSettings = "'VTCL' 0"
            testMinuteSize = TEST_HOUR_SIZE
            minutePaint.textSize = testMinuteSize
            minutePaint.getTextBounds("00", 0, 2, minBounds)
            minWidth = minBounds.width().toFloat()
            val wideStackHeight = hourHeight * 0.5f
            containerHeight = wideStackHeight / 2f
            val desiredTextHeight = containerHeight * 0.55f
            if (amBounds.height() > 0) testAmPmSize *= desiredTextHeight / amBounds.height()
            amPmPaint.textSize = testAmPmSize
            amPmPaint.getTextBounds("AM", 0, 2, amBounds)
            containerWidth = containerHeight * 2.3f
            totalMaxWidth = hourWidth + minWidth + containerWidth + BASE_GAP * 6
            totalMaxHeight = max(hourHeight, wideStackHeight) + BASE_GAP * 4
        } else {
            val amPmPaddingY = testAmPmSize * 0.2f
            containerHeight = amBounds.height() + amPmPaddingY * 2
            val stackedContainersHeight = containerHeight * 2
            val targetMinHeight = hourHeight - stackedContainersHeight - BASE_GAP * 1.5f
            testMinuteSize = TEST_HOUR_SIZE * 0.5f
            minutePaint.fontVariationSettings = "'VTCL' 0"
            minutePaint.textSize = testMinuteSize
            minutePaint.getTextBounds("00", 0, 2, minBounds)
            val desiredBaseMinHeight = targetMinHeight / 1.15f
            if (minBounds.height() > 0) testMinuteSize *= desiredBaseMinHeight / minBounds.height()
            minutePaint.textSize = testMinuteSize
            minutePaint.getTextBounds("00", 0, 2, minBounds)
            var vtcl = 0f
            if (minBounds.height() > 0) {
                val multiplier = targetMinHeight / minBounds.height()
                vtcl = max(0f, min(100f, (multiplier - 1f) / 0.3356f * 100f))
            }
            minutePaint.fontVariationSettings = "'VTCL' $vtcl"
            minutePaint.getTextBounds("00", 0, 2, minBounds)
            minWidth = minutePaint.measureText("00")
            containerWidth = minWidth
            totalMaxWidth = hourWidth + BASE_GAP + minWidth + BASE_GAP * 4
            totalMaxHeight = hourHeight + BASE_GAP * 4
        }
        val scaleX = viewWidth / totalMaxWidth
        val scaleY = viewHeight / totalMaxHeight
        val ratioXtoY = scaleX / scaleY
        val backgroundAlpha = preferences.getInt(BACKGROUND_ALPHA_KEY, 0)
        if (abs(ratioXtoY - 1f) > MIN_SCALING_RATIO_THRESHOLD || backgroundAlpha == 0) {
            val scale = min(viewWidth / totalMaxWidth, viewHeight / totalMaxHeight)
            hourPaint.textSize = TEST_HOUR_SIZE * scale
            minutePaint.textSize = testMinuteSize * scale
            amPmPaint.textSize = testAmPmSize * scale
            val finalGap = BASE_GAP * scale
            val finalPadding = BASE_GAP * 2f * scale
            val finalContainerWidth = containerWidth * scale
            val finalContainerHeight = containerHeight * scale
            containerRadius = finalContainerHeight / 2f
            hourPaint.getTextBounds("00", 0, 2, hourBounds)
            minutePaint.getTextBounds("00", 0, 2, minBounds)
            amPmPaint.getTextBounds("AM", 0, 2, amBounds)
            val finalContentWidth = totalMaxWidth * scale - finalPadding * 2
            val finalContentHeight = totalMaxHeight * scale - finalPadding * 2
            val startX = (viewWidth - finalContentWidth) / 2f
            val startY = (viewHeight - finalContentHeight) / 2f
            contentRect.set(startX - finalPadding, startY - finalPadding,
                startX + finalContentWidth + finalPadding, startY + finalContentHeight + finalPadding)
            hourDrawX = startX
            hourDrawY = startY - hourBounds.top
            val rightColX = hourDrawX + hourPaint.measureText("00") + finalGap
            minuteDrawX = rightColX
            minuteDrawY = startY - minBounds.top
            if (isWideMode) {
                val amPmX = minuteDrawX + minutePaint.measureText("00") + finalGap
                val stackTop = startY + (finalContentHeight - finalContainerHeight * 2) / 2f
                amContainerRect.set(amPmX, stackTop, amPmX + finalContainerWidth, stackTop + finalContainerHeight)
                pmContainerRect.set(amPmX, amContainerRect.bottom, amPmX + finalContainerWidth, amContainerRect.bottom + finalContainerHeight)
            } else {
                val stackBottom = startY + hourBounds.height()
                val amPmTop = stackBottom - finalContainerHeight * 2
                amContainerRect.set(rightColX, amPmTop, rightColX + finalContainerWidth, amPmTop + finalContainerHeight)
                pmContainerRect.set(rightColX, amContainerRect.bottom, rightColX + finalContainerWidth, amContainerRect.bottom + finalContainerHeight)
            }
        } else {
            hourPaint.textSize = TEST_HOUR_SIZE * scaleY
            hourPaint.textScaleX = ratioXtoY
            minutePaint.textSize = testMinuteSize * scaleY
            minutePaint.textScaleX = ratioXtoY
            amPmPaint.textSize = testAmPmSize * scaleY
            amPmPaint.textScaleX = ratioXtoY
            val finalGapX = BASE_GAP * scaleX
            val finalPaddingX = BASE_GAP * 2f * scaleX
            val finalPaddingY = BASE_GAP * 2f * scaleY
            val finalContainerWidth = containerWidth * scaleX
            val finalContainerHeight = containerHeight * scaleY
            containerRadius = min(finalContainerWidth, finalContainerHeight) / 2f
            hourPaint.getTextBounds("00", 0, 2, hourBounds)
            minutePaint.getTextBounds("00", 0, 2, minBounds)
            amPmPaint.getTextBounds("AM", 0, 2, amBounds)
            val scaledHourWidth = hourPaint.measureText("00")
            val scaledMinWidth = minutePaint.measureText("00")
            contentRect.set(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            hourDrawX = finalPaddingX
            hourDrawY = finalPaddingY - hourBounds.top
            val rightColX = hourDrawX + scaledHourWidth + finalGapX
            minuteDrawX = rightColX
            minuteDrawY = finalPaddingY - minBounds.top
            if (isWideMode) {
                val amPmX = minuteDrawX + scaledMinWidth + finalGapX
                val stackTop = finalPaddingY + (totalMaxHeight * scaleY - finalPaddingY * 2 - finalContainerHeight * 2) / 2f
                amContainerRect.set(amPmX, stackTop, amPmX + finalContainerWidth, stackTop + finalContainerHeight)
                pmContainerRect.set(amPmX, amContainerRect.bottom, amPmX + finalContainerWidth, amContainerRect.bottom + finalContainerHeight)
            } else {
                val stackBottom = finalPaddingY + hourBounds.height()
                val amPmTop = stackBottom - finalContainerHeight * 2
                amContainerRect.set(rightColX, amPmTop, rightColX + finalContainerWidth, amPmTop + finalContainerHeight)
                pmContainerRect.set(rightColX, amContainerRect.bottom, rightColX + finalContainerWidth, amContainerRect.bottom + finalContainerHeight)
            }
        }
        amTextX = amContainerRect.centerX()
        amTextY = amContainerRect.centerY() - (amBounds.top + amBounds.bottom) / 2f
        pmTextX = pmContainerRect.centerX()
        pmTextY = pmContainerRect.centerY() - (amBounds.top + amBounds.bottom) / 2f
        outlinePaint.strokeWidth = Measurements.dpToPx(1.5f).toFloat()
        pillBackgroundRect.set(amContainerRect.left, amContainerRect.top, amContainerRect.right, pmContainerRect.bottom)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        if (!::hourPaint.isInitialized) return
        val cornerRadius = min(contentRect.width(), contentRect.height()) * 0.1f
        val halfStroke = outlinePaint.strokeWidth / 2f
        val bgRect = RectF(contentRect.left + halfStroke, contentRect.top + halfStroke,
            contentRect.right - halfStroke, contentRect.bottom - halfStroke)
        val alpha = preferences.getInt(BACKGROUND_ALPHA_KEY, 0)
        backgroundPaint.alpha = alpha
        outlinePaint.alpha = alpha
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, outlinePaint)
        calendar.timeInMillis = System.currentTimeMillis()
        val is24Hour = !preferences.getBoolean(IMPERIAL_KEY, Utils.isSystemUsingImperial(farol))
        var hour = if (is24Hour) calendar.get(Calendar.HOUR_OF_DAY) else calendar.get(Calendar.HOUR)
        if (!is24Hour && hour == 0) hour = 12
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val millisecond = calendar.get(Calendar.MILLISECOND)
        val hourStr = String.format(Locale.US, "%02d", hour)
        val minStr = String.format(Locale.US, "%02d", minute)
        outlinePaint.alpha = 120
        canvas.drawText(hourStr, hourDrawX, hourDrawY, hourPaint)
        outlinePaint.textSize = hourPaint.textSize
        outlinePaint.typeface = hourPaint.typeface
        outlinePaint.textScaleX = hourPaint.textScaleX
        canvas.drawText(hourStr, hourDrawX, hourDrawY, outlinePaint)
        canvas.drawText(minStr, minuteDrawX, minuteDrawY, minutePaint)
        outlinePaint.textSize = minutePaint.textSize
        outlinePaint.typeface = minutePaint.typeface
        outlinePaint.textScaleX = minutePaint.textScaleX
        canvas.drawText(minStr, minuteDrawX, minuteDrawY, outlinePaint)
        if (is24Hour) {
            val centerX = pillBackgroundRect.centerX()
            val centerY = pillBackgroundRect.centerY()
            val width = pillBackgroundRect.width()
            val height = pillBackgroundRect.height()
            val diagonal = sqrt(width * width + height * height)
            val radius = diagonal / 2f
            pillBgPaint.alpha = 255
            canvas.drawRoundRect(pillBackgroundRect, containerRadius, containerRadius, pillBgPaint)
            canvas.withSave {
                val clipPath = Path()
                val padding = containerRadius / 3f
                clipPath.addRoundRect(pillBackgroundRect.left + padding, pillBackgroundRect.top + padding,
                    pillBackgroundRect.right - padding, pillBackgroundRect.bottom - padding,
                    containerRadius - padding, containerRadius - padding, Path.Direction.CW)
                clipPath(clipPath)
                translate(centerX, centerY)
                rotate(gravityAngle)
                val progress = (second * 1000f + millisecond) / 60000f
                val liquidLevel = radius - diagonal * progress
                drawRect(RectF(-radius, liquidLevel, radius, radius + 100f), pillFgPaint)
            }
        } else {
            val isAm = calendar.get(Calendar.AM_PM) == Calendar.AM
            pillBgPaint.alpha = if (isAm) 255 else 80
            amPmPaint.alpha = if (isAm) 255 else 80
            canvas.drawRoundRect(amContainerRect, containerRadius, containerRadius, pillBgPaint)
            if (!isAm) canvas.drawRoundRect(amContainerRect, containerRadius, containerRadius, outlinePaint)
            canvas.drawText("AM", amTextX, amTextY, amPmPaint)
            pillBgPaint.alpha = if (!isAm) 255 else 80
            amPmPaint.alpha = if (!isAm) 255 else 80
            canvas.drawRoundRect(pmContainerRect, containerRadius, containerRadius, pillBgPaint)
            if (isAm) canvas.drawRoundRect(pmContainerRect, containerRadius, containerRadius, outlinePaint)
            canvas.drawText("PM", pmTextX, pmTextY, amPmPaint)
        }
        postInvalidateOnAnimation()
    }
}
