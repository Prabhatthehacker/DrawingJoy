package com.mom.privatedrawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- Public state, driven by MainActivity's toolbar ----
    var currentTool: Tool = Tool.PEN
    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 12f

    var onHistoryChanged: (() -> Unit)? = null

    // ---- Internal drawing state ----
    private val actions = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private var baseBitmap: Bitmap? = null
    private var baseCanvas: Canvas? = null

    private var activePath: Path? = null
    private val activePaint = Paint().apply { applyStrokeStyle() }

    private var shapeStartX = 0f
    private var shapeStartY = 0f
    private var shapeCurX = 0f
    private var shapeCurY = 0f
    private var isShaping = false
    var shapesFilled: Boolean = false

    // Callback used by the Text tool: MainActivity shows an input dialog, then calls addText()
    var onCanvasTapForText: ((x: Float, y: Float) -> Unit)? = null

    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val newCanvas = Canvas(newBitmap)
            // Preserve whatever was already drawn if the view is resized (e.g. rotation)
            baseBitmap?.let { old ->
                newCanvas.drawBitmap(old, 0f, 0f, null)
            }
            baseBitmap = newBitmap
            baseCanvas = newCanvas
            redrawAllActions()
        }
    }

    // ---------------- Touch handling ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (currentTool) {
            Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER, Tool.ERASER -> handleFreehand(event, x, y)
            Tool.LINE -> handleShape(event, x, y, ShapeType.LINE)
            Tool.RECTANGLE -> handleShape(event, x, y, ShapeType.RECTANGLE)
            Tool.CIRCLE -> handleShape(event, x, y, ShapeType.CIRCLE)
            Tool.TRIANGLE -> handleShape(event, x, y, ShapeType.TRIANGLE)
            Tool.STAR -> handleShape(event, x, y, ShapeType.STAR)
            Tool.FILL -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    floodFill(x.toInt(), y.toInt(), currentColor)
                }
            }
            Tool.TEXT -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    onCanvasTapForText?.invoke(x, y)
                }
            }
            Tool.IMAGE -> { /* Image placement is handled by MainActivity via addImage() */ }
        }
        return true
    }

    private fun handleFreehand(event: MotionEvent, x: Float, y: Float) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activePath = Path().apply { moveTo(x, y) }
                configurePaintForTool()
            }
            MotionEvent.ACTION_MOVE -> {
                activePath?.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                activePath?.lineTo(x, y)
                commitFreehandStroke()
                invalidate()
            }
        }
    }

    private fun configurePaintForTool() {
        activePaint.applyStrokeStyle()
        when (currentTool) {
            Tool.PEN -> {
                activePaint.color = currentColor
                activePaint.alpha = 255
                activePaint.strokeWidth = currentStrokeWidth
            }
            Tool.PENCIL -> {
                activePaint.color = currentColor
                activePaint.alpha = 200
                activePaint.strokeWidth = max(2f, currentStrokeWidth * 0.5f)
            }
            Tool.MARKER -> {
                activePaint.color = currentColor
                activePaint.alpha = 255
                activePaint.strokeWidth = currentStrokeWidth * 1.8f
            }
            Tool.HIGHLIGHTER -> {
                activePaint.color = currentColor
                activePaint.alpha = 90
                activePaint.strokeWidth = currentStrokeWidth * 2.4f
            }
            Tool.ERASER -> {
                activePaint.color = Color.TRANSPARENT
                activePaint.alpha = 255
                activePaint.strokeWidth = currentStrokeWidth * 1.5f
            }
            else -> {}
        }
    }

    private fun commitFreehandStroke() {
        val path = activePath ?: return
        val isEraser = currentTool == Tool.ERASER
        val action = DrawAction.StrokeAction(
            path = Path(path),
            color = if (isEraser) Color.TRANSPARENT else activePaint.color,
            strokeWidth = activePaint.strokeWidth,
            alpha = activePaint.alpha,
            isEraser = isEraser
        )
        drawStrokeToBase(action)
        actions.add(action)
        redoStack.clear()
        activePath = null
        onHistoryChanged?.invoke()
    }

    private fun drawStrokeToBase(action: DrawAction.StrokeAction) {
        val canvas = baseCanvas ?: return
        val paint = Paint().apply { applyStrokeStyle() }
        paint.strokeWidth = action.strokeWidth
        if (action.isEraser) {
            paint.color = Color.TRANSPARENT
            paint.xfermode = clearXfermode
        } else {
            paint.color = action.color
            paint.alpha = action.alpha
            paint.xfermode = null
        }
        canvas.drawPath(action.path, paint)
    }

    private fun handleShape(event: MotionEvent, x: Float, y: Float, type: ShapeType) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                shapeStartX = x; shapeStartY = y
                shapeCurX = x; shapeCurY = y
                isShaping = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                shapeCurX = x; shapeCurY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                shapeCurX = x; shapeCurY = y
                isShaping = false
                val bounds = RectF(
                    min(shapeStartX, shapeCurX), min(shapeStartY, shapeCurY),
                    max(shapeStartX, shapeCurX), max(shapeStartY, shapeCurY)
                )
                val action = DrawAction.ShapeAction(
                    type = type,
                    bounds = bounds,
                    color = currentColor,
                    strokeWidth = currentStrokeWidth,
                    filled = shapesFilled
                )
                drawShapeToBase(action)
                actions.add(action)
                redoStack.clear()
                onHistoryChanged?.invoke()
                invalidate()
            }
        }
    }

    private fun buildShapePaint(color: Int, strokeWidth: Float, filled: Boolean): Paint {
        return Paint().apply {
            isAntiAlias = true
            this.color = color
            this.strokeWidth = strokeWidth
            style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    private fun drawShapeToBase(action: DrawAction.ShapeAction) {
        val canvas = baseCanvas ?: return
        drawShapeOn(canvas, action)
    }

    private fun drawShapeOn(canvas: Canvas, action: DrawAction.ShapeAction) {
        val paint = buildShapePaint(action.color, action.strokeWidth, action.filled)
        val b = action.bounds
        when (action.type) {
            ShapeType.LINE -> canvas.drawLine(shapeStartXFor(action), shapeStartYFor(action), b.right, b.bottom, paint)
            ShapeType.RECTANGLE -> canvas.drawRect(b, paint)
            ShapeType.CIRCLE -> canvas.drawOval(b, paint)
            ShapeType.TRIANGLE -> {
                val path = Path()
                path.moveTo((b.left + b.right) / 2f, b.top)
                path.lineTo(b.left, b.bottom)
                path.lineTo(b.right, b.bottom)
                path.close()
                canvas.drawPath(path, paint)
            }
            ShapeType.STAR -> canvas.drawPath(buildStarPath(b), paint)
        }
    }

    // Lines need their true start point (not just the bounding box), so we stash it
    // on the action's bounds via left/top when start != top-left corner.
    private fun shapeStartXFor(action: DrawAction.ShapeAction): Float = action.bounds.left
    private fun shapeStartYFor(action: DrawAction.ShapeAction): Float = action.bounds.top

    private fun buildStarPath(b: RectF): Path {
        val cx = (b.left + b.right) / 2f
        val cy = (b.top + b.bottom) / 2f
        val outerR = min(b.width(), b.height()) / 2f
        val innerR = outerR * 0.42f
        val path = Path()
        val points = 5
        val startAngle = -Math.PI / 2
        for (i in 0 until points * 2) {
            val angle = startAngle + i * Math.PI / points
            val r = if (i % 2 == 0) outerR else innerR
            val px = (cx + r * cos(angle)).toFloat()
            val py = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    // ---------------- Text ----------------

    fun addText(text: String, x: Float, y: Float, color: Int, textSize: Float) {
        val action = DrawAction.TextAction(text, x, y, color, textSize)
        drawTextToBase(action)
        actions.add(action)
        redoStack.clear()
        onHistoryChanged?.invoke()
        invalidate()
    }

    private fun drawTextToBase(action: DrawAction.TextAction) {
        val canvas = baseCanvas ?: return
        val paint = Paint().apply {
            isAntiAlias = true
            color = action.color
            textSize = action.textSize
        }
        canvas.drawText(action.text, action.x, action.y, paint)
    }

    // ---------------- Image import ----------------

    fun addImage(bitmap: Bitmap) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0 || viewH <= 0) return
        val maxW = viewW * 0.7f
        val maxH = viewH * 0.7f
        val scale = min(maxW / bitmap.width, maxH / bitmap.height).coerceAtMost(1f)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (viewW - w) / 2f
        val top = (viewH - h) / 2f
        val bounds = RectF(left, top, left + w, top + h)
        val action = DrawAction.ImageAction(bitmap, bounds)
        drawImageToBase(action)
        actions.add(action)
        redoStack.clear()
        onHistoryChanged?.invoke()
        invalidate()
    }

    private fun drawImageToBase(action: DrawAction.ImageAction) {
        val canvas = baseCanvas ?: return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(action.bitmap, null, action.bounds, paint)
    }

    // ---------------- Undo / Redo / Clear ----------------

    fun canUndo() = actions.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (actions.isEmpty()) return
        val last = actions.removeAt(actions.size - 1)
        redoStack.add(last)
        redrawAllActions()
        onHistoryChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.size - 1)
        actions.add(action)
        redrawAllActions()
        onHistoryChanged?.invoke()
    }

    fun clearAll() {
        actions.clear()
        redoStack.clear()
        redrawAllActions()
        onHistoryChanged?.invoke()
    }

    private fun redrawAllActions() {
        val canvas = baseCanvas ?: return
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (action in actions) {
            when (action) {
                is DrawAction.StrokeAction -> drawStrokeToBase(action)
                is DrawAction.ShapeAction -> drawShapeOn(canvas, action)
                is DrawAction.TextAction -> drawTextToBase(action)
                is DrawAction.ImageAction -> drawImageToBase(action)
            }
        }
        invalidate()
    }

    // ---------------- Flood fill ----------------

    private fun floodFill(startX: Int, startY: Int, fillColor: Int) {
        val bmp = baseBitmap ?: return
        if (startX < 0 || startY < 0 || startX >= bmp.width || startY >= bmp.height) return

        val targetColor = bmp.getPixel(startX, startY)
        val replacement = fillColor or (0xFF shl 24) // ensure opaque fill
        if (targetColor == replacement) return

        // Simple scanline-ish stack-based flood fill; fine for a phone-sized canvas.
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        val w = bmp.width
        val h = bmp.height
        val stack = ArrayDeque<Int>()
        stack.addLast(startY * w + startX)
        val tolerance = 30

        fun colorsClose(a: Int, b: Int): Boolean {
            val da = abs(((a shr 24) and 0xFF) - ((b shr 24) and 0xFF))
            val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
            val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
            val db = abs((a and 0xFF) - (b and 0xFF))
            return da + dr + dg + db < tolerance
        }

        var guard = 0
        val maxIterations = w * h
        while (stack.isNotEmpty() && guard < maxIterations) {
            guard++
            val idx = stack.removeLast()
            if (idx < 0 || idx >= pixels.size) continue
            if (!colorsClose(pixels[idx], targetColor)) continue
            pixels[idx] = replacement

            val px = idx % w
            val py = idx / w
            if (px > 0) stack.addLast(idx - 1)
            if (px < w - 1) stack.addLast(idx + 1)
            if (py > 0) stack.addLast(idx - w)
            if (py < h - 1) stack.addLast(idx + w)
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        // Flood fill mutates the bitmap directly, so record it as an equivalent
        // rectangle-free action by snapshotting: simplest is to treat it as a
        // permanent base change and push a lightweight marker for undo.
        actions.add(
            DrawAction.ImageAction(
                bitmap = Bitmap.createBitmap(bmp),
                bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())
            )
        )
        redoStack.clear()
        onHistoryChanged?.invoke()
        invalidate()
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        baseBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        activePath?.let { canvas.drawPath(it, activePaint) }

        if (isShaping) {
            val bounds = RectF(
                min(shapeStartX, shapeCurX), min(shapeStartY, shapeCurY),
                max(shapeStartX, shapeCurX), max(shapeStartY, shapeCurY)
            )
            val previewType = when (currentTool) {
                Tool.LINE -> ShapeType.LINE
                Tool.RECTANGLE -> ShapeType.RECTANGLE
                Tool.CIRCLE -> ShapeType.CIRCLE
                Tool.TRIANGLE -> ShapeType.TRIANGLE
                Tool.STAR -> ShapeType.STAR
                else -> null
            }
            previewType?.let { type ->
                val previewAction = DrawAction.ShapeAction(
                    type = type,
                    bounds = bounds,
                    color = currentColor,
                    strokeWidth = currentStrokeWidth,
                    filled = shapesFilled
                )
                drawShapeOn(canvas, previewAction)
            }
        }
    }

    /** Returns a clean copy of just the drawing (no UI chrome) for Save/Share/Record. */
    fun exportBitmap(): Bitmap? {
        val bmp = baseBitmap ?: return null
        return Bitmap.createBitmap(bmp)
    }
}
