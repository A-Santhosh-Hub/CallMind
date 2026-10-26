package com.example.callmind.features.phone

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callmind.ui.theme.contactAccent

private val KEY_ROWS = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to "")
)

/** Plays the short keypad beep for a pressed key. */
class DialToneHelper {
    private val generator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_DTMF, 70)
    } catch (e: RuntimeException) {
        null
    }

    fun play(key: Char) {
        val tone = when (key) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (key - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        generator?.startTone(tone, 120)
    }

    fun release() {
        generator?.release()
    }
}

@Composable
fun rememberDialTones(enabled: Boolean): (Char) -> Unit {
    val helper = remember { DialToneHelper() }
    DisposableEffect(Unit) { onDispose { helper.release() } }
    return { key -> if (enabled) helper.play(key) }
}

/**
 * The 12-key pad. Long-press 0 types "+". [keyHeight] lets the in-call keypad use the same pad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 64.dp,
    /** Long-press on 1 (voicemail); 0 always types "+" on long-press. */
    onLongKey: ((Char) -> Unit)? = null,
    showVoicemailIcon: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subColor: Color = MaterialTheme.colorScheme.contactAccent.copy(alpha = 0.7f)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KEY_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (key, letters) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { onKey(key) },
                                onLongClick = when {
                                    key == '0' -> ({ onKey('+') })
                                    onLongKey != null && key == '1' -> ({ onLongKey(key) })
                                    else -> null
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = key.toString(),
                                fontSize = if (key == '*') 36.sp else 32.sp,
                                fontWeight = FontWeight.Normal,
                                color = textColor
                            )
                            if (key == '1' && showVoicemailIcon) {
                                Icon(
                                    Icons.Default.Voicemail,
                                    contentDescription = "Voicemail",
                                    tint = subColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else if (letters.isNotEmpty()) {
                                Text(text = letters, fontSize = 10.sp, letterSpacing = 1.sp, color = subColor)
                            } else {
                                Box(modifier = Modifier.heightIn(min = 12.dp).size(0.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
