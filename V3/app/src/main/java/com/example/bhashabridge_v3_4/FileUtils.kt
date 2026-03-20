package com.example.bhashabridge_v3_4

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun copyAssetFolder(context: Context, assetFolder: String): String {

        val dest = File(context.filesDir, assetFolder)

        if (dest.exists() && dest.listFiles()?.isNotEmpty() == true) {
            return dest.absolutePath
        }

        copy(context, assetFolder, dest)

        return dest.absolutePath
    }

    private fun copy(context: Context, assetPath: String, dest: File) {

        val files = context.assets.list(assetPath) ?: return

        if (files.isEmpty()) {

            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }

        } else {

            dest.mkdirs()

            for (file in files) {
                copy(context, "$assetPath/$file", File(dest, file))
            }
        }
    }
}
