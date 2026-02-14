package me.index.map

import me.index.Holder


const val t = 32
const val MAX_KEYS = 2 * t - 1
const val MAX_CHILDREN = MAX_KEYS + 1


class BNode(
    var msize: Int,
    var children: Array<BNode?>?
) {
    var values: Array<Any?> = arrayOfNulls(MAX_KEYS)
    var keys: LongArray = LongArray(MAX_KEYS)

    val size: Int get() = msize
    val isLeaf: Boolean get() = children == null
    val isFull: Boolean get() = size == MAX_KEYS
    val isVerySmall: Boolean get() = size == t - 1

    // assuming it is full (size == MAX_KEYS)
    // makes itself a left child, returns right child and new splitter (key and value)
    fun split(): Triple<BNode, Long, Any?> {
        val medianIdx = t - 1
        val splitterKey = keys[medianIdx]
        val splitterValue = values[medianIdx]

        // If this is a leaf, the sibling is a leaf. If not, it needs a children array.
        val rightNode = BNode(t - 1, if (isLeaf) null else arrayOfNulls<BNode?>(MAX_CHILDREN))

        // Move the right half of keys and values to the new node
        for (i in 0 until t - 1) {
            rightNode.keys[i] = this.keys[i + t]
            rightNode.values[i] = this.values[i + t]
        }

        // Move the right half of children if this is not a leaf
        if (!isLeaf) {
            for (i in 0 until t) {
                rightNode.children!![i] = this.children!![i + t]
                this.children!![i + t] = null // Clear old refs
            }
        }

        // Update the size of the current (left) node
        this.msize = t - 1
        this.values[medianIdx] = null

        return Triple(rightNode, splitterKey, splitterValue)
    }

    fun insertSimple(position: Int, key: Long, value: Any?, child: BNode? = null) {
        var i = msize++
        while (i > position) {
            keys[i] = keys[i - 1]
            values[i] = values[i - 1]
            children?.let { it.set(i + 1, it[i]) }
            i--
        }
        keys[position] = key
        values[position] = value
        children?.let { it[position + 1] = child }
    }

    fun removeSimple(position: Int): Triple<Long, Any?, BNode?> {
        msize--
        val resKey = keys[position]
        val resValue = values[position]
        val resChild = children?.get(position)
        for (i in position..msize) {
            keys[i] = keys[i + 1]
            values[i] = values[i + 1]
            children?.let { it[i] = it[i + 1] }
        }
        return Triple(resKey, resValue, resChild)
    }

    fun insertNotFull(key: Long, value: Any?): Int {
        var pos = findKey(key)

        // Check for duplicate keys
        if (pos < size && keys[pos] == key) {
            return Storage.FAIL
        }

        if (isLeaf) {
            insertSimple(pos, key, value)
            return Storage.OK
        }

        // Non-leaf case: Check if the child we need to descend into is full
        if (children!![pos]!!.isFull) {
            val (right, splitKey, splitVal) = children!![pos]!!.split()

            // Push the median up into the current node
            insertSimple(pos, splitKey, splitVal, right)

            // After the split, the key might now belong in the new right sibling
            if (key > splitKey) {
                pos++
            } else if (key == splitKey) {
                return Storage.FAIL // Handle potential duplicate after split
            }
        }

        // Recurse into the (possibly updated) child
        return children!![pos]!!.insertNotFull(key, value)
    }


    fun removeNotVerySmall(key: Long): Int {

    }

    fun findKey(key: Long): Int {
        (0..<size).forEach { index ->
            if (keys[index] >= key) return index
        }
        return size
    }
}

class BTree : Storage {
    var treeSize: Int = 0
    var root: BNode = BNode(0, null)
    override fun init(
        keys: List<Long?>?,
        values: List<Any?>?,
        maxErr: Int
    ) {
        keys?.forEachIndexed { index, key -> insert(key!!, values!!.get(index)) }
    }

    override fun find(key: Long, result: Holder<in Any>?): Int {
        var cur: BNode = root;
        while (true) {
            val res = cur.findKey(key)
            if (res < cur.size && cur.keys[res] == key) {
                //found
                result!!.v = cur.values[res]
                return Storage.OK
            }
            //not found
            if (cur.isLeaf) {
                return Storage.FAIL
            }
            //go deeper
            cur = cur.children!![res]!!
        }
    }

    override fun insert(key: Long, value: Any?): Int {
        if (root.isFull) {
            val (right, splitkey, splitval) = root.split()
            val left = root
            root = BNode(
                1, arrayOfNulls<BNode?>(MAX_CHILDREN)
                    .apply { this.set(0, left); this.set(1, right) })
                .apply { this.keys[0] = splitkey; this.values[0] = splitval }
        }
        return when (root.insertNotFull(key, value)) {
            Storage.OK -> run { treeSize += 1; Storage.OK }
            else -> Storage.FAIL
        }
    }

    override fun remove(key: Long): Int {
        TODO("Implement this")
    }

    fun traverse(node: BNode, f: (Long, Any?) -> Unit) {
        if (node.isLeaf) {
            (0..<node.size).forEach { index -> f(node.keys[index], node.values[index]) }
            return
        }
        (0..<node.size).forEach { index ->
            traverse(node.children!![index]!!, f)
            f(node.keys[index], node.values[index])
        }
        traverse(node.children!![node.size]!!, f)
    }

    override fun resort(keys: MutableList<Long>, vals: MutableList<Any>) {
        traverse(root) { index, value ->
            keys.add(index)
            value?.let { vals.add(it) }
        }
    }

    override fun size(): Int {
        return treeSize
    }

}

fun main() {
    val test = arrayOf(
        87921,
        92516,
        94546,
        98194,
        27159,
        5159,
        91117,
        26924,
        51459,
        34753,
        47467,
        50261,
        41558,
        66853,
        82122,
        12248,
        53186,
        26691,
        25689,
        85046,
        33450,
        73323,
        85337,
        86765,
        4157,
        27963,
        54986,
        5719,
        67918,
        58409,
        97641,
        9960,
        9615,
        97210,
        35308,
        24894,
        767,
        95470,
        61937,
        64777,
        25324,
        35279,
        13445,
        6383,
        48321,
        8061,
        43669,
        94558,
        61077,
        52190,
        66419,
        95468,
        35306,
        79227,
        62742,
        46298,
        8484,
        83778,
        51484,
        85889,
        46675,
        96235,
        4693,
        1638,
        31374,
        15309,
        77942,
        50108,
        91832,
        23435,
        29874,
        41924,
        33207,
        16770,
        34751,
        93649,
        6602,
        13750,
        57954,
        74643,
        2004,
        7417,
        13563,
        87523,
        11977,
        66822,
        56497,
        61375,
        60386,
        53661,
        57068,
        69511,
        97444,
        47064,
        14221,
        97969,
        76456,
        64383,
        70321,
        72351,
        55801,
        4968,
        81992,
        17375,
        26795,
        35062,
        16646,
        24667,
        41751,
        30806,
        64998,
        23091,
        36951,
        64938,
        59212,
        8759,
        7905,
        31252,
        99298,
        99902,
        59519,
        93002,
        13081,
        58079
    )
    val tree: BTree = BTree()
    test.forEach {
        System.err.println(it.toString())
        tree.insert(it.toLong(), it)
    }
    tree.insert(16748, 16748)

}