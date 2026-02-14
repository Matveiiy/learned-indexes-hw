package me.index.map

import me.index.Holder
import me.index.algo.LRM
import me.index.algo.Regression
import java.util.TreeMap

class LIndex : Storage {
    private val root: TreeMap<Long, Any> = TreeMap()

    override fun init(keys: List<Long>, values: List<Any>, maxErr: Int) {
        val minKeys = mutableListOf<Long>()
        val maxKeys = mutableListOf<Long>()
        val models = mutableListOf<Model>()

        val lambda: Function3<Int, Int, LRM, Unit> = { start: Int, end: Int, lrm: LRM ->
            minKeys.add(if (start != 0) keys[start] else Long.MIN_VALUE)
            maxKeys.add(if (end < keys.size) (keys[end] - 1) else Long.MAX_VALUE)
            models.add(Model(keys.subList(start, end), values.subList(start, end), lrm, maxErr))
        }

        val regression = Regression()
        regression.split(keys, maxErr, lambda)

        Models(minKeys, maxKeys, models)

        for (i in keys.indices)
            this.insert(keys[i], values[i])
    }

    override fun find(key: Long, result: Holder<Any>): Int {
        val r = root[key]
        return if (r == null) {
            Storage.FAIL
        } else {
            result.v = r
            Storage.OK
        }
    }

    override fun insert(key: Long, value: Any): Int {
        val r = root.putIfAbsent(key, value)
        return if (r == null) {
            Storage.OK
        } else {
            Storage.FAIL
        }
    }

    override fun remove(key: Long): Int {
        val r = root.remove(key)
        return if (r == null) {
            Storage.FAIL
        } else {
            Storage.OK
        }
    }

    override fun resort(keys: MutableList<Long>, vals: MutableList<Any>) {
        for ((k, v) in root) {
            keys.add(k)
            vals.add(v)
        }
    }

    override fun size(): Int {
        return root.size
    }

    // other classes:
    private data class Models(
        val minKeys: List<Long>,
        val maxKeys: List<Long>,
        val models: List<Model>
    )

    private data class Model(
        val keys: List<Long>,
        val values: List<Any>,
        val lrm: LRM,
        val maxErr: Int
    )
}
