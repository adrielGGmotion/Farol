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
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.view.Window
import com.stario.launcher.hidden.WallpaperManagerHidden
import dev.rikka.tools.refine.Refine

open class WallpaperAnimator {
    companion object {
        private const val TAG = "WallpaperAnimation"

        private val handler = Handler(Looper.getMainLooper())

        private const val ANIMATION_FRAME_STEP = 0.005f

        private const val TARGET_FRAME_COUNT = 30

        private const val ANIMATION_FRAME_DELAY = 8

        private var wallpaperManager: WallpaperManagerHidden? = null

        private var hasLoggedMissingMethod = false

        private var lastRecordedZoomValue = 0f

        private var zoomAnimator: Runnable? = null

        @JvmStatic
        fun updateZoom(activity: Activity, zoom: Float) {
            if (hasLoggedMissingMethod || zoom == lastRecordedZoomValue) {
                return
            }

            val previousAnimator = zoomAnimator
            if (previousAnimator != null) {
                handler.removeCallbacks(previousAnimator)
            }

            val direction = Math.signum(zoom - lastRecordedZoomValue)
            val token = getWindowToken(activity)
            zoomAnimator = object : Runnable {
                override fun run() {
                    if (token != null) {
                        lastRecordedZoomValue += direction * Math.max(
                            ANIMATION_FRAME_STEP,
                            Math.abs(zoom - lastRecordedZoomValue) / TARGET_FRAME_COUNT.toFloat()
                        )
                        if ((direction > 0f && lastRecordedZoomValue > zoom) ||
                            (direction < 0f && lastRecordedZoomValue < zoom)
                        ) {
                            lastRecordedZoomValue = zoom
                        }

                        try {
                            getWallpaperManager(activity)
                                .setWallpaperZoomOut(token, lastRecordedZoomValue)
                        } catch (_: NoSuchMethodError) {
                            if (!hasLoggedMissingMethod) {
                                Log.e(
                                    TAG,
                                    "WallpaperManager::setWallpaperZoomOut does not exist. This error message will not be shown again."
                                )
                                hasLoggedMissingMethod = true
                            }
                            return
                        }

                        if (lastRecordedZoomValue != zoom) {
                            handler.postDelayed(this, ANIMATION_FRAME_DELAY.toLong())
                        }
                    }
                }
            }

            zoomAnimator!!.run()
        }

        @JvmStatic
        private fun getWallpaperManager(activity: Activity): WallpaperManagerHidden {
            wallpaperManager?.let { return it }
            wallpaperManager = Refine.unsafeCast<WallpaperManagerHidden>(WallpaperManager.getInstance(activity))
            return wallpaperManager!!
        }

        @JvmStatic
        private fun getWindowToken(activity: Activity): IBinder? {
            val window: Window? = activity.getWindow()
            if (window != null) {
                return window.getDecorView().getWindowToken()
            }
            return null
        }
    }
}
