package com.example.tank1916

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.Random

enum class GameState {
    TITLE, PLAYING, PAUSED, GAME_OVER, STAGE_CLEAR, FINAL_CLEAR, SKIN_SELECT, SKILL_SELECT
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: MainThread? = null
    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
        isSubpixelText = true
    }

    // Interactive Pressed State Tracker
    private var pressedRect: RectF? = null

    // Cached Gradients
    private var bgRadialGradient: RadialGradient? = null
    private var startButtonGradient: LinearGradient? = null

    // Reusable RectFs for status bar rendering to avoid allocations in draw call
    private val statusNotchRect = RectF()
    private val statusBatRect = RectF()
    private val statusBatTipRect = RectF()
    private val statusBatLevelRect = RectF()
    private val statusWifiArc1 = RectF()
    private val statusWifiArc2 = RectF()

    // Reusable paths for camo drawing
    private val camoPath1 = Path()
    private val camoPath2 = Path()
    private val heartPath = Path()

    // Grain drawing configuration (to avoid allocations in onDraw)
    private val grainCount = 12
    private val grainX = floatArrayOf(0.2f, 0.5f, 0.8f, 0.3f, 0.7f, 0.1f, 0.9f, 0.4f, 0.6f, 0.25f, 0.75f, 0.5f)
    private val grainY = floatArrayOf(0.12f, 0.28f, 0.35f, 0.45f, 0.52f, 0.68f, 0.72f, 0.83f, 0.91f, 0.58f, 0.18f, 0.78f)
    private val grainSizes = floatArrayOf(1.5f, 2f, 1.2f, 2.2f, 1.5f, 1.8f, 2.5f, 1.3f, 2f, 1.6f, 2.1f, 1.4f)
    private var soundPool: SoundPool? = null
    private var shootSoundId: Int = 0
    private var boomSoundId: Int = 0
    private var healSoundId: Int = 0
    private var shieldSoundId: Int = 0
    private var hitSoundId: Int = 0
    private var repairSoundId: Int = 0
    private var bgmPlayer: MediaPlayer? = null
    private var currentBgmResId: Int = 0

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
    private val waveDelay = 1800L 
    private var waveCount = 0 
    private val random = Random()

    private var bgOffsetY = 0f
    private val scrollSpeed = 15f

    private var bodyBitmap: Bitmap? = null
    private var bigGunBitmap: Bitmap? = null
    private var rocketLauncherBitmap: Bitmap? = null
    private var trackBitmap: Bitmap? = null

    private var tank2BodyBmp: Bitmap? = null
    private var tank2TurretBmp: Bitmap? = null
    private var tank2BarrelBmp: Bitmap? = null
    private var tank3BodyBmp: Bitmap? = null

    private var enemyBasicBmp: Bitmap? = null
    private var enemyFastBmp: Bitmap? = null
    private var enemyStrongBmp: Bitmap? = null
    private var enemyZigzagBmp: Bitmap? = null
    
    private val treadMarks = mutableListOf<TreadMark>()
    private var treadSpawnTimer = 0

    private var playerHp = 3
    private val maxHp = 3
    private var score = 0
        set(value) {
            field = value
            if (value > bestScore) {
                bestScore = value
                saveBestScore()
            }
        }
    private var bestScore = 0 
    private var gameState = GameState.TITLE
        set(value) {
            field = value
            if (value == GameState.GAME_OVER || value == GameState.STAGE_CLEAR || value == GameState.FINAL_CLEAR) {
                saveBestScore()
            }
        }
    private var weaponLevel = 1 
    private var currentStage = 1 

    private var stageProgress = 0f 
    private val stageDurationMs = 60000L 
    private var lastUpdateTime = 0L
    private var stageElapsedTimeMs = 0L

    private val items = mutableListOf<Item>()
    private val itemDropProbability = 0.08f 

    private val effects = mutableListOf<Effect>()
    private var invincibleTimer = 0 
    private var shakeTimer = 0
    
    private var skillGauge = 0
    private val skillGaugeMax = 100
    private var pendingSkillUse = false
    private var shieldTimer = 0
    private var pendingShieldUse = false
    private var pendingRepairUse = false
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
        
        // Load tank skin sprite bitmaps with safe fallback
        try {
            bodyBitmap = BitmapFactory.decodeResource(resources, R.drawable.medium_tank)
            bigGunBitmap = BitmapFactory.decodeResource(resources, R.drawable.big_gun)
            rocketLauncherBitmap = BitmapFactory.decodeResource(resources, R.drawable.rocket_launcher)
            trackBitmap = BitmapFactory.decodeResource(resources, R.drawable.track)

            tank2BodyBmp = BitmapFactory.decodeResource(resources, R.drawable.tank2_body)
            tank2TurretBmp = BitmapFactory.decodeResource(resources, R.drawable.tank2_turret)
            tank2BarrelBmp = BitmapFactory.decodeResource(resources, R.drawable.tank2_barrel)
            tank3BodyBmp = BitmapFactory.decodeResource(resources, R.drawable.tank3_body)

            enemyBasicBmp = BitmapFactory.decodeResource(resources, R.drawable.enemy_basic)
            enemyFastBmp = BitmapFactory.decodeResource(resources, R.drawable.enemy_fast)
            enemyStrongBmp = BitmapFactory.decodeResource(resources, R.drawable.enemy_strong)
            enemyZigzagBmp = BitmapFactory.decodeResource(resources, R.drawable.enemy_zigzag)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        shootSoundId = soundPool?.load(context, R.raw.shooting, 1) ?: 0
        boomSoundId = soundPool?.load(context, R.raw.boom, 1) ?: 0
        healSoundId = soundPool?.load(context, R.raw.heal, 1) ?: 0
        shieldSoundId = soundPool?.load(context, R.raw.shei, 1) ?: 0
        hitSoundId = soundPool?.load(context, R.raw.thud_sfx, 1) ?: 0
        repairSoundId = soundPool?.load(context, R.raw.bumper, 1) ?: 0
        playBgm(R.raw.lobby)
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
        soundPool?.release()
        soundPool = null
        stopBgm()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initGradients(w, h)
    }

    private fun initGradients(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val radius = Math.max(w, h) * 0.9f
        val colors = intArrayOf(Color.argb(0, 0, 191, 255), Color.argb(40, 0, 150, 255), Color.argb(90, 0, 80, 200))
        val stops = floatArrayOf(0f, 0.6f, 1.0f)
        bgRadialGradient = RadialGradient(
            w / 2f, h / 2f, radius,
            colors, stops, Shader.TileMode.CLAMP
        )
        
        val startY = h * 0.55f
        startButtonGradient = LinearGradient(
            w / 2f - 225f, startY - 60f,
            w / 2f - 225f, startY + 60f,
            Color.rgb(46, 204, 113), Color.rgb(24, 106, 59),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawPremiumBackground(canvas: Canvas) {
        paint.color = 0xFF1E2228.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val gradient = bgRadialGradient
        if (gradient != null) {
            paint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
    }

    private fun drawSimulatedStatusBar(canvas: Canvas) {
        // Semi-transparent dark bar at the top
        paint.color = Color.argb(160, 26, 30, 36)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), 90f, paint)

        // Center rounded notch camera cutout
        paint.color = Color.BLACK
        statusNotchRect.set(width / 2f - 110f, -20f, width / 2f + 110f, 45f)
        canvas.drawRoundRect(statusNotchRect, 25f, 25f, paint)

        // Draw Clock (6:43) on the left
        paint.color = Color.WHITE
        paint.textSize = 34f
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("6:43", 55f, 57f, paint)

        // Draw standard status icons (battery, wifi, signal)
        // Signal (Cellular)
        val sigLeft = width - 210f
        paint.style = Paint.Style.FILL
        for (i in 0 until 4) {
            val barH = 10f + i * 5f
            val x = sigLeft + i * 9f
            canvas.drawRect(x, 57f - barH, x + 5f, 57f, paint)
        }

        // Wifi
        val wifiCenterX = width - 150f
        val wifiCenterY = 50f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        statusWifiArc1.set(wifiCenterX - 12f, wifiCenterY - 12f, wifiCenterX + 12f, wifiCenterY + 12f)
        canvas.drawArc(statusWifiArc1, -140f, 100f, false, paint)
        statusWifiArc2.set(wifiCenterX - 22f, wifiCenterY - 22f, wifiCenterX + 22f, wifiCenterY + 22f)
        canvas.drawArc(statusWifiArc2, -140f, 100f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(wifiCenterX, wifiCenterY + 4f, 4f, paint)

        // Battery
        val batLeft = width - 100f
        val batTop = 33f
        val batWidth = 46f
        val batHeight = 24f
        statusBatRect.set(batLeft, batTop, batLeft + batWidth, batTop + batHeight)
        statusBatTipRect.set(batLeft + batWidth, batTop + 7f, batLeft + batWidth + 4f, batTop + batHeight - 7f)
        statusBatLevelRect.set(batLeft + 4f, batTop + 4f, batLeft + 4f + (batWidth - 8f) * 0.8f, batTop + batHeight - 4f)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(statusBatRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(statusBatTipRect, 2f, 2f, paint)
        canvas.drawRoundRect(statusBatLevelRect, 3f, 3f, paint)
    }

    // Custom drawDesertBarrel method removed as we draw the actual barrel sprite now.

    private fun applyButtonScale(canvas: Canvas, rect: RectF): Boolean {
        if (pressedRect == rect) {
            canvas.save()
            canvas.scale(0.95f, 0.95f, rect.centerX(), rect.centerY())
            canvas.translate(0f, 5f)
            return true
        }
        return false
    }

    private fun applyTitleButtonPulse(canvas: Canvas, rect: RectF) {
        val time = System.currentTimeMillis()
        val pulse = 1.0f + 0.03f * Math.sin(time / 250.0).toFloat()
        val isPressed = pressedRect == rect
        canvas.save()
        if (isPressed) {
            canvas.scale(0.95f * pulse, 0.95f * pulse, rect.centerX(), rect.centerY())
            canvas.translate(0f, 5f)
        } else {
            canvas.scale(pulse, pulse, rect.centerX(), rect.centerY())
        }
    }

    private val tempDrawnRect = RectF()
    private fun getDrawnButtonRect(rect: RectF): RectF {
        if (pressedRect == rect) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val w = rect.width() * 0.95f
            val h = rect.height() * 0.95f
            tempDrawnRect.set(cx - w / 2f, cy - h / 2f + 5f, cx + w / 2f, cy + h / 2f + 5f)
            return tempDrawnRect
        }
        tempDrawnRect.set(rect)
        return tempDrawnRect
    }

    private fun useSkill() {
        if (boomSoundId != 0) {
            soundPool?.play(boomSoundId, 1f, 1f, 1, 0, 1f)
        }
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

    private fun useShield() {
        if (shieldSoundId != 0) {
            soundPool?.play(shieldSoundId, 1f, 1f, 1, 0, 1f)
        }
        shieldTimer = 180
        shakeTimer = 15
        effects.add(Effect(playerX, playerY - 50f, EffectType.PICKUP_TEXT, "+SHIELD"))
        skillGauge = 0
    }

    private fun useRepair() {
        if (playerHp < maxHp) {
            if (repairSoundId != 0) {
                soundPool?.play(repairSoundId, 1f, 1f, 1, 0, 1f)
            }
            playerHp++
            effects.add(Effect(playerX, playerY - 50f, EffectType.PICKUP_TEXT, "REPAIR +1"))
            skillGauge = 0
        }
    }

    private fun playBgm(resId: Int) {
        if (currentBgmResId == resId) return
        try {
            bgmPlayer?.stop()
            bgmPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentBgmResId = resId
        try {
            bgmPlayer = MediaPlayer.create(context, resId).apply {
                isLooping = true
                val volume = when (resId) {
                    R.raw.r1boss -> 1.0f
                    R.raw.r2boss -> 1.0f
                    else -> 0.6f
                }
                setVolume(volume, volume)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopBgm() {
        try {
            bgmPlayer?.stop()
            bgmPlayer?.release()
            bgmPlayer = null
            currentBgmResId = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBgm() {
        when (gameState) {
            GameState.TITLE, GameState.SKIN_SELECT, GameState.SKILL_SELECT -> {
                playBgm(R.raw.lobby)
            }
            GameState.PLAYING -> {
                val b = boss
                if (b != null && b.isActive) {
                    if (currentStage == 1) {
                        playBgm(R.raw.r1boss)
                    } else {
                        playBgm(R.raw.r2boss)
                    }
                } else {
                    if (currentStage == 1) {
                        playBgm(R.raw.round1)
                    } else {
                        playBgm(R.raw.round2)
                    }
                }
            }
            GameState.PAUSED -> {
                // Keep playing current BGM
            }
            GameState.GAME_OVER, GameState.STAGE_CLEAR, GameState.FINAL_CLEAR -> {
                playBgm(R.raw.lobby)
            }
        }
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
        updateBgm()
        if (gameState != GameState.PLAYING) {
            lastUpdateTime = System.currentTimeMillis()
            return 
        }

        if (pendingSkillUse) {
            useSkill()
            pendingSkillUse = false
        }
        if (pendingShieldUse) {
            useShield()
            pendingShieldUse = false
        }
        if (pendingRepairUse) {
            useRepair()
            pendingRepairUse = false
        }
        if (shieldTimer > 0) shieldTimer--
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
        if (bgOffsetY > maxBgOffset) {
            bgOffsetY -= maxBgOffset
        }

        // Spawn/update tread marks left by player tank
        val activeTrackBmp = trackBitmap
        if (activeTrackBmp != null) {
            treadSpawnTimer++
            if (treadSpawnTimer >= 5) {
                treadSpawnTimer = 0
                treadMarks.add(TreadMark(playerX, playerY + 30f))
            }
        }
        treadMarks.forEach { it.update(scrollSpeed) }
        treadMarks.removeAll { !it.isActive || it.y > height + 50f }

        if (shakeTimer > 0) shakeTimer--

        val dynamicFireDelay = if (weaponLevel >= 4) 200L else fireDelay
        if (currentTime - lastFireTime > dynamicFireDelay) {
            fireWeapon()
            lastFireTime = currentTime
        }

        var dynamicWaveDelay = Math.max(800L, waveDelay - (score / 1000) * 100L)
        if (currentStage >= 2) dynamicWaveDelay = (dynamicWaveDelay * 0.7f).toLong()

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

            if (enemy.isActive && RectF.intersects(playerBounds, enemy.getBounds())) {
                if (shieldTimer > 0) {
                    enemy.isActive = false
                    score += enemy.scoreValue
                    effects.add(Effect(enemy.x, enemy.y, EffectType.EXPLOSION))
                } else if (invincibleTimer <= 0) {
                    if (hitSoundId != 0) {
                        soundPool?.play(hitSoundId, 1f, 1f, 1, 0, 1f)
                    }
                    enemy.isActive = false
                    playerHp--
                    invincibleTimer = 60
                    shakeTimer = 15
                    effects.add(Effect(playerX, playerY, EffectType.EXPLOSION))
                    if (playerHp <= 0) { playerHp = 0; gameState = GameState.GAME_OVER }
                }
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

        for (bb in bossBullets) {
            if (bb.isActive && RectF.intersects(bb.getBounds(), playerBounds)) {
                if (shieldTimer > 0) {
                    bb.isActive = false
                    effects.add(Effect(bb.x, bb.y, EffectType.HIT_SPARK, maxLifeTime = 10))
                } else if (invincibleTimer <= 0) {
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
        if (shootSoundId != 0) {
            soundPool?.play(shootSoundId, 1f, 1f, 1, 0, 1f)
        }
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
            // Lower power item drop probability to 30% (from 50%) to adjust difficulty
            val type = if (random.nextFloat() < 0.3f) ItemType.POWER else ItemType.HEAL
            items.add(Item(x, y, type))
        }
    }

    private fun applyItemEffect(type: ItemType) {
        if (healSoundId != 0) {
            soundPool?.play(healSoundId, 1f, 1f, 1, 0, 1f)
        }
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
                    val threshold = if (currentStage >= 2) 0.2f else 0.3f
                    if (random.nextFloat() > threshold) {
                        val r = random.nextFloat()
                        val type = if (currentStage >= 2) {
                            when {
                                r > 0.8f -> EnemyType.STRONG
                                r > 0.5f -> EnemyType.ZIGZAG
                                r > 0.3f -> EnemyType.FAST
                                else -> EnemyType.BASIC
                            }
                        } else {
                            when {
                                r > 0.9f -> EnemyType.STRONG
                                r > 0.7f -> EnemyType.ZIGZAG
                                else -> EnemyType.BASIC
                            }
                        }
                        enemies.add(Enemy(laneWidth * i + laneWidth / 2, -100f, type, currentStage))
                    }
                }
            }
            1 -> {
                val midX = screenWidth / 2
                enemies.add(Enemy(midX, -100f, EnemyType.BASIC, currentStage))
                enemies.add(Enemy(midX - laneWidth, -200f, EnemyType.BASIC, currentStage))
                enemies.add(Enemy(midX + laneWidth, -200f, EnemyType.BASIC, currentStage))
                enemies.add(Enemy(midX - laneWidth * 2, -300f, EnemyType.BASIC, currentStage))
                enemies.add(Enemy(midX + laneWidth * 2, -300f, EnemyType.BASIC, currentStage))
            }
            2 -> {
                val midX = screenWidth / 2
                enemies.add(Enemy(midX, -100f, EnemyType.STRONG, currentStage))
                enemies.add(Enemy(midX - laneWidth, -250f, EnemyType.ZIGZAG, currentStage))
                enemies.add(Enemy(midX + laneWidth, -250f, EnemyType.ZIGZAG, currentStage))
                enemies.add(Enemy(midX, -400f, EnemyType.BASIC, currentStage))
            }
            3 -> {
                val lane = random.nextInt(laneCount)
                enemies.add(Enemy(laneWidth * lane + laneWidth / 2, -100f, EnemyType.FAST, currentStage))
                enemies.add(Enemy(laneWidth * lane + laneWidth / 2, -250f, EnemyType.FAST, currentStage))
                enemies.add(Enemy(laneWidth * lane + laneWidth / 2, -400f, EnemyType.FAST, currentStage))
            }
            4 -> {
                enemies.add(Enemy(screenWidth * 0.25f, -100f, EnemyType.ZIGZAG, currentStage))
                enemies.add(Enemy(screenWidth * 0.75f, -100f, EnemyType.ZIGZAG, currentStage))
            }
            5 -> {
                enemies.add(Enemy(laneWidth * 1 + laneWidth / 2, -100f, EnemyType.STRONG, currentStage))
                enemies.add(Enemy(laneWidth * 3 + laneWidth / 2, -100f, EnemyType.STRONG, currentStage))
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
            val patternHeight = 400f
            var startY = bgOffsetY - patternHeight * 2f // Start far above screen to buffer incoming elements
            while (startY < height + patternHeight) {   // Draw beyond screen bottom to prevent bottom pops
                canvas.drawCircle(width * 0.2f, startY + 100f, 60f, paint)
                canvas.drawCircle(width * 0.8f, startY + 300f, 80f, paint)
                startY += patternHeight
            }
        }

        // Draw tread marks on the ground behind/underneath tanks
        val drawTrackBmp = trackBitmap
        if (drawTrackBmp != null) {
            treadMarks.forEach { it.draw(canvas, drawTrackBmp, paint) }
        }

        bullets.forEach { it.draw(canvas) }
        enemies.forEach { enemy ->
            val bmp = when (enemy.type) {
                EnemyType.BASIC -> enemyBasicBmp
                EnemyType.FAST -> enemyFastBmp
                EnemyType.STRONG -> enemyStrongBmp
                EnemyType.ZIGZAG -> enemyZigzagBmp
            }
            enemy.draw(canvas, bmp)
        }
        bossBullets.forEach { it.draw(canvas) }
        boss?.draw(canvas)
        items.forEach { it.draw(canvas) }
        effects.forEach { it.draw(canvas) }

        if (gameState != GameState.GAME_OVER && gameState != GameState.PAUSED) {
            if (!(invincibleTimer > 0 && (invincibleTimer / 5) % 2 == 0)) {
                // Apply a transparent white flash filter when hit (invincibleTimer > 0)
                if (invincibleTimer > 0) {
                    paint.colorFilter = PorterDuffColorFilter(Color.argb(160, 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
                } else {
                    paint.colorFilter = null
                }

                var drawnWithSprite = false

                when (selectedSkinName) {
                    "DEFAULT" -> {
                        val bodyBmp = bodyBitmap
                        val turretBmp = bigGunBitmap
                        if (bodyBmp != null && turretBmp != null) {
                            val bodyW = playerSize * 0.9f
                            val bodyH = bodyW * (bodyBmp.height.toFloat() / bodyBmp.width.toFloat())
                            val bodyRectF = RectF(playerX - bodyW/2, playerY - bodyH/2, playerX + bodyW/2, playerY + bodyH/2)
                            canvas.drawBitmap(bodyBmp, null, bodyRectF, paint)

                            val turretW = bodyW * (52f / 76f)
                            val turretH = turretW * (turretBmp.height.toFloat() / turretBmp.width.toFloat())
                            val turretCenterY = playerY - 15f
                            val turretRectF = RectF(playerX - turretW/2, turretCenterY - turretH/2, playerX + turretW/2, turretCenterY + turretH/2)
                            
                            canvas.save()
                            canvas.rotate(180f, playerX, turretCenterY)
                            canvas.drawBitmap(turretBmp, null, turretRectF, paint)
                            canvas.restore()
                            
                            drawnWithSprite = true
                        }
                    }
                    "DESERT" -> {
                        val bodyBmp = tank2BodyBmp
                        val turretBmp = tank2TurretBmp
                        val barrelBmp = tank2BarrelBmp
                        if (bodyBmp != null && turretBmp != null && barrelBmp != null) {
                            val bodyW = playerSize * 0.9f
                            val bmpW = bodyBmp.width
                            val bmpH = bodyBmp.height
                            if (bmpW > 0 && bmpH > 0) {
                                val frameW = bmpW / 4
                                val frameH = bmpH
                                val bodyH = bodyW * (frameH.toFloat() / frameW.toFloat())

                                val frameIndex = ((System.currentTimeMillis() / 120) % 4).toInt()
                                val srcRect = android.graphics.Rect(frameIndex * frameW, 0, (frameIndex + 1) * frameW, frameH)
                                val bodyRectF = RectF(playerX - bodyW/2, playerY - bodyH/2, playerX + bodyW/2, playerY + bodyH/2)
                                canvas.drawBitmap(bodyBmp, srcRect, bodyRectF, paint)

                                val scale = bodyW / 48f
                                val turretCenterY = playerY - 8f * scale

                                // Draw barrel (centered, first so turret covers its base)
                                val barrelW = 8f * scale
                                val barrelH = 36f * scale
                                val barrelRectF = RectF(playerX - barrelW/2, turretCenterY - barrelH, playerX + barrelW/2, turretCenterY)
                                canvas.drawBitmap(barrelBmp, android.graphics.Rect(0, 1, barrelBmp.width, 10), barrelRectF, paint)

                                // Draw turret (centered, overlaying the barrel base)
                                val turretW = 26f * scale
                                val turretH = 37f * scale
                                val turretRectF = RectF(playerX - turretW/2, turretCenterY - turretH/2, playerX + turretW/2, turretCenterY + turretH/2)
                                canvas.drawBitmap(turretBmp, null, turretRectF, paint)
                                
                                drawnWithSprite = true
                            }
                        }
                    }
                    "HEAVY" -> {
                        val bodyBmp = tank3BodyBmp
                        if (bodyBmp != null) {
                            val bmpW = bodyBmp.width
                            val bmpH = bodyBmp.height
                            if (bmpW > 0 && bmpH > 0) {
                                val bodyW = playerSize * 1.15f
                                val bodyH = bodyW * (bmpH.toFloat() / bmpW.toFloat())
                                val bodyRectF = RectF(playerX - bodyW/2, playerY - bodyH/2, playerX + bodyW/2, playerY + bodyH/2)
                                canvas.drawBitmap(bodyBmp, null, bodyRectF, paint)
                                drawnWithSprite = true
                            }
                        }
                    }
                }

                paint.colorFilter = null

                if (!drawnWithSprite) {
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
                    if (invincibleTimer > 0) {
                        paint.color = Color.WHITE
                    } else {
                        paint.color = bodyColor
                    }
                    canvas.drawRect(playerX - playerSize/2, playerY - playerSize/2, playerX + playerSize/2, playerY + playerSize/2, paint)
                    
                    if (invincibleTimer > 0) {
                        paint.color = Color.rgb(220, 220, 220)
                    } else {
                        paint.color = cannonColor
                    }
                    canvas.drawRect(playerX - 15f, playerY - playerSize, playerX + 15f, playerY, paint)
                }
            }
            if (shieldTimer > 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                val alpha = Math.min(100, shieldTimer * 120 / 180).coerceAtLeast(30)
                paint.color = Color.argb(alpha, 0, 200, 255)
                canvas.drawCircle(playerX, playerY, playerSize * 0.9f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // --- HUD Readability: Draw HUD background bar ---
        paint.color = Color.argb(140, 10, 10, 10)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 90f, width.toFloat(), 300f, paint)

        // Draw Row 1: HP, STAGE, Score (y = 150f - shifted down by 70f)
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("HP: ", 50f, 150f, paint)
        val hpTextWidth = paint.measureText("HP: ")
        
        var heartX = 50f + hpTextWidth + 30f
        for (i in 0 until maxHp) {
            if (i < playerHp) {
                paint.color = Color.RED
                paint.style = Paint.Style.FILL
                drawHeart(canvas, heartX, 135f, 20f, paint)
            } else {
                paint.color = Color.rgb(80, 80, 80)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                drawHeart(canvas, heartX, 135f, 20f, paint)
            }
            heartX += 60f
        }
        paint.style = Paint.Style.FILL
        
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("STAGE $currentStage", Math.round(width / 2f).toFloat(), 150f, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("Score: $score", Math.round(width - 160f).toFloat(), 150f, paint)

        // Draw Row 2: PWR (y = 210f - shifted down by 70f) in Gold
        paint.color = Color.rgb(255, 215, 0) // Gold
        paint.textSize = 44f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("PWR: $weaponLevel", 50f, 210f, paint)

        // --- Pause Button Configuration and Draw (Vertical align with Row 1) ---
        val btnSize = 70f
        val marginX = 50f
        val marginY = 115f
        pauseBtnRect.set(width - btnSize - marginX, marginY, width - marginX, marginY + btnSize)

        val pauseScaled = applyButtonScale(canvas, pauseBtnRect)

        // Draw Pause button border
        paint.color = Color.rgb(200, 200, 200)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(pauseBtnRect, 12f, 12f, paint)

        // Draw "||" lines
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val barW = 8f
        val barH = 30f
        val gap = 12f
        val centerX = pauseBtnRect.centerX()
        val centerY = pauseBtnRect.centerY()
        canvas.drawRect(centerX - barW - gap/2, centerY - barH/2, centerX - gap/2, centerY + barH/2, paint)
        canvas.drawRect(centerX + gap/2, centerY - barH/2, centerX + barW + gap/2, centerY + barH/2, paint)

        if (pauseScaled) {
            canvas.restore()
        }

        // Boss / Stage Progress Bar (Moved lower to y = 255f)
        if (b == null) {
            paint.color = Color.rgb(60, 60, 60)
            val barWidth = width * 0.8f; val barX = width * 0.1f; val barY = 255f
            canvas.drawRect(barX, barY, barX + barWidth, barY + 15f, paint)
            paint.color = Color.CYAN
            canvas.drawRect(barX, barY, barX + barWidth * (stageProgress / 100f), barY + 15f, paint)
        } else {
            paint.color = Color.rgb(40, 40, 40)
            val barWidth = width * 0.9f; val barX = width * 0.05f; val barY = 250f
            canvas.drawRect(barX, barY, barX + barWidth, barY + 25f, paint)
            paint.color = Color.RED
            val hpRatio = b.hp.toFloat() / b.maxHp
            canvas.drawRect(barX, barY, barX + barWidth * hpRatio, barY + 25f, paint)
            paint.color = Color.WHITE; paint.textSize = 30f; paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("BOSS: ${b.bossName}", Math.round(width / 2f).toFloat(), Math.round(barY + 20f).toFloat(), paint)

            if (b.y < 300f) {
                val blink = (System.currentTimeMillis() / 250) % 2 == 0L
                if (blink) {
                    paint.color = Color.RED
                    paint.textSize = 90f
                    paint.textAlign = Paint.Align.CENTER
                    paint.isFakeBoldText = true
                    canvas.drawText("⚠️ WARNING ⚠️", Math.round(width / 2f).toFloat(), Math.round(height / 3f).toFloat(), paint)
                    paint.textSize = 46f
                    canvas.drawText("BOSS APPROACHING", Math.round(width / 2f).toFloat(), Math.round(height / 3f + 60f).toFloat(), paint)
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

        val skillScaled = applyButtonScale(canvas, skillButtonRect)

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
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            val textY = sBtnY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText("$selectedSkillName READY", Math.round(sBtnX).toFloat(), Math.round(textY).toFloat(), paint)
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
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = false
            val textY = sBtnY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText("$selectedSkillName ${skillGauge}%", Math.round(sBtnX).toFloat(), Math.round(textY).toFloat(), paint)
        }

        if (skillScaled) {
            canvas.restore()
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
        else if (gameState == GameState.STAGE_CLEAR || gameState == GameState.FINAL_CLEAR) drawStageClearScreen(canvas)
        else if (gameState == GameState.PAUSED) drawPausedScreen(canvas)

        if (isShake) {
            canvas.restore()
        }

        // Draw Simulated Status Bar over gameplay UI
        drawSimulatedStatusBar(canvas)
    }

    private fun drawGameOverScreen(canvas: Canvas) {
        paint.color = Color.argb(180, 0, 0, 0); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.RED; paint.textSize = 120f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("GAME OVER", Math.round(width / 2f).toFloat(), Math.round(height / 2f - 50f).toFloat(), paint)
        paint.color = Color.WHITE; paint.textSize = 80f
        canvas.drawText("Final Score: $score", Math.round(width / 2f).toFloat(), Math.round(height / 2f + 80f).toFloat(), paint)
        paint.textSize = 50f
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Touch to Restart", Math.round(width / 2f).toFloat(), Math.round(height / 2f + 200f).toFloat(), paint)
    }

    private fun drawStageClearScreen(canvas: Canvas) {
        paint.color = Color.argb(180, 0, 0, 100); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.GREEN; paint.textSize = 120f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val clearText = if (currentStage == 2) "FINAL CLEAR!" else "STAGE CLEAR!"
        canvas.drawText(clearText, Math.round(width / 2f).toFloat(), Math.round(height / 2f - 50f).toFloat(), paint)
        paint.color = Color.WHITE; paint.textSize = 80f
        canvas.drawText("Score: $score", Math.round(width / 2f).toFloat(), Math.round(height / 2f + 80f).toFloat(), paint)
        paint.textSize = 50f
        val nextText = if (currentStage == 2) "Touch to Restart" else "Touch to Next Stage"
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(nextText, Math.round(width / 2f).toFloat(), Math.round(height / 2f + 200f).toFloat(), paint)
    }

    private fun drawTitleScreen(canvas: Canvas) {
        drawPremiumBackground(canvas)

        val titleY = Math.round(height * 0.22f).toFloat()
        val subtitleY = Math.round(height * 0.28f).toFloat()
        val subtitle2Y = Math.round(height * 0.31f).toFloat()
        val bestScoreY = Math.round(height * 0.36f).toFloat()
        val statusSkinY = Math.round(height * 0.41f).toFloat()
        val statusSkillY = Math.round(height * 0.46f).toFloat()

        // Title: "1916"
        paint.color = Color.RED
        paint.textSize = 240f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.setShadowLayer(25f, 0f, 0f, Color.RED)
        canvas.drawText("1916", Math.round(width / 2f).toFloat(), titleY, paint)
        paint.clearShadowLayer()

        // Subtitle: "Tank Arcade Shooter"
        paint.color = Color.WHITE
        paint.textSize = 55f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("Tank Arcade Shooter", Math.round(width / 2f).toFloat(), subtitleY, paint)

        // Subtitle 2: "A New Gravity Experience"
        paint.color = Color.rgb(180, 200, 220)
        paint.textSize = 34f
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("A New Gravity Experience", Math.round(width / 2f).toFloat(), subtitle2Y, paint)

        // Structured vertical block for status display
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 44f
        
        paint.color = Color.rgb(255, 215, 0)
        canvas.drawText("🏆 BEST SCORE: $bestScore", Math.round(width / 2f).toFloat(), bestScoreY, paint)
        
        paint.color = Color.rgb(0, 230, 255)
        canvas.drawText("🛡️ SKIN: $selectedSkinName", Math.round(width / 2f).toFloat(), statusSkinY, paint)
        
        paint.color = Color.rgb(255, 180, 0)
        canvas.drawText("⚡ SKILL: $selectedSkillName", Math.round(width / 2f).toFloat(), statusSkillY, paint)

        // Button dimensions
        val btnWidth = 450f
        val btnHeight = 120f
        val btnX = width / 2f

        // 1. START Button
        val startY = height * 0.55f
        startButtonRect.set(btnX - btnWidth/2, startY - btnHeight/2, btnX + btnWidth/2, startY + btnHeight/2)

        applyTitleButtonPulse(canvas, startButtonRect)
        
        // Draw fill gradient
        paint.shader = startButtonGradient
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(startButtonRect, 25f, 25f, paint)
        paint.shader = null
        
        // Draw bevel outline
        paint.color = Color.argb(120, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(startButtonRect, 25f, 25f, paint)
        
        // Draw Text
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textSize = 50f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        var textY = startButtonRect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("START", btnX, textY, paint)
        
        canvas.restore()

        // 2. SKIN Button
        val skinY = height * 0.65f
        skinButtonRect.set(btnX - btnWidth/2, skinY - btnHeight/2, btnX + btnWidth/2, skinY + btnHeight/2)

        applyTitleButtonPulse(canvas, skinButtonRect)
        
        // Draw background
        paint.color = Color.argb(180, 20, 22, 28)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(skinButtonRect, 25f, 25f, paint)
        
        // Draw neon border
        paint.color = Color.rgb(0, 240, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(skinButtonRect, 25f, 25f, paint)
        
        // Draw Text
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textSize = 50f
        paint.isFakeBoldText = true
        textY = skinButtonRect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("SKIN", btnX, textY, paint)
        
        canvas.restore()

        // 3. SKILL Button
        val skillY = height * 0.75f
        titleSkillButtonRect.set(btnX - btnWidth/2, skillY - btnHeight/2, btnX + btnWidth/2, skillY + btnHeight/2)

        applyTitleButtonPulse(canvas, titleSkillButtonRect)
        
        // Draw background
        paint.color = Color.argb(180, 20, 22, 28)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(titleSkillButtonRect, 25f, 25f, paint)
        
        // Draw neon border
        paint.color = Color.rgb(255, 200, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(titleSkillButtonRect, 25f, 25f, paint)
        
        // Draw Text
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textSize = 50f
        paint.isFakeBoldText = true
        textY = titleSkillButtonRect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("SKILL", btnX, textY, paint)
        
        canvas.restore()

        // Instruction Text
        paint.color = Color.rgb(150, 150, 150)
        paint.textSize = 35f
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Touch START to Play", width / 2f, height * 0.85f, paint)

        drawSimulatedStatusBar(canvas)
    }

    private fun drawSkinSelectScreen(canvas: Canvas) {
        drawPremiumBackground(canvas)

        // Title: "SELECT TANK"
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("SELECT TANK", Math.round(width / 2f).toFloat(), Math.round(height * 0.15f).toFloat(), paint)

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

        val backScaled = applyButtonScale(canvas, skinBackButtonRect)

        // Draw BACK Button background
        paint.color = Color.argb(180, 20, 22, 28)
        paint.style = Paint.Style.FILL
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
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val textY = skinBackButtonRect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("BACK", cardX, textY, paint)

        if (backScaled) {
            canvas.restore()
        }

        drawSimulatedStatusBar(canvas)
    }

    private fun drawSkinCard(canvas: Canvas, rect: RectF, name: String, desc: String, bodyColor: Int, cannonColor: Int) {
        val scaled = applyButtonScale(canvas, rect)
        val isSelected = selectedSkinName == name

        // Draw Card Background
        paint.color = Color.rgb(35, 35, 35)
        paint.style = Paint.Style.FILL
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

        var drawnPreview = false

        when (name) {
            "DEFAULT" -> {
                val bodyBmp = bodyBitmap
                val turretBmp = bigGunBitmap
                if (bodyBmp != null && turretBmp != null) {
                    val bodyW = tankSize * 0.9f
                    val bodyH = bodyW * (bodyBmp.height.toFloat() / bodyBmp.width.toFloat())
                    val bodyRectF = RectF(previewX - bodyW/2, previewY - bodyH/2, previewX + bodyW/2, previewY + bodyH/2)
                    canvas.drawBitmap(bodyBmp, null, bodyRectF, paint)

                    val turretW = bodyW * (52f / 76f)
                    val turretH = turretW * (turretBmp.height.toFloat() / turretBmp.width.toFloat())
                    val turretCenterY = previewY - 8f
                    val turretRectF = RectF(previewX - turretW/2, turretCenterY - turretH/2, previewX + turretW/2, turretCenterY + turretH/2)
                    
                    canvas.save()
                    canvas.rotate(180f, previewX, turretCenterY)
                    canvas.drawBitmap(turretBmp, null, turretRectF, paint)
                    canvas.restore()
                    
                    drawnPreview = true
                }
            }
            "DESERT" -> {
                val bodyBmp = tank2BodyBmp
                val turretBmp = tank2TurretBmp
                val barrelBmp = tank2BarrelBmp
                if (bodyBmp != null && turretBmp != null && barrelBmp != null) {
                    val bodyW = tankSize * 0.9f
                    val bmpW = bodyBmp.width
                    val bmpH = bodyBmp.height
                    if (bmpW > 0 && bmpH > 0) {
                        val frameW = bmpW / 4
                        val frameH = bmpH
                        val bodyH = bodyW * (frameH.toFloat() / frameW.toFloat())

                        // Preview first frame of sprite sheet (frame index 0)
                        val srcRect = android.graphics.Rect(0, 0, frameW, frameH)
                        val bodyRectF = RectF(previewX - bodyW/2, previewY - bodyH/2, previewX + bodyW/2, previewY + bodyH/2)
                        canvas.drawBitmap(bodyBmp, srcRect, bodyRectF, paint)

                        val scale = bodyW / 48f
                        val turretCenterY = previewY - 8f * scale

                        // Draw barrel (centered, first so turret covers its base)
                        val barrelW = 8f * scale
                        val barrelH = 36f * scale
                        val barrelRectF = RectF(previewX - barrelW/2, turretCenterY - barrelH, previewX + barrelW/2, turretCenterY)
                        canvas.drawBitmap(barrelBmp, android.graphics.Rect(0, 1, barrelBmp.width, 10), barrelRectF, paint)

                        // Draw turret (centered, overlaying the barrel base)
                        val turretW = 26f * scale
                        val turretH = 37f * scale
                        val turretRectF = RectF(previewX - turretW/2, turretCenterY - turretH/2, previewX + turretW/2, turretCenterY + turretH/2)
                        canvas.drawBitmap(turretBmp, null, turretRectF, paint)
                        
                        drawnPreview = true
                    }
                }
            }
            "HEAVY" -> {
                val bodyBmp = tank3BodyBmp
                if (bodyBmp != null) {
                    val bmpW = bodyBmp.width
                    val bmpH = bodyBmp.height
                    if (bmpW > 0 && bmpH > 0) {
                        val bodyW = tankSize * 1.1f
                        val bodyH = bodyW * (bmpH.toFloat() / bmpW.toFloat())
                        val bodyRectF = RectF(previewX - bodyW/2, previewY - bodyH/2, previewX + bodyW/2, previewY + bodyH/2)
                        canvas.drawBitmap(bodyBmp, null, bodyRectF, paint)
                        drawnPreview = true
                    }
                }
            }
        }

        if (!drawnPreview) {
            // Draw preview cannon
            paint.color = cannonColor
            canvas.drawRect(previewX - 10f, previewY - tankSize/2 - 15f, previewX + 10f, previewY, paint)
            // Draw preview body
            paint.color = bodyColor
            canvas.drawRect(previewX - tankSize/2, previewY - tankSize/2, previewX + tankSize/2, previewY + tankSize/2, paint)
        }

        // Draw Skin Information Texts
        val textStartX = rect.left + 180f
        paint.textAlign = Paint.Align.LEFT

        // Name translation for premium display
        val displayName = when (name) {
            "DEFAULT" -> "PREVIEW: DEFAULT"
            "DESERT" -> "PREVIEW: DESERT"
            "HEAVY" -> "PREVIEW: HEAVY"
            else -> name
        }

        // Name
        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(displayName, textStartX, Math.round(rect.centerY() - 10f).toFloat(), paint)

        // Description
        paint.color = Color.rgb(180, 180, 180)
        paint.textSize = 30f
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(desc, textStartX, Math.round(rect.centerY() + 35f).toFloat(), paint)

        if (scaled) {
            canvas.restore()
        }
    }

    private fun drawSkillSelectScreen(canvas: Canvas) {
        drawPremiumBackground(canvas)

        // Title: "SELECT SKILL"
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("SELECT SKILL", Math.round(width / 2f).toFloat(), Math.round(height * 0.15f).toFloat(), paint)

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

        val backScaled = applyButtonScale(canvas, skillBackButtonRect)

        // Draw BACK Button background
        paint.color = Color.argb(180, 20, 22, 28)
        paint.style = Paint.Style.FILL
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
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val textY = skillBackButtonRect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("BACK", cardX, textY, paint)

        if (backScaled) {
            canvas.restore()
        }

        drawSimulatedStatusBar(canvas)
    }

    private fun drawSkillCard(canvas: Canvas, rect: RectF, name: String, desc: String, icon: String) {
        val scaled = applyButtonScale(canvas, rect)
        val isSelected = selectedSkillName == name

        // Draw Card Background
        paint.color = Color.rgb(35, 35, 35)
        paint.style = Paint.Style.FILL
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
        paint.typeface = Typeface.DEFAULT
        val iconY = previewY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(icon, previewX, iconY, paint)

        // Draw Skill Information Texts
        val textStartX = rect.left + 180f
        paint.textAlign = Paint.Align.LEFT

        // Name
        paint.color = Color.WHITE
        paint.textSize = 45f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(name, textStartX, Math.round(rect.centerY() - 10f).toFloat(), paint)

        // Description
        paint.color = Color.rgb(180, 180, 180)
        paint.textSize = 30f
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(desc, textStartX, Math.round(rect.centerY() + 35f).toFloat(), paint)

        if (scaled) {
            canvas.restore()
        }
    }

    private fun drawPausedScreen(canvas: Canvas) {
        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 120f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("PAUSED", Math.round(width / 2f).toFloat(), Math.round(height / 2f - 50f).toFloat(), paint)
        paint.textSize = 50f
        paint.isFakeBoldText = false
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Touch to Resume", Math.round(width / 2f).toFloat(), Math.round(height / 2f + 50f).toFloat(), paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(holder) {
            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    when (gameState) {
                        GameState.TITLE -> {
                            if (startButtonRect.contains(x, y)) pressedRect = startButtonRect
                            else if (skinButtonRect.contains(x, y)) pressedRect = skinButtonRect
                            else if (titleSkillButtonRect.contains(x, y)) pressedRect = titleSkillButtonRect
                        }
                        GameState.SKIN_SELECT -> {
                            if (defaultSkinRect.contains(x, y)) pressedRect = defaultSkinRect
                            else if (desertSkinRect.contains(x, y)) pressedRect = desertSkinRect
                            else if (heavySkinRect.contains(x, y)) pressedRect = heavySkinRect
                            else if (skinBackButtonRect.contains(x, y)) pressedRect = skinBackButtonRect
                        }
                        GameState.SKILL_SELECT -> {
                            if (artillerySkillRect.contains(x, y)) pressedRect = artillerySkillRect
                            else if (shieldSkillRect.contains(x, y)) pressedRect = shieldSkillRect
                            else if (repairSkillRect.contains(x, y)) pressedRect = repairSkillRect
                            else if (skillBackButtonRect.contains(x, y)) pressedRect = skillBackButtonRect
                        }
                        GameState.PLAYING -> {
                            if (pauseBtnRect.contains(x, y)) pressedRect = pauseBtnRect
                            else if (skillButtonRect.contains(x, y)) pressedRect = skillButtonRect
                            else {
                                targetPlayerX = x
                                targetPlayerY = y - 150f
                            }
                        }
                        else -> {}
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val rect = pressedRect
                    if (rect != null && !rect.contains(x, y)) {
                        pressedRect = null
                    }
                    if (gameState == GameState.PLAYING && pressedRect == null) {
                        targetPlayerX = x
                        targetPlayerY = y - 150f
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val rect = pressedRect
                    pressedRect = null
                    if (rect != null && rect.contains(x, y)) {
                        when (gameState) {
                            GameState.TITLE -> {
                                if (rect == startButtonRect) restartGame()
                                else if (rect == skinButtonRect) gameState = GameState.SKIN_SELECT
                                else if (rect == titleSkillButtonRect) gameState = GameState.SKILL_SELECT
                            }
                            GameState.SKIN_SELECT -> {
                                if (rect == defaultSkinRect) selectedSkinName = "DEFAULT"
                                else if (rect == desertSkinRect) selectedSkinName = "DESERT"
                                else if (rect == heavySkinRect) selectedSkinName = "HEAVY"
                                else if (rect == skinBackButtonRect) gameState = GameState.TITLE
                            }
                            GameState.SKILL_SELECT -> {
                                if (rect == artillerySkillRect) selectedSkillName = "ARTILLERY"
                                else if (rect == shieldSkillRect) selectedSkillName = "SHIELD"
                                else if (rect == repairSkillRect) selectedSkillName = "REPAIR"
                                else if (rect == skillBackButtonRect) gameState = GameState.TITLE
                            }
                            GameState.PLAYING -> {
                                if (rect == pauseBtnRect) {
                                    gameState = GameState.PAUSED
                                } else if (rect == skillButtonRect) {
                                    if (skillGauge >= skillGaugeMax) {
                                        if (selectedSkillName == "ARTILLERY") {
                                            pendingSkillUse = true
                                        } else if (selectedSkillName == "SHIELD") {
                                            pendingShieldUse = true
                                        } else if (selectedSkillName == "REPAIR") {
                                            if (playerHp >= maxHp) {
                                                post {
                                                    android.widget.Toast.makeText(context, "HP is already full!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                pendingRepairUse = true
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    } else {
                        if (rect == null) {
                            when (gameState) {
                                GameState.PAUSED -> gameState = GameState.PLAYING
                                GameState.STAGE_CLEAR -> startNextStage()
                                GameState.GAME_OVER, GameState.FINAL_CLEAR -> gameState = GameState.TITLE
                                else -> {}
                            }
                        }
                    }
                }
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
        shieldTimer = 0
        pendingSkillUse = false
        pendingShieldUse = false
        pendingRepairUse = false
        skillTextTimer = 0
        enemies.clear()
        bullets.clear()
        items.clear()
        effects.clear()
        bossBullets.clear()
        boss = null
        treadMarks.clear()
    }

    private fun loadBestScore() {
        val prefs = context.getSharedPreferences("TankGamePrefs", Context.MODE_PRIVATE)
        bestScore = prefs.getInt("BestScore", 0)
    }

    private fun saveBestScore() {
        val prefs = context.getSharedPreferences("TankGamePrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("BestScore", bestScore).apply()
    }

    fun pause() {
        synchronized(holder) {
            if (gameState == GameState.PLAYING) {
                gameState = GameState.PAUSED
            }
            saveBestScore()
        }
    }

    fun resume() {
        synchronized(holder) {
            lastUpdateTime = System.currentTimeMillis()
        }
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float, paint: Paint) {
        heartPath.reset()
        val w = size
        val h = size
        heartPath.moveTo(x, y - h * 0.25f)
        heartPath.cubicTo(x - w * 0.5f, y - h * 0.75f, x - w, y - h * 0.1f, x, y + h * 0.5f)
        heartPath.cubicTo(x + w, y - h * 0.1f, x + w * 0.5f, y - h * 0.75f, x, y - h * 0.25f)
        heartPath.close()
        canvas.drawPath(heartPath, paint)
    }
}

data class TreadMark(
    var x: Float,
    var y: Float,
    var alpha: Int = 120,
    var isActive: Boolean = true
) {
    fun update(scrollSpeed: Float) {
        y += scrollSpeed
        alpha -= 2
        if (alpha <= 0) {
            isActive = false
        }
    }

    fun draw(canvas: Canvas, bitmap: Bitmap, paint: Paint) {
        paint.alpha = alpha
        canvas.drawBitmap(bitmap, x - bitmap.width / 2f, y - bitmap.height / 2f, paint)
        paint.alpha = 255
    }
}
