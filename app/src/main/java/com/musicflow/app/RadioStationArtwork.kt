package com.musicflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/** Stable, clearly non-official fallback; never pretend a made-up tile is a station logo. */
internal object RadioStationArtworkFallback {
    fun initials(name: String): String {
        val words = name.split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return "♫"
        val first = words.first().first().uppercase()
        val second = words.drop(1).firstOrNull()
        return when {
            second != null && second.equals("плюс", true) -> first + "+"
            second != null -> first + second.first().uppercase()
            words.first().length <= 2 -> words.first().uppercase()
            else -> first
        }
    }
}

/** Logo when available, readable branded-color lettermark for any missing/broken image. */
@Composable
internal fun RadioStationArtwork(
    name: String,
    logo: String?,
    modifier: Modifier = Modifier,
    radius: Dp = 11.dp
) {
    val palette = LocalMusicFlowPalette.current
    val shape = RoundedCornerShape(radius)
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(
                    palette.gradientTop,
                    palette.accent.copy(alpha = 0.45f),
                    palette.gradientBottom
                ))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                RadioStationArtworkFallback.initials(name),
                color = palette.foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
    Box(modifier.clip(shape)) {
        if (logo.isNullOrBlank()) placeholder()
        else SubcomposeAsyncImage(
            model = logo,
            contentDescription = "Логотип станции: " + name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            loading = { placeholder() },
            error = { placeholder() },
            success = { SubcomposeAsyncImageContent() }
        )
    }
}
