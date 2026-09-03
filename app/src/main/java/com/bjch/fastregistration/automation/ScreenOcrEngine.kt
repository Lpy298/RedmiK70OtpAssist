package com.bjch.fastregistration.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * In-memory, on-device OCR fallback for WeChat mini-program Canvas/XWeb pages that expose no
 * Accessibility descendants. Screenshots are never written to disk or sent over the network.
 */
class ScreenOcrEngine(
    private val service: AccessibilityService,
    private val logger: PerformanceLogger
) {
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val callbacks = mutableListOf<(Result<NodeTree>) -> Unit>()
    private var captureInFlight = false
    private var lastCaptureStartedMs = 0L
    private var requestedWidth = 0
    private var requestedHeight = 0

    fun capture(width: Int, height: Int, callback: (Result<NodeTree>) -> Unit) {
        callbacks += callback
        requestedWidth = width
        requestedHeight = height
        if (captureInFlight) return
        captureInFlight = true

        val elapsed = SystemClock.elapsedRealtime() - lastCaptureStartedMs
        val delay = (MIN_SCREENSHOT_INTERVAL_MS - elapsed).coerceAtLeast(0L)
        if (delay == 0L) beginCapture() else handler.postDelayed(::beginCapture, delay)
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        callbacks.clear()
        recognizer.close()
    }

    fun cancelPending() {
        handler.removeCallbacksAndMessages(null)
        callbacks.clear()
    }

    private fun beginCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finish(Result.failure(IllegalStateException("屏幕 OCR 需要 Android 11 或更高版本")))
            return
        }
        lastCaptureStartedMs = SystemClock.elapsedRealtime()
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { command -> handler.post(command) },
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        buffer.close()
                    }
                    if (bitmap == null) {
                        finish(Result.failure(IllegalStateException("无法读取无障碍截图")))
                        return
                    }
                    recognize(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    finish(Result.failure(IllegalStateException("无障碍截图失败，错误码 $errorCode")))
                }
            }
        )
    }

    private fun recognize(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
            val regions = buildRegions(result, bitmap.width, bitmap.height)
                val tree = NodeTree.fromTextRegions(
                    regions = regions,
                    width = requestedWidth.takeIf { it > 0 } ?: bitmap.width,
                    height = requestedHeight.takeIf { it > 0 } ?: bitmap.height
                )
                logger.info("Screen OCR completed: regions=${regions.size}, text=${tree.allText.length}")
                finish(Result.success(tree))
            }
            .addOnFailureListener { error ->
                finish(Result.failure(error))
            }
            .addOnCompleteListener {
                bitmap.recycle()
            }
    }

    private fun buildRegions(result: Text, bitmapWidth: Int, bitmapHeight: Int): List<TextRegion> {
        val scaleX = (requestedWidth.takeIf { it > 0 } ?: bitmapWidth).toFloat() / bitmapWidth
        val scaleY = (requestedHeight.takeIf { it > 0 } ?: bitmapHeight).toFloat() / bitmapHeight
        fun scaled(source: android.graphics.Rect) = android.graphics.Rect(
            (source.left * scaleX).toInt(), (source.top * scaleY).toInt(),
            (source.right * scaleX).toInt(), (source.bottom * scaleY).toInt()
        )
        val regions = mutableListOf<TextRegion>()
        result.textBlocks.forEach blockLoop@ { block ->
            val blockBounds = block.boundingBox ?: return@blockLoop
            val blockIndex = regions.size
            regions += TextRegion(-1, 0, "ocr.TextBlock", block.text, scaled(blockBounds))
            block.lines.forEach lineLoop@ { line ->
                val lineBounds = line.boundingBox ?: return@lineLoop
                val lineIndex = regions.size
                regions += TextRegion(blockIndex, 1, "ocr.Line", line.text, scaled(lineBounds))
                line.elements.forEach elementLoop@ { element ->
                    val elementBounds = element.boundingBox ?: return@elementLoop
                    regions += TextRegion(lineIndex, 2, "ocr.Element", element.text, scaled(elementBounds))
                }
            }
        }
        return regions
    }

    private fun finish(result: Result<NodeTree>) {
        if (result.isFailure) {
            logger.info("Screen OCR failed: ${result.exceptionOrNull()?.message.orEmpty()}")
        }
        val pending = callbacks.toList()
        callbacks.clear()
        captureInFlight = false
        pending.forEach { it(result) }
    }

    companion object {
        private const val MIN_SCREENSHOT_INTERVAL_MS = 900L
    }
}
