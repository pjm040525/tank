package com.example.tank1916

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Bullet(
    var x: Float,
    var y: Float,
    private val speed: Float,
    private val vx: Float = 0f,
    val radius: Float = 10f,
    val damage: Int = 1,
    private val color: Int = Color.RED
) {
    var isActive = true

    private val paint = Paint().apply {
        color = this@Bullet.color
        style = Paint.Style.FILL
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    fun update() {
        y -= speed
        x += vx
        if (y < -50f || x < -100f || x > 2000f) {
            isActive = false
        }
    }

    fun draw(canvas: Canvas) {
        if (!isActive) return
        canvas.drawCircle(x, y, radius, paint)
    }

    fun getBounds(): RectF {
        return RectF(x - radius, y - radius, x + radius, y + radius)
    }
}
