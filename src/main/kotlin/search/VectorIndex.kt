package com.vhtor.search

import com.vhtor.data.ReferenceStore

/**
 * Índice de busca vetorial brute-force sobre vetores quantizados (int8).
 *
 * A distância euclidiana ao quadrado é calculada em aritmética inteira:
 * - Cada byte [0, 255] representa um float [-1, 1]
 * - diff = byteA - byteB (range: -255 a 255)
 * - dist += diff * diff
 */
class VectorIndex(private val store: ReferenceStore) {
    companion object {
        const val K = 5
    }

    private val dimensions = store.dimensions

    /**
     * Encontra os K vizinhos mais próximos do query vector.
     *
     * @param query FloatArray de 14 dimensões (output do Vectorizer)
     * @return Lista de K Neighbors ordenada por distância crescente
     */
    fun findNearest(query: FloatArray): List<Neighbor> {
        val quantizedQuery = ReferenceStore.quantizeVector(query)
        val topDistances = IntArray(K) { Int.MAX_VALUE }
        val topIndices = IntArray(K) { -1 }

        val vectors = store.vectors
        val size = store.size

        for (i in 0 until size) {
            val baseOffset = i * dimensions

            // Calcula distância euclidiana ao quadrado
            var distanceSquared = 0
            for (d in 0 until dimensions) {
                val referenceValue = vectors[baseOffset + d].toInt() and 0xFF // Conversão para unsigned
                val queryValue = quantizedQuery[d].toInt() and 0xFF
                val diff = referenceValue - queryValue
                distanceSquared += diff * diff
            }

            // Se esta distância é menor que a maior no top-K, insere
            if (distanceSquared < topDistances[K-1]) {
                insertSorted(topDistances, topIndices, distanceSquared, i)
            }
        }

        return (0 until K)
            .filter { topIndices[it] != -1 }
            .map { Neighbor(topIndices[it], topDistances[it]) }
    }

    /**
     * Insertion sort no bounded array de tamanho K.
     * Para k=5, isso é no máximo 4 comparações + shifts
     *
     * O array está sempre ordenado por distância crescente:
     * topDistances[0] = menor distância (vizinho mais próximo)
     * topDistances[K-1] = maior distância (candidato a ser substituído)
     */
    private fun insertSorted(
        distances: IntArray,
        indices: IntArray,
        newDistance: Int,
        newIndex: Int
    ) {
        // Encontra a posição correta (do final para o início)
        var position = K - 1
        while (position > 0 && distances[position - 1] > newDistance) {
            // Shift para a direita (o último elemento cai fora)
            distances[position] = distances[position - 1]
            indices[position] = indices[position - 1]
            position--
        }

        distances[position] = newDistance
        indices[position] = newIndex
    }
}


/**
 * Resultado de uma busca k-NN: o índice do vetor no ReferenceStore
 * e a distância ao quadrado (em escala inteira) até o query vector.
 *
 *  Nota: distanceSquared é proporcional à distância float² (fator: 127.5²)
 *  mas o valor absoluto não é usado — apenas a ordenação importa.
 */
data class Neighbor(
    val index: Int,
    val distanceSquared: Int
) {}
