package grant.services

fun main() {

    //language=sql

    val input = """
        select :qwe::json->2
""".trimIndent()

    val rg = Regex("(^|[^:])[?:]([a-zA-Z0-9]+)")

    println(rg.findAll(input).toList().map { it.groups[2]?.value })
    println()
    println()
    println()
    println(
    rg.replace(input) {
        it.groups[1]!!.value + "?"
    }
    )
}
