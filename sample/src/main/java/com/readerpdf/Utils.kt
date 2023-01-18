package com.readerpdf

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.*

fun copyAssets(context: Context) {
    val folder = File("${context.cacheDir.absolutePath}/assets")
    if (folder.exists())
        folder.delete()
    folder.mkdirs()
    val assetManager: AssetManager = context.assets
    try {
        val files = assetManager.list("")

        if (files != null) for (filename in files) {
            val inputStream = assetManager.open(filename)
            val outStream = File("${context.cacheDir.absolutePath}/assets", filename).outputStream()
            inputStream.use { input ->
                outStream.use { output ->
                    input.copyTo(output)
                }
            }
        }
    } catch (e: IOException) {
        Log.e("tag", "Failed to get asset file list.", e)
    }
}