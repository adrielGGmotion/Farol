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

package com.farol.launcher.utils

import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import com.farol.launcher.apps.ProfileApplicationManager
import com.farol.launcher.apps.ProfileManager
import com.farol.launcher.ui.Measurements
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

open class ImageUtils {
    companion object {
        @JvmStatic
        fun getIcon(service: LauncherApps, packageName: String): Drawable? {
            var drawable: Drawable? = null

            val profiles: List<ProfileApplicationManager> = ProfileManager.getInstance().getProfiles()

            for (profile in profiles) {
                val main = Utils.getMainActivity(service, packageName, profile.handle)

                if (main != null) {
                    drawable = main.getIcon(Measurements.getDotsPerInch())

                    if (drawable is AdaptiveIconDrawable) {
                        return drawable
                    }
                }
            }

            if (drawable != null) {
                return AdaptiveIconDrawable(
                    Color.WHITE.toDrawable(),
                    InsetDrawable(drawable, AdaptiveIconDrawable.getExtraInsetFraction())
                )
            }

            return null
        }

        @JvmStatic
        fun toBitmap(view: View): Bitmap {
            val bitmap = createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight())
            val canvas = Canvas(bitmap)

            view.layout(view.getLeft(), view.getTop(), view.getRight(), view.getBottom())
            view.draw(canvas)

            return bitmap
        }
    }
}
