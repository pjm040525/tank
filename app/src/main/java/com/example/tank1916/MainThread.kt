package com.example.tank1916

import android.graphics.Canvas
import android.view.SurfaceHolder

class MainThread(private val surfaceHolder: SurfaceHolder, private val gameView: GameView) : Thread() {
    var isRunning = false

    override fun run() {
        while (isRunning) {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.update()
                        gameView.render(canvas)
                    }
                }
            } catch (e: Exception) {
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {}
                }
            }
            try { sleep(16) } catch (e: Exception) {}
        }
    }
}
