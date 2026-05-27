package com.vhtor.data

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

object ReferenceLoader {
    private const val DIMENSIONS = 14
    private const val DEFAULT_EXPECTED_SIZE = 3_000_000
    private const val GZIP_BUFFER_SIZE = 65536

    fun load(path: String, expectedSize: Int = DEFAULT_EXPECTED_SIZE): ReferenceStore {
        val vectors = FloatArray(expectedSize * DIMENSIONS)
        val labels = BooleanArray(expectedSize)

        var count = 0

        val factory = JsonFactory()
        val stream = GZIPInputStream(FileInputStream(path), GZIP_BUFFER_SIZE)

        factory.createParser(stream).use { parser ->
            parser.nextToken()

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                readEntry(parser, vectors, labels, count)
                count++
            }
        }

        return ReferenceStore(vectors, labels, count, DIMENSIONS)
    }

    private fun readEntry(
        parser: JsonParser,
        vectors: FloatArray,
        labels: BooleanArray,
        index: Int
    ) {
        val baseOffset = index * DIMENSIONS

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName()

            when (fieldName) {
                "vector" -> {
                    parser.nextToken()
                    var dimension = 0

                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        vectors[baseOffset + dimension] = parser.floatValue
                        dimension++
                    }
                }

                "label" -> {
                    parser.nextToken()
                    labels[index] = parser.text == "fraud"
                }
            }
        }
    }
}
