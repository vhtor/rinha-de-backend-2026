package com.vhtor.data

class ReferenceStore(
    val vectors: ByteArray,
    val labels: BooleanArray,
    val size: Int,
    val dimensions: Int = 14
) {
    fun isFraud(index: Int): Boolean = labels[index]

    companion object {
        /**
         * Quantiza um float [-1, 1] para byte [0, 255].
         * -1.0 → 0, 0.0 → 128, 1.0 → 255
         */
        fun quantize(value: Float): Byte {
            val scaled = ((value + 1f) * 127.5f + 0.5f).toInt()
            return scaled.coerceIn(0, 255).toByte()
        }

        /**
         * Quantiza um FloatArray inteiro para ByteArray.
         */
        fun quantizeVector(floats: FloatArray): ByteArray {
            return ByteArray(floats.size) { i -> quantize(floats[i]) }
        }
    }
}
