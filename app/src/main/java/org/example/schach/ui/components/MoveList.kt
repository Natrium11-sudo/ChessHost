package org.example.schach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import org.example.schach.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Zugliste als "1. e4 e5"-Paare, scrollt automatisch zum letzten Zug. */
@Composable
fun MoveList(
    pairs: List<Pair<Int, Pair<String?, String?>>>,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 180.dp,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(pairs.size) {
        if (pairs.isNotEmpty()) listState.animateScrollToItem(pairs.size - 1)
    }

    if (pairs.isEmpty()) {
        Text(
            text = "Noch keine Züge",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(pairs) { index, (number, moves) ->
            val isLast = index == pairs.lastIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLast) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$number.",
                    modifier = Modifier.width(34.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = moves.first ?: "",
                    modifier = Modifier.width(70.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isLast && moves.second == null) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = moves.second ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
