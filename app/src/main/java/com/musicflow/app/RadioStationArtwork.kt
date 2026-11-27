package com.musicflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
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
import java.util.Locale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/** Stable, clearly non-official fallback; never pretend a made-up tile is a station logo. */
internal object RadioStationArtworkFallback {
    /** A reproducible visual identity when the station has no usable logo. */
    fun hue(name: String): Float {
        val seed = name.trim().lowercase(Locale.ROOT).hashCode().toLong() and 0x7fff_ffffL
        return (seed % 360).toFloat()
    }

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
        val hue = RadioStationArtworkFallback.hue(name)
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(
                    Color.hsv(hue, 0.64f, 0.56f),
                    Color.hsv((hue + 34f) % 360f, 0.73f, 0.29f)
                ))
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Radio, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(14.dp))
                Text(
                    RadioStationArtworkFallback.initials(name),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
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
