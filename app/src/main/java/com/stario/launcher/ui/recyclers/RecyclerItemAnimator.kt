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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.stario.launcher.ui.recyclers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.ui.utils.animation.Animation
import java.util.ArrayList
import java.util.Collections

open class RecyclerItemAnimator(
    private val animationFlags: Int,
    private val animation: Animation
) : DefaultItemAnimator() {
    private val pendingRemovals: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList<RecyclerView.ViewHolder>())
    private val pendingAdditions: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList<RecyclerView.ViewHolder>())
    private val removeAnimations: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList<RecyclerView.ViewHolder>())
    private val addAnimations: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList<RecyclerView.ViewHolder>())

    init {
        if ((animationFlags and CHANGING) != CHANGING) {
            setChangeDuration(0)
            setMoveDuration(0)
        }
    }

    open override fun runPendingAnimations() {
        super.runPendingAnimations()

        val removalsPending = pendingRemovals.isNotEmpty()
        val additionsPending = pendingAdditions.isNotEmpty()
        if (removalsPending || additionsPending) {
            for (holder in pendingRemovals) {
                animateRemoveImplementation(holder)
            }
            pendingRemovals.clear()

            for (holder in pendingAdditions) {
                animateAddImplementation(holder)
            }
            pendingAdditions.clear()
        }
    }

    open override fun endAnimation(item: RecyclerView.ViewHolder) {
        if (pendingRemovals.remove(item)) {
            item.itemView.setAlpha(getTargetAlpha())
            item.itemView.setScaleX(getTargetScaleX())
            item.itemView.setScaleY(getTargetScaleY())
            dispatchRemoveFinished(item)
        }

        if (pendingAdditions.remove(item)) {
            item.itemView.setAlpha(getTargetAlpha())
            item.itemView.setScaleX(getTargetScaleX())
            item.itemView.setScaleY(getTargetScaleY())
            dispatchAddFinished(item)
        }

        removeAnimations.remove(item)
        addAnimations.remove(item)
        super.endAnimation(item)
    }

    open override fun endAnimations() {
        val removeIterator = removeAnimations.iterator()
        while (removeIterator.hasNext()) {
            val holder = removeIterator.next()
            holder.itemView.setAlpha(getTargetAlpha())
            holder.itemView.setScaleX(getTargetScaleX())
            holder.itemView.setScaleY(getTargetScaleY())
            dispatchAddFinished(holder)
            removeIterator.remove()
        }

        val addIterator = addAnimations.iterator()
        while (addIterator.hasNext()) {
            val holder = addIterator.next()
            holder.itemView.setAlpha(getTargetAlpha())
            holder.itemView.setScaleX(getTargetScaleX())
            holder.itemView.setScaleY(getTargetScaleY())
            dispatchRemoveFinished(holder)
            addIterator.remove()
        }

        super.endAnimations()
    }

    open override fun isRunning(): Boolean {
        return super.isRunning() || removeAnimations.isNotEmpty() || addAnimations.isNotEmpty()
    }

    final override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)

        if ((animationFlags and APPEARANCE) == APPEARANCE) {
            holder.itemView.setScaleY(getRemovedScaleY())
            holder.itemView.setScaleX(getRemovedScaleX())
            holder.itemView.setAlpha(getRemovedAlpha())
            holder.itemView.setTranslationZ(0f)
        }

        pendingAdditions.add(holder)
        return true
    }

    protected open fun animateAddImplementation(holder: RecyclerView.ViewHolder) {
        val view: View = holder.itemView
        val animator: ViewPropertyAnimator = view.animate()
        addAnimations.add(holder)

        animator.alpha(getTargetAlpha())
            .scaleY(getTargetScaleY())
            .scaleX(getTargetScaleX())
            .setDuration(
                if ((animationFlags and APPEARANCE) == APPEARANCE) {
                    animation.getDuration().toLong()
                } else {
                    0L
                }
            )
            .setInterpolator(DecelerateInterpolator(3f))
            .setListener(object : AnimatorListenerAdapter() {
                private fun cleanup() {
                    animator.setListener(null)
                    view.setAlpha(getTargetAlpha())
                    view.setScaleX(getTargetScaleX())
                    view.setScaleY(getTargetScaleY())
                    dispatchAddFinished(holder)
                    addAnimations.remove(holder)

                    if (!isRunning()) {
                        dispatchAnimationsFinished()
                    }
                }

                override fun onAnimationStart(animation: Animator) {
                    dispatchAddStarting(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    cleanup()
                }

                override fun onAnimationEnd(animation: Animator) {
                    cleanup()
                }
            })
    }

    final override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)

        if ((animationFlags and DISAPPEARANCE) == DISAPPEARANCE) {
            holder.itemView.setScaleY(getTargetScaleY())
            holder.itemView.setScaleX(getTargetScaleX())
            holder.itemView.setAlpha(getTargetAlpha())

            // keep in front without adding elevation shadow
            holder.itemView.setTranslationZ(-100_000_000f)
        }

        pendingRemovals.add(holder)
        return true
    }

    protected open fun animateRemoveImplementation(holder: RecyclerView.ViewHolder) {
        val view: View = holder.itemView
        val animator: ViewPropertyAnimator = view.animate()
        removeAnimations.add(holder)

        animator.alpha(getRemovedAlpha())
            .scaleY(getRemovedScaleY())
            .scaleX(getRemovedScaleX())
            .setDuration(
                if ((animationFlags and DISAPPEARANCE) == DISAPPEARANCE) {
                    animation.getDuration().toLong()
                } else {
                    0L
                }
            )
            .setInterpolator(DecelerateInterpolator(3f))
            .setListener(object : AnimatorListenerAdapter() {
                private fun cleanup() {
                    animator.setListener(null)
                    view.setAlpha(getTargetAlpha())
                    view.setScaleX(getTargetScaleX())
                    view.setScaleY(getTargetScaleY())
                    view.setTranslationZ(0f)
                    dispatchRemoveFinished(holder)
                    removeAnimations.remove(holder)

                    if (!isRunning()) {
                        dispatchAnimationsFinished()
                    }
                }

                override fun onAnimationStart(animation: Animator) {
                    dispatchRemoveStarting(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    cleanup()
                }

                override fun onAnimationEnd(animation: Animator) {
                    cleanup()
                }
            })
    }

    open fun getTargetAlpha(): Float = 1f

    open fun getTargetScaleX(): Float = 1f

    open fun getTargetScaleY(): Float = 1f

    open fun getRemovedAlpha(): Float = 0f

    open fun getRemovedScaleX(): Float = 0.9f

    open fun getRemovedScaleY(): Float = 0.9f

    open fun getFlags(): Int = animationFlags

    companion object {
        @JvmField
        var APPEARANCE = 0b100

        @JvmField
        var DISAPPEARANCE = 0b010

        @JvmField
        var CHANGING = 0b001
    }
}
