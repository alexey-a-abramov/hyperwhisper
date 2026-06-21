package com.hyperwhisper.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChunkerTest {

    private val sampleRate = 16000
    private val frameMs = 100
    private val samplesPerFrame = sampleRate * frameMs / 1000 // 1600

    /** A 100ms frame of constant-amplitude 16-bit LE PCM. */
    private fun frame(amplitude: Int): ByteArray {
        val b = ByteArray(samplesPerFrame * 2)
        var i = 0
        repeat(samplesPerFrame) {
            b[i] = (amplitude and 0xff).toByte()
            b[i + 1] = ((amplitude shr 8) and 0xff).toByte()
            i += 2
        }
        return b
    }

    private val speech = frame(10_000) // RMS ≈ 0.30 → well above threshold
    private val silence = frame(0)

    private fun feed(c: AudioChunker, f: ByteArray) = c.accept(f, f.size)

    @Test
    fun cutsAfterSpeechThenSilenceGap() {
        val c = AudioChunker(sampleRate)
        var cutWhileSpeaking = false
        repeat(6) { cutWhileSpeaking = cutWhileSpeaking || feed(c, speech) } // 600ms speech
        assertFalse("must not cut while still speaking", cutWhileSpeaking)

        var cuts = 0
        repeat(8) { if (feed(c, silence)) cuts++ } // pause closes the chunk
        assertTrue("a boundary fires once the pause is long enough", cuts >= 1)
    }

    @Test
    fun noCutForShortBlip() {
        val c = AudioChunker(sampleRate)
        var cut = false
        repeat(2) { cut = cut || feed(c, speech) }   // 200ms < minSpeech
        repeat(15) { cut = cut || feed(c, silence) } // long silence
        assertFalse("a brief blip then silence must not cut", cut)
    }

    @Test
    fun cutsOnMaxLengthWithoutPause() {
        val c = AudioChunker(sampleRate)
        var cuts = 0
        repeat(260) { if (feed(c, speech)) cuts++ }  // 26s of unbroken speech
        assertTrue("unbroken speech is still cut at the length cap", cuts >= 1)
    }

    @Test
    fun hasPendingSpeechReflectsState() {
        val c = AudioChunker(sampleRate)
        assertFalse(c.hasPendingSpeech())
        feed(c, speech)
        assertTrue(c.hasPendingSpeech())
        c.reset()
        assertFalse(c.hasPendingSpeech())
    }
}
