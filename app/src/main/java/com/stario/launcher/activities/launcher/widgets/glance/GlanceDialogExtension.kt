/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.stario.launcher.activities.launcher.widgets.glance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.PathInterpolator
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.glance.GlanceConstraintLayout
import com.stario.launcher.ui.dialogs.PersistentFullscreenDialog
import com.stario.launcher.ui.utils.HomeWatcher
import com.stario.launcher.ui.utils.animation.Animation
import java.util.concurrent.CopyOnWriteArrayList

abstract class GlanceDialogExtension protected constructor() : DialogFragment(), GlanceExtension {
    private val layoutChangeListener: View.OnLayoutChangeListener
    private val listeners: MutableList<TransitionListener> = CopyOnWriteArrayList()

    protected lateinit var activity: ThemedActivity

    protected fun isActivityInitialized(): Boolean = ::activity.isInitialized

    private var localDialog: PersistentFullscreenDialog? = null
    private lateinit var container: GlanceConstraintLayout
    private var targetTranslationY = 0f
    private lateinit var homeWatcher: HomeWatcher
    private var isHiding = false
    private lateinit var glance: Glance

    init {
        layoutChangeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                recalculatePosition()
            }
        }
    }

    override fun getDialog(): PersistentFullscreenDialog? = localDialog

    fun attach(glance: Glance) {
        this.glance = glance
        show(glance.getActivity().supportFragmentManager, tag)
        glance.attachViewExtension(getViewExtensionPreview()) { show() }
    }

    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        activity = context
        homeWatcher = HomeWatcher(activity)
        homeWatcher.setOnHomePressedListener(this::hide)
        homeWatcher.startWatch()

        super.onAttach(context as Context)
    }

    override fun onDetach() {
        homeWatcher.stopWatch()
        super.onDetach()
    }

    override fun dismiss() {
        hide()
    }

    override fun onStop() {
        hide(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            dismissAllowingStateLoss()
        }
        super.onCreate(null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        localDialog = PersistentFullscreenDialog(activity, theme, true)
        localDialog!!.setOnBackPressed {
            hide()
            false
        }
        return localDialog!!
    }

    override fun onCreateView(inflater: LayoutInflater, rootContainer: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = ConstraintLayout(activity)
        root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        container = inflateExpanded(inflater, root)
        container.addOnLayoutChangeListener(layoutChangeListener)
        root.addView(container)

        updateScalingInternal(0f)
        root.setOnClickListener { hide() }

        return root
    }

    override fun onDestroyView() {
        if (::container.isInitialized) {
            container.removeOnLayoutChangeListener(layoutChangeListener)
        }
        super.onDestroyView()
    }

    private fun recalculatePosition() {
        val anchor = glance.getRootView()
        val dialog = localDialog

        if (anchor != null && dialog != null && dialog.isShowing) {
            val location = IntArray(2)
            anchor.getLocationInWindow(location)
            updateLayout(location, anchor.width, anchor.height)

            if (!isHiding && container.scaleY == 1f) {
                container.translationY = targetTranslationY
            }
        }
    }

    fun updateLayout(location: IntArray, width: Int, height: Int) {
        val params = container.layoutParams as ConstraintLayout.LayoutParams
        val window: Window = localDialog?.window ?: return
        val decorView = window.decorView
        val screenHeight = decorView.measuredHeight
        val expandedHeight = container.measuredHeight
        val sysUIHeight = Measurements.getSysUIHeight()
        val navHeight = Measurements.getNavHeight()
        val safeAreaBottom = screenHeight - navHeight

        container.setMaxRadius(height / 2f)
        params.width = width
        targetTranslationY = 0f

        var gravity = Gravity.BOTTOM
        if (expandedHeight > location[1] - sysUIHeight) {
            gravity = Gravity.TOP
        }

        if (gravity == Gravity.BOTTOM) {
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.UNSET
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomMargin = screenHeight - location[1] - height
            params.leftMargin = location[0]
            container.pivotY = expandedHeight.toFloat()

            val topEdge = location[1] + height - expandedHeight
            if (topEdge < sysUIHeight) {
                targetTranslationY = (sysUIHeight - topEdge).toFloat()
            }
        } else {
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.topMargin = location[1]
            params.leftMargin = location[0]
            container.pivotY = 0f

            val bottomEdge = location[1] + expandedHeight
            if (bottomEdge > safeAreaBottom) {
                targetTranslationY = -(bottomEdge - safeAreaBottom).toFloat()
            }
        }

        container.layoutParams = params
    }

    protected open fun show() {
        val dialog = localDialog
        if (dialog != null && isEnabled() && !dialog.isShowing && dialog.showDialog()) {
            Vibrations.getInstance().vibrate()
            isHiding = false

            container.post(object : Runnable {
                override fun run() {
                    if (isHiding || !isAdded) {
                        return
                    }

                    recalculatePosition()
                    val scale = glance.getHeight() / container.measuredHeight

                    if (scale >= 0 && !scale.isInfinite()) {
                        container.scaleY = scale
                        container.translationY = 0f
                        container.post {
                            container.animate()
                                .scaleY(1f)
                                .translationY(targetTranslationY)
                                .setInterpolator(PathInterpolator(X1, Y1, X2, Y2))
                                .setDuration(Animation.LONG.getDuration().toLong())
                                .setUpdateListener { animation -> updateScalingInternal(animation.animatedFraction) }
                                .setListener(object : AnimatorListenerAdapter() {
                                    private var canceled = false

                                    override fun onAnimationCancel(animation: Animator) {
                                        canceled = true
                                    }

                                    override fun onAnimationEnd(animation: Animator) {
                                        if (!canceled) {
                                            updateScalingInternal(1f)
                                        }
                                    }
                                })
                        }
                    } else {
                        container.post(this)
                    }
                }
            })

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }

    protected fun hide(animate: Boolean) {
        val dialog = localDialog
        if (dialog != null && dialog.isShowing) {
            if (animate) {
                hide()
            } else {
                updateScalingInternal(0f)
                container.post { dialog.hide() }
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    protected fun hide() {
        hideInternal(false)
    }

    protected fun urgentHide() {
        hideInternal(true)
    }

    protected fun isShowing(): Boolean = localDialog?.isShowing == true

    private fun hideInternal(force: Boolean) {
        val dialog = localDialog ?: return
        if (!isShowing()) {
            return
        }

        isHiding = true

        if (container.scaleY == 1f || force) {
            val targetScale = glance.getHeight() / container.measuredHeight
            container.animate()
                .scaleY(targetScale)
                .translationY(0f)
                .setInterpolator(PathInterpolator(X1, Y1, X2, Y2))
                .setDuration(Animation.MEDIUM.getDuration().toLong())
                .setUpdateListener { animation ->
                    val fraction = 1f - (animation.animatedValue as Float)
                    updateScalingInternal(fraction)
                }
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        updateScalingInternal(0f)
                        container.post { dialog.hide() }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        updateScalingInternal(0f)
                        container.post { dialog.hide() }
                    }
                })

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun updateScalingInternal(@FloatRange(from = 0.0, to = 1.0) fraction: Float) {
        val dialog = localDialog ?: return
        val scale = 1f / container.scaleY

        if (!scale.isNaN() && scale != Float.POSITIVE_INFINITY) {
            updateScaling(fraction, scale)
        }

        container.setRadiusPercentage(1f - fraction)
        dialog.setDimmingFactor(fraction)

        for (listener in listeners) {
            listener.onProgressFraction(fraction)
        }

        container.visibility = if (fraction == 0f) View.INVISIBLE else View.VISIBLE
        container.invalidate()
    }

    fun updateSheetSystemUI(lightMode: Boolean) {
        val window = localDialog?.window ?: return
        UiUtils.setLightSystemBars(window, !lightMode)
    }

    fun addTransitionListener(listener: TransitionListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeTransitionListener(listener: TransitionListener?) {
        listeners.remove(listener)
    }

    fun interface TransitionListener {
        fun onProgressFraction(fraction: Float)
    }

    abstract fun getTAG(): String

    protected abstract fun inflateExpanded(inflater: LayoutInflater, container: ConstraintLayout): GlanceConstraintLayout

    protected abstract fun getViewExtensionPreview(): GlanceViewExtension

    protected abstract fun isEnabled(): Boolean

    protected abstract fun updateScaling(@FloatRange(from = 0.0, to = 1.0) fraction: Float, scale: Float)

    companion object {
        private const val X1 = 0.2f
        private const val Y1 = 1f
        private const val X2 = 0.4f
        private const val Y2 = 1f
    }
}
