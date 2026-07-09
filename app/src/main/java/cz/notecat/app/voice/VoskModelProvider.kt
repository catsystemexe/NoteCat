package cz.notecat.app.voice

import android.content.Context
import java.io.File
import org.vosk.Model

/**
 * Rozbalí český Vosk model z assets do interního úložiště (jen jednou,
 * verzované přes soubor `uuid`) a načte jej. Model slouží výhradně
 * k lokálnímu rozpoznání povelu „konec" – žádná data neopouštějí zařízení.
 */
object VoskModelProvider {

    private const val ASSET_DIR = "model-cs"

    @Volatile
    private var cached: Model? = null

    /** @return null pokud model v assets chybí (hlasový povel se pak vypne). */
    @Synchronized
    fun getModel(context: Context): Model? {
        cached?.let { return it }
        return runCatching {
            val dir = unpackIfNeeded(context)
            Model(dir.absolutePath).also { cached = it }
        }.getOrNull()
    }

    private fun unpackIfNeeded(context: Context): File {
        val target = File(context.filesDir, ASSET_DIR)
        val assetUuid = runCatching {
            context.assets.open("$ASSET_DIR/uuid").bufferedReader().readText().trim()
        }.getOrDefault("")
        val localUuid = File(target, "uuid").takeIf { it.exists() }?.readText()?.trim()
        if (target.exists() && assetUuid.isNotEmpty() && assetUuid == localUuid) return target

        target.deleteRecursively()
        copyAssetDir(context, ASSET_DIR, target)
        return target
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // Soubor
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        } else {
            target.mkdirs()
            for (child in children) {
                copyAssetDir(context, "$assetPath/$child", File(target, child))
            }
        }
    }
}
