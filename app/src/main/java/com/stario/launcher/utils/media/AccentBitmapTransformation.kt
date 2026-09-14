/*
 * Copyright (C) 2025 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.stario.launcher.utils.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.palette.graphics.Palette
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class AccentBitmapTransformation : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        bitmap: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val color = getAccentColor(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val radius = maxOf(width, height) / 1.5f

        val paint = Paint()
        val canvas = Canvas(bitmap)
        val gradient = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(
                Color.argb(
                    0.1f,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f
                ),
                Color.argb(
                    0.3f,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f
                ),
                Color.argb(
                    0.7f,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f
                ),
                Color.argb(
                    0.9f,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f
                )
            ),
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )

        paint.isAntiAlias = true
        paint.shader = gradient
        canvas.drawRect(Rect(0, 0, width, height), paint)
        return bitmap
    }

    private fun getAccentColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).generate()
        var color = checkLuma(palette.getDominantColor(Color.BLACK), Color.BLACK)

        if (color == Color.BLACK) {
            color = palette.getDarkVibrantColor(color)
        }
        if (color == Color.BLACK) {
            color = checkLuma(palette.getVibrantColor(color), color)
        }
        if (color == Color.BLACK) {
            color = palette.getDarkMutedColor(color)
        }
        if (color == Color.BLACK) {
            color = checkLuma(palette.getMutedColor(color), color)
        }

        return color
    }

    private fun checkLuma(target: Int, color: Int): Int {
        val luma = 0.2126 * Color.red(target) +
            0.7152 * Color.green(target) +
            0.0722 * Color.blue(target)
        return if (luma < 70) target else color
    }

    override fun equals(other: Any?): Boolean = other is AccentBitmapTransformation

    override fun hashCode(): Int = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    companion object {
        private const val ID = "com.bumptech.glide.transformations.DarkenBitmapTransformation"
        private val ID_BYTES = ID.toByteArray(StandardCharsets.UTF_8)
    }
}
