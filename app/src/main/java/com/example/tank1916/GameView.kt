package com.example.tank1916

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.Random

enum class GameState {
    TITLE, PLAYING, PAUSED, GAME_OVER, STAGE_CLEAR, FINAL_CLEAR, SKIN_SELECT, SKILL_SELECT
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: MainThread? = null
    private val paint = Paint()

    private var playerX = 0f
    private var playerY = 0f
    private var targetPlayerX = 0f
    private var targetPlayerY = 0f
    private val playerSize = 100f

    private val bullets = mutableListOf<Bullet>()
    private var lastFireTime = 0L
    private val fireDelay = 300L 
    private val bulletSpeed = 25f

    private val enemies = mutableListOf<Enemy>()
    private var lastWaveTime = 0L
    private val waveDelay = 2500L 
    private var waveCount = 0 
    private val random = Random()

    private var bgOffsetY = 0f
    private val scrollSpeed = 15f

    private var playerHp = 3
    private val maxHp = 3
    private var score = 0
    private var bestScore = 0 
    private var gameState = GameState.TITLE 
    private var weaponLevel = 1 
    private var currentStage = 1 

    private var stageProgress = 0f 
    private val stageDurationMs = 60000L 
    private var lastUpdateTime = 0L
    private var stageElapsedTimeMs = 0L

    private val items = mutableListOf<Item>()
    private val itemDropProbability = 0.2f 

    private val effects = mutableListOf<Effect>()
    private var invincibleTimer = 0 
    private var shakeTimer = 0
    
    private var skillGauge = 0
    private val skillGaugeMax = 100
    private var pendingSkillUse = false
    private var skillTextTimer = 0
    private val skillButtonRect = RectF()

    private var boss: Boss? = null
    private val bossBullets = mutableListOf<BossBullet>()

    private val pauseBtnRect = RectF()
    private val startButtonRect = RectF()
    private val skinButtonRect = RectF()
    private val titleSkillButtonRect = RectF()
    private val defaultSkinRect = RectF()
    private val desertSkinRect = RectF()
    private val heavySkinRect = RectF()
    private val skinBackButtonRect = RectF()
    private val artillerySkillRect = RectF()
    private val shieldSkillRect = RectF()
    private val repairSkillRect = RectF()
    private val skillBackButtonRect = RectF()

    private var selectedSkinName = "DEFAULT"
    private var selectedSkillName = "ARTILLERY"

    init {
        holder.addCallback(this)
        isFocusable = true
        loadBestScore()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        playerX = width / 2f
        playerY = height - 250f
        targetPlayerX = playerX
        targetPlayerY = playerY
        thread = MainThread(holder, this)
        thread?.isRunning = true
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        thread?.isRunning = false
        while (retry) {
            try {
                thread?.join()
                retry = false
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun useSkill() {
        shakeTimer = 40
        skillTextTimer = 45

        // 1. Deal massive damage to all active ordinary enemies
        for (enemy in enemies) {
            if (enemy.isActive) {
                enemy.takeDamage(10)
                if (!enemy.isActive) {
                    score += enemy.scoreValue
                    effects.add(Effect(enemy.x, enemy.y, EffectType.EXPLOSION))
                }
            }
        }

        // 2. Deal limited damage to boss
        val b = boss
        if (b != null && b.isActive) {
            val dmg = if (currentStage == 1) 10 else 12
            b.takeDamage(dmg)
            effects.add(Effect(b.x, b.y, EffectType.EXPLOSION))
            if (!b.isActive) {
                score += 5000 + (currentStage * 1000)
                for (i in 0..10) {
                    effects.add(Effect(b.x + random.nextInt(200) - 100f, b.y + random.nextInt(200) - 100f, EffectType.EXPLOSION))
                }
                gameState = if (currentStage == 2) GameState.FINAL_CLEAR else GameState.STAGE_CLEAR
                if (gameState == GameState.FINAL_CLEAR) saveBestScore()
                boss = null
            }
        }

        // 3. Clear all boss bullets
        bossBullets.forEach { it.isActive = false }
        bossBullets.clear()

        // 4. Generate multiple random screen-wide explosions
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        for (i in 0..8) {
            val rx = random.nextFloat() * screenW
            val ry = random.nextFloat() * screenH
            effects.add(Effect(rx, ry, EffectType.EXPLOSION))
        }

        skillGauge = 0
    }

    private fun increaseSkillGauge(type: EnemyType) {
        val gain = when (type) {
            EnemyType.BASIC -> 10
            EnemyType.FAST -> 12
            EnemyType.ZIGZAG -> 15
            EnemyType.STRONG -> 20
        }
        skillGauge = Math.min(skillGaugeMax, skillGauge + gain)
    }

    fun update() {
        if (gameState != GameState.PLAYING) {
            lastUpdateTime = System.currentTimeMillis()
            return 
        }

        if (pendingSkillUse) {
            useSkill()
            pendingSkillUse = false
        }
        if (skillTextTimer > 0) skillTextTimer--

        val currentTime = System.currentTimeMillis()
        var deltaTime = currentTime - lastUpdateTime
        if (deltaTime < 0 || deltaTime > 100L) {
            deltaTime = 16L
        }
        lastUpdateTime = currentTime

        stageElapsedTimeMs += deltaTime
        stageProgress = (stageElapsedTimeMs.toFloat() / stageDurationMs) * 100f
        if (stageProgress > 100f) stageProgress = 100f

        // Clamp target position to keep tank within screen boundaries before doing Lerp
        targetPlayerX = Math.max(playerSize/2, Math.min(width.toFloat() - playerSize/2, targetPlayerX))
        targetPlayerY = Math.max(playerSize, Math.min(height.toFloat() - playerSize/2, targetPlayerY))

        // Smooth player movement interpolation (Lerp)
        playerX += (targetPlayerX - playerX) * 0.4f
        playerY += (targetPlayerY - playerY) * 0.4f
        playerX = Math.max(playerSize/2, Math.min(width - playerSize/2, playerX))
        playerY = Math.max(playerSize, Math.min(height - playerSize/2, playerY))

        bgOffsetY += scrollSpeed
        val maxBgOffset = if (currentStage == 1) 200f else 400f
        if (bgOffsetY > maxBgOffset) bgOffsetY = 0f

        if (shakeTimer > 0) shakeTimer--

        val dynamicFireDelay = if (weaponLevel >= 4) 200L else fireDelay
        if (currentTime - lastFireTime > dynamicFireDelay) {
            fireWeapon()
            lastFireTime = currentTime
        }

        var dynamicWaveDelay = Math.max(1000L, waveDelay - (score / 1000) * 100L)
        if (currentStage >= 2) dynamicWaveDelay = (dynamicWaveDelay * 0.8f).toLong()

        if (stageProgress < 95f && currentTime - lastWaveTime > dynamicWaveDelay) {
            spawnEnemyWave()
            lastWaveTime = currentTime
            waveCount++
        } else if (stageProgress >= 95f && boss == null && enemies.isEmpty()) {
            boss = Boss(width / 2f, -300f, currentStage)
        }

        val b = boss
        if (b != null) {
            b.update(width.toFloat(), bossBullets)
            if (!b.isActive) {
                score += 5000 + (currentStage * 1000)
                for (i in 0..10) {
                    effects.add(Effect(b.x + random.nextInt(200) - 100f, b.y + random.nextInt(200) - 100f, EffectType.EXPLOSION))
                }
                gameState = if (currentStage == 2) GameState.FINAL_CLEAR else GameState.STAGE_CLEAR
                if (gameState == GameState.FINAL_CLEAR) saveBestScore()
                boss = null
            }
        }
        bossBullets.forEach { it.update() }

        bullets.forEach { it.update() }
        enemies.forEach { it.update() }
        items.forEach { it.update() }
        effects.forEach { it.update() }

        if (invincibleTimer > 0) invincibleTimer--

        val playerBounds = RectF(playerX - playerSize/2, playerY - playerSize/2, playerX + playerSize/2, playerY + playerSize/2)

        for (enemy in enemies) {
            if (!enemy.isActive) continue
            for (bullet in bullets) {
                if (bullet.isActive && RectF.intersects(bullet.getBounds(), enemy.getBounds())) {
                    bullet.isActive = false
                    enemy.takeDamage()
                    effects.add(Effect(bullet.x, bullet.y, EffectType.HIT_SPARK, maxLifeTime = 10))
                    if (!enemy.isActive) {
                        score += enemy.scoreValue
                        checkItemDrop(enemy.x, enemy.y)
                        effects.add(Effect(enemy.x, enemy.y, EffectType.EXPLOSION))
                        increaseSkillGauge(enemy.type)
                    }
                    break
                }
            }

            if (invincibleTimer <= 0 && enemy.isActive && RectF.intersects(playerBounds, enemy.getBounds())) {
                enemy.isActive = false
                playerHp--
                invincibleTimer = 60
                shakeTimer = 15
                effects.add(Effect(playerX, playerY, EffectType.EXPLOSION))
                if (playerHp <= 0) { playerHp = 0; gameState = GameState.GAME_OVER }
            }
        }

        if (b != null && b.isActive) {
            for (bullet in bullets) {
                if (bullet.isActive && RectF.intersects(bullet.getBounds(), b.getBounds())) {
                    bullet.isActive = false
                    b.takeDamage(1)
                    effects.add(Effect(bullet.x, bullet.y, EffectType.HIT_SPARK, maxLifeTime = 10))
                }
            }
        }

        if (invincibleTimer <= 0) {
            for (bb in bossBullets) {
                if (bb.isActive && RectF.intersects(bb.getBounds(), playerBounds)) {
                    bb.isActive = false
                    playerHp--
                    invincibleTimer = 60
                    shakeTimer = 15
                    effects.add(Effect(playerX, playerY, EffectType.EXPLOSION))
                    if (playerHp <= 0) { playerHp = 0; gameState = GameState.GAME_OVER }
                    break
                }
            }
        }

        for (item in items) {
            if (item.isActive && RectF.intersects(playerBounds, item.getBounds())) {
                item.isActive = false
                applyItemEffect(item.type)
            }
        }

        bullets.removeAll { !it.isActive }
        enemies.removeAll { !it.isActive || it.isOffScreen(height) }
        items.removeAll { !it.isActive || it.isOffScreen(height) }
        effects.removeAll { !it.isActive }
        bossBullets.removeAll { !it.isActive }
    }

    private fun fireWeapon() {
        when (weaponLevel) {
            1 -> bullets.add(Bullet(playerX, playerY - playerSize, bulletSpeed))
            2 -> {
                bullets.add(Bullet(playerX - 25f, playerY - playerSize, bulletSpeed))
                bullets.add(Bullet(playerX + 25f, playerY - playerSize, bulletSpeed))
            }
            3 -> {
                bullets.add(Bullet(playerX, playerY - playerSize, bulletSpeed))
                bullets.add(Bullet(playerX - 30f, playerY - playerSize, bulletSpeed, -5f))
                bullets.add(Bullet(playerX + 30f, playerY - playerSize, bulletSpeed, 5f))
            }
            else -> {
                bullets.add(Bullet(playerX, playerY - playerSize, bulletSpeed))
                bullets.add(Bullet(playerX - 25f, playerY - playerSize, bulletSpeed))
                bullets.add(Bullet(playerX + 25f, playerY - playerSize, bulletSpeed))
                bullets.add(Bullet(playerX - 50f, playerY - playerSize, bulletSpeed, -8f))
                bullets.add(Bullet(playerX + 50f, playerY - playerSize, bulletSpeed, 8f))
            }
        }
    }

    private fun checkItemDrop(x: Float, y: Float) {
        if (random.nextFloat() < itemDropProbability) {
            val type = if (random.nextBoolean()) ItemType.POWER else ItemType.HEAL
            items.add(Item(x, y, type))
        }
    }

    private fun applyItemEffect(type: ItemType) {
        when (type) {
            ItemType.POWER -> {
                weaponLevel++
                if (weaponLevel > 5) weaponLevel = 5
                effects.add(Effect(playerX, playerY - 50f, EffectType.PICKUP_TEXT, "+POWER"))
            }
            ItemType.HEAL -> {
                playerHp++
                if (playerHp > maxHp) playerHp = maxHp
                effects.add(Effect(playerX, playerY - 50f, EffectType.PICKUP_TEXT, "+HP"))
            }
        }
    }

    private fun spawnEnemyWave() {
        val screenWidth = width.toFloat()
        val laneCount = 5
        val laneWidth = screenWidth / laneCount
        val patternType = if (currentStage == 1) {
            when {
                stageProgress < 30f -> random.nextInt(3)
                stageProgress < 70f -> random.nextInt(6)
                else -> 3 + random.nextInt(3)
            }
        } else {
            when {
                stageProgress < 30f -> random.nextInt(4)
                else -> 2 + random.nextInt(4)
            }
        }

        when (patternType) {
            0 -> {
                for (i in 0 until laneCount) {
                    if (random.nextFloat() > 0.3f) {
                        val r = random.nextFloat()
                        val type = when {
                            r > 0.9f -> EnemyType.STRONG
                            r > 0.7f -> EnemyType.ZIGZAG
                            else -> EnemyType.BASIC
                        }
                        enemies.add(Enemy(laneWidth * i + laneWidth / 2, -100f, type))
                    }
                }
            }
            1 -> {
                val midX = screenWidth / 2
                enemies.add(Enemy(midX, -100f, EnemyType.BASIC))
                enemies.add(Enemy(midX - laneWidth, -200f, EnemyType.BASIC))
                enemies.add(Enemy(midX + laneWidth, -200f, EnemyType.BASIC))
                enemies.add(Enemy(midX - laneWidth * 2, -300f, EnemyType.BASIC))
                enemies.add(Enemy(midX + laneWidth * 2, -300f, EnemyType.BASIC))
            }
            2 -> {
                val midX = screenWidth / 2
                enemies.add(Enemy(midX, -100f, EnemyType.STRONG))
                enemies.add(Enemy(midX - laneWidth, -250f, EnemyType.ZIGZAG))
                enemies.add(Enemy(midX + laneWidth, -250f, EnemyType.ZIGZAG))
                enemies.add(Enemy(midX, -400f, EnemyType.BASIC))
            }
            3 -> {
                val lane = random.nextInt(laneCount)
                enemies.add(Enemy(laneWidth * lane + laneWidth / 2, -100f, EnemyType.FAST))
                enemies.add(Enemy(laneWidth * lane + laneWidth / 2, -250f, EnemyType.FAST))
                enemies.add(Enemy(laneWidth * lane + laneWidth / 2, -400f, EnemyType.FAST))
            }
            4 -> {
                enemies.add(Enemy(screenWidth * 0.25f, -100f, EnemyType.ZIGZAG))
                enemies.add(Enemy(screenWidth * 0.75f, -100f, EnemyType.ZIGZAG))
            }
            5 -> {
                enemies.add(Enemy(laneWidth * 1 + laneWidth / 2, -100f, EnemyType.STRONG))
                enemies.add(Enemy(laneWidth * 3 + laneWidth / 2, -100f, EnemyType.STRONG))
            }
        }
    }

    fun render(canvas: Canvas) {
        if (gameState == GameState.TITLE) {
            drawTitleScreen(canvas)
            return
        } else if (gameState == GameState.SKIN_SELECT) {
            drawSkinSelectScreen(canvas)
            return
        } else if (gameState == GameState.SKILL_SELECT) {
            drawSkillSelectScreen(canvas)
            return
        }

        val b = boss
        val isShake = shakeTimer > 0 || (b != null && b.y < 300f)
        if (isShake) {
            canvas.save()
            val rx = (random.nextFloat() - 0.5f) * 15f
            val ry = (random.nextFloat() - 0.5f) * 15f
            canvas.translate(rx, ry)
        }

        if (currentStage == 1) {
            canvas.drawColor(Color.DKGRAY)
            paint.color = Color.YELLOW; paint.strokeWidth = 15f
            var startY = bgOffsetY - 200f; val centerX = width / 2f
            while (startY < height) {
                canvas.drawLine(centerX, startY, centerX, startY + 100f, paint)
                startY += 200f
            }
        } else {
            canvas.drawColor(Color.rgb(210, 180, 140))
            paint.color = Color.rgb(180, 150, 110); paint.strokeWidth = 0f
            var startY = bgOffsetY - 400f
            while (startY < height) {
                canvas.drawCircle(width * 0.2f, startY + 100f, 60f, paint)
                canvas.drawCircle(width * 0.8f, startY + 300f, 80f, paint)
                startY += 400f
            }
        }

        bullets.forEach { it.draw(canvas) }
        enemies.forEach { it.draw(canvas) }
        items.forEach { it.draw(canvas) }
        effects.forEach { it.draw(canvas) }

        if (gameState != GameState.GAME_OVER && gameState != GameState.PAUSED) {
            if (!(invincibleTimer > 0 && (invincibleTimer / 5) % 2 == 0)) {
                val bodyColor: Int
                val cannonColor: Int
                when (selectedSkinName) {
                    "DESERT" -> {
                        bodyColor = Color.rgb(210, 180, 100)
                        cannonColor = Color.rgb(120, 90, 50)
                    }
                    "HEAVY" -> {
                        bodyColor = Color.rgb(70, 80, 70)
                        cannonColor = Color.rgb(40, 50, 40)
                    }
                    else -> { // DEFAULT
                        bodyColor = Color.GREEN
                        cannonColor = Color.rgb(50, 100, 50)
                    }
                }
                paint.color = bodyColor
                canvas.drawRect(playerX - playerSize/2, playerY - playerSize/2, playerX + playerSize/2, playerY + playerSize/2, paint)
                paint.color = cannonColor
                canvas.drawRect(playerX - 15f, playerY - playerSize, playerX + 15f, playerY, paint)
            }
        }

        // --- HUD Readability: Draw HUD background bar ---
        paint.color = Color.argb(120, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), 180f, paint)

        // Draw HUD Text & HP Hearts
        paint.color = Color.WHITE; paint.textSize = 60f; paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = false
        canvas.drawText("HP: ", 50f, 100f, paint)
        val hpTextWidth = paint.measureText("HP: ")
        
        var heartX = 50f + hpTextWidth
        for (i in 0 until maxHp) {
            paint.color = if (i < playerHp) Color.RED else Color.rgb(80, 80, 80)
            canvas.drawText("♥", heartX, 100f, paint)
            heartX += 55f
        }
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PWR: $weaponLevel", width / 2f, 100f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Score: $score", width - 180f, 100f, paint) // Shift slightly left to avoid overlapping Pause button
        paint.textAlign = Paint.Align.CENTER; paint.textSize = 40f
        canvas.drawText("STAGE $currentStage", width / 2f, 50f, paint)

        // --- Pause Button Configuration and Draw ---
        val btnSize = 80f
        val margin = 50f
        pauseBtnRect.set(width - btnSize - margin, margin, width - margin, margin + btnSize)

        // Draw Pause button border
        paint.color = Color.rgb(200, 200, 200)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(pauseBtnRect, 15f, 15f, paint)

        // Draw "||" lines
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val barW = 10f
        val barH = 40f
        val gap = 15f
        val centerX = pauseBtnRect.centerX()
        val centerY = pauseBtnRect.centerY()
        canvas.drawRect(centerX - barW - gap/2, centerY - barH/2, centerX - gap/2, centerY + barH/2, paint)
        canvas.drawRect(centerX + gap/2, centerY - barH/2, centerX + barW + gap/2, centerY + barH/2, paint)

        // Boss / Stage Progress Bar
        if (b == null) {
            paint.color = Color.GRAY
            val barWidth = width * 0.8f; val barX = width * 0.1f; val barY = 150f
            canvas.drawRect(barX, barY, barX + barWidth, barY + 20f, paint)
            paint.color = Color.CYAN
            canvas.drawRect(barX, barY, barX + barWidth * (stageProgress / 100f), barY + 20f, paint)
        } else {
            paint.color = Color.DKGRAY
            val barWidth = width * 0.9f; val barX = width * 0.05f; val barY = 150f
            canvas.drawRect(barX, barY, barX + barWidth, barY + 30f, paint)
            paint.color = Color.RED
            val hpRatio = b.hp.toFloat() / b.maxHp
            canvas.drawRect(barX, barY, barX + barWidth * hpRatio, barY + 30f, paint)
            paint.color = Color.WHITE; paint.textSize = 30f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("BOSS: ${b.bossName}", width / 2f, barY + 25f, paint)

            if (b.y < 300f) {
                val blink = (System.currentTimeMillis() / 250) % 2 == 0L
                if (blink) {
                    paint.color = Color.RED
                    paint.textSize = 100f
                    paint.textAlign = Paint.Align.CENTER
                    paint.isFakeBoldText = true
                    canvas.drawText("⚠️ WARNING ⚠️", width / 2f, height / 3f, paint)
                    paint.textSize = 50f
                    canvas.drawText("BOSS APPROACHING", width / 2f, height / 3f + 80f, paint)
                }
            }
        }

        // --- Skill Button Configuration and Draw ---
        val sBtnWidth = 240f
        val sBtnHeight = 110f
        val sBtnMargin = 50f
        val sBtnX = width - sBtnWidth/2 - sBtnMargin
        val sBtnY = height - sBtnHeight/2 - sBtnMargin - 100f
        skillButtonRect.set(sBtnX - sBtnWidth/2, sBtnY - sBtnHeight/2, sBtnX + sBtnWidth/2, sBtnY + sBtnHeight/2)

        val isReady = skillGauge >= skillGaugeMax
        if (isReady) {
            paint.color = Color.rgb(255, 69, 0) // Orange Red
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(skillButtonRect, 20f, 20f, paint)
            
            paint.color = Color.rgb(255, 215, 0) // Gold
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawRoundRect(skillButtonRect, 20f, 20f, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            val textY = sBtnY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText("$selectedSkillName READY", sBtnX, textY, paint)
        } else {
            paint.color = Color.argb(140, 50, 50, 50)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(skillButtonRect, 20f, 20f, paint)
            
            paint.color = Color.argb(140, 100, 100, 100)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRoundRect(skillButtonRect, 20f, 20f, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.rgb(180, 180, 180)
            paint.textSize = 26f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = false
            val textY = sBtnY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText("$selectedSkillName ${skillGauge}%", sBtnX, textY, paint)
        }

        // Draw "ARTILLERY STRIKE!" overlay in center
        if (skillTextTimer > 0) {
            paint.color = Color.RED
            paint.textSize = 90f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.alpha = (255 * (skillTextTimer.toFloat() / 45f)).toInt()
            canvas.drawText("💥 ARTILLERY STRIKE! 💥", width / 2f, height / 2f, paint)
        }

        if (gameState == GameState.GAME_OVER) drawGameOverScreen(canvas)
        else if (gameState == GameState.STAGE_CLEAR) drawStageClearScreen(canvas)
        else if (gameState == GameState.PAUSED) drawPausedScreen(canvas)

        if (isShake) {
            canvas.restore()
        }
    }

    private fun drawGameOverScreen(canvas: Canvas) {
        paint.color = Color.argb(180, 0, 0, 0); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.RED; paint.textSize = 120f
        canvas.drawText("GAME OVER", width / 2f, height / 2f - 50f, paint)
        paint.color = Color.WHITE; paint.textSize = 80f
        canvas.drawText("Final Score: $score", width / 2f, height / 2f + 80f, paint)
        paint.textSize = 50f; canvas.drawText("Touch to Restart", width / 2f, height / 2f + 200f, paint)
    }

    private fun drawStageClearScreen(canvas: Canvas) {
        paint.color = Color.argb(180, 0, 0, 100); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.GREEN; paint.textSize = 120f
        val clearText = if (currentStage == 2) "FINAL CLEAR!" else "STAGE CLEAR!"
        canvas.drawText(clearText, width / 2f, height / 2f - 50f, paint)
        paint.color = Color.WHITE; paint.textSize = 80f
        canvas.drawText("Score: $score", width / 2f, height / 2f + 80f, paint)
        paint.textSize = 50f
        val nextText = if (currentStage == 2) "Touch to Restart" else "Touch to Next Stage"
        canvas.drawText(nextText, width / 2f, height / 2f + 200f, paint)
    }

    private fun drawTitleScreen(canvas: Canvas) {
        // Background: very dark gray/black
        canvas.drawColor(Color.rgb(20, 20, 20))

        // Title: "1916"
        paint.color = Color.RED
        paint.textSize = 180f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("1916", width / 2f, height / 3f, paint)

        // Subtitle: "Tank Arcade Shooter"
        paint.color = Color.rgb(200, 200, 200)
        paint.textSize = 50f
        paint.isFakeBoldText = false
        canvas.drawText("Tank Arcade Shooter", width / 2f, height / 3f + 80f, paint)

        // Best Score
        paint.color = Color.YELLOW
        paint.textSize = 40f
        canvas.drawText("BEST SCORE: $bestScore", width / 2f, height / 3f + 160f, paint)

        // Selection State Display
        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        canvas.drawText("SKIN: $selectedSkinName", width / 2f, height * 0.40f, paint)
        canvas.drawText("SKILL: $selectedSkillName", width / 2f, height * 0.45f, paint)

        // Button dimensions
        val btnWidth = 450f
        val btnHeight = 120f
        val btnX = width / 2f

        // 1. START Button
        val startY = height * 0.55f
        startButtonRect.set(btnX - btnWidth/2, startY - btnHeight/2, btnX + btnWidth/2, startY + btnHeight/2)

        paint.color = Color.rgb(34, 139, 34) // Forest Green
        canvas.drawRoundRect(startButtonRect, 25f, 25f, paint)
        paint.color = Color.rgb(50, 205, 50) // Lime Green
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawRoundRect(startButtonRect, 25f, 25f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 50f
        paint.isFakeBoldText = true
        var textY = startY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("START", btnX, textY, paint)

        // 2. SKIN Button
        val skinY = height * 0.65f
        skinButtonRect.set(btnX - btnWidth/2, skinY - btnHeight/2, btnX + btnWidth/2, skinY + btnHeight/2)

        paint.color = Color.rgb(47, 79, 79) // Slate Gray
        canvas.drawRoundRect(skinButtonRect, 25f, 25f, paint)
        paint.color = Color.rgb(0, 255, 255) // Cyan
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawRoundRect(skinButtonRect, 25f, 25f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 50f
        paint.isFakeBoldText = true
        textY = skinY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("SKIN", btnX, textY, paint)

        // 3. SKILL Button
        val skillY = height * 0.75f
        titleSkillButtonRect.set(btnX - btnWidth/2, skillY - btnHeight/2, btnX + btnWidth/2, skillY + btnHeight/2)

        paint.color = Color.rgb(72, 61, 139) // Dark Slate Blue
        canvas.drawRoundRect(titleSkillButtonRect, 25f, 25f, paint)
        paint.color = Color.rgb(255, 215, 0) // Gold
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawRoundRect(titleSkillButtonRect, 25f, 25f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 50f
        paint.isFakeBoldText = true
        textY = skillY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("SKILL", btnX, textY, paint)

        // Instruction Text
        paint.color = Color.rgb(150, 150, 150)
        paint.textSize = 35f
        paint.isFakeBoldText = false
        canvas.drawText("Touch START to Play", width / 2f, height * 0.85f, paint)
    }

    private fun drawSkinSelectScreen(canvas: Canvas) {
        // Background
        canvas.drawColor(Color.rgb(20, 20, 20))

        // Title: "SELECT TANK"
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("SELECT TANK", width / 2f, height * 0.15f, paint)

        // Card Configuration
        val cardWidth = width * 0.85f
        val cardHeight = 180f
        val cardX = width / 2f

        // 1. DEFAULT Skin Card
        val defaultY = height * 0.32f
        defaultSkinRect.set(cardX - cardWidth/2, defaultY - cardHeight/2, cardX + cardWidth/2, defaultY + cardHeight/2)
        drawSkinCard(canvas, defaultSkinRect, "DEFAULT", "Balanced", Color.GREEN, Color.rgb(50, 100, 50))

        // 2. DESERT Skin Card
        val desertY = height * 0.50f
        desertSkinRect.set(cardX - cardWidth/2, desertY - cardHeight/2, cardX + cardWidth/2, desertY + cardHeight/2)
        drawSkinCard(canvas, desertSkinRect, "DESERT", "Desert Type", Color.rgb(210, 180, 100), Color.rgb(120, 90, 50))

        // 3. HEAVY Skin Card
        val heavyY = height * 0.68f
        heavySkinRect.set(cardX - cardWidth/2, heavyY - cardHeight/2, cardX + cardWidth/2, heavyY + cardHeight/2)
        drawSkinCard(canvas, heavySkinRect, "HEAVY", "Heavy Armor", Color.rgb(70, 80, 70), Color.rgb(40, 50, 40))

        // BACK Button
        val backWidth = 400f
        val backHeight = 110f
        val backY = height * 0.85f
        skinBackButtonRect.set(cardX - backWidth/2, backY - backHeight/2, cardX + backWidth/2, backY + backHeight/2)

        // Draw BACK Button background
        paint.color = Color.rgb(50, 50, 60)
        canvas.drawRoundRect(skinBackButtonRect, 20f, 20f, paint)
        paint.color = Color.rgb(180, 180, 180)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(skinBackButtonRect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val textY = backY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("BACK", cardX, textY, paint)
    }

    private fun drawSkinCard(canvas: Canvas, rect: RectF, name: String, desc: String, bodyColor: Int, cannonColor: Int) {
        val isSelected = selectedSkinName == name

        // Draw Card Background
        paint.color = Color.rgb(35, 35, 35)
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        // Draw Card Border
        if (isSelected) {
            paint.color = Color.rgb(0, 255, 255) // Neon Cyan
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
        } else {
            paint.color = Color.rgb(70, 70, 70)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        // Draw Tank Preview
        val previewX = rect.left + 90f
        val previewY = rect.centerY()
        val tankSize = 55f

        // Draw preview cannon
        paint.color = cannonColor
        canvas.drawRect(previewX - 10f, previewY - tankSize/2 - 15f, previewX + 10f, previewY, paint)
        // Draw preview body
        paint.color = bodyColor
        canvas.drawRect(previewX - tankSize/2, previewY - tankSize/2, previewX + tankSize/2, previewY + tankSize/2, paint)

        // Draw Skin Information Texts
        val textStartX = rect.left + 180f
        paint.textAlign = Paint.Align.LEFT

        // Name
        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        canvas.drawText(name, textStartX, rect.centerY() - 10f, paint)

        // Description
        paint.color = Color.rgb(180, 180, 180)
        paint.textSize = 30f
        paint.isFakeBoldText = false
        canvas.drawText(desc, textStartX, rect.centerY() + 35f, paint)
    }

    private fun drawSkillSelectScreen(canvas: Canvas) {
        // Background
        canvas.drawColor(Color.rgb(20, 20, 20))

        // Title: "SELECT SKILL"
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("SELECT SKILL", width / 2f, height * 0.15f, paint)

        // Card Configuration
        val cardWidth = width * 0.85f
        val cardHeight = 180f
        val cardX = width / 2f

        // 1. ARTILLERY Card
        val artilleryY = height * 0.32f
        artillerySkillRect.set(cardX - cardWidth/2, artilleryY - cardHeight/2, cardX + cardWidth/2, artilleryY + cardHeight/2)
        drawSkillCard(canvas, artillerySkillRect, "ARTILLERY", "Area Strike", "💥")

        // 2. SHIELD Card
        val shieldY = height * 0.50f
        shieldSkillRect.set(cardX - cardWidth/2, shieldY - cardHeight/2, cardX + cardWidth/2, shieldY + cardHeight/2)
        drawSkillCard(canvas, shieldSkillRect, "SHIELD", "Temporary Guard", "🛡️")

        // 3. REPAIR Card
        val repairY = height * 0.68f
        repairSkillRect.set(cardX - cardWidth/2, repairY - cardHeight/2, cardX + cardWidth/2, repairY + cardHeight/2)
        drawSkillCard(canvas, repairSkillRect, "REPAIR", "Restore 1 HP", "➕")

        // BACK Button
        val backWidth = 400f
        val backHeight = 110f
        val backY = height * 0.85f
        skillBackButtonRect.set(cardX - backWidth/2, backY - backHeight/2, cardX + backWidth/2, backY + backHeight/2)

        // Draw BACK Button background
        paint.color = Color.rgb(50, 50, 60)
        canvas.drawRoundRect(skillBackButtonRect, 20f, 20f, paint)
        paint.color = Color.rgb(180, 180, 180)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(skillBackButtonRect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val textY = backY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("BACK", cardX, textY, paint)
    }

    private fun drawSkillCard(canvas: Canvas, rect: RectF, name: String, desc: String, icon: String) {
        val isSelected = selectedSkillName == name

        // Draw Card Background
        paint.color = Color.rgb(35, 35, 35)
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        // Draw Card Border
        if (isSelected) {
            paint.color = Color.rgb(0, 255, 255) // Neon Cyan
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
        } else {
            paint.color = Color.rgb(70, 70, 70)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        // Draw Skill Preview Icon/Emoji
        val previewX = rect.left + 90f
        val previewY = rect.centerY()
        
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        val iconY = previewY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(icon, previewX, iconY, paint)

        // Draw Skill Information Texts
        val textStartX = rect.left + 180f
        paint.textAlign = Paint.Align.LEFT

        // Name
        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        canvas.drawText(name, textStartX, rect.centerY() - 10f, paint)

        // Description
        paint.color = Color.rgb(180, 180, 180)
        paint.textSize = 30f
        paint.isFakeBoldText = false
        canvas.drawText(desc, textStartX, rect.centerY() + 35f, paint)
    }

    private fun drawPausedScreen(canvas: Canvas) {
        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 120f
        paint.isFakeBoldText = true
        canvas.drawText("PAUSED", width / 2f, height / 2f - 50f, paint)
        paint.textSize = 50f
        paint.isFakeBoldText = false
        canvas.drawText("Touch to Resume", width / 2f, height / 2f + 50f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(holder) {
            if (event.action != MotionEvent.ACTION_DOWN) {
                if (gameState == GameState.PLAYING) {
                    targetPlayerX = event.x
                    targetPlayerY = event.y - 150f
                }
                return true
            }

            when (gameState) {
                GameState.TITLE -> {
                    if (startButtonRect.contains(event.x, event.y)) {
                        restartGame()
                    } else if (skinButtonRect.contains(event.x, event.y)) {
                        gameState = GameState.SKIN_SELECT
                    } else if (titleSkillButtonRect.contains(event.x, event.y)) {
                        gameState = GameState.SKILL_SELECT
                    }
                }
                GameState.SKIN_SELECT -> {
                    if (defaultSkinRect.contains(event.x, event.y)) {
                        selectedSkinName = "DEFAULT"
                    } else if (desertSkinRect.contains(event.x, event.y)) {
                        selectedSkinName = "DESERT"
                    } else if (heavySkinRect.contains(event.x, event.y)) {
                        selectedSkinName = "HEAVY"
                    } else if (skinBackButtonRect.contains(event.x, event.y)) {
                        gameState = GameState.TITLE
                    }
                }
                GameState.SKILL_SELECT -> {
                    if (artillerySkillRect.contains(event.x, event.y)) {
                        selectedSkillName = "ARTILLERY"
                    } else if (shieldSkillRect.contains(event.x, event.y)) {
                        selectedSkillName = "SHIELD"
                    } else if (repairSkillRect.contains(event.x, event.y)) {
                        selectedSkillName = "REPAIR"
                    } else if (skillBackButtonRect.contains(event.x, event.y)) {
                        gameState = GameState.TITLE
                    }
                }
                GameState.PLAYING -> {
                    if (pauseBtnRect.contains(event.x, event.y)) {
                        gameState = GameState.PAUSED
                    } else if (skillButtonRect.contains(event.x, event.y)) {
                        if (skillGauge >= skillGaugeMax) {
                            if (selectedSkillName == "ARTILLERY") {
                                pendingSkillUse = true
                            } else {
                                post {
                                    android.widget.Toast.makeText(context, "$selectedSkillName activated! (Effect coming soon)", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                skillGauge = 0
                            }
                        }
                    } else {
                        targetPlayerX = event.x
                        targetPlayerY = event.y - 150f
                    }
                }
                GameState.PAUSED -> gameState = GameState.PLAYING
                GameState.STAGE_CLEAR -> startNextStage()
                GameState.GAME_OVER, GameState.FINAL_CLEAR -> gameState = GameState.TITLE
            }
            return true
        }
    }

    private fun restartGame() {
        playerHp = maxHp
        score = 0
        weaponLevel = 1
        currentStage = 1
        skillGauge = 0
        pendingSkillUse = false
        skillTextTimer = 0
        resetStageState()
        invincibleTimer = 0
        playerX = width / 2f
        playerY = height - 250f
        targetPlayerX = playerX
        targetPlayerY = playerY
    }

    private fun startNextStage() {
        currentStage++
        resetStageState()
        playerHp = Math.min(maxHp, playerHp + 1)
    }

    private fun resetStageState() {
        gameState = GameState.PLAYING
        stageProgress = 0f
        stageElapsedTimeMs = 0L
        lastUpdateTime = System.currentTimeMillis()
        lastWaveTime = System.currentTimeMillis()
        lastFireTime = System.currentTimeMillis()
        waveCount = 0
        shakeTimer = 0
        pendingSkillUse = false
        skillTextTimer = 0
        enemies.clear()
        bullets.clear()
        items.clear()
        effects.clear()
        bossBullets.clear()
        boss = null
    }

    private fun loadBestScore() {
        val prefs = context.getSharedPreferences("TankGamePrefs", Context.MODE_PRIVATE)
        bestScore = prefs.getInt("BestScore", 0)
    }

    private fun saveBestScore() {
        if (score > bestScore) {
            bestScore = score
            val prefs = context.getSharedPreferences("TankGamePrefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("BestScore", bestScore).apply()
        }
    }

    fun pause() {
        synchronized(holder) {
            if (gameState == GameState.PLAYING) {
                gameState = GameState.PAUSED
            }
        }
    }

    fun resume() {
        synchronized(holder) {
            lastUpdateTime = System.currentTimeMillis()
        }
    }
}
