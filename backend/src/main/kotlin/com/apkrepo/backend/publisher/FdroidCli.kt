package com.apkrepo.backend.publisher

import com.apkrepo.backend.config.ApkRepoProperties
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Thin subprocess wrapper for the fdroidserver CLI (fdroid init, fdroid
 * update). Captures combined stdout+stderr, enforces a timeout and never
 * throws on a non-zero exit — the caller decides whether the output is
 * fatal and gets the full output for error reporting.
 */
@Component
class FdroidCli(private val properties: ApkRepoProperties) {

    data class CliResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
    )

    fun run(workingDir: Path, vararg args: String): CliResult {
        val command = mutableListOf(properties.publisher.fdroidCommand)
        command.addAll(args)

        val process = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()

        // Drain the output on a dedicated thread so a full pipe can never
        // deadlock the subprocess while we wait for it to finish.
        val outputBuilder = StringBuilder()
        val reader = thread {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(outputBuilder) { outputBuilder.appendLine(line) }
                }
            } catch (_: Exception) {
                // process killed / stream closed — keep whatever was collected
            }
        }

        val timeoutSec = properties.publisher.commandTimeoutSec
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        val exitCode = if (finished) process.exitValue() else -1
        reader.join(TimeUnit.SECONDS.toMillis(5))

        var output: String
        synchronized(outputBuilder) { output = outputBuilder.toString() }
        if (!finished) {
            output += "\n[fdroid did not exit within ${timeoutSec}s and was killed]"
        }
        if (output.length > MAX_OUTPUT_CHARS) {
            output = output.substring(output.length - MAX_OUTPUT_CHARS)
        }
        return CliResult(exitCode, output.trim(), timedOut = !finished)
    }

    private companion object {
        /** Cap stored CLI output (job error messages) at 8 KB. */
        private const val MAX_OUTPUT_CHARS = 8 * 1024
    }
}
