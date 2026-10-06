package gr.volley.tacticsboard

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class TacticsBoardView(context: Context) : View(context) {

    private data class Stroke(val path: Path, val erase: Boolean)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var eraserMode = false

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 25, 25)
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val erasePaint = Paint(drawPaint).apply {
        strokeWidth = dp(34f)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val courtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = dp(14f)
    }

    private val toolbarHeight get() = dp(92f)
    private val buttonGap get() = dp(8f)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        background = GradientDrawable().apply { setColor(Color.rgb(245,245,245)) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCourt(canvas)

        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height - toolbarHeight, null)
        strokes.forEach { s -> canvas.drawPath(s.path, if (s.erase) erasePaint else drawPaint) }
        currentPath?.let { canvas.drawPath(it, if (eraserMode) erasePaint else drawPaint) }
        canvas.restoreToCount(save)

        drawToolbar(canvas)
    }

    private fun drawCourt(canvas: Canvas) {
        val top = dp(18f)
        val bottom = height - toolbarHeight - dp(14f)
        val availableH = bottom - top
        val courtW = min(width - dp(28f), availableH * 0.67f)
        val left = (width - courtW) / 2f
        val right = left + courtW
        val rect = RectF(left, top, right, bottom)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 111, 46); style = Paint.Style.FILL }
        canvas.drawRoundRect(rect, dp(5f), dp(5f), fill)
        canvas.drawRect(rect, courtPaint)

        val midY = (top + bottom) / 2f
        canvas.drawLine(left, midY, right, midY, courtPaint)

        val attackOffset = (bottom - top) / 6f
        canvas.drawLine(left, midY - attackOffset, right, midY - attackOffset, courtPaint)
        canvas.drawLine(left, midY + attackOffset, right, midY + attackOffset, courtPaint)
    }

    private fun drawToolbar(canvas: Canvas) {
        val top = height - toolbarHeight
        val bg = Paint().apply { color = Color.rgb(244,244,244) }
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), bg)

        val labels = arrayOf("✏  ΣΧΕΔΙΟ", "▰  ΣΦΟΥΓΓΑΡΙ", "↶  UNDO", "✕  ΚΑΘΑΡΙΣΜΟΣ")
        val count = labels.size
        val totalGap = buttonGap * (count + 1)
        val bw = (width - totalGap) / count
        val bh = dp(66f)
        val y = top + (toolbarHeight - bh) / 2f

        for (i in labels.indices) {
            val x = buttonGap + i * (bw + buttonGap)
            val rect = RectF(x, y, x + bw, y + bh)
            val selected = (i == 0 && !eraserMode) || (i == 1 && eraserMode)
            buttonPaint.color = if (selected) Color.rgb(21,101,192) else Color.WHITE
            buttonPaint.setShadowLayer(dp(2f), 0f, dp(1f), 0x33000000)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), buttonPaint)
            buttonPaint.clearShadowLayer()

            textPaint.color = if (selected) Color.WHITE else Color.rgb(35,35,35)
            val parts = labels[i].split("  ")
            textPaint.textSize = dp(23f)
            canvas.drawText(parts[0], rect.centerX(), rect.centerY() - dp(5f), textPaint)
            textPaint.textSize = dp(10.5f)
            canvas.drawText(parts.getOrElse(1){""}, rect.centerX(), rect.centerY() + dp(18f), textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val toolbarTop = height - toolbarHeight
        if (event.action == MotionEvent.ACTION_DOWN && event.y >= toolbarTop) {
            handleToolbarTap(event.x)
            return true
        }
        if (event.y >= toolbarTop) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply { moveTo(event.x, event.y) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.let { strokes.add(Stroke(it, eraserMode)) }
                currentPath = null
                invalidate()
            }
        }
        return true
    }

    private fun handleToolbarTap(x: Float) {
        val totalGap = buttonGap * 5
        val bw = (width - totalGap) / 4
        val index = ((x - buttonGap) / (bw + buttonGap)).toInt().coerceIn(0, 3)
        when (index) {
            0 -> eraserMode = false
            1 -> eraserMode = true
            2 -> if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
            3 -> strokes.clear()
        }
        invalidate()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
