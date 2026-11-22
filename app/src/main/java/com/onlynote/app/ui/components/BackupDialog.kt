package com.onlynote.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onlynote.app.R
import com.onlynote.app.data.BackupEntry

private enum class BackupStep { MAIN, IMPORT_LIST }

/** Что произошло последним: успех показываем галочкой у пункта, а не строкой текста. */
enum class BackupFeedback { None, Exported, ExportFailed, Imported, ImportFailed }

/** Ширина окна копии: две подписи встают в строку с запасом. */
private val BACKUP_WIDTH = 320.dp

/** Поле окна по краям карточки. */
private val WINDOW_MARGIN = 24.dp

/** Внутренний отступ строки: он же делает строку удобной для пальца. */
private val ROW_PADDING = 14.dp

/**
 * Нижнее поле карточки меньше верхнего ровно на внутренний отступ строки:
 * у последней подписи до края те же [WINDOW_MARGIN], что и у заголовка сверху.
 * Без этой поправки внизу оставалась пустая полоса — окно выглядело приподнятым.
 */
private val BOTTOM_MARGIN = WINDOW_MARGIN - ROW_PADDING

/**
 * Окно «Резервная копия»: экспорт в «Документы» и импорт из автокопии
 * или из файлов папки «OnlyNote backup».
 *
 * Здесь нет описаний серым текстом и кнопки «Закрыть»: окно закрывается
 * касанием мимо или системной кнопкой «назад», а об успехе говорит галочка.
 */
@Composable
fun BackupDialog(
    autoEntry: BackupEntry?,
    documentEntries: List<BackupEntry>,
    feedback: BackupFeedback,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onPick: (BackupEntry) -> Unit,
) {
    var step by remember { mutableStateOf(BackupStep.MAIN) }

    // Импорт закончился — возвращаемся к главному шагу: галочка (или сообщение
    // об ошибке) живёт там. Раньше окно оставалось на списке копий, и казалось,
    // что нажатие ничего не сделало.
    LaunchedEffect(feedback) {
        if (feedback != BackupFeedback.None) step = BackupStep.MAIN
    }

    DialogCard(
        width = BACKUP_WIDTH,
        onDismiss = onDismiss,
        // Заголовок есть только у главного шага: у списка копий он повторял бы
        // строку, которую человек только что нажал.
        padding = PaddingValues(
            start = WINDOW_MARGIN,
            end = WINDOW_MARGIN,
            top = if (step == BackupStep.MAIN) WINDOW_MARGIN else BOTTOM_MARGIN,
            bottom = BOTTOM_MARGIN,
        ),
    ) {
        if (step == BackupStep.MAIN) {
            Text(
                text = stringResource(R.string.backup_title),
                style = windowTitleStyle(),
            )
            Spacer(Modifier.height(16.dp))
        }

        Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            when (step) {
                BackupStep.MAIN -> {
                    // Экспорт и импорт — списком, а не строчкой: окно низкое,
                    // а два действия в одну строку спорили за ширину.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BackupAction(
                            title = stringResource(R.string.backup_export),
                            done = feedback == BackupFeedback.Exported,
                            onClick = onExport,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BackupAction(
                            title = stringResource(R.string.backup_import),
                            done = feedback == BackupFeedback.Imported,
                            onClick = { step = BackupStep.IMPORT_LIST },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // О неудаче молчать нельзя — единственный текст, который остался.
                    val failure = when (feedback) {
                        BackupFeedback.ExportFailed -> stringResource(R.string.backup_export_failed)
                        BackupFeedback.ImportFailed -> stringResource(R.string.backup_import_failed)
                        else -> null
                    }
                    if (failure != null) {
                        Text(
                            text = failure,
                            style = windowTextStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp, bottom = ROW_PADDING),
                        )
                    }
                }

                BackupStep.IMPORT_LIST -> {
                    val entries = buildList {
                        autoEntry?.let { add(it) }
                        addAll(documentEntries)
                    }
                    if (entries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.backup_empty),
                            style = windowTextStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = ROW_PADDING),
                        )
                    } else {
                        entries.forEach { entry ->
                            BackupEntryRow(entry = entry, onClick = { onPick(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupAction(
    title: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = windowTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        AnimatedVisibility(
            visible = done,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(10.dp))
                Tick(
                    color = MaterialTheme.colorScheme.primary,
                    description = stringResource(R.string.backup_done),
                )
            }
        }
    }
}

@Composable
private fun BackupEntryRow(entry: BackupEntry, onClick: () -> Unit) {
    Text(
        text = entry.title,
        style = windowTextStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ROW_PADDING),
    )
}
