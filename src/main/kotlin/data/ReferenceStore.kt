package com.vhtor.data

class ReferenceStore(
    val vectors: FloatArray,
    val labels: BooleanArray,
    val size: Int,
    val dimensions: Int = 14
) {
    fun getVector(index: Int): FloatArray {
        val offset = index * dimensions
        return vectors.copyOfRange(offset, offset + dimensions)
    }

    fun isFraud(index: Int): Boolean = labels[index]
}
