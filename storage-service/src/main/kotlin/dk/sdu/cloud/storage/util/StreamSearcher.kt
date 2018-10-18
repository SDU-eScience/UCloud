package dk.sdu.cloud.storage.util

import java.io.IOException
import java.util.Arrays
import kotlin.math.min

/**
 * An efficient stream searching class based on the Knuth-Morris-Pratt algorithm.
 * For more on the algorithm works see: http://www.inf.fh-flensburg.de/lang/algorithmen/pattern/kmpen.htm.
 *
 * Code is based on: https://github.com/twitter/elephant-bird/blob/master/core
 *                  /src/main/java/com/twitter/elephantbird/util/StreamSearcher.java
 *
 * Code has been ported to Kotlin and tweaked to fit our use-case.
 */
class StreamSearcher internal constructor(pattern: ByteArray) {
    private val pattern: ByteArray = Arrays.copyOf(pattern, pattern.size)
    private var borders: IntArray

    init {
        borders = IntArray(this.pattern.size + 1)
        preProcess()
    }

    /**
     * Searches for the next occurrence of the pattern in the stream, starting from the current stream position. Note
     * that the position of the stream is changed. If a match is found, the stream points to the end of the match --
     * i.e. the byte AFTER the pattern. Else, the stream is entirely consumed. The latter is because InputStream
     * semantics make it difficult to have another reasonable default, i.e. leave the stream unchanged.
     *
     * @return bytes consumed if found, -1 otherwise.
     */
    @Throws(IOException::class)
    internal fun search(array: ByteArray, offset: Int, length: Int): Int {
        var j = 0

        for (index in offset until (min(array.size, offset + length))) {
            val b = array[index]

            while (j >= 0 && b != pattern[j]) {
                j = borders[j]
            }
            // Move to the next character in the pattern.
            ++j

            // If we've matched up to the full pattern length, we found it.  Return,
            // which will automatically save our position in the InputStream at the point immediately
            // following the pattern match.
            if (j == pattern.size) {
                return index - (pattern.size - 1)
            }
        }

        // No dice, Note that the stream is now completely consumed.
        return -1
    }

    /**
     * Builds up a table of longest "borders" for each prefix of the pattern to find. This table is stored internally
     * and aids in implementation of the Knuth-Moore-Pratt string search.
     *
     *
     * For more information, see: http://www.inf.fh-flensburg.de/lang/algorithmen/pattern/kmpen.htm.
     */
    private fun preProcess() {
        var i = 0
        var j = -1
        borders[i] = j
        while (i < pattern.size) {
            while (j >= 0 && pattern[i] != pattern[j]) {
                j = borders[j]
            }
            borders[++i] = ++j
        }
    }
}
