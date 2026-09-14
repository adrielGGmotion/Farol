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

import android.app.Activity
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.stario.launcher.hidden.WallpaperManagerHidden
import dev.rikka.tools.refine.Refine

class WallpaperAnimator private constructor() {
    companion object {
        private const val TAG = "WallpaperAnimation"
        private val handler = Handler()
        private const val ANIMATION_FRAME_STEP = 0.005f
        private const val TARGET_FRAME_COUNT = 30
        private const val ANIMATION_FRAME_DELAY = 8L

        private var wallpaperManager: WallpaperManagerHidden? = null
        private var hasLoggedMissingMethod = false
        private var lastRecordedZoomValue = 0f
        private var zoomAnimator: Runnable? = null

        @JvmStatic
        fun updateZoom(activity: Activity, zoom: Float) {
            if (hasLoggedMissingMethod || zoom == lastRecordedZoomValue) {
                return
            }

            zoomAnimator?.let(handler::removeCallbacks)

            val direction = Math.signum(zoom - lastRecordedZoomValue)
            val token = getWindowToken(activity)
            zoomAnimator = object : Runnable {
                override fun run() {
                    if (token != null) {
                        lastRecordedZoomValue += direction * maxOf(
                            ANIMATION_FRAME_STEP,
                            kotlin.math.abs(zoom - lastRecordedZoomValue) / TARGET_FRAME_COUNT,
                        )
                        if ((direction > 0f && lastRecordedZoomValue > zoom) ||
                            (direction < 0f && lastRecordedZoomValue < zoom)
                        ) {
                            lastRecordedZoomValue = zoom
                        }

                        try {
                            getWallpaperManager(activity).setWallpaperZoomOut(token, lastRecordedZoomValue)
                        } catch (_: NoSuchMethodError) {
                            if (!hasLoggedMissingMethod) {
                                Log.e(
                                    TAG,
                                    "WallpaperManager::setWallpaperZoomOut does not exist. This error message will not be shown again.",
                                )
                                hasLoggedMissingMethod = true
                            }
                            return
                        }

                        if (lastRecordedZoomValue != zoom) {
                            handler.postDelayed(this, ANIMATION_FRAME_DELAY)
                        }
                    }
                }
            }
            zoomAnimator?.run()
        }

        private fun getWallpaperManager(activity: Activity): WallpaperManagerHidden {
            return wallpaperManager ?: Refine.unsafeCast<WallpaperManagerHidden>(
                WallpaperManagerHidden.getInstance(activity),
            ).also { wallpaperManager = it }
        }

        private fun getWindowToken(activity: Activity): IBinder? {
            val window = activity.window
            return window?.decorView?.windowToken
        }
    }
}
