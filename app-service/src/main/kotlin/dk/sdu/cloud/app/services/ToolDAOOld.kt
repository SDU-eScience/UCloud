package dk.sdu.cloud.app.services

/*
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.NormalizedToolDescription

object ToolDAO {
    val inMemoryDB = mutableMapOf(
        "hello_world" to listOf(
            NormalizedToolDescription(
                title = "hello",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "hello",
                info = NameAndVersion("hello_world", "1.0.0"),
                container = "hello.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(hours = 0, minutes = 1, seconds = 0),
                requiredModules = emptyList()
            )
        ),

        "figlet" to listOf(
            NormalizedToolDescription(
                title = "Figlet",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                info = NameAndVersion("figlet", "1.0.0"),
                container = "figlet.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(0, 1, 0),
                requiredModules = emptyList()
            )
        ),

        "parms" to listOf(
            NormalizedToolDescription(
                title = "parms",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "parms",
                info = NameAndVersion("parms", "1.0.0"),
                container = "parms.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(0, 10, 0),
                requiredModules = emptyList()
            )
        ),

        "tqdist" to listOf(
            NormalizedToolDescription(
                title = "tqDist",
                authors = listOf(
                    "Andreas Sand",
                    "Morten K. Holt",
                    "Jens Johansen",
                    "Gerth Stølting Brodal",
                    "Thomas Mailund",
                    "Christian N.S. Pedersen"
                ),
                createdAt = 1521121661000L,
                modifiedAt = 1521121661000L,
                description = """
                   Distance measures between trees are useful for comparing trees in a systematic manner and
                   several different distance measures have been proposed. The triplet and quartet distances, for
                   rooted and unrooted trees, are defined as the number of subsets of three or four leaves,
                   respectively, where the topologies of the induced sub-trees differ. These distances can trivially
                   be computed by explicitly enumerating all sets of three or four leaves and testing if the
                   topologies are different, but this leads to time complexities at least of the order n³ or n⁴ just
                   for enumerating the sets. The different topologies can be counted implicitly, however, and using
                   this tqDist computes the triplet distance between rooted trees in O(n log n) time and the quartet
                   distance between unrooted trees in O(dn log n) time, where d degree of the tree with the smallest
                   degree.
                """.trimIndent(),
                info = NameAndVersion("tqdist", "1.0.1"),
                container = "tqdist.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(0, 10, 0),
                requiredModules = emptyList()
            )
        ),

        "rapidnj" to listOf(
            NormalizedToolDescription(
                title = "RapidNJ",
                authors = listOf(
                    "Martin Simonsen",
                    "Thomas Mailund",
                    "Christian N. S. Pedersen"
                ),
                createdAt = 1521121661000L,
                modifiedAt = 1521121661000L,
                description = """
                    RapidNJ is an algorithmic engineered implementation of canonical neighbour-joining. It uses an
                    efficient search heuristic to speed-up the core computations of the neighbour-joining method that
                    enables RapidNJ to outperform other state-of-the-art neighbour-joining implementations
                """.trimIndent(),
                info = NameAndVersion("rapidnj", "2.3.2"),
                container = "rapidnj.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(0, 10, 0),
                requiredModules = emptyList()
            )
        ),

        "searchgui" to listOf(
            NormalizedToolDescription(
                title = "SearchGUI",
                authors = listOf(
                    "Vaudel M",
                    "Barsnes H",
                    "Berven FS",
                    "Sickmann A",
                    "Martens L."
                ),
                createdAt = 1521121661000L,
                modifiedAt = 1521121661000L,
                description = "",
                info = NameAndVersion("searchgui", "3.3.0"),
                container = "sgui",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(3, 0, 0),
                requiredModules = emptyList(),
                backend = ToolBackend.UDOCKER
            )
        ),

        "bwa-sambamba" to listOf(
            NormalizedToolDescription(
                title = "bwa-sambamba",
                authors = listOf("BWA Authors", "Sambamba Authors"),
                createdAt = 1527663964000L,
                modifiedAt = 1527663964000L,
                description = "",
                info = NameAndVersion("bwa-sambamba", "1.0.0"),
                container = "bwa-sambamba",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(1, 0, 0),
                requiredModules = emptyList(),
                backend = ToolBackend.UDOCKER
            )
        )
    )

    fun findByNameAndVersion(name: String, version: String): NormalizedToolDescription? =
        inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<NormalizedToolDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<NormalizedToolDescription> = inMemoryDB.values.flatten()
}
        */