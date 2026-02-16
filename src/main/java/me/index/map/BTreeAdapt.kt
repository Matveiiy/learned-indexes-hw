package me.index.map

import me.index.Holder
import java.util.TreeMap


private class BNode {
    final val t = 32
    val keys =
    val children = arrayOfNulls<BNode?>(t)
}

class BTreeAdapt : Storage {
    private val root: TreeMap<Long, Any> = TreeMap()
    override fun init(
        keys: List<Long?>?,
        values: List<Any?>?,
        maxErr: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun find(key: Long, result: Holder<in Any>?): Int {
        TODO("Not yet implemented")
    }

    override fun insert(key: Long, value: Any?): Int {
        TODO("Not yet implemented")
    }

    override fun remove(key: Long): Int {
        TODO("Not yet implemented")
    }

    override fun resort(keys: List<Long?>?, vals: List<Any?>?) {
        TODO("Not yet implemented")
    }

    override fun size(): Int {
        TODO("Not yet implemented")
    }

}
