@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.neojou.tsumego.play

actual fun playStoneSound(kind: StoneSoundKind) {
    if (kind == StoneSoundKind.Place) beepPlace() else beepCapture()
}

private fun beepPlace() {
    js(
        """
        {
          const C = window.AudioContext || window.webkitAudioContext;
          if (!C) return;
          const c = new C();
          const o = c.createOscillator();
          const g = c.createGain();
          o.connect(g); g.connect(c.destination);
          o.frequency.value = 920;
          g.gain.setValueAtTime(0.12, c.currentTime);
          g.gain.exponentialRampToValueAtTime(0.001, c.currentTime + 0.08);
          o.start(); o.stop(c.currentTime + 0.09);
        }
        """,
    )
}

private fun beepCapture() {
    js(
        """
        {
          const C = window.AudioContext || window.webkitAudioContext;
          if (!C) return;
          const c = new C();
          const o = c.createOscillator();
          const g = c.createGain();
          o.connect(g); g.connect(c.destination);
          o.frequency.value = 240;
          g.gain.setValueAtTime(0.14, c.currentTime);
          g.gain.exponentialRampToValueAtTime(0.001, c.currentTime + 0.12);
          o.start(); o.stop(c.currentTime + 0.13);
        }
        """,
    )
}
