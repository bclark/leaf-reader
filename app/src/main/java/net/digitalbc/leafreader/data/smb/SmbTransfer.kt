package net.digitalbc.leafreader.data.smb

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Blocking streaming boundary, independently testable without a network or Android context. */
internal fun copySmbStream(
    input: InputStream,
    output: OutputStream,
    total: Long,
    onProgress: (SmbTransferProgress) -> Unit,
    isCancelled: () -> Boolean,
) {
    var copied = 0L
    var lastReportTime = System.nanoTime()
    var lastReportBytes = 0L
    var speed = 0L
    val buffer = ByteArray(1024 * 1024)
    checkSmbCancellation(isCancelled)
    onProgress(SmbTransferProgress(0L, total, 0L))
    while (true) {
        checkSmbCancellation(isCancelled)
        val count = input.read(buffer)
        checkSmbCancellation(isCancelled)
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
        copied += count
        val now = System.nanoTime()
        val elapsed = (now - lastReportTime) / 1_000_000L
        if (elapsed >= 150L) {
            speed = (copied - lastReportBytes) * 1000L / elapsed
            lastReportBytes = copied
            lastReportTime = now
            onProgress(SmbTransferProgress(copied, total, speed))
        }
    }
    if (total >= 0L && copied != total) throw EOFException("SMB download length changed")
    checkSmbCancellation(isCancelled)
    output.flush()
    checkSmbCancellation(isCancelled)
    onProgress(SmbTransferProgress(copied, total, speed))
}
