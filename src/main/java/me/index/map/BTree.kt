package me.index.map

import me.index.Holder


const val t = 3
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
                    //TODO: steal from left
                    cur.rotateRight(pos - 1)
                } else if (pos < cur.size && !cur.children!![pos + 1]!!.isSmall) {
                    //TODO: steal from right
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

    override fun resort(keys: MutableList<Long>, vals: MutableList<Any?>) {
        traverse(root) { index, value ->
            keys.add(index)
            vals.add(value)
        }
    }

    override fun size(): Int {
        return treeSize
    }

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


fun main() {
    val tree = BTree()
    val arr = (0..100000).shuffled().toIntArray()
    val brr = arrayOf(-4979, -4977, -4975, -4962, -4957, -4935, -4932, -4928, -4926, -4922, -4912, -4899, -4886, -4865, -4846, -4844, -4835, -4834, -4832, -4830, -4827, -4811, -4781, -4774, -4771, -4750, -4749, -4746, -4738, -4729, -4715, -4713, -4703, -4702, -4691, -4684, -4679, -4660, -4657, -4648, -4647, -4646, -4641, -4636, -4635, -4633, -4619, -4609, -4598, -4592, -4591, -4584, -4581, -4580, -4573, -4572, -4562, -4551, -4545, -4523, -4522, -4516, -4515, -4506, -4505, -4503, -4488, -4484, -4470, -4469, -4468, -4463, -4460, -4459, -4452, -4450, -4445, -4439, -4432, -4428, -4424, -4407, -4395, -4392, -4389, -4381, -4377, -4366, -4353, -4351, -4344, -4321, -4313, -4311, -4310, -4302, -4298, -4285, -4276, -4274, -4272, -4267, -4266, -4265, -4258, -4257, -4250, -4232, -4226, -4207, -4205, -4198, -4196, -4195, -4184, -4181, -4171, -4169, -4161, -4157, -4156, -4154, -4151, -4140, -4131, -4127, -4113, -4100, -4096, -4087, -4084, -4082, -4080, -4079, -4078, -4077, -4053, -4051, -4041, -4034, -4031, -4026, -4024, -4022, -4000, -3996, -3991, -3987, -3960, -3957, -3952, -3947, -3941, -3934, -3931, -3929, -3928, -3926, -3918, -3917, -3914, -3910, -3901, -3895, -3892, -3891, -3886, -3877, -3870, -3822, -3819, -3818, -3813, -3812, -3810, -3803, -3798, -3789, -3784, -3781, -3777, -3775, -3774, -3768, -3765, -3762, -3755, -3750, -3740, -3739, -3732, -3728, -3725, -3703, -3695, -3692, -3674, -3672, -3665, -3654, -3638, -3634, -3625, -3624, -3622, -3611, -3600, -3587, -3567, -3562, -3540, -3534, -3531, -3523, -3506, -3505, -3504, -3486, -3482, -3469, -3467, -3461, -3458, -3457, -3455, -3451, -3450, -3441, -3425, -3423, -3408, -3407, -3406, -3381, -3373, -3367, -3365, -3361, -3350, -3328, -3323, -3317, -3314, -3313, -3309, -3297, -3289, -3288, -3270, -3261, -3256, -3249, -3236, -3228, -3222, -3215, -3213, -3211, -3201, -3171, -3164, -3163, -3148, -3130, -3125, -3115, -3110, -3084, -3079, -3074, -3073, -3068, -3067, -3062, -3061, -3057, -3056, -3038, -3031, -3018, -3005, -2996, -2986, -2985, -2974, -2963, -2961, -2957, -2956, -2955, -2952, -2949, -2946, -2940, -2936, -2926, -2914, -2911, -2908, -2906, -2904, -2901, -2893, -2887, -2885, -2878, -2877, -2871, -2870, -2858, -2855, -2851, -2849, -2848, -2841, -2828, -2821, -2816, -2802, -2799, -2797, -2790, -2785, -2778, -2775, -2767, -2758, -2755, -2748, -2742, -2738, -2735, -2724, -2715, -2714, -2711, -2692, -2689, -2675, -2659, -2646, -2638, -2625, -2613, -2610, -2606, -2605, -2595, -2592, -2584, -2574, -2570, -2566, -2545, -2544, -2529, -2525, -2509, -2508, -2504, -2503, -2501, -2489, -2484, -2483, -2475, -2463, -2450, -2429, -2427, -2415, -2396, -2394, -2392, -2389, -2358, -2351, -2349, -2347, -2337, -2335, -2312, -2309, -2297, -2292, -2281, -2279, -2256, -2253, -2251, -2249, -2242, -2238, -2207, -2195, -2191, -2190, -2188, -2184, -2181, -2176, -2167, -2158, -2151, -2150, -2149, -2145, -2140, -2131, -2125, -2121, -2119, -2115, -2109, -2108, -2106, -2103, -2098, -2085, -2078, -2072, -2060, -2058, -2054, -2048, -2043, -2030, -2020, -2015, -2007, -2002, -1999, -1996, -1986, -1957, -1956, -1955, -1953, -1947, -1939, -1938, -1934, -1931, -1899, -1894, -1893, -1887, -1881, -1875, -1858, -1856, -1854, -1852, -1849, -1848, -1837, -1835, -1824, -1817, -1815, -1810, -1796, -1793, -1791, -1788, -1781, -1773, -1766, -1744, -1741, -1738, -1733, -1727, -1722, -1720, -1717, -1711, -1708, -1699, -1687, -1676, -1668, -1659, -1651, -1649, -1648, -1646, -1639, -1638, -1634, -1631, -1630, -1624, -1619, -1618, -1617, -1603, -1598, -1587, -1579, -1575, -1574, -1573, -1571, -1567, -1562, -1534, -1531, -1529, -1525, -1522, -1509, -1505, -1496, -1493, -1487, -1474, -1473, -1457, -1440, -1439, -1438, -1436, -1431, -1430, -1417, -1416, -1414, -1413, -1411, -1408, -1407, -1391, -1389, -1387, -1380, -1372, -1370, -1367, -1365, -1364, -1356, -1344, -1338, -1336, -1335, -1333, -1327, -1325, -1324, -1321, -1311, -1309, -1303, -1299, -1291, -1288, -1287, -1276, -1266, -1259, -1249, -1242, -1238, -1229, -1219, -1214, -1212, -1208, -1207, -1192, -1188, -1186, -1182, -1176, -1175, -1167, -1158, -1154, -1149, -1147, -1142, -1134, -1132, -1129, -1124, -1122, -1117, -1115, -1114, -1104, -1097, -1096, -1095, -1094, -1089, -1088, -1068, -1058, -1050, -1048, -1045, -1042, -1035, -1033, -1012, -1008, -999, -997, -995, -992, -980, -978, -973, -972, -970, -963, -962, -961, -958, -949, -937, -935, -932, -928, -924, -915, -910, -906, -905, -890, -885, -879, -863, -853, -852, -851, -847, -846, -840, -827, -822, -812, -789, -788, -786, -785, -784, -774, -771, -768, -743, -739, -736, -734, -731, -730, -716, -698, -695, -691, -689, -688, -686, -682, -681, -680, -667, -663, -657, -656, -648, -641, -640, -635, -634, -625, -622, -617, -616, -615, -607, -603, -600, -594, -593, -592, -578, -567, -556, -552, -549, -546, -536, -535, -532, -525, -518, -514, -513, -512, -507, -506, -497, -494, -482, -479, -472, -470, -450, -440, -422, -418, -412, -411, -401, -399, -398, -395, -386, -379, -378, -377, -374, -364, -350, -328, -319, -318, -304, -295, -286, -277, -262, -244, -238, -216, -213, -195, -179, -176, -171, -169, -166, -156, -154, -149, -133, -127, -115, -105, -93, -92, -87, -77, -74, -70, -69, -68, -48, -47, -17, -16, -11, -8, -3, 0, 1, 26, 32, 46, 57, 58, 61, 64, 68, 70, 73, 76, 89, 104, 106, 110, 118, 122, 124, 125, 138, 140, 158, 159, 164, 178, 181, 184, 187, 197, 198, 203, 204, 206, 209, 214, 222, 228, 236, 237, 239, 247, 262, 267, 272, 279, 283, 285, 286, 288, 289, 295, 296, 308, 316, 323, 324, 327, 333, 347, 349, 358, 387, 407, 413, 432, 438, 439, 443, 470, 482, 484, 485, 489, 496, 505, 508, 521, 529, 541, 545, 583, 588, 592, 596, 597, 598, 603, 618, 619, 621, 628, 629, 633, 637, 641, 655, 657, 665, 668, 697, 698, 699, 724, 749, 752, 762, 764, 774, 775, 790, 793, 796, 798, 801, 806, 815, 826, 829, 832, 835, 838, 844, 845, 853, 857, 864, 873, 876, 877, 880, 882, 884, 886, 894, 904, 913, 926, 938, 939, 940, 941, 942, 961, 968, 971, 974, 975, 981, 982, 983, 984, 987, 988, 998, 1010, 1011, 1016, 1022, 1029, 1037, 1039, 1051, 1053, 1055, 1056, 1062, 1073, 1084, 1092, 1093, 1095, 1096, 1098, 1101, 1109, 1115, 1118, 1120, 1122, 1123, 1142, 1149, 1150, 1162, 1175, 1177, 1180, 1181, 1182, 1197, 1204, 1209, 1215, 1222, 1231, 1233, 1239, 1240, 1245, 1247, 1252, 1254, 1257, 1259, 1261, 1268, 1275, 1284, 1286, 1290, 1308, 1312, 1323, 1324, 1334, 1336, 1337, 1339, 1344, 1346, 1356, 1375, 1377, 1379, 1382, 1383, 1385, 1391, 1395, 1397, 1398, 1420, 1422, 1423, 1436, 1437, 1450, 1458, 1463, 1470, 1472, 1481, 1482, 1495, 1505, 1507, 1511, 1513, 1533, 1535, 1538, 1565, 1566, 1576, 1587, 1590, 1594, 1595, 1601, 1602, 1603, 1612, 1613, 1616, 1619, 1628, 1632, 1639, 1649, 1671, 1674, 1675, 1677, 1680, 1700, 1706, 1707, 1712, 1715, 1727, 1736, 1737, 1738, 1741, 1746, 1754, 1758, 1772, 1788, 1789, 1793, 1795, 1802, 1805, 1806, 1818, 1832, 1835, 1840, 1841, 1851, 1857, 1860, 1863, 1866, 1867, 1888, 1891, 1909, 1911, 1915, 1919, 1924, 1939, 1940, 1947, 1955, 1968, 1970, 1972, 1977, 1978, 1981, 1997, 2001, 2003, 2005, 2007, 2015, 2019, 2022, 2034, 2045, 2059, 2067, 2075, 2080, 2091, 2106, 2108, 2110, 2114, 2116, 2117, 2119, 2121, 2124, 2127, 2128, 2132, 2136, 2172, 2178, 2180, 2190, 2196, 2203, 2210, 2213, 2215, 2221, 2222, 2224, 2228, 2237, 2239, 2246, 2250, 2253, 2254, 2255, 2258, 2265, 2272, 2293, 2296, 2299, 2306, 2312, 2319, 2325, 2330, 2358, 2371, 2373, 2375, 2378, 2381, 2382, 2391, 2394, 2395, 2400, 2401, 2413, 2415, 2419, 2444, 2450, 2463, 2464, 2470, 2478, 2480, 2486, 2487, 2498, 2502, 2508, 2510, 2512, 2515, 2528, 2532, 2538, 2559, 2560, 2563, 2570, 2581, 2593, 2597, 2601, 2603, 2615, 2617, 2619, 2620, 2624, 2644, 2654, 2660, 2663, 2665, 2672, 2675, 2677, 2682, 2695, 2697, 2700, 2707, 2709, 2714, 2718, 2725, 2740, 2768, 2776, 2781, 2788, 2789, 2810, 2815, 2829, 2830, 2835, 2844, 2846, 2858, 2859, 2861, 2871, 2873, 2876, 2879, 2884, 2905, 2917, 2918, 2922, 2924, 2925, 2937, 2941, 2946, 2951, 2962, 2966, 2978, 2980, 2984, 2986, 2990, 3006, 3007, 3010, 3012, 3014, 3020, 3022, 3023, 3031, 3038, 3055, 3056, 3068, 3097, 3098, 3103, 3107, 3110, 3111, 3142, 3146, 3147, 3168, 3176, 3182, 3187, 3192, 3199, 3200, 3202, 3205, 3207, 3218, 3222, 3224, 3227, 3231, 3232, 3238, 3240, 3241, 3250, 3256, 3257, 3263, 3272, 3275, 3294, 3295, 3301, 3310, 3321, 3324, 3329, 3352, 3356, 3371, 3373, 3397, 3418, 3421, 3428, 3430, 3438, 3442, 3444, 3445, 3448, 3452, 3462, 3465, 3470, 3472, 3485, 3487, 3495, 3498, 3501, 3502, 3505, 3514, 3517, 3518, 3521, 3523, 3524, 3528, 3530, 3536, 3542, 3559, 3571, 3586, 3592, 3600, 3604, 3630, 3637, 3640, 3646, 3649, 3651, 3656, 3657, 3659, 3661, 3668, 3677, 3680, 3704, 3713, 3714, 3719, 3728, 3731, 3733, 3734, 3747, 3761, 3764, 3771, 3772, 3780, 3790, 3792, 3793, 3795, 3803, 3808, 3809, 3830, 3839, 3846, 3857, 3874, 3875, 3877, 3879, 3883, 3892, 3921, 3935, 3936, 3940, 3944, 3947, 3951, 3954, 3958, 3959, 3967, 3968, 3983, 3988, 3997, 4009, 4010, 4012, 4017, 4022, 4023, 4026, 4029, 4030, 4037, 4045, 4053, 4063, 4067, 4069, 4075, 4077, 4088, 4102, 4106, 4109, 4116, 4117, 4118, 4121, 4125, 4131, 4134, 4135, 4138, 4142, 4143, 4147, 4162, 4169, 4172, 4173, 4181, 4183, 4187, 4193, 4196, 4197, 4199, 4499, 4504, 4517, 4519, 4525, 4529, 4530, 4536, 4550, 4554, 4558, 4560, 4564, 4571, 4572, 4576, 4609, 4612, 4615, 4617, 4618, 4633, 4636, 4638, 4639, 4641, 4644, 4646, 4664, 4665, 4675, 4679, 4698, 4703, 4719, 4722, 4723, 4724, 4734, 4742, 4746, 4756, 4757, 4765, 4781, 4784, 4498, 4206, 4212, 4219, 4223, 4236, 4240, 4246, 4252, 4254, 4259, 4267, 4270, 4275, 4287, 4296, 4304, 4305, 4311, 4317, 4319, 4327, 4336, 4340, 4348, 4361, 4363, 4374, 4384, 4388, 4390, 4394, 4399, 4419, 4422, 4433, 4441, 4449, 4450, 4457, 4465, 4466, 4468, 4469, 4477, 4488, 4492, 4792, 4795, 4796, 4800, 4802, 4805, 4812, 4815, 4826, 4827, 4828, 4842, 4851, 4853, 4858, 4862, 4869, 4883, 4888, 4889, 4890, 4900, 4903, 4907, 4911, 4927, 4930, 4933, 4934, 4942, 4950, 4958, 4959, 4963, 4966, 4971, 4972, 4978, 4988, 4989, 4991, 4993, 4998)
    println(brr[1494])
    println(brr[1495])
    println(brr[1496])
    println(brr[1497])
    println(brr[1498])
    println(brr[1499])
    for (i in (1..<brr.size)) {
        if (brr[i] < brr[i - 1]) {
            println("${i - 1} ${brr[i - 1]}")
        }
    }
//    arr.copyOfRange(0, 2849).forEachIndexed { i, v -> test1(i, tree, arr) }
//    arr.forEachIndexed { i, v -> test1(i, tree, arr) }
//    tree.insert(arr[2849].toLong(), arr[2849])
}
