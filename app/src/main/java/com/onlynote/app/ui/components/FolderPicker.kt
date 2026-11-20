package com.onlynote.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onlynote.app.R
import com.onlynote.app.data.Folder
import com.onlynote.app.ui.theme.HOME_SCALE

/**
 * Выбор папки для новой заметки: существующая папка или сразу новая.
 * Отдельной «пустой» папки не бывает — новая папка создаётся вместе с заметкой.
 *
 * Заголовка нет: список папок говорит сам за себя, а лишняя строка над ним
 * повторяла вопрос, который человек только что решил нажатием «+».
 *
 * Кегль — как у названий заметок в списке: главный экран крупнее остального
 * на 20 %, и меню создания обязано совпадать с ним, а не жить в своём размере.
 * Значок папки держит ту же строку, что и в списке: по нему видно, что это
 * папки, а плюс внизу — что можно завести новую.
 */
@Composable
fun FolderPickerContent(
    folders: List<Folder>,
    onPick: (Long) -> Unit,
    onNewFolder: () -> Unit,
) {
    val rowStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * HOME_SCALE,
    )
    // Значок растёт вместе с кеглем, иначе он утонул бы рядом с крупной строкой.
    val iconSize = (18 * HOME_SCALE).dp
    val iconGap = (14 * HOME_SCALE).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        folders.forEach { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(folder.id) }
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(Modifier.width(iconGap))
                Text(
                    text = folder.name,
                    style = rowStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Ни разделителя, ни отступа: «новая папка» — такой же пункт списка.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNewFolder() }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(iconGap))
            Text(
                text = stringResource(R.string.new_folder_option),
                style = rowStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
