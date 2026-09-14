/*
 * Copyright (C) 2025 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.stario.launcher.utils

import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.ui.Measurements

object ImageUtils {
    @JvmStatic
    fun getIcon(service: LauncherApps, packageName: String): Drawable? {
        var drawable: Drawable? = null
        val profiles = ProfileManager.getInstance().profiles

        for (profile in profiles) {
            val main = Utils.getMainActivity(service, packageName, profile.handle)
            if (main != null) {
                drawable = main.getIcon(Measurements.getDotsPerInch())
                if (drawable is AdaptiveIconDrawable) {
                    return drawable
                }
            }
        }

        return if (drawable != null) {
            AdaptiveIconDrawable(
                ColorDrawable(Color.WHITE),
                InsetDrawable(drawable, AdaptiveIconDrawable.getExtraInsetFraction())
            )
        } else {
            null
        }
    }

    @JvmStatic
    fun toBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth,
            view.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        view.layout(view.left, view.top, view.right, view.bottom)
        view.draw(canvas)

        return bitmap
    }
}
