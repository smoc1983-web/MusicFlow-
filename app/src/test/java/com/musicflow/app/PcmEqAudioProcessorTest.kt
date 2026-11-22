package com.musicflow.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmEqAudioProcessorTest {
    private fun buffer(vararg samples: Int): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                for (value in samples) putShort(value.toShort())
                flip()
            }

    @Test fun processorContinuesOutputWithoutFlushingOnEqToggle() {
        val processor = PcmEqAudioProcessor(AudioEqState())
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(C.ENCODING_PCM_16BIT, processor.configure(format).encoding)
        processor.flush()
        processor.queueInput(buffer(1200, -1200, 2200, -2200))
        val before = processor.output
        assertEquals(8, before.remaining())
        assertEquals(1200, before.getShort().toInt())

        processor.update(AudioEqState(enabled = true,
            gainsDb = listOf(4f, 0f, 0f, 0f, 0f)))
        processor.queueInput(buffer(1200, -1200, 2200, -2200))
        val during = processor.output
        assertEquals(8, during.remaining())
        assertTrue(during.getShort().toInt() != 0)

        processor.update(AudioEqState())
        processor.queueInput(buffer(1200, -1200, 2200, -2200))
        val after = processor.output
        assertEquals(8, after.remaining())
        assertEquals(1200, after.getShort().toInt())
        processor.reset()
    }
}
