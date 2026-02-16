package me.index.map

import me.index.Holder


const val t = 32
const val MAX_KEYS = 2 * t - 1
const val MAX_CHILDREN = MAX_KEYS + 1


class BTree : Storage {

    var treeSize: Int = 0
    private var root: BNode = BNode(0, null)

    private class BNode(
        var msize: Int,
        var children: Array<BNode?>?
    ) {
        var values: Array<Any?> = arrayOfNulls(MAX_KEYS)
        var keys: LongArray = LongArray(MAX_KEYS)

        val size: Int get() = msize
        val isLeaf: Boolean get() = children == null
        val isFull: Boolean get() = size == MAX_KEYS
        val isSmall: Boolean get() = size == t - 1

        // assuming it is full (size == MAX_KEYS)
        // makes itself a left child, returns right child and new splitter (key and value)
        fun split(): Triple<BNode, Long, Any?> {
            val medianIdx = t - 1
            val splitterKey = keys[medianIdx]
            val splitterValue = values[medianIdx]

            // If this is a leaf, the sibling is a leaf. If not, it needs a children array.
            val rightNode = BNode(t - 1, if (isLeaf) null else arrayOfNulls<BNode?>(MAX_CHILDREN))


            this.keys.copyInto(rightNode.keys, 0, t, msize)
            this.values.copyInto(rightNode.values, 0, t, msize)

            this.children?.let {
                it.copyInto(rightNode.children!!, 0, t, msize + 1)
            }

            msize = t - 1
            return Triple(rightNode, splitterKey, splitterValue)
        }

        //inserts into a node, position <= size
        fun insertSimple(position: Int, key: Long, value: Any?, leftChild: BNode? = null) {
//        require(position <= size)
            if (position < size) {
                keys.copyInto(keys, position + 1, position, msize)
                values.copyInto(values, position + 1, position, msize)
            }
            msize++
            children?.let {
                it.copyInto(it, position + 1, position, msize)
                it[position] = leftChild
            }
            keys[position] = key
            values[position] = value
        }

        fun splitChild(position: Int) {
            children!!.let { children ->
                val (right, key, value) = children[position]!!.split()
                insertSimple(position, key, value, children[position]!!.also { children[position] = right })
            }
        }

        //removes from node, position < size
        fun removeSimple(pos: Int): Triple<Long, Any?, BNode?> {
            val resKey = keys[pos]
            val resValue = values[pos]
            val resChild = children?.let { children ->
                children[pos].also {
                    children.copyInto(children, pos, pos + 1, msize + 1)
                }
            }
            if (pos < size) {
                keys.copyInto(keys, pos, pos + 1, msize)
                values.copyInto(values, pos, pos + 1, msize)
            }
            msize--
            return Triple(resKey, resValue, resChild)
        }

        //merges nodes so that receiver node will be like this: [node key/value other]
        //assumes that this node's and another node's size == t - 1
        //returns itself
        fun mergeTo(other: BNode, key: Long, value: Any?): BNode {
            keys[t - 1] = key
            values[t - 1] = value
            other.keys.copyInto(this.keys, t, 0, t - 1)
            other.values.copyInto(this.values, t, 0, t - 1)
            other.children?.let { children ->
                children.copyInto(this.children!!, t, 0, t)
            }
            msize = MAX_KEYS
            return this
        }

        //merges children at (pos and pos + 1)
        //returns merged child for convience
        fun mergeChild(pos: Int): BNode {
            children!!.let { children ->
                val (lKey, lValue, lChild) = removeSimple(pos)
                children[pos] = lChild!!.mergeTo(children[pos]!!, lKey, lValue)
                return children[pos]!!
            }
        }

        //swaps two specific children if there are any
        fun swap(leftIdx: Int, rightIdx: Int) = children?.let { array ->
            array[leftIdx] = array[rightIdx].also { array[rightIdx] = array[leftIdx] }
        }

        //rotates left around the node, left child is pos, right child is pos + 1
        fun rotateLeft(pos: Int) {
            val aKey = keys[pos]
            val aValue = values[pos]
            val (zKey, zValue, childLast) = children!![pos + 1]!!.removeSimple(0)
            keys[pos] = zKey
            values[pos] = zValue
            val leftChild = children!![pos]!!
            leftChild.insertSimple(leftChild.size, aKey, aValue, childLast)
            //last will be moved before the insertion, so we swap last and the one before last
            leftChild.swap(leftChild.size - 1, leftChild.size)
            //leftChild.addLast(aKey, aValue, childLast)
//        leftChild.keys[leftChild.msize] = aKey
//        leftChild.values[leftChild.msize] = aValue
//        leftChild.msize++
//        leftChild.children?.let { it[leftChild.msize] = childLast }
        }

        //rotates right around the node, left child is pos, right child is pos + 1
        fun rotateRight(pos: Int) {
            val aKey = keys[pos]
            val aValue = values[pos]
            val leftChild = children!![pos]!!
            //child that is before last will be removed so we swap them beforehand
            leftChild.swap(leftChild.size - 1, leftChild.size)
            val (zKey, zValue, childFirst) = leftChild.removeSimple(leftChild.size - 1)
            keys[pos] = zKey
            values[pos] = zValue
            children!![pos + 1]!!.insertSimple(0, aKey, aValue, childFirst)
        }

        fun findKey(key: Long): Int {
            (0..<size).forEach { index ->
                if (keys[index] >= key) return index
            }
            return size
        }
    }

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
            root = BNode(0, arrayOfNulls(MAX_CHILDREN)).also { it.children?.set(0, root) }
            root.splitChild(0)
        }
        var cur = root
        while (true) {
            var pos = cur.findKey(key)
            // Check for duplicate keys
            if (pos < cur.size && cur.keys[pos] == key) {
                return Storage.FAIL
            }
            if (cur.isLeaf) {
                cur.insertSimple(pos, key, value)
                treeSize++;
                return Storage.OK
            }
            // Non-leaf case: Check if the child we need to descend into is full
            if (cur.children!![pos]!!.isFull) {
                cur.splitChild(pos)
                // After the split, the key might now belong in the new right sibling
                if (key > cur.keys[pos]) {
                    pos++
                } else if (key == cur.keys[pos]) {
                    return Storage.FAIL // Handle potential duplicate after split
                }
            }
            cur = cur.children!![pos]!!
        }
    }

    fun innerRemove(initialKey: Long): Int {
        var cur = root
        var key = initialKey
        while (true) {
            val pos = cur.findKey(key)
            if (pos < cur.size && cur.keys[pos] == key) {
                if (cur.children != null) {
                    val l = cur.children!![pos]!!
                    val r = cur.children!![pos + 1]!!
                    if (!l.isSmall) {
                        var temp = l
                        while (temp.children != null)
                            temp = temp.children!![temp.size]!!
                        cur.keys[pos] = temp.keys[temp.size - 1]
                        cur.values[pos] = temp.values[temp.size - 1]
                        key = cur.keys[pos]
                        cur = l
                    } else if (!r.isSmall) {
                        var temp = r
                        while (temp.children != null)
                            temp = temp.children!![0]!!
                        cur.keys[pos] = temp.keys[0]
                        cur.values[pos] = temp.values[0]
                        key = cur.keys[pos]
                        cur = r
                    } else {
                        cur = cur.mergeChild(pos)
                    }
                    continue
                } else return cur.removeSimple(pos).let { treeSize--; Storage.OK }
            }
            if (cur.isLeaf)
                return Storage.FAIL
            val child = cur.children!![pos]!!
            if (child.isSmall) {
                if (pos > 0 && !cur.children!![pos - 1]!!.isSmall) {
                    cur.rotateRight(pos - 1)
                } else if (pos < cur.size && !cur.children!![pos + 1]!!.isSmall) {
                    cur.rotateLeft(pos)
                } else {
                    if (pos < cur.size) cur.mergeChild(pos)
                    else cur.mergeChild(pos - 1)
                }
            }
            cur = cur.children!![if (pos > cur.size) pos - 1 else pos]!!
        }
    }

    override fun remove(initialKey: Long): Int {
        return innerRemove(initialKey).also {
            if (!root.isLeaf && root.size == 0) {
                root = root.children!![0]!!
            }
        }
    }

    private fun collect(node: BNode, keys: MutableList<Long>, vals: MutableList<Any?>) {
        if (node.isLeaf) {
            (0..<node.size).forEach { index -> keys.add(node.keys[index]); vals.add(node.values[index]) }
        } else {
            (0..<node.size).forEach { index ->
                collect(node.children!![index]!!, keys, vals)
                keys.add(node.keys[index])
                vals.add(node.values[index])
            }
            collect(node.children!![node.size]!!, keys, vals)
        }
    }

    override fun resort(keys: MutableList<Long>, vals: MutableList<Any?>) = collect(root, keys, vals)

    override fun size() = treeSize
}

fun test1(index: Int, tree: BTree, arr: IntArray) {
    System.err.println(index)
    val curSize = tree.size()
    try {
        tree.insert(arr[index].toLong(), arr[index])
        require(curSize != tree.size())
        val keys = mutableListOf<Long>()
        val vals = mutableListOf<Any?>()
        tree.resort(keys, vals)
        if (keys != arr.copyOfRange(0, keys.size).sorted().map { it.toLong() }) {
            System.err.println(index)
            require(false)
        }
    } catch (e: Exception) {
        System.err.println(arr.copyOfRange(0, index + 1).contentToString())
        throw e
    }

}

/*
fun main() {
    val tree = BTree()
    val arr = (0..100000).shuffled().toIntArray()
//    arr.copyOfRange(0, 2849).forEachIndexed { i, v -> test1(i, tree, arr) }
//    arr.forEachIndexed { i, v -> test1(i, tree, arr) }
//    tree.insert(arr[2849].toLong(), arr[2849])
}
*/
