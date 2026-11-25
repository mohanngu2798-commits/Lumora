package app.lumora.editor.ai

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Loads bundled AI models (from the app or its Play asset pack) as memory-mapped buffers. */
object ModelFiles {
    fun map(context: Context, name: String): ByteBuffer {
        runCatching {
            val fd = context.assets.openFd(name)
            return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                .also { fd.close() }
        }
        // A compressed asset (possible inside a Play asset pack) can't be mapped directly: unpack it once.
        val file = File(context.noBackupFilesDir, name)
        if (!file.exists()) {
            val tmp = File(file.path + ".tmp")
            context.assets.open(name).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.renameTo(file)
        }
        return FileInputStream(file).channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }
    }

    fun exists(context: Context, name: String) = runCatching { context.assets.open(name).close(); true }.getOrDefault(false)
}
