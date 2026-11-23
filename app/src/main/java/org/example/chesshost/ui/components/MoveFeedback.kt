package org.example.chesshost.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Kurzes Feedback bei jedem neuen Zug: Vibration und/oder ein Ton. [moveCount]
 * ist die Zahl der gespielten Züge; beim ersten Aufbau bleibt es still, damit
 * das Öffnen des Screens nicht piept.
 */
@Composable
fun MoveFeedback(
    moveCount: Int,
    check: Boolean,
    gameOver: Boolean,
    sound: Boolean,
    haptics: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val tone = remember(sound) {
        if (!sound) {
            null
        } else {
            runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()
        }
    }
    DisposableEffect(tone) {
        onDispose { runCatching { tone?.release() } }
    }

    var seen by remember { mutableIntStateOf(moveCount) }
    LaunchedEffect(moveCount, check, gameOver) {
        if (moveCount == seen) return@LaunchedEffect
        seen = moveCount
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tone?.let { generator ->
            val type = when {
                gameOver -> ToneGenerator.TONE_PROP_ACK
                check -> ToneGenerator.TONE_PROP_BEEP2
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            runCatching { generator.startTone(type, 70) }
        }
    }
}
