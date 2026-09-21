package com.mom.privatedrawing

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var leftPanel: View
    private lateinit var toolStrip: View
    private lateinit var topActions: View
    private lateinit var colorGrid: GridLayout
    private lateinit var customColorGrid: GridLayout
    private lateinit var brushSizeSlider: SeekBar
    private lateinit var recordingBadge: View
    private lateinit var recordingTime: TextView

    private var customColors = mutableListOf<Int>()
    private var selectedSwatch: View? = null

    private var isFullscreen = false
    private var recordingSeconds = 0
    private val recordingHandler = Handler(Looper.getMainLooper())
    private var recordingOutputFile: java.io.File? = null

    private val recordingTicker = object : Runnable {
        override fun run() {
            recordingSeconds++
            val m = recordingSeconds / 60
            val s = recordingSeconds % 60
            recordingTime.text = String.format("%02d:%02d", m, s)
            recordingHandler.postDelayed(this, 1000)
        }
    }

    private val defaultPalette = listOf(
        Color.BLACK, Color.parseColor("#808080"), Color.WHITE,
        Color.parseColor("#E5484D"), Color.parseColor("#F4A340"), Color.parseColor("#F6D34E"),
        Color.parseColor("#3FB950"), Color.parseColor("#7BE0A0"), Color.parseColor("#4CC9C0"),
        Color.parseColor("#3D7CF4"), Color.parseColor("#8FB4F5"), Color.parseColor("#8E5BEF"),
        Color.parseColor("#F5A3C7"), Color.parseColor("#8B5A2B")
    )

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, it)
            drawingView.addImage(bitmap)
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            beginRecording(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Recording permission was not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        leftPanel = findViewById(R.id.leftPanel)
        toolStrip = findViewById(R.id.toolStrip)
        topActions = findViewById(R.id.topActions)
        colorGrid = findViewById(R.id.colorGrid)
        customColorGrid = findViewById(R.id.customColorGrid)
        brushSizeSlider = findViewById(R.id.brushSizeSlider)
        recordingBadge = findViewById(R.id.recordingBadge)
        recordingTime = findViewById(R.id.recordingTime)

        customColors = ColorStore.loadCustomColors(this)

        buildColorGrid()
        buildCustomColorGrid()
        buildToolStrip()
        wireBrushSizeControls()
        wireTopActions()

        drawingView.onHistoryChanged = { refreshUndoRedoState() }
        drawingView.onCanvasTapForText = { x, y -> showTextInputDialog(x, y) }
    }

    // ---------------- Colors ----------------

    private fun buildColorGrid() {
        colorGrid.removeAllViews()
        for (color in defaultPalette) {
            colorGrid.addView(makeSwatch(color) { selectColor(color, it) })
        }
        colorGrid.addView(makeRainbowSwatch())
    }

    private fun buildCustomColorGrid() {
        customColorGrid.removeAllViews()
        for (color in customColors) {
            customColorGrid.addView(makeSwatch(color) { selectColor(color, it) })
        }
        val addButton = TextView(this).apply {
            text = "+"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(3, ContextCompat.getColor(this@MainActivity, R.color.panel_border))
            }
            layoutParams = swatchLayoutParams()
            setOnClickListener { openColorPicker() }
        }
        customColorGrid.addView(addButton)
    }

    private fun swatchLayoutParams(): GridLayout.LayoutParams {
        val sizePx = (40 * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()
        val params = GridLayout.LayoutParams()
        params.width = sizePx
        params.height = sizePx
        params.setMargins(marginPx, marginPx, marginPx, marginPx)
        return params
    }

    private fun makeSwatch(color: Int, onClick: (View) -> Unit): View {
        val swatch = View(this)
        swatch.layoutParams = swatchLayoutParams()
        swatch.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, ContextCompat.getColor(this@MainActivity, R.color.panel_border))
        }
        swatch.setOnClickListener { onClick(swatch) }
        return swatch
    }

    private fun makeRainbowSwatch(): View {
        val swatch = View(this)
        swatch.layoutParams = swatchLayoutParams()
        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
        swatch.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            gradientType = GradientDrawable.SWEEP_GRADIENT
            shape = GradientDrawable.OVAL
        }
        swatch.setOnClickListener { openColorPicker() }
        return swatch
    }

    private fun selectColor(color: Int, view: View) {
        drawingView.currentColor = color
        selectedSwatch = view
        highlightSelected(view)
    }

    private fun highlightSelected(view: View) {
        for (grid in listOf(colorGrid, customColorGrid)) {
            for (i in 0 until grid.childCount) {
                grid.getChildAt(i).scaleX = 1f
                grid.getChildAt(i).scaleY = 1f
            }
        }
        view.scaleX = 1.15f
        view.scaleY = 1.15f
    }

    private fun openColorPicker() {
        ColorPickerDialog.show(
            this,
            drawingView.currentColor,
            onUseColor = { color -> drawingView.currentColor = color },
            onAddToMyColors = { color ->
                customColors.add(0, color)
                if (customColors.size > 12) customColors = customColors.take(12).toMutableList()
                ColorStore.saveCustomColors(this, customColors)
                buildCustomColorGrid()
                drawingView.currentColor = color
            }
        )
    }

    // ---------------- Tools ----------------

    private data class ToolEntry(val tool: Tool, val label: String, val emoji: String)

    private val toolEntries = listOf(
        ToolEntry(Tool.PEN, "Pen", "🖊️"),
        ToolEntry(Tool.PENCIL, "Pencil", "✏️"),
        ToolEntry(Tool.MARKER, "Marker", "🖍️"),
        ToolEntry(Tool.HIGHLIGHTER, "Highlighter", "🖌️"),
        ToolEntry(Tool.ERASER, "Eraser", "🧽"),
        ToolEntry(Tool.FILL, "Fill", "🪣"),
        ToolEntry(Tool.TEXT, "Text", "🔤"),
        ToolEntry(Tool.LINE, "Line", "／"),
        ToolEntry(Tool.RECTANGLE, "Rectangle", "▭"),
        ToolEntry(Tool.CIRCLE, "Circle", "◯"),
        ToolEntry(Tool.TRIANGLE, "Triangle", "△"),
        ToolEntry(Tool.STAR, "Star", "★"),
        ToolEntry(Tool.IMAGE, "Image", "🖼️")
    )

    private val toolButtons = mutableMapOf<Tool, TextView>()
    private lateinit var undoButton: TextView
    private lateinit var redoButton: TextView

    private fun buildToolStrip() {
        val container = findViewById<LinearLayout>(R.id.toolStripInner)
        container.removeAllViews()

        for (entry in toolEntries) {
            val btn = TextView(this).apply {
                text = "${entry.emoji}\n${entry.label}"
                gravity = android.view.Gravity.CENTER
                textSize = 11f
                setPadding(4, 8, 4, 8)
                minWidth = (64 * resources.displayMetrics.density).toInt()
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (entry.tool == Tool.IMAGE) {
                        imagePicker.launch("image/*")
                    } else {
                        selectTool(entry.tool)
                    }
                }
            }
            toolButtons[entry.tool] = btn
            container.addView(btn)
        }

        undoButton = TextView(this).apply {
            text = "↩️\nUndo"
            gravity = android.view.Gravity.CENTER
            textSize = 11f
            setPadding(4, 8, 4, 8)
            minWidth = (64 * resources.displayMetrics.density).toInt()
            setOnClickListener { drawingView.undo() }
        }
        redoButton = TextView(this).apply {
            text = "↪️\nRedo"
            gravity = android.view.Gravity.CENTER
            textSize = 11f
            setPadding(4, 8, 4, 8)
            minWidth = (64 * resources.displayMetrics.density).toInt()
            setOnClickListener { drawingView.redo() }
        }
        val clearButton = TextView(this).apply {
            text = "🗑️\nClear"
            gravity = android.view.Gravity.CENTER
            textSize = 11f
            setPadding(4, 8, 4, 8)
            minWidth = (64 * resources.displayMetrics.density).toInt()
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.record_red))
            setOnClickListener { confirmClear() }
        }

        container.addView(undoButton)
        container.addView(redoButton)
        container.addView(clearButton)

        selectTool(Tool.PEN)
        refreshUndoRedoState()
    }

    private fun selectTool(tool: Tool) {
        drawingView.currentTool = tool
        for ((t, btn) in toolButtons) {
            btn.alpha = if (t == tool) 1f else 0.55f
            btn.setBackgroundColor(if (t == tool) Color.parseColor("#EEF2FB") else Color.TRANSPARENT)
        }
    }

    private fun refreshUndoRedoState() {
        undoButton.alpha = if (drawingView.canUndo()) 1f else 0.4f
        redoButton.alpha = if (drawingView.canRedo()) 1f else 0.4f
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear the entire drawing?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> drawingView.clearAll() }
            .show()
    }

    // ---------------- Brush size ----------------

    private fun wireBrushSizeControls() {
        brushSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val density = resources.displayMetrics.density
                drawingView.currentStrokeWidth = (2 + progress * 0.58f) * density
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        findViewById<TextView>(R.id.sizeSmall).setOnClickListener { brushSizeSlider.progress = 10 }
        findViewById<TextView>(R.id.sizeMedium).setOnClickListener { brushSizeSlider.progress = 30 }
        findViewById<TextView>(R.id.sizeLarge).setOnClickListener { brushSizeSlider.progress = 55 }
        findViewById<TextView>(R.id.sizeXL).setOnClickListener { brushSizeSlider.progress = 85 }
    }

    // ---------------- Text tool ----------------

    private fun showTextInputDialog(x: Float, y: Float) {
        val input = EditText(this)
        input.hint = "Type your text"
        AlertDialog.Builder(this)
            .setTitle("Add Text")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    val textSize = 18f + (brushSizeSlider.progress * 0.6f)
                    drawingView.addText(text, x, y, drawingView.currentColor, textSize)
                }
            }
            .show()
    }

    // ---------------- Top actions: fullscreen / save / share / record ----------------

    private fun wireTopActions() {
        findViewById<View>(R.id.btnFullscreen).setOnClickListener { toggleFullscreen() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveDrawing() }
        findViewById<View>(R.id.btnShare).setOnClickListener { shareDrawing() }
        findViewById<View>(R.id.btnRecord).setOnClickListener { onRecordButtonClicked() }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        leftPanel.visibility = if (isFullscreen) View.GONE else View.VISIBLE
        toolStrip.visibility = if (isFullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.appTitle).visibility = if (isFullscreen) View.GONE else View.VISIBLE
        val fullscreenLabel = findViewById<TextView>(R.id.btnFullscreen)
        fullscreenLabel.text = if (isFullscreen) "⤢  Exit Full Screen" else "⤢  Full Screen"
    }

    private fun saveDrawing() {
        val bitmap = drawingView.exportBitmap() ?: return
        val uri = SaveUtil.savePngToGallery(this, bitmap)
        if (uri != null) {
            Toast.makeText(this, "Saved to Pictures / DrawingJoy", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Could not save the drawing.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDrawing() {
        val bitmap = drawingView.exportBitmap() ?: return
        val uri = SaveUtil.saveTempPngForShare(this, bitmap)
        SaveUtil.shareFile(this, uri, "image/png")
    }

    // ---------------- Recording ----------------

    private fun onRecordButtonClicked() {
        if (RecordingService.isRecording) {
            stopRecordingFlow()
        } else {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    private fun beginRecording(resultCode: Int, data: Intent) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val outputFile = SaveUtil.newRecordingFile(this)
        recordingOutputFile = outputFile

        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecordingService.EXTRA_RESULT_DATA, data)
            putExtra(RecordingService.EXTRA_WIDTH, metrics.widthPixels)
            putExtra(RecordingService.EXTRA_HEIGHT, metrics.heightPixels)
            putExtra(RecordingService.EXTRA_DENSITY, metrics.densityDpi)
            putExtra(RecordingService.EXTRA_OUTPUT_PATH, outputFile.absolutePath)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        recordingSeconds = 0
        recordingBadge.visibility = View.VISIBLE
        recordingHandler.post(recordingTicker)
        findViewById<TextView>(R.id.btnRecord).text = "⏹  Stop"

        Toast.makeText(this, "Recording started. Keep drawing!", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingFlow() {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        startService(stopIntent)

        recordingHandler.removeCallbacks(recordingTicker)
        recordingBadge.visibility = View.GONE
        findViewById<TextView>(R.id.btnRecord).text = "🔴  Record"

        val file = recordingOutputFile
        if (file != null) {
            recordingHandler.postDelayed({ showRecordingResultDialog(file) }, 500)
        }
    }

    private fun showRecordingResultDialog(file: java.io.File) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "Recording did not save correctly.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Recording finished")
            .setMessage("Your drawing video is ready: ${file.name}")
            .setNegativeButton("Delete") { _, _ -> file.delete() }
            .setNeutralButton("Share") { _, _ ->
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", file
                )
                SaveUtil.shareFile(this, uri, "video/mp4")
            }
            .setPositiveButton("Done", null)
            .show()
    }
}
