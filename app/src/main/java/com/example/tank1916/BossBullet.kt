package com.example.tank1916

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class BossBullet(var x: Float, var y: Float, private val speed: Float, private val vx: Float = 0f) {
    var isActive = true
    private val radius = 20f

    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    fun update() {
        y += speed
        x += vx
        if (y > 2500f || x < -100f || x > 2000f) isActive = false
    }

    fun draw(canvas: Canvas) {
        if (!isActive) return
        
        // Glow / Outer energy ring
        paint.color = Color.RED
        canvas.drawCircle(x, y, radius, paint)
        
        // Inner core
        paint.color = Color.YELLOW
        canvas.drawCircle(x, y, radius * 0.6f, paint)
    }

    fun getBounds(): RectF = RectF(x - radius, y - radius, x + radius, y + radius)
}
