package com.example.callmind.features.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callmind.ui.theme.contactAccent
import com.example.callmind.ui.theme.tertiaryText

/**
 * A-Z strip on the right edge. Tap or drag to jump; while a finger is down a green bubble
 * shows the current letter, like the reference.
 */
@Composable
fun AlphabetIndex(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    if (letters.isEmpty()) return
    var heightPx by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<Char?>(null) }

    fun letterAt(y: Float): Char {
        val slot = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[slot]
    }

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        active?.let { letter ->
            Box(
                modifier = Modifier
                    .padding(end = 44.dp)
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.contactAccent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(letter.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(
            modifier = Modifier
                .width(28.dp)
                .height((letters.size * 20).dp)
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    detectTapGestures(
                        onPress = { offset ->
                            val letter = letterAt(offset.y)
                            active = letter
                            onLetterSelected(letter)
                            tryAwaitRelease()
                            active = null
                        }
                    )
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val letter = letterAt(offset.y)
                            active = letter
                            onLetterSelected(letter)
                        },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onVerticalDrag = { change, _ ->
                            val letter = letterAt(change.position.y)
                            if (letter != active) {
                                active = letter
                                onLetterSelected(letter)
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = letter.toString(),
                        fontSize = 11.sp,
                        color = if (letter == active) MaterialTheme.colorScheme.contactAccent
                        else MaterialTheme.colorScheme.tertiaryText
                    )
                }
            }
        }
    }
}
