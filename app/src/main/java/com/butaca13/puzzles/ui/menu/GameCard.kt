package com.butaca13.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.SavedGame
import com.butaca13.puzzles.ui.common.AvatarButton
import com.butaca13.puzzles.ui.common.AppTitle
import com.butaca13.puzzles.ui.common.CardTitle
import com.butaca13.puzzles.ui.common.SettingsButton
import com.butaca13.puzzles.ui.common.widestTitle
import com.butaca13.puzzles.ui.theme.Card as CardColor
import com.butaca13.puzzles.ui.theme.PuzzlesTheme

/** A paid game not bought yet, with its price when Google Play has said it. */
internal class LockBadge(val price: String?)

/** A game as a card: its picture, its title in tiles, and whether it is locked or in progress. */
@Composable
internal fun GameCard(info: GameInfo, widestTitle: Float, inProgress: Boolean, lock: LockBadge?, onClick: () -> Unit) {
    val title = stringResource(info.title)
    val status = stringResource(R.string.game_in_progress)
    val lockLabel = lock?.price?.let { stringResource(R.string.shop_locked_price, it) } ?: stringResource(R.string.shop_locked)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = when {
                    lock != null -> "$title, $lockLabel"
                    inProgress -> "$title, $status"
                    else -> title
                }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(Brush.linearGradient(info.gradient)),
        ) {
            Text(
                text = info.icon,
                fontSize = 42.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (lock != null) {
                Text(
                    text = listOfNotNull("🔒", lock.price).joinToString(" "),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (inProgress) {
                Text(
                    text = status,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        CardTitle(title, widestTitle, Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
