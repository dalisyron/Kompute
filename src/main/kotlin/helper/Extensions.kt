package helper

fun <T> List<T>.dquals(list: List<T>): Boolean {
    val s1 = toSet()
    val s2 = list.toSet()
    return s1 == s2
}