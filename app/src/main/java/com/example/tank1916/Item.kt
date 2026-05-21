package com.example.tank1916

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

enum class ItemType {
    POWER, HEAL
}

class Item(var x: Float, var y: Float, val type: ItemType) {
    val width = 60f
    val height = 60f
    val speed = 10f
    var isActive = true

    private val paint = Paint().apply { style = Paint.Style.FILL }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER
    }

    init {
        paint.color = if (type == ItemType.POWER) Color.rgb(255, 215, 0) else Color.rgb(255, 105, 180)
    }

    fun update() {
        y += speed
    }

    fun draw(canvas: Canvas) {
        if (!isActive) return
        canvas.drawRect(x - width / 2, y - height / 2, x + width / 2, y + height / 2, paint)
        canvas.drawText(if (type == ItemType.POWER) "P" else "H", x, y + 15f, textPaint)
    }

    fun isOffScreen(screenHeight: Int): Boolean = y > screenHeight + height

    fun getBounds(): RectF = RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
}
