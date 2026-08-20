@file:OptIn(ExperimentalWasmJsInterop::class)

package com.neojou.tsumego.io

import com.neojou.tsumego.diagram.DiagramReader
import com.neojou.tsumego.diagram.EmptyDiagramReader
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop

actual suspend fun openProblemText(): String? {
    val picked = jsPickFile(".json,.tsumego.json,application/json", asText = true) ?: return null
    return picked.second
}

actual suspend fun saveProblemText(suggestedName: String, content: String): Boolean {
    jsDownload(suggestedName, content)
    return true
}

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun openDiagramImage(): PickedImage? {
    val picked = jsPickFile("image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp", asText = false) ?: return null
    val bytes = Base64.decode(picked.second)
    return PickedImage(bytes, picked.first)
}

actual fun platformDiagramReader(): DiagramReader = EmptyDiagramReader

private suspend fun jsPickFile(accept: String, asText: Boolean): Pair<String, String>? =
    suspendCancellableCoroutine { cont ->
        pickFile(accept, asText) { name, data ->
            if (!cont.isActive) return@pickFile
            if (name == null || data == null) cont.resume(null) else cont.resume(name to data)
        }
    }

@JsFun(
    """
    (filename, content) => {
      const blob = new Blob([content], {type: 'application/json'});
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }
    """,
)
private external fun jsDownload(filename: String, content: String)

@JsFun(
    """
    (accept, asText, callback) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = accept;
      input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) { callback(null, null); return; }
        const reader = new FileReader();
        reader.onload = () => {
          if (asText) {
            callback(file.name, reader.result);
          } else {
            const arr = new Uint8Array(reader.result);
            let binary = '';
            for (let i = 0; i < arr.length; i++) binary += String.fromCharCode(arr[i]);
            callback(file.name, btoa(binary));
          }
        };
        reader.onerror = () => callback(null, null);
        if (asText) reader.readAsText(file); else reader.readAsArrayBuffer(file);
      };
      input.click();
    }
    """,
)
private external fun pickFile(accept: String, asText: Boolean, callback: (String?, String?) -> Unit)
