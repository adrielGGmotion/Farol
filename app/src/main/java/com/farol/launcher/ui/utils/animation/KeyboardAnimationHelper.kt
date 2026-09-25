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

package com.farol.launcher.ui.utils.animation

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.farol.launcher.ui.keyboard.ImeAnimationController
import com.farol.launcher.ui.keyboard.KeyboardHeightProvider
import com.farol.launcher.ui.utils.UiUtils
import java.util.LinkedList
import java.util.Queue
import androidx.core.view.size

@RequiresApi(Build.VERSION_CODES.R)
open class KeyboardAnimationHelper {
    companion object {
        private const val DEBOUNCED_KEYBOARD_RESIZE_DELAY = 50

        private const val CONTROLLER_DISALLOW_DELAY = 50

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.R)
        fun configureKeyboardAnimator(
            root: View,
            heightProvider: KeyboardHeightProvider,
            listener: ContentTranslationListener
        ) {
            configureKeyboardAnimator(root, heightProvider, null, listener)
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.R)
        fun configureKeyboardAnimator(
            root: View,
            heightProvider: KeyboardHeightProvider,
            controller: ImeAnimationController?,
            listener: ContentTranslationListener
        ) {
            ViewCompat.setWindowInsetsAnimationCallback(
                root,
                object : WindowInsetsAnimationCompat.Callback(
                    WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP
                ) {
                    private val allowControllerAnimation: Runnable?
                    private val queue: Queue<Float> = LinkedList()
                    private var imeAnimation: WindowInsetsAnimationCompat? = null
                    private var debouncedResize: Runnable? = null
                    private var startBottom = 0f
                    private var endBottom = 0f
                    private var running = false

                    init {
                        allowControllerAnimation = controller?.let {
                            Runnable { it.disallowAnimationControl(false) }
                        }

                        heightProvider.addKeyboardHeightListener { height ->
                            if (!UiUtils.areAnimationsOn()) {
                                listener.translate(-height.toFloat())
                            } else {
                                if (!running) {
                                    debouncedResize?.let(UiUtils::removeUICallback)
                                    debouncedResize = Runnable {
                                        listener.translate(-height.toFloat())
                                    }
                                    UiUtils.postDelayed(
                                        debouncedResize!!,
                                        DEBOUNCED_KEYBOARD_RESIZE_DELAY.toLong()
                                    )
                                }

                                endBottom = height.toFloat()
                            }
                        }
                    }

                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        running = true
                        debouncedResize?.let(UiUtils::removeUICallback)
                    }

                    override fun onStart(
                        animation: WindowInsetsAnimationCompat,
                        bounds: WindowInsetsAnimationCompat.BoundsCompat
                    ): WindowInsetsAnimationCompat.BoundsCompat {
                        startBottom = if (UiUtils.isKeyboardVisible(root)) {
                            0f
                        } else {
                            heightProvider.getKeyboardHeight().toFloat()
                        }
                        endBottom = if (startBottom > 0f) {
                            0f
                        } else {
                            heightProvider.getKeyboardHeight().toFloat()
                        }
                        imeAnimation = null
                        return bounds
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        if (controller != null && controller.isAnimationInProgress()) {
                            val delta = endBottom - startBottom
                            val fraction = controller.getExpandedFraction()
                            val translation = Math.abs(delta) * -fraction
                            listener.translate(translation)
                            return insets
                        }

                        val animation = imeAnimation ?: runningAnimations.firstOrNull {
                            (it.getTypeMask() and WindowInsetsCompat.Type.ime()) != 0
                        }?.also { imeAnimation = it }

                        if (animation != null &&
                            (animation.getDurationMillis() < 0L || UiUtils.areAnimationsOn())
                        ) {
                            allowControllerAnimation?.let(UiUtils::removeUICallback)
                            controller?.disallowAnimationControl(true)

                            var fraction = animation.getInterpolatedFraction()
                            if (queue.size >= 2) {
                                queue.remove()
                            }
                            queue.add(fraction)

                            val delta = endBottom - startBottom
                            fraction = (if (delta < 0f) 1f else 0f) - fraction
                            val translation = delta * fraction
                            listener.translate(translation)
                        }

                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        running = false
                        imeAnimation = null

                        if (queue.size > 1) {
                            val delta = endBottom - startBottom
                            val fraction =
                                (if (delta < 0f) 1f else 0f) - queue.poll()!!
                            val translation = delta * fraction
                            listener.translate(translation)
                        }

                        queue.clear()
                        allowControllerAnimation?.let {
                            UiUtils.postDelayed(it, CONTROLLER_DISALLOW_DELAY.toLong())
                        }
                    }
                }
            )
        }
    }

    fun interface ContentTranslationListener {
        fun translate(translation: Float)
    }
}
