@file:OptIn(ExperimentalWasmJsInterop::class)

package com.apkrepo.web.api

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.fetch.RequestInit
import org.w3c.files.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * Kotlin/Wasm JS interop helpers.
 *
 * Kotlin/Wasm has no `dynamic` type and kotlinx-browser 0.5 exposes fetch
 * results as `JsString`/`ArrayBuffer`, so every JS touchpoint needed by
 * [ApiClient] lives here. Each js(...) body must be a single expression of a
 * top-level function; parameters are referenced by name.
 */

/**
 * Attaches fulfillment/rejection handlers to a promise. Written as a raw
 * js() helper because Promise.then's type parameter S must itself be a
 * JsAny subtype, which rules out Unit-returning handlers.
 */
private fun <T : JsAny?> promiseThenBoth(
    promise: Promise<T>,
    onFulfilled: (T) -> Unit,
    onRejected: (JsAny) -> Unit,
): Unit = js("promise.then(v => { onFulfilled(v); return v; }, e => { onRejected(e); throw e; })")

/** Bridges a JavaScript promise into a coroutine continuation. */
suspend fun <T : JsAny?> Promise<T>.await(): T =
    suspendCoroutine { cont ->
        promiseThenBoth(
            this,
            onFulfilled = { value: T -> cont.resume(value) },
            onRejected = { error: JsAny -> cont.resumeWithException(IllegalStateException(jsErrorMessage(error))) },
        )
    }

/** Best-effort message extraction from a rejected promise value. */
fun jsErrorMessage(e: JsAny): String = js("String((e && e.message) || e)")

/** JS string to Kotlin string. */
fun jsToString(s: JsString): String = js("s")

/** JS number (e.g. Blob.size) to Kotlin Double. */
fun jsNumberToDouble(n: JsNumber): Double = js("n")

/** Empty object literal, used as fetch RequestInit. */
fun newRequestInit(): RequestInit = js("({})")

/** Plain object used as a fetch headers map. */
fun newHeadersObject(): JsAny = js("({})")

/** Sets one entry on a headers object created by [newHeadersObject]. */
fun setHeader(obj: JsAny, name: String, value: String): Unit = js("obj[name] = value")

/** window.encodeURIComponent. */
fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")

// ---- multipart upload (kotlinx-browser has no FormData declaration) ----

/** new FormData() */
fun newFormData(): JsAny = js("new FormData()")

/** form.append(name, file, file.name) */
fun formDataAppendFile(form: JsAny, name: String, file: File): Unit = js("form.append(name, file, file.name)")

/** File.size as Double (to avoid the Long/BigInt interop mapping). */
fun fileSizeBytes(file: File): Double = js("file.size")

// ---- binary responses ----

private fun bufferByteLength(buffer: ArrayBuffer): Int = js("buffer.byteLength")

private fun int8Get(view: Int8Array, index: Int): Byte = js("view[index]")

/** Copies a JS ArrayBuffer into a Kotlin ByteArray. */
fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
    val view = Int8Array(buffer, 0, bufferByteLength(buffer))
    return ByteArray(view.length) { int8Get(view, it) }
}
