@file:OptIn(ExperimentalWasmJsInterop::class)

package com.neojou.tsumego.solverLdrz

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop

actual suspend fun ldrzPickProblemJson(): LdrzPickedFile? =
    suspendCancellableCoroutine { cont ->
        pickJson { name, data ->
            if (!cont.isActive) return@pickJson
            if (name == null || data == null) cont.resume(null)
            else cont.resume(LdrzPickedFile(name = name, text = data))
        }
    }

actual suspend fun ldrzWriteResultFiles(stem: String, json: String, sgf: String): String? =
    "寫 result/ 僅 Desktop"

actual suspend fun ldrzReadResultFiles(stem: String): Pair<String, String>? = null

@JsFun(
    """
    (callback) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = '.json,application/json';
      input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) { callback(null, null); return; }
        const reader = new FileReader();
        reader.onload = () => callback(file.name, reader.result);
        reader.onerror = () => callback(null, null);
        reader.readAsText(file);
      };
      input.click();
    }
    """,
)
private external fun pickJson(callback: (String?, String?) -> Unit)
