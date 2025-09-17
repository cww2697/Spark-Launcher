package net.canyonwolf.sparklauncher.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView

/**
 * Loads native system icons for files (e.g., Windows .exe) and caches them.
 */
object SystemIconLoader {
    private val cache = ConcurrentHashMap<String, ImageBitmap?>()
    private val fsv: FileSystemView? = try {
        if (GraphicsEnvironment.isHeadless()) null else FileSystemView.getFileSystemView()
    } catch (_: Throwable) {
        null
    }

    fun getIcon(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        return cache.computeIfAbsent(path) {
            try {
                val file = File(path)
                if (!file.exists()) return@computeIfAbsent null
                val icon: Icon = fsv?.getSystemIcon(file) ?: return@computeIfAbsent null
                val w = icon.iconWidth.coerceAtLeast(1)
                val h = icon.iconHeight.coerceAtLeast(1)
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics()
                try {
                    icon.paintIcon(null, g, 0, 0)
                } finally {
                    g.dispose()
                }
                img.toComposeImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }
}
