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

package com.stario.launcher.ui.utils.animation

import android.util.Pair
import android.view.View
import android.view.animation.PathInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.ChangeTransform
import androidx.transition.Transition
import androidx.transition.TransitionSet
import java.util.HashMap
import java.util.HashSet

open class SharedElementTransition(targets: List<View>) : TransitionSet() {
    init {
        setOrdering(ORDERING_TOGETHER)

        val iconChangeBounds = ChangeBounds()
        iconChangeBounds.setResizeClip(true)
        addTransition(iconChangeBounds)

        val changeTransform = ChangeTransform()
        changeTransform.setReparentWithOverlay(false)
        changeTransform.addListener(object : Transition.TransitionListener {
            private val startingVisibility: MutableMap<Transition, MutableSet<Pair<View, Int>>> = HashMap()

            private fun reset(transition: Transition) {
                val startingVisibilityForTransition = startingVisibility.remove(transition)
                if (startingVisibilityForTransition != null) {
                    for (pair in startingVisibilityForTransition) {
                        pair.first.setVisibility(pair.second)
                    }
                }
            }

            override fun onTransitionStart(transition: Transition) {
                for (target in targets) {
                    val startingVisibilityForTransition =
                        startingVisibility.computeIfAbsent(transition) { HashSet() }
                    startingVisibilityForTransition.add(Pair(target, target.getVisibility()))
                    target.setVisibility(View.INVISIBLE)
                }
            }

            override fun onTransitionEnd(transition: Transition) {
                reset(transition)
            }

            override fun onTransitionCancel(transition: Transition) {
                reset(transition)
            }

            override fun onTransitionPause(transition: Transition) {
            }

            override fun onTransitionResume(transition: Transition) {
            }
        })

        addTransition(changeTransform)
        setPathMotion(SharedElementMotion())
        setInterpolator(PathInterpolator(0.3f, 0.9f, 0.3f, 0.95f))
        setDuration(Animation.LONG.getDuration().toLong())
    }

    override fun isSeekingSupported(): Boolean = true
}
