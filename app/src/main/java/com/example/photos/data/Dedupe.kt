package com.example.photos.data

import kotlin.math.abs

/** Items are sorted best-first: the first one is the suggested keeper. */
data class DuplicateGroup(val items: List<Media>)

object Dedupe {
    /**
     * Groups photos whose perceptual hashes differ by at most [threshold] bits (0..7).
     * Uses 8 x 8-bit buckets: any two hashes within 7 bits share at least one identical byte.
     */
    fun find(media: List<Media>, index: Map<String, IndexEntry>, threshold: Int): List<DuplicateGroup> {
        val items = media.filter { !it.isVideo && index[it.key]?.hash != null }
        val n = items.size
        if (n < 2) return emptyList()
        val hashes = LongArray(n) { index.getValue(items[it].key).hash!! }
        val ratio = DoubleArray(n) { val m = items[it]; if (m.height > 0) m.width.toDouble() / m.height else 0.0 }
        val parent = IntArray(n) { it }

        fun root(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val nx = parent[c]; parent[c] = r; c = nx }
            return r
        }

        for (chunk in 0 until 8) {
            val buckets = HashMap<Int, MutableList<Int>>()
            for (i in 0 until n) {
                val k = ((hashes[i] ushr (chunk * 8)) and 0xFF).toInt()
                buckets.getOrPut(k) { ArrayList() }.add(i)
            }
            for (list in buckets.values) {
                if (list.size < 2 || list.size > 3000) continue // huge buckets = blank/flat images
                for (a in list.indices) {
                    val i = list[a]
                    for (b in a + 1 until list.size) {
                        val j = list[b]
                        if (java.lang.Long.bitCount(hashes[i] xor hashes[j]) <= threshold &&
                            abs(ratio[i] - ratio[j]) < 0.05
                        ) {
                            val ri = root(i); val rj = root(j)
                            if (ri != rj) parent[ri] = rj
                        }
                    }
                }
            }
        }

        val best = compareByDescending<Media> { it.width.toLong() * it.height }
            .thenByDescending { it.size }
            .thenBy { it.dateTaken }
        return (0 until n).groupBy { root(it) }.values
            .filter { it.size >= 2 }
            .map { idx -> DuplicateGroup(idx.map { items[it] }.sortedWith(best)) }
            .sortedByDescending { it.items.size }
    }
}
