package com.musicflow.app

/**
 * Slow-network streaming profile for live HLS IPTV.
 *
 * The uploaded look.m3u8 playlist has 3244 HLS URLs on a single HTTP host.
 * HLS playlists describe sources, not measured segment bitrate or latency;
 * these parameters are a conservative starting configuration, not a claim
 * that every channel can be played over bandwidth below its encoded bitrate.
 */
internal object TvPlaybackTuning {
    // Keep startup responsive while allowing ExoPlayer to preload ~25 sec
    // and grow to 75 sec where the source live window permits.
    const val MIN_BUFFER_MS = 25_000
    const val MAX_BUFFER_MS = 75_000
    const val START_BUFFER_MS = 6_000
    const val REBUFFER_MS = 12_000

    // Bound RAM use with high-bitrate HD/FHD/4K channels.
    const val TARGET_BUFFER_BYTES = 64 * 1024 * 1024

    // A failed segment read should time out before the live retry watchdog.
    const val CONNECT_TIMEOUT_MS = 20_000
    const val READ_TIMEOUT_MS = 45_000
}
