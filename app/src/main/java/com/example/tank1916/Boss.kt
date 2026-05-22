package com.example.tank1916

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Boss(var x: Float, var y: Float, val stage: Int = 1) {
    val width = if (stage == 1) 400f else 500f
    val height = 300f
    var hp = if (stage == 1) 120 else 250
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
        isAntiAlias = true
    }

    private val corePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val barrelPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
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
                // --- Stage 1 Attack Patterns (Interval: 1200ms) ---
                val shootInterval = 1200L
                if (currentTime - shootTimer > shootInterval) {
                    if (attackPatternIndex % 2 == 0) {
                        // Pattern 1: 3-way spread (Center, Left Diagonal, Right Diagonal)
                        bossBullets.add(BossBullet(x, y + height / 2, 16f, 0f))      // Center
                        bossBullets.add(BossBullet(x, y + height / 2, 15f, -4f))     // Left diagonal
                        bossBullets.add(BossBullet(x, y + height / 2, 15f, 4f))      // Right diagonal
                    } else {
                        // Pattern 2: Triple straight shots (Center, Left Wing, Right Wing)
                        bossBullets.add(BossBullet(x, y + height / 2, 17f, 0f))
                        bossBullets.add(BossBullet(x - width / 3f, y + height / 2, 17f, 0f))
                        bossBullets.add(BossBullet(x + width / 3f, y + height / 2, 17f, 0f))
                    }
                    attackPatternIndex++
                    shootTimer = currentTime
                }
            } else {
                // --- Stage 2 Attack Patterns (Interval: 800ms, faster & stronger) ---
                if (burstCount > 0) {
                    if (currentTime - lastBurstTime > 150L) {
                        // Fire a rapid burst of straight bullets
                        bossBullets.add(BossBullet(x, y + height / 2, 18f, 0f))
                        bossBullets.add(BossBullet(x - width / 3f, y + height / 2, 18f, 0f))
                        bossBullets.add(BossBullet(x + width / 3f, y + height / 2, 18f, 0f))
                        burstCount--
                        lastBurstTime = currentTime
                    }
                } else {
                    val shootInterval = 800L
                    if (currentTime - shootTimer > shootInterval) {
                        if (attackPatternIndex % 2 == 0) {
                            // Pattern 1: 5-way fan spread
                            bossBullets.add(BossBullet(x, y + height / 2, 18f, 0f))      // Center
                            bossBullets.add(BossBullet(x, y + height / 2, 17f, -3.5f))   // Left 1
                            bossBullets.add(BossBullet(x, y + height / 2, 16f, -7f))     // Left 2
                            bossBullets.add(BossBullet(x, y + height / 2, 17f, 3.5f))    // Right 1
                            bossBullets.add(BossBullet(x, y + height / 2, 16f, 7f))     // Right 2
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
        
        // 2. Inner armor plates & decorative details (Caterpillar track previews)
        bodyPaint.color = if (stage == 1) Color.rgb(85, 107, 47) else Color.rgb(139, 69, 19)
        canvas.drawRect(x - width * 0.4f, y - height * 0.4f, x + width * 0.4f, y + height * 0.4f, bodyPaint)
        
        // Caterpillars details
        bodyPaint.color = Color.rgb(30, 30, 30)
        canvas.drawRect(x - width / 2 - 10f, y - height / 2 + 30f, x - width / 2 + 10f, y + height / 2 - 30f, bodyPaint)
        canvas.drawRect(x + width / 2 - 10f, y - height / 2 + 30f, x + width / 2 + 10f, y + height / 2 - 30f, bodyPaint)

        // Glowing emblem / Core
        corePaint.color = if (stage == 1) Color.RED else Color.YELLOW
        canvas.drawCircle(x, y, 40f, corePaint)
        corePaint.color = Color.WHITE
        canvas.drawCircle(x, y, 20f, corePaint)
        
        // 3. Turrets (Base of the guns)
        val turretPaint = Paint().apply {
            color = if (stage == 1) Color.rgb(105, 127, 67) else Color.rgb(169, 99, 49)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        // Center main turret base
        canvas.drawRect(x - 50f, y + height / 2 - 35f, x + 50f, y + height / 2, turretPaint)
        // Left auxiliary turret base
        canvas.drawRect(x - width / 3f - 30f, y + height / 2 - 25f, x - width / 3f + 30f, y + height / 2, turretPaint)
        // Right auxiliary turret base
        canvas.drawRect(x + width / 3f - 30f, y + height / 2 - 25f, x + width / 3f + 30f, y + height / 2, turretPaint)
        
        // 4. Gun barrels (Highly visible extended dark barrels)
        barrelPaint.color = Color.rgb(35, 35, 35) // High-contrast dark gray
        
        // Center main barrel (Extended to 95f)
        val centerBarrelY1 = y + height / 2
        val centerBarrelY2 = y + height / 2 + 95f
        canvas.drawRect(x - 25f, centerBarrelY1, x + 25f, centerBarrelY2, barrelPaint)
        
        // Left wing barrel (Extended to 75f)
        val leftBarrelY1 = y + height / 2
        val leftBarrelY2 = y + height / 2 + 75f
        canvas.drawRect(x - width / 3f - 18f, leftBarrelY1, x - width / 3f + 18f, leftBarrelY2, barrelPaint)
        
        // Right wing barrel (Extended to 75f)
        val rightBarrelY1 = y + height / 2
        val rightBarrelY2 = y + height / 2 + 75f
        canvas.drawRect(x + width / 3f - 18f, rightBarrelY1, x + width / 3f + 18f, rightBarrelY2, barrelPaint)
        
        // 5. Muzzles (High-contrast bright caps at barrel tips)
        val muzzlePaint = Paint().apply {
            color = if (stage == 1) Color.RED else Color.YELLOW
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        // Center muzzle
        canvas.drawRect(x - 28f, centerBarrelY2, x + 28f, centerBarrelY2 + 15f, muzzlePaint)
        // Left muzzle
        canvas.drawRect(x - width / 3f - 20f, leftBarrelY2, x - width / 3f + 20f, leftBarrelY2 + 12f, muzzlePaint)
        // Right muzzle
        canvas.drawRect(x + width / 3f - 20f, rightBarrelY2, x + width / 3f + 20f, rightBarrelY2 + 12f, muzzlePaint)
    }

    fun getBounds(): RectF = RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
}
