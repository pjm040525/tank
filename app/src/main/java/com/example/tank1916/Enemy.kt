package com.example.tank1916

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF

enum class EnemyType {
    BASIC, FAST, STRONG, ZIGZAG
}

class Enemy(var x: Float, var y: Float, val type: EnemyType, val stage: Int = 1) {
    var width = 100f
    var height = 100f
    var speed = 8f
    var hp = 1
    var maxHp = 1
    var scoreValue = 100
    var isActive = true
    var flashTimer = 0

    private var angle = 0f
    private val amplitude = 150f
    private val centerX = x

    private val bodyPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }
    private val detailPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }
    private val hpBarBackgroundPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val hpBarForegroundPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    init {
        when (type) {
            EnemyType.BASIC -> {
                speed = if (stage >= 2) 12f else 8f
                hp = if (stage >= 2) 2 else 1
                scoreValue = 100; bodyPaint.color = Color.BLUE
            }
            EnemyType.FAST -> {
                speed = if (stage >= 2) 20f else 15f
                hp = 1
                scoreValue = 150; bodyPaint.color = Color.CYAN
                width = 80f; height = 80f
            }
            EnemyType.STRONG -> {
                speed = if (stage >= 2) 7f else 5f
                hp = if (stage >= 2) 5 else 3
                scoreValue = 300; bodyPaint.color = Color.rgb(139, 0, 0)
                width = 140f; height = 140f
            }
            EnemyType.ZIGZAG -> {
                speed = if (stage >= 2) 10f else 7f
                hp = if (stage >= 2) 2 else 1
                scoreValue = 200; bodyPaint.color = Color.MAGENTA
                width = 90f; height = 90f
            }
        }
        maxHp = hp
    }

    fun update() {
        y += speed
        if (type == EnemyType.ZIGZAG) {
            angle += 0.1f
            x = centerX + Math.sin(angle.toDouble()).toFloat() * amplitude
        }
        if (flashTimer > 0) flashTimer--
    }

    fun takeDamage() {
        takeDamage(1)
    }

    fun takeDamage(amount: Int) {
        hp -= amount
        flashTimer = 5
        if (hp <= 0) isActive = false
    }

    fun draw(canvas: Canvas, bmp: Bitmap?) {
        if (!isActive) return

        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
            if (flashTimer > 0) {
                bodyPaint.colorFilter = PorterDuffColorFilter(Color.argb(160, 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
            } else {
                bodyPaint.colorFilter = null
            }
            
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            val destRect = getBounds()
            
            canvas.drawBitmap(bmp, srcRect, destRect, bodyPaint)
            bodyPaint.colorFilter = null
        } else {
            if (flashTimer > 0) {
                bodyPaint.colorFilter = PorterDuffColorFilter(Color.argb(160, 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
            } else {
                bodyPaint.colorFilter = null
            }
            canvas.drawRect(x - width / 2, y - height / 2, x + width / 2, y + height / 2, bodyPaint)
            canvas.drawRect(x - 10f, y, x + 10f, y + height / 2 + 10f, detailPaint)
            bodyPaint.colorFilter = null
        }

        if (type == EnemyType.STRONG && hp < maxHp) {
            val barW = width * 0.7f
            val barH = 10f
            val barX = x - barW / 2
            val barY = y - height / 2 - 15f
            
            canvas.drawRect(barX, barY, barX + barW, barY + barH, hpBarBackgroundPaint)
            canvas.drawRect(barX, barY, barX + barW * (hp.toFloat() / maxHp), barY + barH, hpBarForegroundPaint)
        }
    }

    fun isOffScreen(screenHeight: Int): Boolean = y > screenHeight + height

    fun getBounds(): RectF = RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
}
