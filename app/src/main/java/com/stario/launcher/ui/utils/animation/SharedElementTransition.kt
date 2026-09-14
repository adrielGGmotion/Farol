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

class SharedElementTransition(private val targets: List<View>) : TransitionSet() {
    init {
        ordering = ORDERING_TOGETHER

        val iconChangeBounds = ChangeBounds().apply {
            resizeClip = true
        }
        addTransition(iconChangeBounds)

        // https://issuetracker.google.com/issues/339169168
        // It has not been fixed...
        val changeTransform = ChangeTransform().apply {
            // This has to be false to avoid the issue
            reparentWithOverlay = false
        }
        changeTransform.addListener(object : Transition.TransitionListener {
            private val startingVisibility = HashMap<Transition, MutableSet<Pair<View, Int>>>()

            private fun reset(transition: Transition) {
                startingVisibility.remove(transition)?.forEach { pair ->
                    pair.first.visibility = pair.second
                }
            }

            override fun onTransitionStart(transition: Transition) {
                val startingVisibilityForTransition = startingVisibility.getOrPut(transition) { HashSet() }
                targets.forEach { target ->
                    startingVisibilityForTransition.add(Pair(target, target.visibility))
                    target.visibility = View.INVISIBLE
                }
            }

            override fun onTransitionEnd(transition: Transition) {
                reset(transition)
            }

            override fun onTransitionCancel(transition: Transition) {
                reset(transition)
            }

            override fun onTransitionPause(transition: Transition) = Unit

            override fun onTransitionResume(transition: Transition) = Unit
        })
        addTransition(changeTransform)

        setPathMotion(SharedElementMotion())
        interpolator = PathInterpolator(0.3f, 0.9f, 0.3f, 0.95f)
        duration = Animation.LONG.getDuration().toLong()
    }

    override fun isSeekingSupported(): Boolean {
        // ChangeTransform is not seekable, suppress the logcat warning
        return true
    }
}
