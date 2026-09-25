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

package com.stario.launcher.ui.keyboard

import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.stario.launcher.utils.Utils
import androidx.core.view.isVisible

private const val SCROLL_THRESHOLD = 0.15f

open class ImeAnimationController {
    private var insetsAnimationController: WindowInsetsAnimationControllerCompat? = null
    private var cancellationSignal: CancellationSignal? = null
    private var disallowAnimationControl = false
    private var springAnimation: SpringAnimation? = null
    private var isImeShownAtStart = false

    init {
        if (!Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
            throw RuntimeException("Keyboard animation controller requires at least API 30.")
        }

        disallowAnimationControl = false
    }

    open fun startControlRequest(view: View) {
        if (disallowAnimationControl) {
            return
        }

        val insets = ViewCompat.getRootWindowInsets(view)

        if (insets != null) {
            isImeShownAtStart = insets.isVisible(WindowInsetsCompat.Type.ime())
            val cancellationSignal = CancellationSignal()
            this.cancellationSignal = cancellationSignal

            @Suppress("DEPRECATION")
            val windowInsetsController = ViewCompat.getWindowInsetsController(view)

            if (windowInsetsController != null) {
                windowInsetsController.controlWindowInsetsAnimation(
                    WindowInsetsCompat.Type.ime(),
                    -1,
                    LinearInterpolator(),
                    cancellationSignal,
                    object : WindowInsetsAnimationControlListenerCompat {
                        override fun onReady(
                            controller: WindowInsetsAnimationControllerCompat,
                            types: Int
                        ) {
                            insetsAnimationController = controller
                            this@ImeAnimationController.cancellationSignal = null

                            insetTo(
                                if (isImeShownAtStart) {
                                    controller.shownStateInsets.bottom
                                } else {
                                    0
                                }
                            )
                        }

                        override fun onFinished(controller: WindowInsetsAnimationControllerCompat) {
                            reset()
                        }

                        override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                            reset()
                        }
                    }
                )
            }
        }
    }

    open fun insetBy(dy: Int): Int {
        val controller = insetsAnimationController
        if (controller != null && !disallowAnimationControl) {
            return insetTo(controller.currentInsets.bottom - dy)
        }

        return 0
    }

    open fun insetTo(inset: Int): Int {
        val controller = insetsAnimationController
        if (controller != null && !disallowAnimationControl) {
            val hiddenBottom = controller.hiddenStateInsets.bottom
            val shownBottom = controller.shownStateInsets.bottom

            val startBottom = if (isImeShownAtStart) shownBottom else hiddenBottom
            val endBottom = if (isImeShownAtStart) hiddenBottom else shownBottom

            val bottom = maxOf(hiddenBottom, minOf(shownBottom, inset))

            val consumed = controller.currentInsets.bottom - bottom

            controller.setInsetsAndAlpha(
                Insets.of(0, 0, 0, bottom),
                1f,
                (bottom - startBottom).toFloat() / (endBottom - startBottom)
            )

            return consumed
        }

        return 0
    }

    open fun isAnimationInProgress(): Boolean {
        return insetsAnimationController != null
    }

    open fun isSettleAnimationInProgress(): Boolean {
        return springAnimation != null
    }

    open fun isRequestPending(): Boolean {
        return cancellationSignal != null
    }

    open fun getExpandedFraction(): Float {
        if (!isAnimationInProgress()) {
            throw RuntimeException("Fraction can only be returned if the animation is running.")
        }

        val controller = insetsAnimationController!!
        return controller.currentInsets.bottom.toFloat() /
                (controller.shownStateInsets.bottom - controller.hiddenStateInsets.bottom)
    }

    open fun isCurrentPositionFullyHidden(): Boolean {
        val controller = insetsAnimationController
        return controller != null &&
                controller.currentInsets.bottom == controller.hiddenStateInsets.bottom
    }

    open fun isCurrentPositionFullyShown(): Boolean {
        val controller = insetsAnimationController
        return controller != null &&
                controller.currentInsets.bottom == controller.shownStateInsets.bottom
    }

    open fun cancel() {
        cancellationSignal?.cancel()
    }

    private fun finish(shown: Boolean) {
        insetsAnimationController!!.finish(shown)
    }

    open fun finish() {
        finish(null)
    }

    open fun finish(velocity: Int?) {
        springAnimation?.cancel()

        val controller = insetsAnimationController
        if (controller != null) {
            if (velocity != null && velocity != 0) {
                if (velocity > 0 && isCurrentPositionFullyShown()) {
                    controller.finish(true)
                } else if (velocity < 0 && isCurrentPositionFullyHidden()) {
                    controller.finish(false)
                } else {
                    setVisibilityWithAnimation(velocity > 0, velocity)
                }
            } else if (isCurrentPositionFullyShown()) {
                finish(true)
            } else if (isCurrentPositionFullyHidden()) {
                finish(false)
            } else {
                if (controller.currentFraction >= SCROLL_THRESHOLD) {
                    setVisibilityWithAnimation(!isImeShownAtStart, null)
                } else {
                    setVisibilityWithAnimation(isImeShownAtStart, null)
                }
            }
        } else {
            cancel()
        }
    }

    open fun disallowAnimationControl(value: Boolean) {
        disallowAnimationControl = value

        if (disallowAnimationControl) {
            if (isRequestPending()) {
                reset()
            } else {
                finish()
            }
        }
    }

    private fun reset() {
        cancel()

        springAnimation = null
        insetsAnimationController = null
        cancellationSignal = null
        isImeShownAtStart = false
    }

    private fun setVisibilityWithAnimation(visible: Boolean, velocity: Int?) {
        val controller = insetsAnimationController!!
        val animation = SpringAnimation(FloatValueHolder(controller.currentInsets.bottom.toFloat()))
        springAnimation = animation

        animation.addUpdateListener { _, value, _ ->
            insetTo(Math.round(value))
        }
        animation.addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                val activeController = insetsAnimationController
                if (activeController != null) {
                    if (isCurrentPositionFullyShown()) {
                        finish(true)
                    } else if (isCurrentPositionFullyHidden()) {
                        finish(false)
                    } else {
                        if (activeController.currentFraction >= SCROLL_THRESHOLD) {
                            finish(isImeShownAtStart)
                        } else {
                            finish(!isImeShownAtStart)
                        }
                    }
                }
            }

            springAnimation = null
        }

        if (velocity != null) {
            animation.setStartVelocity(velocity.toFloat())
        }

        animation.animateToFinalPosition(
            if (visible) controller.shownStateInsets.bottom.toFloat()
            else controller.hiddenStateInsets.bottom.toFloat()
        )

        animation.spring.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        animation.spring.setStiffness(3000f)
    }

    open fun isAnimationControlDisallowed(): Boolean {
        return disallowAnimationControl
    }
}
