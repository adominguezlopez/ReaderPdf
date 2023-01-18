package com.ReaderPdf

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.*

fun copyAssets(context: Context) {
    val folder = File("${context.cacheDir.absolutePath}/assets")
    if (folder.exists())
        folder.delete()
    folder.mkdir()
    val assetManager: AssetManager = context.assets
    var files: Array<String>? = null
    try {
        files = assetManager.list("")

        if (files != null) for (filename in files) {
            var `in`: InputStream? = null
            var out: OutputStream? = null
            try {
                `in` = assetManager.open(filename)
                val outFile = File("${context.cacheDir.absolutePath}/assets", filename)
                out = FileOutputStream(outFile)
                copyFile(`in`, out)
                Log.e("tag", "file copied: ${filename}")
            } catch (e: IOException) {
                Log.e("tag", "Failed to copy asset file: $filename", e)
            } finally {
                if (`in` != null) {
                    try {
                        `in`.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                if (out != null) {
                    try {
                        out.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

    } catch (e: IOException) {
        Log.e("tag", "Failed to get asset file list.", e)
    }

}

@Throws(IOException::class)
private fun copyFile(`in`: InputStream?, out: OutputStream) {
    val buffer = ByteArray(1024)
    var read: Int? = null
    while (`in`?.read(buffer).also({ read = it!! }) != -1) {
        read?.let { out.write(buffer, 0, it) }
    }
}