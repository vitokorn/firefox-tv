/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.preference.ListPreference
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import org.atmofox.tv.R
import org.atmofox.tv.components.locale.LocaleManager
import org.atmofox.tv.components.locale.Locales
import java.nio.ByteBuffer
import java.text.Collator
import java.util.Arrays
import java.util.Locale

class LocaleListPreference @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null
) : ListPreference(context, attributes) {

    companion object {
        private const val LOG_TAG = "GeckoLocaleList"

        private val languageCodeToNameMap = mutableMapOf<String, String>().apply {
            // Only ICU 57 actually contains the Asturian name for Asturian, even Android 7.1 is still
            // shipping with ICU 56, so we need to override the Asturian name (otherwise displayName will
            // be the current locales version of Asturian, see:
            // https://github.com/mozilla-mobile/focus-android/issues/634#issuecomment-303886118
            put("ast", "Asturianu")
            // On an Android 8.0 device those languages are not known and we need to add the names
            // manually. Loading the resources at runtime works without problems though.
            put("cak", "Kaqchikel")
            put("ia", "Interlingua")
            put("meh", "Tu´un savi ñuu Yasi'í Yuku Iti")
            put("mix", "Tu'un savi")
            put("trs", "Triqui")
            put("zam", "DíɁztè")
        }
    }

    /**
     * With thanks to <http://stackoverflow.com/a/22679283/22003> for the
     * initial solution.
     *
     * This class encapsulates an approach to checking whether a script
     * is usable on a device. We attempt to draw a character from the
     * script (e.g., ব). If the fonts on the device don't have the correct
     * glyph, Android typically renders whitespace (rather than .notdef).
     *
     * Pass in part of the name of the locale in its local representation,
     * and a whitespace character; this class performs the graphical comparison.
     *
     * See Bug 1023451 Comment 24 for extensive explanation.
     */
    private class CharacterValidator(missing: String) {
        companion object {
            private const val BITMAP_WIDTH = 32
            private const val BITMAP_HEIGHT = 48
        }

        private val paint = Paint()
        private val missingCharacter: ByteArray

        // Note: this constructor fails when running in Robolectric: robolectric only supports bitmaps
        // with 4 bytes per pixel ( https://github.com/robolectric/robolectric/blob/master/robolectric-shadows/shadows-core/src/main/java/org/robolectric/shadows/ShadowBitmap.java#L540 ).
        // We need to either make this code test-aware, or fix robolectric.
        init {
            this.missingCharacter = getPixels(drawBitmap(missing))
        }

        private fun drawBitmap(text: String): Bitmap {
            val b = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ALPHA_8)
            val c = Canvas(b)
            c.drawText(text, 0f, (BITMAP_HEIGHT / 2).toFloat(), paint)
            return b
        }

        private fun getPixels(b: Bitmap): ByteArray {
            val byteCount = b.allocationByteCount

            val buffer = ByteBuffer.allocate(byteCount)
            try {
                b.copyPixelsToBuffer(buffer)
            } catch (e: RuntimeException) {
                // Android throws this if there's not enough space in the buffer.
                // This should never occur, but if it does, we don't
                // really care -- we probably don't need the entire image.
                // This is awful. I apologize.
                if ("Buffer not large enough for pixels" == e.message) {
                    return buffer.array()
                }
                throw e
            }

            return buffer.array()
        }

        fun characterIsMissingInFont(ch: String): Boolean {
            val rendered = getPixels(drawBitmap(ch))
            return Arrays.equals(rendered, missingCharacter)
        }
    }

    private var entriesLocale: Locale? = null
    private var characterValidator: CharacterValidator? = null

    override fun onAttachedToActivity() {
        super.onAttachedToActivity()

        // Thus far, missing glyphs are replaced by whitespace, not a box
        // or other Unicode codepoint.
        characterValidator = CharacterValidator(" ")

        buildList()
    }

    private class LocaleDescriptor(locale: Locale, val tag: String) : Comparable<LocaleDescriptor> {
        companion object {
            // We use Locale.US here to ensure a stable ordering of entries.
            private val COLLATOR: Collator = Collator.getInstance(Locale.US)
        }

        private val nativeName: String

        init {
            val displayName = if (languageCodeToNameMap.containsKey(locale.language)) {
                languageCodeToNameMap[locale.language]!!
            } else {
                locale.getDisplayName(locale)
            }

            if (TextUtils.isEmpty(displayName)) {
                // There's nothing sane we can do.
                Log.w(LOG_TAG, "Display name is empty. Using ${locale}")
                nativeName = locale.toString()
            } else {
                // For now, uppercase the first character of LTR locale names.
                // This is pretty much what Android does. This is a reasonable hack
                // for Bug 1014602, but it won't generalize to all locales.
                val directionality = Character.getDirectionality(displayName[0])
                nativeName = if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                    displayName.substring(0, 1).toUpperCase(locale) + displayName.substring(1)
                } else {
                    displayName
                }
            }
        }

        fun getDisplayName(): String {
            return nativeName
        }

        override fun toString(): String {
            return nativeName
        }

        override fun equals(other: Any?): Boolean {
            return other is LocaleDescriptor && compareTo(other) == 0
        }

        override fun hashCode(): Int {
            return tag.hashCode()
        }

        override fun compareTo(other: LocaleDescriptor): Int {
            // We sort by name, so we use Collator.
            return COLLATOR.compare(nativeName, other.nativeName)
        }

        /**
         * See Bug 1023451 Comment 10 for the research that led to
         * this method.
         *
         * @return true if this locale can be used for displaying UI
         *         on this device without known issues.
         */
        fun isUsable(validator: CharacterValidator): Boolean {
            // Oh, for Java 7 switch statements.
            if (tag == "bn-IN") {
                // Bengali sometimes has an English label if the Bengali script
                // is missing. This prevents us from simply checking character
                // rendering for bn-IN; we'll get a false positive for "B", not "ব".
                //
                // This doesn't seem to affect other Bengali-script locales
                // (below), which always have a label in native script.
                if (!nativeName.startsWith("বাংলা")) {
                    // We're on an Android version that doesn't even have
                    // characters to say বাংলা. Definite failure.
                    return false
                }
            }

            // These locales use a script that is often unavailable
            // on common Android devices. Make sure we can show them.
            // See documentation for CharacterValidator.
            // Note that bn-IN is checked here even if it passed above.
            if (tag == "or" ||
                tag == "my" ||
                tag == "pa-IN" ||
                tag == "gu-IN" ||
                tag == "bn-IN"
            ) {
                if (validator.characterIsMissingInFont(nativeName.substring(0, 1))) {
                    return false
                }
            }

            return true
        }
    }

    /**
     * Not every locale we ship can be used on every device, due to
     * font or rendering constraints.
     *
     * This method filters down the list before generating the descriptor array.
     */
    private fun getUsableLocales(): Array<LocaleDescriptor> {
        val shippingLocales = LocaleManager.getPackagedLocaleTags(context)

        val initialCount = shippingLocales.size
        val locales = HashSet<LocaleDescriptor>(initialCount)
        for (tag in shippingLocales) {
            val descriptor = LocaleDescriptor(Locales.parseLocaleCode(tag), tag)

            if (!descriptor.isUsable(characterValidator!!)) {
                Log.w(LOG_TAG, "Skipping locale $tag on this device.")
                continue
            }

            locales.add(descriptor)
        }

        val usableCount = locales.size
        val descriptors = locales.toTypedArray()
        Arrays.sort(descriptors, 0, usableCount)
        return descriptors
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        // The superclass will take care of persistence.
        super.onDialogClosed(positiveResult)

        // Use this hook to try to fix up the environment ASAP.
        // Do this so that the redisplayed fragment is inflated
        // with the right locale.
        val selectedLocale = selectedLocale
        val context = context
        LocaleManager.getInstance().updateConfiguration(context, selectedLocale)
    }

    private val selectedLocale: Locale
        get() {
            val tag = value
            return if (tag == null || tag == "") {
                Locale.getDefault()
            } else {
                Locales.parseLocaleCode(tag)
            }
        }

    override fun getSummary(): CharSequence {
        val value = value

        return if (TextUtils.isEmpty(value)) {
            context.getString(R.string.preference_language_systemdefault)
        } else {
            // We can't trust super.getSummary() across locale changes,
            // apparently, so let's do the same work.
            LocaleDescriptor(Locales.parseLocaleCode(value), value).getDisplayName()
        }
    }

    private fun buildList() {
        val currentLocale = Locale.getDefault()
        Log.d(LOG_TAG, "Building locales list. Current locale: $currentLocale")

        if (currentLocale == entriesLocale && entries != null) {
            Log.v(LOG_TAG, "No need to build list.")
            return
        }

        val descriptors = getUsableLocales()
        val count = descriptors.size

        entriesLocale = currentLocale

        // We leave room for "System default".
        val entries = arrayOfNulls<String>(count + 1)
        val values = arrayOfNulls<String>(count + 1)

        entries[0] = context.getString(R.string.preference_language_systemdefault)
        values[0] = ""

        for (i in 0 until count) {
            val displayName = descriptors[i].getDisplayName()
            val tag = descriptors[i].tag
            Log.v(LOG_TAG, "$displayName => $tag")
            entries[i + 1] = displayName
            values[i + 1] = tag
        }

        setEntries(entries)
        setEntryValues(values)
    }
}
