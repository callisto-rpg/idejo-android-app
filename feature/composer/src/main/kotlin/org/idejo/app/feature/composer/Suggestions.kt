// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Suggestion

/** The suggestions under the box (CO-12): what is suggested, which one is picked. */
@Stable
internal class Suggesting {
    var at by mutableStateOf<SuggestAt?>(null)
    var items by mutableStateOf(emptyList<Suggestion>())
    var index by mutableIntStateOf(0)

    /** Escape or back closed the list for this sign. */
    var closed by mutableStateOf<Int?>(null)

    val open: Boolean get() = at != null && at?.start != closed && items.isNotEmpty()

    fun close() {
        closed = at?.start
    }

    /** Hardware keys over the list: arrows move, Enter or Tab take, Escape closes. */
    fun key(
        event: KeyEvent,
        onPick: (Suggestion) -> Unit,
    ): Boolean {
        if (!open) return false
        val handled =
            event.key in setOf(Key.DirectionDown, Key.DirectionUp, Key.Enter, Key.Tab, Key.Escape)
        if (handled && event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionDown -> index = (index + 1) % items.size
                Key.DirectionUp -> index = (index - 1 + items.size) % items.size
                Key.Escape -> close()
                else -> items.getOrNull(index)?.let(onPick)
            }
        }
        return handled
    }
}

/** Asks [suggest] as the member types after a sign, narrowing as typed (CO-12). */
@Composable
internal fun rememberSuggesting(
    value: TextFieldValue,
    suggest: suspend (Char, String) -> List<Suggestion>,
): Suggesting {
    val suggesting = remember { Suggesting() }
    val at = value.suggestAt()
    suggesting.at = at
    val source by rememberUpdatedState(suggest)
    LaunchedEffect(at?.sign, at?.query) {
        if (at == null) {
            suggesting.items = emptyList()
        } else {
            delay(SUGGEST_DELAY_MILLIS)
            suggesting.items = source(at.sign, at.query).take(SUGGESTIONS)
            suggesting.index = 0
        }
    }
    BackHandler(enabled = suggesting.open) { suggesting.close() }
    return suggesting
}

@Composable
internal fun SuggestionList(
    suggesting: Suggesting,
    onPick: (Suggestion) -> Unit,
) {
    if (!suggesting.open) return
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            suggesting.items.forEachIndexed { i, item ->
                val picked = i == suggesting.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(
                            if (picked) {
                                Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                Modifier
                            },
                        ).semantics { selected = picked }
                        .clickable(role = Role.Button) { onPick(item) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, Modifier.weight(1f), style = IdejoTheme.typography.bodyMedium)
                    val at = suggesting.at
                    if (at != null && at.sign != '!' && item.insert != item.label) {
                        Text(
                            "${at.sign}${item.insert}",
                            style = IdejoTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val SUGGEST_DELAY_MILLIS = 150L
private const val SUGGESTIONS = 8
