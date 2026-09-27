package com.farol.launcher.activities.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.withClip
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

class CookieAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private val polygon = RoundedPolygon.star(
        numVerticesPerRadius = 12,
        innerRadius = .8f,
        rounding = CornerRounding(.5f)
    ).normalized()
    private val crop = Path()

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val side = minOf(width, height).toFloat()
        polygon.toPath(crop)
        crop.transform(Matrix().apply {
            setScale(side, side)
            postTranslate((width - side) / 2f, (height - side) / 2f)
        })
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withClip(crop) { super.onDraw(this) }
    }
}
