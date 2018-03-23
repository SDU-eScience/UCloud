package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.NameAndVersion

object ApplicationDAO {
    val inMemoryDB: MutableMap<String, List<ApplicationDescription>> = mutableMapOf(
        "figlet" to listOf(
            ApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Figlet",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                tool = NameAndVersion("figlet", "1.0.0"),
                info = NameAndVersion("figlet", "1.0.0"),
                invocation = listOf(WordInvocationParameter("figlet"), VariableInvocationParameter(listOf("text"))),
                parameters = listOf(
                    ApplicationParameter.Text(
                        name = "text",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Text",
                        description = "Some text to render with figlet"
                    )
                ),
                outputFileGlobs = listOf("stdout.txt")
            )
        ),

        "figlet-count" to listOf(
            ApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Figlet Counter",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Count with Figlet!",
                tool = NameAndVersion("figlet", "1.0.0"),
                info = NameAndVersion("figlet-count", "1.0.0"),
                invocation = listOf(WordInvocationParameter("figlet-count"), VariableInvocationParameter(listOf("n"))),
                parameters = listOf(
                    ApplicationParameter.Integer(
                        name = "n",
                        optional = true,
                        defaultValue = 100,
                        prettyName = "Count",
                        description = "How much should we count to?"
                    )
                ),
                outputFileGlobs = listOf("stdout.txt", "stderr.txt")
            )
        ),

        "parms" to listOf(
            ApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Parms",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Good his morning he fruitful signs days years fruit their them our fish rule shall greater can't she'd him god seas seas bearing land whales you're. Behold in winged, darkness she'd doesn't herb Called multiply abundantly. God signs creeping after doesn't. Make creature had which can't appear fish fly, said living void moved rule appear you blessed the. Multiply Meat saw is above in that of won't firmament have let a waters abundantly wherein divide subdue bearing creeping isn't is man. Isn't our itself stars our two fish. Fill great greater gathered abundantly creature created morning in second gathering. Seasons. Place us. Be after saying created lights moved give seas was stars own lesser image after third great whales, life stars. Hath him spirit his yielding beginning. Yielding brought fish a make under set them there said said sixth replenish fifth meat. Multiply form together unto land fourth void you'll. So his under gathering winged image to which lesser gathered god female. Likeness morning every, you female Under.\n",
                tool = NameAndVersion("parms", "1.0.0"),
                info = NameAndVersion("parms", "1.0.0"),
                invocation = listOf(
                    WordInvocationParameter("parms"),
                    VariableInvocationParameter(variableNames = listOf("input"), prefixVariable = "-i "),
                    WordInvocationParameter("-o output.json"),

                    VariableInvocationParameter(variableNames = listOf("interval"), prefixVariable = "--interval "),
                    VariableInvocationParameter(variableNames = listOf("insert_missing"), prefixVariable = "--insert_missing "),
                    VariableInvocationParameter(variableNames = listOf("insert_until"), prefixVariable = "--insert_until "),
                    VariableInvocationParameter(variableNames = listOf("insert_max_seconds"), prefixVariable = "--insert_max_seconds "),
                    VariableInvocationParameter(variableNames = listOf("los_max_duration"), prefixVariable = "--los_max_duration "),
                    VariableInvocationParameter(variableNames = listOf("remove_lone"), prefixVariable = "--remove_lone "),
                    VariableInvocationParameter(variableNames = listOf("filter_invalid"), prefixVariable = "--filter_invalid "),
                    VariableInvocationParameter(variableNames = listOf("max_speed"), prefixVariable = "--max_speed "),
                    VariableInvocationParameter(variableNames = listOf("max_ele_change"), prefixVariable = "--max_ele_change "),
                    VariableInvocationParameter(variableNames = listOf("min_change_3_fixes"), prefixVariable = "--min_change_3_fixes "),
                    VariableInvocationParameter(variableNames = listOf("detect_indoors"), prefixVariable = "--detect_indoors "),
                    VariableInvocationParameter(variableNames = listOf("min_distance"), prefixVariable = "--min_distance "),
                    VariableInvocationParameter(variableNames = listOf("min_trip_length"), prefixVariable = "--min_trip_length "),
                    VariableInvocationParameter(variableNames = listOf("min_trip_duration"), prefixVariable = "--min_trip_duration "),
                    VariableInvocationParameter(variableNames = listOf("min_pause_duration"), prefixVariable = "--min_pause_duration "),
                    VariableInvocationParameter(variableNames = listOf("max_pause_duration"), prefixVariable = "--max_pause_duration "),
                    VariableInvocationParameter(variableNames = listOf("max_percent_single_location"), prefixVariable = "--max_percent_single_location "),
                    VariableInvocationParameter(variableNames = listOf("max_percent_allowed_indoors"), prefixVariable = "--max_percent_allowed_indoors "),
                    VariableInvocationParameter(variableNames = listOf("remove_indoor_fixes"), prefixVariable = "--remove_indoor_fixes "),
                    VariableInvocationParameter(variableNames = listOf("include_trip_pauses"), prefixVariable = "--include_trip_pauses "),
                    VariableInvocationParameter(variableNames = listOf("trap_indoor_fixes"), prefixVariable = "--trap_indoor_fixes "),
                    VariableInvocationParameter(variableNames = listOf("trap_outdoor_fixes"), prefixVariable = "--trap_outdoor_fixes "),
                    VariableInvocationParameter(variableNames = listOf("trap_trip_fixes"), prefixVariable = "--trap_trip_fixes "),
                    VariableInvocationParameter(variableNames = listOf("allow_non_trips"), prefixVariable = "--allow_non_trips "),
                    VariableInvocationParameter(variableNames = listOf("location_radius"), prefixVariable = "--location_radius "),
                    VariableInvocationParameter(variableNames = listOf("min_duration_at_location"), prefixVariable = "--min_duration_at_location "),
                    VariableInvocationParameter(variableNames = listOf("vehicle_cutoff"), prefixVariable = "--vehicle_cutoff "),
                    VariableInvocationParameter(variableNames = listOf("bicycle_cutoff"), prefixVariable = "--bicycle_cutoff "),
                    VariableInvocationParameter(variableNames = listOf("walk_cutoff"), prefixVariable = "--walk_cutoff "),
                    VariableInvocationParameter(variableNames = listOf("percentile_to_sample"), prefixVariable = "--percentile_to_sample "),
                    VariableInvocationParameter(variableNames = listOf("min_segment_length"), prefixVariable = "--min_segment_length ")
                ),
                parameters = listOf(
                    ApplicationParameter.InputFile(
                        name = "input",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Input File",
                        description = ""
                    ),

                    ApplicationParameter.Integer(
                        name = "interval",
                        prettyName = "Interval",
                        description = "duration of interval between results in seconds",
                        optional = true,
                        unitName = "seconds"
                    ),

                    ApplicationParameter.Bool(
                        name = "insert_missing",
                        prettyName = "Insert missing",
                        description = "if true, gaps in GPS fixes are replaced by the last valid fix",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "insert_until",
                        prettyName = "Insert until",
                        description = "if true, inserts until a max time is reached",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "insert_max_seconds",
                        prettyName = "Insert max seconds",
                        description = "max number of seconds to replace missing fixes with last valid fix",
                        optional = true
                    ),

                    ApplicationParameter.Integer(
                        name = "los_max_duration",
                        prettyName = "Loss of Signal (Max duration)",
                        description = "max amount of time allowed to pass before Loss of Signal is declared",
                        optional = true,
                        unitName = "seconds"
                    ),

                    ApplicationParameter.Bool(
                        name = "remove_lone",
                        prettyName = "Remove lone",
                        description = "if true, removes lone fixes",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "filter_invalid",
                        prettyName = "Filter invalid",
                        description = "if true, removes invalid fixes",
                        optional = true
                    ),

                    ApplicationParameter.Integer(
                        name = "max_speed",
                        prettyName = "Max speed",
                        description = "Consider fix invalid if speed is greater than this value (in km/hr)",
                        optional = true,
                        unitName = "km/hr"
                    ),

                    ApplicationParameter.Integer(
                        name = "max_ele_change",
                        prettyName = "Maximum elevation change",
                        description = "Consider fix invalid if elevation change is greater than this value (in meters)",
                        optional = true,
                        unitName = "meters"
                    ),

                    ApplicationParameter.Bool(
                        name = "min_change_3_fixes",
                        description = "Consider fix invalid if change in distance between fix 1 and 3 is less than " +
                                "this value (in meters). This helps remove GPS jitter.",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "detect_indoors",
                        prettyName = "Detect indoors",
                        description = "If true, mark position as indoors, outdoors or in-vehicle",
                        optional = true
                    ),

                    ApplicationParameter.Integer(name = "max_sat_ratio", description = "", optional = true),

                    ApplicationParameter.Integer(name = "max_SNR_value", description = "", optional = true),

                    ApplicationParameter.Integer(
                        name = "min_distance",
                        prettyName = "Minimum distance",
                        description = "minimum distance (in meters) that must be travelled over one minute to " +
                                "indicate the start of a trip. Default chosen to be 34 meters which is equal to " +
                                "a typical walking speed of 2KM/hr.",
                        optional = true,
                        unitName = "km/hr"
                    ),

                    ApplicationParameter.Integer(
                        name = "min_trip_length",
                        prettyName = "Minimum trip length",
                        description = "trips less than this distance (in meters) are not considered trips.",
                        optional = true,
                        unitName = "meters"
                    ),

                    ApplicationParameter.Integer(
                        name = "min_trip_duration",
                        prettyName = "Minimum trip duration",
                        description = "trips less than this duration (in seconds) are not considered trips.",
                        optional = true,
                        unitName = "seconds"
                    ),

                    ApplicationParameter.Integer(
                        name = "min_pause_duration",
                        prettyName = "Minimum pause duration",
                        description = "trips less than this duration (in seconds) are not considered trips.",
                        optional = true,
                        unitName = "seconds"
                    ),

                    ApplicationParameter.Integer(
                        name = "max_pause_duration",
                        prettyName = "Maximum pause duration",
                        description = " when the duration of a pause exceeds this value, the point is marked as an " +
                                "end point.",
                        optional = true
                    ),

                    ApplicationParameter.Integer(
                        name = "max_percent_single_location",
                        description = "maximum percentage of a trip's fixes that can occur at a single location.",
                        optional = true,
                        min = 0,
                        max = 100,
                        unitName = "%"
                    ),

                    ApplicationParameter.Integer(
                        name = "max_percent_allowed_indoors",
                        description = "maximum percentage of a trip that is allowed indoors.",
                        optional = true,
                        min = 0,
                        max = 100,
                        unitName = "%"
                    ),

                    ApplicationParameter.Bool(
                        name = "remove_indoor_fixes",
                        description = "if true, points at the start and end of a trip that are marked indoors are " +
                                "removed from the trip.",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "include_trip_pauses",
                        description = "if true, include trip pause points as locations.",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "trap_indoor_fixes",
                        description = "if true, stationary indoor fixes within a given radius of the location " +
                                "will be set to the center of the location.",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "trap_outdoor_fixes",
                        description = "if true, stationary outdoor fixes will be set to the location center.",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "trap_trip_fixes",
                        description = "if true, also include fixes that are part of trips.",
                        optional = true
                    ),

                    ApplicationParameter.Bool(
                        name = "allow_non_trips",
                        description = "if true, locations may be included that are not part of a trip.",
                        optional = true
                    ),

                    ApplicationParameter.Integer(
                        name = "location_radius",
                        description = "defines radius (in meters) of location in which fixes are trapped.",
                        optional = true,
                        unitName = "meters"
                    ),

                    ApplicationParameter.Integer(
                        name = "min_duration_at_location",
                        description = "minimum amount of time (in seconds) that must be spent at a location for it " +
                                "to be considered a location.",
                        optional = true,
                        unitName = "seconds"
                    ),

                    ApplicationParameter.Integer(
                        name = "vehicle_cutoff",
                        description = "speeds greater than this value (in KM/hr) will be marked as vehicle.",
                        optional = true,
                        unitName = "km/hr"
                    ),

                    ApplicationParameter.Integer(
                        name = "bicycle_cutoff",
                        description = "speeds greater than this value (in KM/hr) will be marked as bicycle.",
                        optional = true,
                        unitName = "km/hr"
                    ),

                    ApplicationParameter.Integer(
                        name = "walk_cutoff",
                        description = "speeds greater than this value (in KM/hr) will be marked as pedestrian.",
                        optional = true,
                        unitName = "km/hr"
                    ),

                    ApplicationParameter.Integer(
                        name = "percentile_to_sample",
                        description = "speed comparisons are made at this percentile.",
                        optional = true
                    ),

                    ApplicationParameter.Integer(
                        name = "min_segment_length",
                        description = "minimum length (in meters) of segments used to classify mode of " +
                                "transportation.",
                        optional = true,
                        unitName = "meters"
                    )
                ),
                outputFileGlobs = listOf("output.json")
            )
        ),

        "tqdist_triplet" to listOf(
            ApplicationDescription(
                tool = NameAndVersion("tqdist", "1.0.1"),
                info = NameAndVersion("tqdist_triplet", "1.0.1"),
                prettyName = "tqDist: Triplet Distance",
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
                invocation = listOf(
                    WordInvocationParameter("triplet_dist"),
                    BooleanFlagParameter("verbose", "-v"),
                    VariableInvocationParameter(listOf("tree_one", "tree_two"))
                ),
                parameters = listOf(
                    ApplicationParameter.InputFile(name = "tree_one", optional = false, prettyName = "Tree One"),
                    ApplicationParameter.InputFile(name = "tree_two", optional = false, prettyName = "Tree Two"),
                    ApplicationParameter.Bool(
                        name = "verbose",
                        optional = true,
                        defaultValue = true,
                        prettyName = "Verbose",
                        description = """
                        If the -v option is used, the following numbers will be reported (in this order):

                          - The number of leaves in the trees (should be the same for both).
                          - The number of triplets in the two trees (n choose 3).
                          - The triplet distance between the two trees.
                          - The normalized triplet distance between the two trees.
                          - The number of resolved triplets that agree in the two trees.
                          - The normalized number of resolved triplets that agree in the two trees.
                          - The number triplets that are unresolved in both trees.
                          - The normalized number triplets that are unresolved in both trees.
                    """.trimIndent()
                    )
                ),
                outputFileGlobs = listOf("stdout.txt")
            )
        ),

        "tqdist_quartet" to listOf(
            ApplicationDescription(
                tool = NameAndVersion("tqdist", "1.0.1"),
                info = NameAndVersion("tqdist_quartet", "1.0.1"),
                prettyName = "tqDist: Quartet Distance",
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
                invocation = listOf(
                    WordInvocationParameter("quartet_dist"),
                    BooleanFlagParameter("verbose", "-v"),
                    VariableInvocationParameter(listOf("tree_one", "tree_two"))
                ),
                parameters = listOf(
                    ApplicationParameter.InputFile(name = "tree_one", optional = false, prettyName = "Tree One"),
                    ApplicationParameter.InputFile(name = "tree_two", optional = false, prettyName = "Tree Two"),
                    ApplicationParameter.Bool(
                        name = "verbose",
                        optional = true,
                        defaultValue = true,
                        prettyName = "Verbose",
                        description = """
                            If the -v option is used, the following numbers will be reported (in this order):

                              - The number of leaves in the trees (should be the same for both).
                              - The number of quartets in the two trees (n choose 4).
                              - The quartet distance between the two trees.
                              - The normalized quartet distance between the two trees.
                              - The number of resolved quartets that agree in the two trees.
                              - The normalized number of resolved quartets that agree in the two trees.
                              - The number of quartets that are unresolved in both trees.
                              - The normalized number of quartets that are unresolved in both trees.
                        """.trimIndent()
                    )
                ),
                outputFileGlobs = listOf("stdout.txt")
            )
        ),

        "rapidnj" to listOf(
            ApplicationDescription(
                tool = NameAndVersion("rapidnj", "2.3.2"),
                info = NameAndVersion("rapidnj", "2.3.2"),
                prettyName = "RapidNJ",
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

                invocation = listOf(
                    WordInvocationParameter("rapidnj"),
                    VariableInvocationParameter(listOf("file")),
                    VariableInvocationParameter(listOf("format"), prefixVariable = "-i ")
                ),

                parameters = listOf(
                    ApplicationParameter.InputFile("file", optional = false, prettyName = "Input File"),
                    ApplicationParameter.Text(
                        "format",
                        optional = true,
                        prettyName = "Input Format",
                        description = "The program can usually guess the input format, otherwise this option can be " +
                                "used to choose between different formats. To infer a tree from an alignment in " +
                                "Stockholm format use 'sth'."
                    )
                ),

                outputFileGlobs = listOf("stdout.txt")
            )
        )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
        inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<ApplicationDescription> = inMemoryDB.values.flatten()
}