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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/** Stable, clearly non-official fallback; never pretend a made-up tile is a station logo. */
internal object RadioStationArtworkFallback {
    /** A reproducible visual identity when the station has no usable logo. */
    /** Nine bundled sketch covers; same station always gets the same style. */
    fun artworkIndex(name: String): Int {
        val key = name.trim().lowercase(Locale.ROOT).replace('ё', 'е')
        val hash = key.hashCode().toLong() and 0x7fff_ffffL
        return (hash % 9).toInt()
    }

    private val pictures = intArrayOf(
        R.drawable.musicflow_radio_fallback_0,
        R.drawable.musicflow_radio_fallback_1,
        R.drawable.musicflow_radio_fallback_2,
        R.drawable.musicflow_radio_fallback_3,
        R.drawable.musicflow_radio_fallback_4,
        R.drawable.musicflow_radio_fallback_5,
        R.drawable.musicflow_radio_fallback_6,
        R.drawable.musicflow_radio_fallback_7,
        R.drawable.musicflow_radio_fallback_8
    )

    fun artworkResource(name: String) = pictures[artworkIndex(name)]

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
    val shape = RoundedCornerShape(radius)
    // A real logo always wins. The nine bundled styles are offline placeholders,
    // never passed off as the broadcasters' official graphics.
    val placeholder: @Composable () -> Unit = {
        Image(
            painter = painterResource(RadioStationArtworkFallback.artworkResource(name)),
            contentDescription = "Обложка-заглушка радиостанции",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
