package org.atmofox.tv.utils

import android.content.Context
import androidx.annotation.RawRes
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object HtmlLoader {
    /**
     * Load a given (html or css) resource file into a String. The input can contain tokens that will
     * be replaced with localised strings.
     *
     * @param substitutionTable A table of substitions, e.g. %shortMessage% -> "Error loading page..."
     *                          Can be null, in which case no substitutions will be made.
     * @return The file content, with all substitutions having being made.
     */
    @JvmStatic
    fun loadResourceFile(
        context: Context,
        @RawRes resourceID: Int,
        substitutionTable: Map<String, String>?
    ): String {
        try {
            BufferedReader(
                InputStreamReader(
                    context.resources.openRawResource(resourceID),
                    StandardCharsets.UTF_8
                )
            ).use { fileReader ->
                val outputBuffer = StringBuilder()

                var line: String?
                while (fileReader.readLine().also { line = it } != null) {
                    var processedLine = line!!
                    substitutionTable?.let { table ->
                        for ((key, value) in table) {
                            processedLine = processedLine.replace(key, value)
                        }
                    }
                    outputBuffer.append(processedLine)
                }

                return outputBuffer.toString()
            }
        } catch (e: IOException) {
            throw IllegalStateException("Unable to load error page data", e)
        }
    }
}
