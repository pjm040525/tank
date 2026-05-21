package com.example.tank1916

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Boss(var x: Float, var y: Float, val stage: Int = 1) {
    val width = if (stage == 1) 400f else 500f
    val height = 300f
    var hp = if (stage == 1) 100 else 200
    val maxHp = hp
    var isActive = true
    val bossName = if (stage == 1) "IRON FORTRESS" else "DESERT STORM"

    private var state = 0 
    private var moveDir = 1
    private var shootTimer = 0L
    private var attackPatternIndex = 0

    // Properties for Stage 2 3-shot burst
    private var burstCount = 0
    private var lastBurstTime = 0L

    private val bodyPaint = Paint().apply { 
        color = if (stage == 1) Color.rgb(85, 107, 47) else Color.rgb(139, 69, 19)
    }

    fun update(screenWidth: Float, bossBullets: MutableList<BossBullet>) {
        if (!isActive) return
        if (state == 0) {
            y += 2f
            if (y >= 300f) {
                state = 1
                shootTimer = System.currentTimeMillis()
            }
        } else {
            // Sideways movement
            x += moveDir * 4f
            if (x < width / 2 || x > screenWidth - width / 2) moveDir *= -1

            val currentTime = System.currentTimeMillis()
            if (stage == 1) {
                // --- Stage 1 Attack Patterns (Interval: 1500ms) ---
                val shootInterval = 1500L
                if (currentTime - shootTimer > shootInterval) {
                    if (attackPatternIndex % 2 == 0) {
                        // Pattern 1: 3-way spread (Center, Left Diagonal, Right Diagonal)
                        bossBullets.add(BossBullet(x, y + height / 2, 14f, 0f))      // Center
                        bossBullets.add(BossBullet(x, y + height / 2, 13f, -4f))     // Left diagonal
                        bossBullets.add(BossBullet(x, y + height / 2, 13f, 4f))      // Right diagonal
                    } else {
                        // Pattern 2: Triple straight shots (Center, Left Wing, Right Wing)
                        bossBullets.add(BossBullet(x, y + height / 2, 15f, 0f))
                        bossBullets.add(BossBullet(x - width / 3f, y + height / 2, 15f, 0f))
                        bossBullets.add(BossBullet(x + width / 3f, y + height / 2, 15f, 0f))
                    }
                    attackPatternIndex++
                    shootTimer = currentTime
                }
            } else {
                // --- Stage 2 Attack Patterns (Interval: 1000ms, faster & stronger) ---
                if (burstCount > 0) {
                    if (currentTime - lastBurstTime > 200L) {
                        // Fire a rapid burst of straight bullets
                        bossBullets.add(BossBullet(x, y + height / 2, 16f, 0f))
                        bossBullets.add(BossBullet(x - width / 3f, y + height / 2, 16f, 0f))
                        bossBullets.add(BossBullet(x + width / 3f, y + height / 2, 16f, 0f))
                        burstCount--
                        lastBurstTime = currentTime
                    }
                } else {
                    val shootInterval = 1000L
                    if (currentTime - shootTimer > shootInterval) {
                        if (attackPatternIndex % 2 == 0) {
                            // Pattern 1: 5-way fan spread
                            bossBullets.add(BossBullet(x, y + height / 2, 15f, 0f))      // Center
                            bossBullets.add(BossBullet(x, y + height / 2, 14f, -3.5f))   // Left 1
                            bossBullets.add(BossBullet(x, y + height / 2, 13f, -7f))     // Left 2
                            bossBullets.add(BossBullet(x, y + height / 2, 14f, 3.5f))    // Right 1
                            bossBullets.add(BossBullet(x, y + height / 2, 13f, 7f))     // Right 2
                        } else {
                            // Pattern 2: Trigger 3-shot burst
                            burstCount = 3
                            lastBurstTime = 0L // will trigger immediately on next check
                        }
                        attackPatternIndex++
                        shootTimer = currentTime
                    }
                }
            }
        }
    }

    fun takeDamage(damage: Int) {
        hp -= damage
        if (hp <= 0) { hp = 0; isActive = false }
    }

    fun draw(canvas: Canvas) {
        if (!isActive) return
        
        // 1. Base shadow / outer hull
        bodyPaint.color = if (stage == 1) Color.rgb(55, 77, 17) else Color.rgb(99, 39, 0)
        canvas.drawRect(x - width / 2, y - height / 2, x + width / 2, y + height / 2, bodyPaint)
        
        // 2. Inner armor plates
        bodyPaint.color = if (stage == 1) Color.rgb(85, 107, 47) else Color.rgb(139, 69, 19)
        canvas.drawRect(x - width * 0.4f, y - height * 0.4f, x + width * 0.4f, y + height * 0.4f, bodyPaint)
        
        // 3. Decorative glowing panel or emblem (Red/Yellow core)
        val corePaint = Paint().apply {
            color = if (stage == 1) Color.RED else Color.YELLOW
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, 40f, corePaint)
        corePaint.color = Color.WHITE
        canvas.drawCircle(x, y, 20f, corePaint)
        
        // 4. Gun barrels/turrets at the bottom
        val barrelPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.FILL
        }
        // Center barrel
        canvas.drawRect(x - 20f, y + height / 2, x + 20f, y + height / 2 + 50f, barrelPaint)
        // Left wing barrel
        canvas.drawRect(x - width / 3f - 15f, y + height / 2, x - width / 3f + 15f, y + height / 2 + 40f, barrelPaint)
        // Right wing barrel
        canvas.drawRect(x + width / 3f - 15f, y + height / 2, x + width / 3f + 15f, y + height / 2 + 40f, barrelPaint)
    }

    fun getBounds(): RectF = RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
}
