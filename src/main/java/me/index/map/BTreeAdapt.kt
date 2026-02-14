package me.index.map

import me.index.Holder
import java.util.TreeMap

class BTreeAdapt : Storage {
    private val root: TreeMap<Long, Any> = TreeMap()

    override fun init(keys: List<Long>, values: List<Any>, maxErr: Int) {
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
}
