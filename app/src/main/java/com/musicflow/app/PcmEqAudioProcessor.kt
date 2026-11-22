package com.musicflow.app

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Always stays in the PCM chain; settings are updated without sink/session recreation. */
@OptIn(UnstableApi::class)
internal class PcmEqAudioProcessor(initial: AudioEqState) : BaseAudioProcessor() {
    @Volatile private var state: AudioEqState = initial
    private var engine = PcmEqEngine()

    fun update(settings: AudioEqState) {
        state = settings
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat):
        AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT)
            inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush() {
        engine = PcmEqEngine()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val output = replaceOutputBuffer(size)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        engine.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, state)
        var channel = 0
        while (inputBuffer.remaining() >= 2) {
            output.putShort(engine.processSample(inputBuffer.short, channel))
            channel = (channel + 1) % inputAudioFormat.channelCount
        }
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }
}
