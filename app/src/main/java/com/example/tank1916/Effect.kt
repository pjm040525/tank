package com.example.tank1916

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

enum class EffectType {
    EXPLOSION, PICKUP_TEXT, HIT_SPARK
}

class Effect(
    var x: Float, 
    var y: Float, 
    val type: EffectType, 
    val label: String = "", 
    var maxLifeTime: Int = 20
) {
    var lifeTime = maxLifeTime
    var isActive = true
    private val paint = Paint()

    fun update() {
        lifeTime--
        if (lifeTime <= 0) isActive = false
        if (type == EffectType.PICKUP_TEXT) y -= 2f
    }

    fun draw(canvas: Canvas) {
        if (!isActive) return
        val progress = lifeTime.toFloat() / maxLifeTime
        paint.alpha = (progress * 255).toInt()

        when (type) {
            EffectType.EXPLOSION -> {
                paint.color = Color.YELLOW
                canvas.drawCircle(x, y, 60f * (1f - progress + 0.5f), paint)
            }
            EffectType.HIT_SPARK -> {
                paint.color = Color.WHITE
                canvas.drawRect(x - 10f, y - 10f, x + 10f, y + 10f, paint)
            }
            EffectType.PICKUP_TEXT -> {
                paint.color = Color.WHITE; paint.textSize = 50f; paint.textAlign = Paint.Align.CENTER
                canvas.drawText(label, x, y, paint)
            }
        }
    }
}
