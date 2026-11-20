package com.onlynote.app.ui.screens

import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Layout
import android.text.style.CharacterStyle
import android.text.style.SuggestionSpan
import android.text.style.UnderlineSpan
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.OverScroller
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.onlynote.app.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Потолок длины заметки — как у ColorNote.
 *
 * Не «сколько влезет в память», а сколько можно набирать с сохранением
 * отзывчивости. Знаки в Android считаются в единицах UTF-16, поэтому
 * эмодзи занимает два.
 */
internal const val MAX_NOTE_CHARS = 150_000

/** Поля заметки: примерно 1 мм от края экрана. */
private const val FIELD_SIDE_PADDING = 6f

/**
 * Воздух между разделителем и первой строкой — тот же, что у шапки.
 *
 * Это не padding вида, а часть прокрутки: padding обрезал бы текст за 10 dp до
 * разделителя (см. scrollMinPx).
 */
private const val NOTE_AIR_TOP = 10f

/** Воздух под последней строкой — ровно как под строкой курсора у клавиатуры. */
private const val NOTE_AIR_BOTTOM = 10f


/** Фейд под шапкой: снизу растворяется плавно, на 20 % длиннее прежнего.
 *  Плотность — как в 3.8: 0,88 у самого края. Слабее нельзя: на чёрном фоне
 *  недокрашенный фейд не растворяет строку, а оставляет её обрезанной,
 *  и это читается как чёрная полоска поверх текста. */
private const val FADE_TOP_HEIGHT = 34f
private const val FADE_TOP_ALPHA = 0.88f

/** Фейд у клавиатуры — только в правке и поиске. Плотность как в 3.8. */
private const val FADE_BOTTOM_HEIGHT = 19f
private const val FADE_BOTTOM_ALPHA = 0.80f

/** Полоска прокрутки: ширина, поле от краёв, наименьшая длина. */
private const val THUMB_WIDTH = 3f
private const val THUMB_INSET = 12f
private const val THUMB_MIN = 28f
private const val THUMB_FADE_IN_MS = 160L
private const val THUMB_FADE_OUT_MS = 310L
private const val THUMB_IDLE_MS = 600L

/** Сколько ещё после отпускания пальца считать прокрутку «рукой» (инерция). */
private const val SCROLL_AFTER_UP_MS = 1_200L

/** Пауза после набора, в которую прокрутка к курсору не показывает полоску. */
private const val TYPING_SCROLL_SUPPRESS_MS = 180L

/** Сколько висит сообщение о потолке длины. */
private const val NOTICE_MS = 2_200L

/** Мигание курсора: полсекунды виден, полсекунды нет — как у платформы. */
private const val CARET_BLINK_MS = 500L

/**
 * Поле заметки на всё содержимое сразу — настоящее [EditText].
 *
 * Раньше заметка резалась на блоки по ~1200 знаков, и это лечило скорость набора,
 * но ломало выделение: «выбрать всё» брало только блок, выделение через границу
 * не работало. Платформа раскладывает текст инкрементально, поэтому одно поле
 * на 110 000 знаков стоит 5 мс на кадр (замер 16 ноября). Здесь оно и живёт.
 *
 * Всё, что раньше рисовала Compose поверх блоков, рисуется в самом поле:
 * фейды, полоска прокрутки, сообщение о потолке. Так не приходится спорить
 * с порядком отрисовки нативного вида и Compose.
 */
@SuppressLint("WrongConstant", "ClickableViewAccessibility")
internal class NoteEditText(context: Context) : EditText(context) {

    /** Тап по тексту в просмотре: смещение знака под пальцем. */
    var onTapAt: ((Int) -> Unit)? = null

    /** Попытка вставить больше потолка. */
    var onLimitReached: (() -> Unit)? = null

    /** Отмена и возврат с аппаратной клавиатуры. */
    var onUndoKey: (() -> Unit)? = null
    var onRedoKey: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    /** Клавиатурный слушатель режима правки: в просмотре его снимаем. */
    private var editingKeys = keyListener

    /** Правка или просмотр: поле редактируемо всегда, режим задаёт этот флаг. */
    private var modeEditable = false

    private val fadePaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noticePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noticeBoxPaint = Paint()
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Свой курсор: платформенный спрятан прозрачным, чтобы не рисовался дважды. */
    private var caretVisible = true
    private val caretBlinkRunnable: Runnable = object : Runnable {
        override fun run() {
            caretVisible = !caretVisible
            invalidate()
            if (caretShouldBlink()) postDelayed(this, CARET_BLINK_MS)
        }
    }

    private var topShader: Shader? = null
    private var bottomShader: Shader? = null
    private var shaderHeight = -1

    private var bottomFadeVisible = false

    /** Человек уже листал: воздух над первой строкой больше не навязываем. */
    private var userScrolled = false

    private var thumbAlpha = 0f
    private var thumbTarget = 0f
    private var thumbAnimator: ValueAnimator? = null
    private val hideThumbRunnable = Runnable { animateThumb(0f, THUMB_FADE_OUT_MS) }

    private var touching = false
    private var lastTouchUp = 0L
    private var thumbSuppressedUntil = 0L

    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    // Порог начала прокрутки меньше системного: иначе короткий свайп «заедает»
    // и первые 20 px хода пропадают.
    private val scrollSlop by lazy { (touchSlop * 0.5f).coerceAtLeast(dp(2f).toFloat()) }
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()

    /** Инерция после отпускания пальца: у TextView своей нет, а без неё
     *  длинную заметку приходится таскать экран за экраном. */
    private val flingScroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var gestureScroll = false
    private var handScroll = false
    private var lastY = 0f
    private val minFlingVelocity by lazy {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity
    }

    private var notice: String? = null
    private var noticeUntil = 0L
    private val noticeRunnable = Runnable { invalidate() }

    /** Совпадения поиска: рисуем сами под текстом — разметка участков в уже
     *  построенной раскладке TextView не подхватывается без пересборки. */
    private var highlightRanges: List<IntRange> = emptyList()
    private var highlightActive = 0
    private var highlightActiveColor = 0
    private var highlightOtherColor = 0
    private val highlightPaint = Paint()

    private var textColorInt = Color.WHITE
    private var cursorColorInt = Color.WHITE
    private var currentTextSize = 0f
    private var currentLineHeight = 0f
    private var noticeTextSize = 0f

    init {
        background = null
        // Поля считаем сами: у Compose их не было, у TextView они по умолчанию другие.
        includeFontPadding = false
        gravity = Gravity.TOP or Gravity.START
        isSingleLine = false
        // Подсказки и автозамена выключены: заметка — это текст, который человек
        // написал сам, а не черновик для автозамены.
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            // Заглавная в начале строки и после точки: без этого флага клавиатура
            // писала всё строчными, хотя в строке названия папки заглавная была.
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        // Поле не должно восстанавливать текст из состояния вида: источник правды — база.
        isSaveEnabled = false
        isFocusableInTouchMode = true
        isCursorVisible = false
        showSoftInputOnFocus = false
        // По вертикали полей нет: padding вида обрезает текст за 10 dp до
        // разделителя и до клавиатуры — полоса, в которую текст не заходит.
        // Воздух живёт в границах прокрутки (scrollMin/scrollMax).
        setPadding(dp(FIELD_SIDE_PADDING), 0, dp(FIELD_SIDE_PADDING), 0)
        typeface = ResourcesCompat.getFont(context, R.font.rubik_regular) ?: Typeface.DEFAULT
        breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        letterSpacing = 0f
        setLineSpacing(0f, 1f)
        editingKeys = keyListener
        // Поле остаётся редактируемым всегда: так платформа сама прокручивает
        // своё содержимое и выделяет текст. В просмотре клавиатура и курсор
        // выключены (applyMode), а правка включается фокусом.
        setTextIsSelectable(true)
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private fun blackAlpha(alpha: Float): Int =
        Color.argb((alpha * 255f).roundToInt(), 0, 0, 0)

    /** Типографика и цвета заметки. Вызывается и при создании, и при смене цвета. */
    fun applyStyle(
        textColor: Int,
        cursorColor: Int,
        textSizePx: Float,
        lineHeightPx: Float,
    ) {
        if (currentTextSize != textSizePx || currentLineHeight != lineHeightPx) {
            currentTextSize = textSizePx
            currentLineHeight = lineHeightPx
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            // Межстрочный 120 %: TextView множитель считает от высоты шрифта,
            // а не от кегля, поэтому добираем разницу добавкой. Шаг округляем
            // вверх до целого пикселя — этим Compose и отличался: 55.44 px
            // он раскладывал как 56, а TextView как 55.
            val metrics = paint.fontMetrics
            val target = ceil(max(lineHeightPx, textSizePx * 1.2f))
            val extra = (target - (metrics.descent - metrics.ascent)).coerceAtLeast(0f)
            setLineSpacing(extra, 1f)
            noticeTextSize = textSizePx * 0.8f
            noticePaint.textSize = noticeTextSize
            noticePaint.typeface = typeface
        }
        if (textColorInt != textColor) {
            textColorInt = textColor
            setTextColor(textColor)
            noticePaint.color = withAlpha(textColor, 0.72f)
            invalidate()
        }
        if (cursorColorInt != cursorColor) {
            cursorColorInt = cursorColor
            tintCursorAndHandles()
            highlightColor = withAlpha(cursorColor, 0.30f)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255f).roundToInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    /** Курсор и ручки выделения красятся цветом заметки — там, где платформа это умеет. */
    private fun tintCursorAndHandles() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        // Свой курсор рисуем сами (см. drawCaret), а платформенный прячем
        // прозрачным: он всё ещё нужен полю, чтобы автопрокрутка к курсору
        // при наборе работала. У темы, в которой живёт поле, своего курсора
        // может не быть вовсе — тогда он просто не рисуется.
        textCursorDrawable = ShapeDrawable().apply {
            intrinsicWidth = 1
            intrinsicHeight = 1
        }
        textSelectHandle?.let { drawable ->
            drawable.mutate().setTint(cursorColorInt)
            setTextSelectHandle(drawable)
        }
        textSelectHandleLeft?.let { drawable ->
            drawable.mutate().setTint(cursorColorInt)
            setTextSelectHandleLeft(drawable)
        }
        textSelectHandleRight?.let { drawable ->
            drawable.mutate().setTint(cursorColorInt)
            setTextSelectHandleRight(drawable)
        }
    }

    /**
     * Правка или просмотр.
     *
     * В просмотре поле остаётся выделяемым: долгое нажатие даёт родное меню
     * «Копировать» и «Выбрать всё», а клавиатура не выезжает.
     */
    fun applyMode(editable: Boolean) {
        if (modeEditable == editable) {
            if (editable) isCursorVisible = true
            return
        }
        modeEditable = editable
        if (editable) {
            // Поле остаётся выделяемым и в правке: иначе долгое нажатие не
            // выделяет слово и не показывает «Копировать / Выбрать всё».
            setTextIsSelectable(true)
            isFocusableInTouchMode = true
            isFocusable = true
            isClickable = true
            isLongClickable = true
            showSoftInputOnFocus = true
            keyListener = editingKeys
            isCursorVisible = true
            restartCaretBlink()
            suppressThumb(500)
        } else {
            // Просмотр: поле по-прежнему редактируемо — иначе оно не умеет
            // прокручивать своё содержимое, — но клавиатура и курсор выключены.
            isCursorVisible = false
            showSoftInputOnFocus = false
            isFocusableInTouchMode = true
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setTextIsSelectable(true)
            removeCallbacks(caretBlinkRunnable)
            caretVisible = false
            invalidate()
            hideThumbNow()
        }
    }

    fun isModeEditable(): Boolean = modeEditable

    fun setBottomFade(visible: Boolean) {
        if (bottomFadeVisible == visible) return
        bottomFadeVisible = visible
        invalidate()
    }

    fun suppressThumb(ms: Long) {
        thumbSuppressedUntil = max(thumbSuppressedUntil, SystemClock.uptimeMillis() + ms)
    }

    /** Совпадения поиска для отрисовки под текстом. */
    fun setHighlights(
        ranges: List<IntRange>,
        activeIndex: Int,
        activeColor: Int,
        otherColor: Int,
    ) {
        highlightRanges = ranges
        highlightActive = activeIndex
        highlightActiveColor = activeColor
        highlightOtherColor = otherColor
        invalidate()
    }

    fun clearHighlights() {
        if (highlightRanges.isEmpty()) return
        highlightRanges = emptyList()
        invalidate()
    }

    /** Сообщение о потолке длины — прямо в поле, над нижним краем. */
    fun showNotice(text: String) {
        notice = text
        noticeUntil = SystemClock.uptimeMillis() + NOTICE_MS
        invalidate()
        removeCallbacks(noticeRunnable)
        postDelayed(noticeRunnable, NOTICE_MS + 32)
    }

    // ---------- отрисовка ----------

    override fun onDraw(canvas: Canvas) {
        // Подсветка поиска — под текстом, поэтому до super.onDraw.
        val highlightSave = canvas.save()
        if (scrollY != 0) canvas.translate(0f, scrollY.toFloat())
        drawHighlights(canvas)
        canvas.restoreToCount(highlightSave)

        super.onDraw(canvas)
        // Canvas у прокручиваемого TextView уже сдвинут на -scrollY: рисуем
        // фейды, полоску и курсор в координатах вида, компенсируя этот сдвиг.
        val save = canvas.save()
        if (scrollY != 0) canvas.translate(0f, scrollY.toFloat())
        drawCaret(canvas)
        drawThumb(canvas)
        drawFades(canvas)
        drawNotice(canvas)
        canvas.restoreToCount(save)
    }

    /** Подсветка совпадений поиска: прямоугольники под текстом, а не участки
     *  разметки — у TextView раскладка уже построена и новые участки не видит. */
    private fun drawHighlights(canvas: Canvas) {
        val ranges = highlightRanges
        if (ranges.isEmpty()) return
        val l = layout ?: return
        val visibleTop = scrollY.toFloat()
        val visibleBottom = (scrollY + height).toFloat()
        for (i in ranges.indices) {
            val range = ranges[i]
            val start = range.first.coerceIn(0, l.text.length)
            val end = (range.last + 1).coerceIn(start, l.text.length)
            if (start >= end) continue
            highlightPaint.color =
                if (i == highlightActive) highlightActiveColor else highlightOtherColor
            val firstLine = l.getLineForOffset(start)
            val lastLine = l.getLineForOffset(end - 1)
            for (line in firstLine..lastLine) {
                val lineTop = l.getLineTop(line).toFloat()
                val lineBottom = l.getLineBottom(line).toFloat()
                if (lineBottom <= visibleTop || lineTop >= visibleBottom) continue
                val from = l.getLineStart(line).coerceAtLeast(start)
                val to = l.getLineEnd(line).coerceAtMost(end)
                if (from >= to) continue
                val left = paddingLeft + l.getPrimaryHorizontal(from)
                val right = paddingLeft + l.getPrimaryHorizontal(to)
                val top = lineTop - scrollY
                val bottom = lineBottom - scrollY
                canvas.drawRect(left, top, right, bottom, highlightPaint)
            }
        }
    }

    /**
     * Прячет платформенные «капли» — ручку вставки и ручки выделения.
     *
     * Текст двигаем мы сами (`handScroll`, инерция), платформа об этом не знает:
     * её ручка остаётся на старом месте, висит поверх текста и может вылезти за
     * цветной разделитель — она рисуется вне поля. Своего курсора у нас хватает,
     * поэтому чужие ручки убираем, как только начинается движение.
     */
    private fun hidePlatformHandles() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val editorField = android.widget.TextView::class.java
                .getDeclaredField("mEditor")
                .apply { isAccessible = true }
            val editor = editorField.get(this) ?: return
            val controllerNames = arrayOf(
                "mInsertionPointCursorController",
                "mSelectionModifierCursorController",
            )
            for (name in controllerNames) {
                val field = editor.javaClass.getDeclaredField(name).apply { isAccessible = true }
                val controller = field.get(editor) ?: continue
                controller.javaClass.getMethod("hide").invoke(controller)
            }
        } catch (_: Throwable) {
            // Внутренности платформы могут отличаться от ожидаемых: без них
            // просто остаётся стандартное поведение.
        }
    }

    /** Курсор рисуем сами: у платформенного в этой теме своя жизнь и свой цвет. */
    private fun drawCaret(canvas: Canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!caretVisible || !modeEditable || !hasFocus()) return
        val start = selectionStart
        if (start != selectionEnd) return
        val l = layout ?: return
        val offset = start.coerceIn(0, l.text.length)
        val line = l.getLineForOffset(offset)
        val x = paddingLeft + l.getPrimaryHorizontal(offset) - scrollX
        val top = (l.getLineTop(line) - scrollY).toFloat()
        val bottom = (l.getLineBottom(line) - scrollY).toFloat()
        // Полоска рисуется только целиком внутри поля. У верхней кромки она
        // прижималась к цветному разделителю и читалась как его продолжение —
        // а курсор, ушедший под клавиатуру, и вовсе не должен торчать срезом.
        if (top < 0f || bottom > height) return
        val bar = dp(2f).coerceAtLeast(2)
        // Полоска стоит по центру знака: «капля» выделения указывает на середину
        // полоски, а не на её левый край.
        val left = (x - bar / 2f)
            .roundToInt()
            .coerceIn(paddingLeft, (width - paddingRight - bar).coerceAtLeast(paddingLeft))
            .toFloat()
        caretPaint.color = cursorColorInt
        canvas.drawRect(left, top, left + bar, bottom, caretPaint)
    }

    private fun drawThumb(canvas: Canvas) {
        if (thumbAlpha <= 0.01f) return
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        if (range <= extent) return

        val laneInset = dp(THUMB_INSET).toFloat()
        val bar = dp(THUMB_WIDTH).toFloat()
        val minLength = dp(THUMB_MIN).toFloat()
        val lane = (height - laneInset * 2f).coerceAtLeast(1f)
        val length = (lane * extent / range)
            .coerceIn(min(minLength, lane), lane)
        val path = (range - extent).coerceAtLeast(1)
        val fraction = (computeVerticalScrollOffset().toFloat() / path).coerceIn(0f, 1f)
        val right = width - dp(2f)
        val left = right - bar
        val top = laneInset + (lane - length) * fraction

        thumbPaint.color = Color.argb(
            (0.35f * thumbAlpha * 255f).roundToInt(),
            255,
            255,
            255,
        )
        canvas.drawRect(left.toFloat(), top, right.toFloat(), top + length, thumbPaint)
    }

    private fun drawFades(canvas: Canvas) {
        val h = height.toFloat()
        if (topShader == null || bottomShader == null || shaderHeight != height) {
            val topHeight = min(dp(FADE_TOP_HEIGHT).toFloat(), h)
            val bottomHeight = min(dp(FADE_BOTTOM_HEIGHT).toFloat(), h)
            topShader = LinearGradient(
                0f, 0f, 0f, topHeight,
                blackAlpha(FADE_TOP_ALPHA), Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            bottomShader = LinearGradient(
                0f, h - bottomHeight, 0f, h,
                Color.TRANSPARENT, blackAlpha(FADE_BOTTOM_ALPHA),
                Shader.TileMode.CLAMP,
            )
            shaderHeight = height
        }
        fadePaint.shader = topShader
        canvas.drawRect(0f, 0f, width.toFloat(), dp(FADE_TOP_HEIGHT).toFloat(), fadePaint)
        if (bottomFadeVisible) {
            val bottomHeight = dp(FADE_BOTTOM_HEIGHT).toFloat()
            fadePaint.shader = bottomShader
            canvas.drawRect(0f, h - bottomHeight, width.toFloat(), h, fadePaint)
        }
        fadePaint.shader = null
    }

    private fun drawNotice(canvas: Canvas) {
        val text = notice ?: return
        if (SystemClock.uptimeMillis() >= noticeUntil) return
        if (noticeTextSize <= 0f) return

        val padH = dp(12f).toFloat()
        val padV = dp(5f).toFloat()
        val textWidth = noticePaint.measureText(text)
        val textHeight = noticePaint.descent() - noticePaint.ascent()
        val boxWidth = textWidth + padH * 2f
        val boxHeight = textHeight + padV * 2f
        val left = (width - boxWidth) / 2f
        val bottom = height - dp(NOTE_AIR_BOTTOM).toFloat() - dp(2f)
        val top = bottom - boxHeight

        noticeBoxPaint.color = Color.BLACK
        canvas.drawRect(left, top, left + boxWidth, bottom, noticeBoxPaint)
        val baseline = top + padV - noticePaint.ascent()
        canvas.drawText(text, left + padH, baseline, noticePaint)
    }

    private fun showThumb() {
        removeCallbacks(hideThumbRunnable)
        // Анимацию запускаем один раз: если перезапускать её на каждом
        // событии прокрутки, альфа топчется на месте и полоска «долго не
        // появляется» — как раз то, на что жаловался пользователь.
        if (thumbTarget != 1f) animateThumb(1f, THUMB_FADE_IN_MS)
        postDelayed(hideThumbRunnable, THUMB_IDLE_MS)
    }

    private fun animateThumb(target: Float, duration: Long) {
        if (thumbTarget == target && thumbAnimator?.isRunning == true) return
        thumbTarget = target
        thumbAnimator?.cancel()
        if (abs(thumbAlpha - target) < 0.01f) {
            thumbAlpha = target
            invalidate()
            return
        }
        thumbAnimator = ValueAnimator.ofFloat(thumbAlpha, target).apply {
            this.duration = duration
            addUpdateListener {
                thumbAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun hideThumb() {
        hideThumbNow()
    }

    /** Воздух над первой строкой и под последней — в пикселях, из прокрутки. */
    /**
     * Подтяжка строки курсора едет, а не прыгает.
     *
     * 70 мс — четыре кадра: глаз читает это как «строка поднялась», а не как
     * рывок. Быстрее нельзя — движение не прочитается; медленнее — набор начнёт
     * отставать от пальца.
     */
    private val insetTop = dp(NOTE_AIR_TOP)
    private val insetBottom = dp(NOTE_AIR_BOTTOM)

    /** Верхняя граница прокрутки: первая строка на 10 dp ниже разделителя. */
    fun scrollMinPx(): Int = -insetTop

    /** Нижняя: последняя строка на 10 dp выше клавиатуры. */
    fun scrollMaxPx(): Int {
        val layoutHeight = layout?.height ?: 0
        return (layoutHeight - height + insetBottom).coerceAtLeast(scrollMinPx())
    }

    /** Высота, в которой живёт текст: до клавиатуры без нижнего воздуха. */
    fun visibleContentHeight(): Int = height - insetBottom

    /** Диапазон, видимая часть и пройденное — для полоски и переходов поиском. */
    override fun computeVerticalScrollRange(): Int =
        (layout?.height ?: 0) + insetTop + insetBottom

    override fun computeVerticalScrollExtent(): Int = height

    override fun computeVerticalScrollOffset(): Int =
        (scrollY - scrollMinPx()).coerceAtLeast(0)

    /** Переставить курсор уже после фокуса: до фокуса поле могло не создать редактор. */
    fun refreshCursor() {
        tintCursorAndHandles()
        isCursorVisible = modeEditable
        restartCaretBlink()
    }

    /** Мигание перезапускается на любом движении курсора и на каждой букве. */
    fun restartCaretBlink() {
        removeCallbacks(caretBlinkRunnable)
        caretVisible = true
        invalidate()
        if (caretShouldBlink()) postDelayed(caretBlinkRunnable, CARET_BLINK_MS)
    }

    private fun caretShouldBlink(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            modeEditable &&
            hasFocus() &&
            selectionStart == selectionEnd

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            restartCaretBlink()
            applyTopAir()
        } else {
            removeCallbacks(caretBlinkRunnable)
            caretVisible = false
            invalidate()
        }
    }

    /**
     * Платформа на каждом кадре сама подтягивает поле к курсору — по своим
     * границам (0…layoutH − height). Из-за этого воздух над первой и под
     * последней строкой сбрасывался в ноль: текст прилипал к разделителю,
     * а внизу возвращалась чёрная полоса. Строку курсора двигаем сами
     * ([ensureCaretVisible]), поэтому платформенную подтяжку выключаем.
     */
    override fun bringPointIntoView(offset: Int): Boolean = false

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        restartCaretBlink()
        ensureCaretVisible()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Воздух над первой строкой: платформа при получении фокуса сама
        // прокручивает поле к курсору (строка 0) и обнуляет прокрутку, поэтому
        // возвращаем его на каждой раскладке, пока человек не начал листать.
        applyTopAir()
        // Перевёрстка после буквы/Enter: подтягиваем строку курсора сразу,
        // не дожидаясь, пока это сделает платформа (она делает это с задержкой).
        ensureCaretVisible()
    }

    /**
     * Строка курсора не должна уходить за клавиатуру.
     *
     * Нижняя граница — `height - insetBottom`: это и есть отступ 10 dp до
     * клавиатуры. Двигаем ровно на нехватку, в том же кадре, где пришла буква.
     */
    private fun ensureCaretVisible() {
        if (!modeEditable || !hasFocus()) return
        // Backspace в начале строки склеивает её с предыдущей: текст становится
        // короче, и scrollY может оказаться за новой границей. Тогда внизу
        // повисает пустота, а платформа подтягивает её с задержкой. Клампим сразу.
        val min = scrollMinPx()
        val max = scrollMaxPx()
        if (scrollY > max) scrollTo(0, max)
        if (scrollY < min) scrollTo(0, min)
        if (selectionStart != selectionEnd) return
        val l = layout ?: return
        val offset = selectionEnd.coerceIn(0, l.text.length)
        val line = l.getLineForOffset(offset)
        val top = l.getLineTop(line) - scrollY
        val bottom = l.getLineBottom(line) - scrollY
        // Сверху текст может дойти до разделителя (воздух в прокрутке),
        // снизу — до воздуха над клавиатурой.
        val areaTop = 0
        val areaBottom = visibleContentHeight()
        val target = when {
            bottom > areaBottom -> scrollY + (bottom - areaBottom)
            top < areaTop -> scrollY + (top - areaTop)
            else -> scrollY
        }
        if (target == scrollY) return
        // Прыжок, а не ход: анимация подтяжки давала овершут, и от неё отказались.
        scrollTo(0, target.coerceIn(min, max))
    }

    fun ensureCaretVisibleNow() {
        ensureCaretVisible()
    }

    /**
     * Воздух над первой строкой: 10 dp между разделителем и текстом.
     *
     * Он живёт в прокрутке (`scrollY = -10 dp`), а не в padding вида: padding
     * обрезал бы текст за 10 dp до разделителя, и при листании над ним висела
     * бы чёрная полоса, в которую текст не заходит.
     */
    private fun applyTopAir() {
        if (modeEditable || userScrolled) return
        if (selectionStart != 0 || selectionEnd != 0) return
        if (scrollY <= 0) scrollTo(0, scrollMinPx())
    }

    private fun hideThumbNow() {
        removeCallbacks(hideThumbRunnable)
        thumbAnimator?.cancel()
        thumbTarget = 0f
        thumbAlpha = 0f
        invalidate()
    }

    // ---------- жесты и прокрутка ----------

    /**
     * Долгое нажатие: платформа выделяет слово и сразу же пытается начать
     * перетаскивание выделенного. На Android 15–16 тень выделения иногда
     * получается нулевой, и система падает с «Drag shadow dimensions must be
     * positive». Выделение и панель «Копировать» к этому моменту уже стоят,
     * а перетаскивание текста нам не нужно — падение платформы гасим.
     */
    override fun performLongClick(): Boolean = try {
        super.performLongClick()
    } catch (_: IllegalStateException) {
        true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                userScrolled = true
                downAt = now
                downX = event.x
                downY = event.y
                lastY = event.y
                gestureScroll = false
                handScroll = false
                if (!flingScroller.isFinished) flingScroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dy > touchSlop) gestureScroll = true
                // Нативное поле в правке тянет курсор за пальцем, а не прокручивает
                // текст. Забираем вертикальный жест себе: содержимое едет за пальцем.
                // Долгое начало жеста не запрет: если человек подержал палец и
                // повёл, это прокрутка, а не выделение (выделение даёт
                // непустое selection, и оно отсекается ниже).
                if (!handScroll &&
                    dy > scrollSlop &&
                    dy > dx &&
                    selectionStart == selectionEnd
                ) {
                    handScroll = true
                    // Движение начинается — платформенные ручки убираем сразу.
                    hidePlatformHandles()
                    cancelLongPress()
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                    // Ведём от точки нажатия, чтобы короткий свайп не терял ход.
                    lastY = downY
                }
                if (handScroll) {
                    val delta = lastY - event.y
                    lastY = event.y
                    if (delta != 0f) {
                        scrollTo(
                            0,
                            (scrollY + delta.toInt()).coerceIn(scrollMinPx(), scrollMaxPx()),
                        )
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                touching = false
                lastTouchUp = now
                velocityTracker?.addMovement(event)
                val tracker = velocityTracker
                velocityTracker = null
                val wasHand = handScroll
                handScroll = false
                if (!wasHand) {
                    val moved = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                    val tap = !moved && now - downAt < longPressTimeout - 50
                    if (tap && !modeEditable) {
                        // Тап в просмотре открывает правку в этом месте. Долгое
                        // нажатие остаётся за платформой: это выделение и копирование.
                        layout?.let { onTapAt?.invoke(getOffsetForPosition(event.x, event.y)) }
                    }
                }
                startFling(tracker)
                if (wasHand) return true
            }

            MotionEvent.ACTION_CANCEL -> {
                touching = false
                lastTouchUp = now
                handScroll = false
                velocityTracker?.recycle()
                velocityTracker = null
                if (!flingScroller.isFinished) flingScroller.forceFinished(true)
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Инерция: продолжаем прокрутку с той скоростью, с какой палец отпустил текст.
     *
     * Раньше здесь стоял запрет на жест длиннее longPressTimeout. Из-за него
     * медленный свайп и свайп «вперёд-назад» (он всегда длиннее полусекунды)
     * отпускались без инерции — текст резко вставал. Долгое нажатие отличается
     * не длительностью, а тем, что оно не стало прокруткой: это и проверяем.
     */
    private fun startFling(tracker: VelocityTracker?) {
        if (tracker == null) return
        tracker.computeCurrentVelocity(1000)
        val velocityY = tracker.yVelocity
        tracker.recycle()
        if (!gestureScroll) return
        if (selectionStart != selectionEnd) return
        if (abs(velocityY) < minFlingVelocity) return
        hidePlatformHandles()
        val min = scrollMinPx()
        val max = scrollMaxPx()
        if (max <= min) return
        flingScroller.fling(0, scrollY, 0, (-velocityY).toInt(), 0, 0, min, max)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (flingScroller.computeScrollOffset()) {
            scrollTo(0, flingScroller.currY.coerceIn(scrollMinPx(), scrollMaxPx()))
            postInvalidateOnAnimation()
        }
        super.computeScroll()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val now = SystemClock.uptimeMillis()
        // Полоска — только на движение рукой: прокрутка к курсору при наборе
        // и переходы поиском её не показывают. Инерция после пальца — тоже рука.
        // Подавление относится только к программной прокрутке: если палец на
        // экране или едет инерция, полоска появляется сразу, без задержки.
        val userDriven = touching || !flingScroller.isFinished
        if (!userDriven && now < thumbSuppressedUntil) return
        if (userDriven || now - lastTouchUp <= SCROLL_AFTER_UP_MS) showThumb()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shaderHeight = -1
        // Клавиатура уехала — нижняя граница прокрутки сместилась: не оставляем
        // прокрутку за ней, иначе под последней строкой повиснет пустота.
        val max = scrollMaxPx()
        if (scrollY > max) scrollTo(0, max)
        // Клавиатура выехала и область стала ниже — строка курсора могла
        // оказаться за краем. Подтягиваем сразу, не после анимации клавиатуры.
        ensureCaretVisible()
    }

    /**
     * Вставка всегда простым текстом.
     *
     * Заметка — это только буквы: ни начертаний, ни размеров, ни цветов. Из
     * буфера обмена приходит размеченный текст (браузер, мессенджер, документ),
     * и без этой развилки в заметке появлялись чужие шрифты — как в Word, а не
     * как в блокноте.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val plain = plainClipboardText()
            if (plain != null) {
                insertPlainText(plain)
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }

    private fun plainClipboardText(): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = manager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        // coerceToText разбирает и HTML, и Intent: на выходе обычная строка.
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    private fun insertPlainText(plain: String) {
        val editable = text ?: return
        val from = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val to = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val keep = MAX_NOTE_CHARS - (editable.length - (to - from))
        if (plain.length > keep) onLimitReached?.invoke()
        val piece = if (plain.length > keep) plain.take(keep.coerceAtLeast(0)) else plain
        if (piece.isEmpty()) return
        editable.replace(from, to, piece)
        setSelection(from + piece.length)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // В просмотре поле редактируемо ради прокрутки и выделения, но чужие
        // клавиши не должны менять текст: печатать можно только в правке.
        if (!modeEditable) {
            val printable = event.unicodeChar != 0
            val editing = keyCode == KeyEvent.KEYCODE_DEL ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_FORWARD_DEL
            if (printable || editing) return true
        }
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    (if (event.isShiftPressed) onRedoKey else onUndoKey)?.invoke()
                    return true
                }

                KeyEvent.KEYCODE_Y -> {
                    onRedoKey?.invoke()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideThumbRunnable)
        removeCallbacks(noticeRunnable)
        removeCallbacks(caretBlinkRunnable)
        thumbAnimator?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        if (!flingScroller.isFinished) flingScroller.forceFinished(true)
        super.onDetachedFromWindow()
    }
}

/**
 * Состояние поля, живущее вне композиции: набор текста не должен пересобирать
 * экран из-за нескольких чисел.
 */
internal class NoteFieldController(private val initialText: String) {

    var onTextChanged: (previous: String, next: String) -> Unit = { _, _ -> }
    var onLimitReached: () -> Unit = {}
    var onTap: (offset: Int) -> Unit = {}
    var onUndoKey: () -> Unit = {}
    var onRedoKey: () -> Unit = {}

    private var view: NoteEditText? = null
    private var lastText = initialText
    private var suppress = false
    private var editable = false
    private var pendingFocus: Pair<Int, Boolean>? = null
    private var pendingText: Pair<String, Int>? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (s == null) return
            stripFormatting(s)
            if (suppress) return
            val next = s.toString()
            if (next == lastText) return
            val previous = lastText
            lastText = next
            view?.suppressThumb(TYPING_SCROLL_SUPPRESS_MS)
            view?.restartCaretBlink()
            onTextChanged(previous, next)
        }
    }

    /**
     * Потолок длины отсекается до вставки, а не после: иначе вставка на двести
     * тысяч знаков успела бы разложиться и только потом откатиться.
     */
    private val limitFilter = InputFilter { source, start, end, dest, dstart, dend ->
        val insert = end - start
        val keep = MAX_NOTE_CHARS - (dest.length - (dend - dstart))
        if (insert <= keep) {
            null
        } else {
            onLimitReached()
            if (keep <= 0) "" else source.subSequence(start, start + keep)
        }
    }

    /**
     * Убирает из текста чужую разметку: начертания, размеры, цвета, фон.
     *
     * Подчёркивание и подсказки проверки орфографии остаются: ими пользуется
     * клавиатура во время набора, и без них ломается ввод и разметка опечаток.
     */
    private fun stripFormatting(text: Editable) {
        val spans = text.getSpans(0, text.length, CharacterStyle::class.java)
        if (spans.isEmpty()) return
        for (span in spans) {
            if (span is UnderlineSpan || span is SuggestionSpan) continue
            text.removeSpan(span)
        }
    }

    fun attach(field: NoteEditText) {
        view = field
        field.onTapAt = { offset -> onTap(offset) }
        field.onLimitReached = { onLimitReached() }
        field.onUndoKey = { onUndoKey() }
        field.onRedoKey = { onRedoKey() }

        field.setText(initialText)
        lastText = initialText
        field.addTextChangedListener(watcher)
        field.filters = arrayOf(limitFilter)
        field.applyMode(editable)

        pendingText?.let { (text, caret) ->
            pendingText = null
            applyText(text, caret)
        }
        pendingFocus?.let { (offset, keyboard) ->
            pendingFocus = null
            focusAt(offset, keyboard)
        }
    }

    fun applyStyle(textColor: Int, cursorColor: Int, textSizePx: Float, lineHeightPx: Float) {
        view?.applyStyle(textColor, cursorColor, textSizePx, lineHeightPx)
    }

    fun setEditing(on: Boolean) {
        editable = on
        view?.applyMode(on)
    }

    fun setBottomFade(visible: Boolean) {
        view?.setBottomFade(visible)
    }

    /** Поставить текст извне (отмена, возврат) — без записи в историю и без потолка. */
    fun applyText(text: String, caret: Int) {
        val field = view ?: run {
            pendingText = text to caret
            return
        }
        suppress = true
        val filters = field.filters
        field.filters = emptyArray()
        field.setText(text)
        field.filters = filters
        val position = caret.coerceIn(0, text.length)
        field.setSelection(position)
        lastText = text
        suppress = false
        field.suppressThumb(400)
        if (field.isModeEditable()) {
            field.post { field.ensureCaretVisibleNow() }
        }
    }

    fun focusAt(offset: Int, showKeyboard: Boolean) {
        val field = view
        if (field == null) {
            pendingFocus = offset to showKeyboard
            return
        }
        editable = true
        field.applyMode(true)
        field.suppressThumb(700)
        field.post {
            field.requestFocus()
            val caret = offset.coerceIn(0, field.length())
            field.setSelection(caret)
            field.ensureCaretVisibleNow()
            field.refreshCursor()
            if (showKeyboard) {
                field.post {
                    val imm = field.context.getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                    field.ensureCaretVisibleNow()
                }
            }
        }
    }

    fun stopEditing() {
        val field = view ?: return
        editable = false
        field.clearFocus()
        field.applyMode(false)
        val imm = field.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(field.windowToken, 0)
        field.hideThumb()
    }

    fun length(): Int = view?.length() ?: lastText.length

    fun showNotice(text: String) {
        view?.showNotice(text)
    }

    fun clearHighlight() {
        view?.clearHighlights()
    }

    fun highlight(
        matches: List<IntRange>,
        index: Int,
        activeColor: Int,
        otherColor: Int,
    ) {
        val field = view ?: return
        if (matches.isEmpty()) {
            field.clearHighlights()
            return
        }
        if (matches.size <= MAX_HIGHLIGHT_SPANS) {
            field.setHighlights(matches, index, activeColor, otherColor)
        } else {
            // Совпадений слишком много: светим только текущее, счётчик знает все.
            val active = index.coerceIn(0, matches.lastIndex)
            field.setHighlights(listOf(matches[active]), 0, activeColor, otherColor)
        }
    }

    /** Показать найденное ближе к верхней трети — так видно и строку, и строки вокруг. */
    fun scrollToOffset(offset: Int) {
        val field = view ?: return
        field.suppressThumb(400)
        field.post {
            val layout = field.layout ?: return@post
            val line = layout.getLineForOffset(offset.coerceIn(0, field.length()))
            val visible = field.visibleContentHeight().coerceAtLeast(1)
            val target = (layout.getLineTop(line) - visible * 0.3f).roundToInt()
            field.scrollTo(0, target.coerceIn(field.scrollMinPx(), field.scrollMaxPx()))
        }
    }
}

/** Поле заметки в Compose: нативный вид и ничего поверх него. */
@Composable
internal fun NoteField(
    controller: NoteFieldController,
    editing: Boolean,
    bottomFade: Boolean,
    textColor: ComposeColor,
    cursorColor: ComposeColor,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSize.toPx() }
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val text = textColor.toArgb()
    val cursor = cursorColor.toArgb()

    AndroidView(
        factory = { context ->
            NoteEditText(context).also { field ->
                controller.attach(field)
                controller.applyStyle(text, cursor, textSizePx, lineHeightPx)
                controller.setEditing(editing)
                controller.setBottomFade(bottomFade)
            }
        },
        update = { field ->
            controller.applyStyle(text, cursor, textSizePx, lineHeightPx)
            controller.setEditing(editing)
            controller.setBottomFade(bottomFade)
        },
        modifier = modifier.fillMaxSize(),
    )
}
