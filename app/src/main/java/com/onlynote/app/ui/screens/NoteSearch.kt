package com.onlynote.app.ui.screens

/**
 * Сколько подсветок разрешено ставить в поле за раз.
 *
 * Запрос в одну букву на заметке в сто тысяч знаков даёт десятки тысяч
 * совпадений. Столько фоновых участков поле не переживёт: каждая подсветка —
 * отдельный участок разметки. Поэтому при большом числе совпадений светим
 * только текущее, а счётчик по-прежнему показывает все.
 */
internal const val MAX_HIGHLIGHT_SPANS = 400

/**
 * Все вхождения запроса, без пересечений и без учёта регистра.
 *
 * Возвращаются диапазоны в общем тексте заметки: начало включается, конец —
 * нет. Пустой запрос не ищет ничего.
 */
internal fun matchesOf(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val result = ArrayList<IntRange>()
    var index = text.indexOf(query, ignoreCase = true)
    while (index >= 0) {
        result += index until (index + query.length)
        index = text.indexOf(query, index + query.length, ignoreCase = true)
    }
    return result
}
