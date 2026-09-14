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

package com.stario.launcher.utils.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import jp.wasabeef.glide.transformations.BlurTransformation

class BlurBitmapTransformation(radius: Int) : BlurTransformation(radius) {
    override fun transform(
        context: Context,
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val blurred = super.transform(context, pool, toTransform, outWidth, outHeight)
        val canvas = Canvas(blurred)

        canvas.drawBitmap(
            createFadedBitmap(toTransform),
            Rect(0, 0, toTransform.width, toTransform.height),
            Rect(0, 0, blurred.width, blurred.height),
            null
        )

        return blurred
    }

    private fun createFadedBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val radialGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(1f, 0f, 0f, 0f),
                Color.argb(0.3f, 0f, 0f, 0f),
                Color.argb(0.05f, 0f, 0f, 0f),
                Color.argb(0f, 0f, 0f, 0f)
            )
        )
        radialGradient.setBounds(0, 0, width, height)
        radialGradient.gradientRadius = maxOf(width, height) / 1.5f
        radialGradient.gradientCenter = 0.5f to 0.5f
        radialGradient.gradientType = GradientDrawable.RADIAL_GRADIENT

        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        radialGradient.draw(canvas)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    override fun equals(other: Any?): Boolean = other is BlurBitmapTransformation

    override fun hashCode(): Int = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    companion object {
        private const val ID = "com.bumptech.glide.transformations.BlurBitmapTransformation"
        private val ID_BYTES = ID.toByteArray(StandardCharsets.UTF_8)
    }
}
