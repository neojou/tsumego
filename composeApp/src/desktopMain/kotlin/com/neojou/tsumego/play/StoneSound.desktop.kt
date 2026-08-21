package com.neojou.tsumego.play

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem

actual fun playStoneSound(kind: StoneSoundKind) {
    val name = if (kind == StoneSoundKind.Place) "files/place.wav" else "files/capture.wav"
    val bytes = StoneSound::class.java.classLoader.getResourceAsStream(name)?.readBytes() ?: return
    runCatching {
        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
        val clip = AudioSystem.getClip()
        clip.open(stream)
        clip.start()
    }
}

private class StoneSound
