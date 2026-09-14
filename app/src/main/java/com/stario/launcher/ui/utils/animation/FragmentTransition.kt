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

import android.view.View
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionPropagation
import androidx.transition.TransitionSet
import androidx.transition.TransitionValues
import com.google.android.material.transition.MaterialElevationScale
import com.stario.launcher.R

class FragmentTransition @JvmOverloads constructor(
    growing: Boolean,
    exclusions: List<out View>? = null,
) : TransitionSet() {
    private class StaggerPropagation : TransitionPropagation() {
        override fun getStartDelay(
            sceneRoot: ViewGroup,
            transition: Transition,
            startValues: TransitionValues?,
            endValues: TransitionValues?,
        ): Long {
            if (endValues == null || !endValues.values.containsKey(PROP_STAGGER_INDEX)) {
                return 0L
            }

            val staggerIndex = endValues.values[PROP_STAGGER_INDEX] as? Int ?: 0
            return staggerIndex * STAGGER_DELAY_MS
        }

        override fun captureValues(transitionValues: TransitionValues) {
            val tag = transitionValues.view.getTag(R.id.stagger_order_tag)
            transitionValues.values[PROP_STAGGER_INDEX] = tag as? Int ?: 0
        }

        override fun getPropagationProperties(): Array<String> = arrayOf(PROP_STAGGER_INDEX)

        companion object {
            private const val PROP_STAGGER_INDEX = "com.stario.launcher:propagation:staggerIndex"
            private const val STAGGER_DELAY_MS = 20L
        }
    }

    init {
        ordering = ORDERING_TOGETHER
        addTransition(Fade())
        addTransition(MaterialElevationScale(growing))

        exclusions?.forEach { view -> excludeTarget(view, true) }

        interpolator = FastOutSlowInInterpolator()
        duration = Animation.LONG.getDuration().toLong()
        propagation = StaggerPropagation()
    }
}
