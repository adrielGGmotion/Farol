/*
 * Copyright (C) 2025 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.stario.launcher.ui.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.stario.launcher.R
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.preferences.Entry
import com.stario.launcher.ui.Measurements
import com.stario.launcher.utils.Utils
import com.stario.launcher.utils.objects.ObjectDelegate
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation

open class AdaptiveIconView : View {
    private lateinit var pathAlgorithm: ObjectDelegate<PathCornerTreatmentAlgorithm>
    private var profileStateBinding: ProfileStateBinding? = null
    private lateinit var grayscaleFilter: ColorMatrixColorFilter
    private lateinit var iconDelegate: ObjectDelegate<Drawable?>
    private lateinit var preferences: SharedPreferences
    private lateinit var radius: ObjectDelegate<Float>
    private var applyBadge = false
    private var badge: Drawable? = null
    private var sizeRestricted = false
    private var looseClipping = false
    private lateinit var shadowPaint: Paint
    private var grayscale: Boolean? = null
    private var paused = false
    private lateinit var path: Path

    private val iconPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            CORNER_RADIUS_ENTRY -> radius.setValue(preferences.getFloat(CORNER_RADIUS_ENTRY, DEFAULT_CORNER_RADIUS))
            PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY -> pathAlgorithm.setValue(
                PathCornerTreatmentAlgorithm.fromIdentifier(
                    preferences.getInt(
                        PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                        PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
                    )
                )
            )
        }
    }

    private val pathAlgorithmValue: PathCornerTreatmentAlgorithm
        get() = pathAlgorithm.getValue()
            ?: PathCornerTreatmentAlgorithm.fromIdentifier(
                PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
            )

    private val radiusValue: Float
        get() = radius.getValue() ?: DEFAULT_CORNER_RADIUS

    constructor(context: Context) : super(context) {
        initialize(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        preferences = context.getApplicationContext()
            .getSharedPreferences(Entry.ICONS.toString(), Context.MODE_PRIVATE)
        if (attrs != null) {
            context.getApplicationContext()
                .withStyledAttributes(attrs, R.styleable.AdaptiveIconView) {

                sizeRestricted = getBoolean(R.styleable.AdaptiveIconView_sizeRestricted, true)
                looseClipping = getBoolean(R.styleable.AdaptiveIconView_looseClipping, true)

            }
        } else {
            sizeRestricted = true
            looseClipping = true
        }

        path = Path()
        iconDelegate = ObjectDelegate<Drawable?> { _ -> invalidate() }
        pathAlgorithm = ObjectDelegate(
            PathCornerTreatmentAlgorithm.fromIdentifier(
                preferences.getInt(
                    PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                    PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
                )
            )
        ) { _ -> requestLayout() }
        radius = ObjectDelegate(
            preferences.getFloat(CORNER_RADIUS_ENTRY, DEFAULT_CORNER_RADIUS)
        ) { _ -> requestLayout() }
        badge = ResourcesCompat.getDrawable(
            context.getResources(),
            R.drawable.ic_alternate_badge,
            context.getTheme()
        )

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        grayscaleFilter = ColorMatrixColorFilter(matrix)
        profileStateBinding = null
        paused = false

        shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.setColor(Color.TRANSPARENT)

        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        preferences.registerOnSharedPreferenceChangeListener(iconPreferencesListener)

        val binding = profileStateBinding
        if (binding != null) {
            paused = binding.isPaused(getContext())
            applyBadge = binding.shouldApplyManagedBadge()

            binding.register(getContext())
        }

        val currentPathCornerTreatmentAlgorithm = PathCornerTreatmentAlgorithm.fromIdentifier(
            preferences.getInt(
                PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
            )
        )
        if (pathAlgorithmValue != currentPathCornerTreatmentAlgorithm) {
            pathAlgorithm.setValue(currentPathCornerTreatmentAlgorithm)
        }

        val currentRadius = preferences.getFloat(CORNER_RADIUS_ENTRY, DEFAULT_CORNER_RADIUS)
        if (radiusValue != currentRadius) {
            radius.setValue(currentRadius)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        preferences.unregisterOnSharedPreferenceChangeListener(iconPreferencesListener)

        val binding = profileStateBinding
        if (binding != null) {
            binding.unregister(getContext())
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxIconSize = getMaxIconSize()
        var widthSpec = widthMeasureSpec
        var heightSpec = heightMeasureSpec
        var measuredWidth = MeasureSpec.getSize(widthSpec)
        val measuredHeight = MeasureSpec.getSize(heightSpec)

        if (measuredHeight > 0 && measuredWidth > 0) {
            if (measuredWidth != measuredHeight || (sizeRestricted && measuredHeight > maxIconSize)) {
                var size = minOf(measuredWidth, measuredHeight)

                if (sizeRestricted) {
                    size = minOf(maxIconSize, size)
                }

                val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.getMode(widthSpec))
                widthSpec = spec
                heightSpec = spec
                measuredWidth = size
            }

            setClipBounds(Rect(0, 0, measuredWidth, measuredWidth))
        }

        super.onMeasure(widthSpec, heightSpec)
    }

    private fun updateClipPath(width: Int, height: Int) {
        if (pathAlgorithmValue === PathCornerTreatmentAlgorithm.SQUIRCLE) {
            createClipPathSquircle(width, height)
        } else {
            createClipPathRegular(width, height)
        }
    }

    private fun createClipPathSquircle(width: Int, height: Int) {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val arc = minOf(halfWidth, halfHeight) * (0.45f - (1f - radiusValue) * 0.45f)

        path.reset()
        path.moveTo(0f, halfHeight)

        path.cubicTo(0f, arc, arc, 0f, halfWidth, 0f)
        path.cubicTo(width - arc, 0f, width.toFloat(), arc, width.toFloat(), halfHeight)
        path.cubicTo(width.toFloat(), height - arc, width - arc, height.toFloat(), halfWidth, height.toFloat())
        path.cubicTo(arc, height.toFloat(), 0f, height - arc, 0f, halfHeight)

        path.close()
    }

    private fun createClipPathRegular(width: Int, height: Int) {
        val cornerRadius = radiusValue * width / 2f

        path.reset()
        path.addRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )
        path.close()
    }

    open fun getIcon(): Drawable? = iconDelegate.getValue()

    open fun setApplication(application: LauncherApplication?) {
        if (application != null) {
            setIcon(application.getIcon())

            profileStateBinding = ProfileStateBinding(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        paused = !intent.getBooleanExtra(ProfileManager.PROFILE_AVAILABLE_EXTRA, true)

                        post { invalidate() }
                    }
                },
                IntentFilter(
                    ProfileManager.getProfileAvailabilityIntentAction(application.getProfile())
                ),
                application.getProfile()
            )

            val binding = profileStateBinding!!
            paused = binding.isPaused(getContext())
            applyBadge = binding.shouldApplyManagedBadge()

            binding.register(getContext())
        } else {
            setIcon(null)
        }
    }

    open fun setIcon(icon: Drawable?) {
        val binding = profileStateBinding
        if (binding != null) {
            binding.unregister(getContext())
            profileStateBinding = null
        }

        if (icon != null && icon.getConstantState() != null) {
            val constantStateIcon = icon.getConstantState()!!.newDrawable()

            constantStateIcon.setBounds(
                MAX_SHADOW_SIZE,
                MAX_SHADOW_SIZE,
                getMeasuredWidth() - MAX_SHADOW_SIZE,
                getMeasuredHeight() - MAX_SHADOW_SIZE
            )

            iconDelegate.setValue(constantStateIcon)
        } else {
            iconDelegate.setValue(null)
        }

        paused = false
        applyBadge = false

        post { requestLayout() }
    }

    override fun setClipBounds(clipBounds: Rect?) {
        if (iconDelegate.getValue() != null) {
            val size = clipBounds!!.width()
            val inset = size - MAX_SHADOW_SIZE * 2

            val icon = iconDelegate.getValue()
            if (icon != null) {
                icon.setBounds(
                    MAX_SHADOW_SIZE,
                    MAX_SHADOW_SIZE,
                    inset + MAX_SHADOW_SIZE,
                    inset + MAX_SHADOW_SIZE
                )
            }

            updateClipPath(inset, inset)
            shadowPaint.setShadowLayer(
                size.toFloat() / getMaxIconSize() * MAX_SHADOW_SIZE * 0.75f,
                0f,
                0f,
                Color.argb(100, 0, 0, 0)
            )
        }

        super.setClipBounds(clipBounds)
        invalidate()
    }

    open fun setLooseClipping(value: Boolean) {
        looseClipping = value
    }

    open fun setSizeRestricted(value: Boolean) {
        sizeRestricted = value
    }

    /** If set, will override default application paused grayscale state. */
    open fun setGrayscale(value: Boolean) {
        grayscale = value

        post { invalidate() }
    }

    override fun draw(canvas: Canvas) {
        canvas.withTranslation(MAX_SHADOW_SIZE.toFloat(), MAX_SHADOW_SIZE.toFloat()) {
            if (!looseClipping || iconDelegate.getValue() is AdaptiveIconDrawable) {
                canvas.drawPath(path, shadowPaint)
                canvas.withClip(path) {
                    super.draw(canvas)
                }
            } else {
                super.draw(canvas)
            }

            if (applyBadge && iconDelegate.getValue() != null) {
                val iconBounds = iconDelegate.getValue()!!.getBounds()

                val intrinsicW = badge!!.getIntrinsicWidth()
                val intrinsicH = badge!!.getIntrinsicHeight()
                badge!!.setBounds(0, 0, intrinsicW, intrinsicH)

                val targetSize = iconBounds.width() * BADGE_SIZE
                val scale = targetSize / intrinsicW

                canvas.withTranslation(iconBounds.right - targetSize, iconBounds.bottom - targetSize) {
                    canvas.scale(scale, scale)
                    badge!!.draw(canvas)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (iconDelegate.getValue() != null) {
            val icon = iconDelegate.getValue()!!

            if (icon is AdaptiveIconDrawable) {
                val background = icon.getBackground()
                val foreground = icon.getForeground()

                if (background != null) {
                    if (paused || grayscale == true) {
                        background.setColorFilter(grayscaleFilter)
                    }

                    background.draw(canvas)
                    background.setColorFilter(null)
                }

                if (foreground != null) {
                    if (paused || grayscale == true) {
                        foreground.setColorFilter(grayscaleFilter)
                    }

                    foreground.draw(canvas)
                    foreground.setColorFilter(null)
                }
            } else {
                if (paused || grayscale == true) {
                    icon.setColorFilter(grayscaleFilter)
                }

                icon.draw(canvas)
                icon.setColorFilter(null)
            }
        }
    }

    open class ProfileStateBinding internal constructor(
        receiverValue: BroadcastReceiver,
        filterValue: IntentFilter,
        private val handle: UserHandle
    ) {
        @JvmField
        val receiver: BroadcastReceiver = receiverValue

        @JvmField
        val filter: IntentFilter = filterValue

        internal fun isPaused(context: Context): Boolean {
            return !Utils.isProfileAvailable(context, handle)
        }

        internal fun shouldApplyManagedBadge(): Boolean {
            return !Utils.isMainProfile(handle)
        }

        internal fun register(context: Context) {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        internal fun unregister(context: Context) {
            context.applicationContext.unregisterReceiver(receiver)
        }
    }

    companion object {
        const val CORNER_RADIUS_ENTRY = "com.stario.CORNER_RADIUS"

        const val DEFAULT_CORNER_RADIUS = 1f

        const val MAX_SCALE = 1.12f

        private const val MAX_SHADOW_SIZE = 5
        private const val BADGE_SIZE = 0.4f

        @JvmStatic
        fun getMaxIconSize(): Int {
            return Measurements.dpToPx(60f)
        }
    }
}
