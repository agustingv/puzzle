package com.butaca13.puzzles.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.AvatarPiece
import com.butaca13.puzzles.data.AvatarPrizes
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Card

private val NoteAvatar = 40.dp

/** Why a prize is won, said once it has been. */
@Composable
fun prizeReason(piece: AvatarPiece): String = when (piece.id) {
    AvatarPrizes.CROWN -> stringResource(R.string.prize_reason_corona)
    AvatarPrizes.TROPHY -> stringResource(R.string.prize_reason_trofeo)
    AvatarPrizes.CAP -> stringResource(R.string.prize_reason_birrete)
    AvatarPrizes.GLASSES -> stringResource(R.string.prize_reason_gafas)
    AvatarPrizes.TOP_HAT -> stringResource(R.string.prize_reason_chistera, AvatarPrizes.WINS_FOR_TOP_HAT)
    else -> ""
}

/** What still has to be done to win a prize, with the progress made where it can be counted. */
@Composable
fun prizeGoal(piece: AvatarPiece, prizes: AvatarPrizes): String {
    @StringRes val goal: Int
    val args: Array<Any>
    when (piece.id) {
        AvatarPrizes.CROWN -> { goal = R.string.prize_goal_corona; args = emptyArray() }
        AvatarPrizes.TROPHY -> { goal = R.string.prize_goal_trofeo; args = arrayOf(prizes.gamesWon, AvatarPrizes.GAMES.size) }
        AvatarPrizes.CAP -> { goal = R.string.prize_goal_birrete; args = emptyArray() }
        AvatarPrizes.GLASSES -> { goal = R.string.prize_goal_gafas; args = emptyArray() }
        AvatarPrizes.TOP_HAT -> {
            goal = R.string.prize_goal_chistera
            args = arrayOf(minOf(prizes.wins, AvatarPrizes.WINS_FOR_TOP_HAT), AvatarPrizes.WINS_FOR_TOP_HAT)
        }
        else -> return ""
    }
    return stringResource(R.string.prize_goal, piece.emoji, piece.label, stringResource(goal, *args))
}

/**
 * A prize just won, like the secret's clues: the avatar wearing it, why it was won, and a button to
 * keep it on. Without the avatar at hand, there is no button.
 */
@Composable
fun PrizeNote(piece: AvatarPiece, modifier: Modifier = Modifier) {
    val shortcut = LocalAvatarShortcut.current
    val shape = RoundedCornerShape(12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Card, shape)
            .border(1.dp, Accent, shape)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        // Room above and to the right for the accessory, which sticks out of the tile.
        val wearing = shortcut?.avatar?.wears(piece) == true
        if (shortcut != null) {
            AvatarTile(shortcut.avatar.wear(piece), NoteAvatar, Modifier.padding(top = 6.dp, end = 6.dp))
        } else {
            Emoji(piece.emoji, NoteAvatar)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.prize_won, piece.label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = prizeReason(piece), style = MaterialTheme.typography.bodySmall)
        }
        when {
            shortcut == null -> Unit
            wearing -> Text(
                text = stringResource(R.string.prize_wearing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            else -> TextButton(onClick = { shortcut.wear(piece) }) { Text(stringResource(R.string.prize_wear)) }
        }
    }
}
