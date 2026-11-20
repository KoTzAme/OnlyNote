package com.onlynote.app.data

import java.text.Collator
import java.util.Locale

/**
 * Фиксированная палитра: цвета радуги плюс нейтральные варианты.
 *
 * [rank] задаёт место в радуге — по нему сортируются заметки в папке:
 * красный → оранжевый → жёлтый → зелёный → синий → фиолетовый,
 * затем песочный и «без цвета» (у них нет места в радуге).
 */
enum class NoteColor(val id: String, val rank: Int) {
    DEFAULT("default", 90),
    ROSE("rose", 10),
    PEACH("peach", 20),
    LEMON("lemon", 30),
    SAGE("sage", 40),
    // id «mint» остался от версий 1.x: по нему цвет записан в существующих базах
    // и в старых резервных копиях. Сам цвет давно синий, менять id нельзя.
    BLUE("mint", 50),
    LAVENDER("lavender", 70),
    // id «sand» остался от песочного: по нему цвет записан в базах и старых копиях.
    GRAY("sand", 80),
    ;

    companion object {
        fun fromId(id: String?): NoteColor = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Порядок для палитры: сначала радуга, нейтральные — в конце. */
        val rainbowOrder: List<NoteColor> get() = entries.sortedBy { it.rank }
    }
}

/**
 * Заголовок заметки — её первая непустая строка.
 *
 * Считается и в списке, и в шапке заметки, поэтому живёт одной функцией:
 * две копии одного правила однажды разойдутся.
 */
fun String.noteHeading(): String =
    lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

data class Folder(
    val id: Long,
    val name: String,
    val createdAt: Long,
)

/**
 * Заметка — это просто текст. Заголовок не хранится отдельно:
 * им считается первая непустая строка.
 *
 * [heading] считается один раз при создании объекта: заметки бывают по сто тысяч
 * символов, а заголовок спрашивают на каждой сортировке и на каждой строке списка.
 */
data class Note(
    val id: Long,
    val folderId: Long,
    val body: String,
    val color: NoteColor,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Заголовок заметки в списке, в шапке и в сортировке — её первая непустая строка. */
    val heading: String = body.noteHeading()
}

data class AppSnapshot(
    val folders: List<Folder> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    fun folder(folderId: Long): Folder? = folders.firstOrNull { it.id == folderId }
}

/**
 * Порядок заметок: сначала по цвету в порядке радуги, внутри цвета — по алфавиту
 * (русская сортировка). Заметки без первой строки уезжают в конец своей группы.
 */
object NoteOrdering {

    private val collator: Collator = Collator.getInstance(Locale("ru", "RU"))
    private val byHeading = Comparator<String> { a, b -> collator.compare(a, b) }

    val comparator: Comparator<Note> = compareBy<Note> { it.heading.isBlank() }
        .thenBy { it.color.rank }
        .thenBy(byHeading) { it.heading }
        .thenBy { it.id }
}
