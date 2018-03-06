package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.NameAndVersion

object ApplicationDAO {
    val inMemoryDB = mutableMapOf(
        "figlet" to listOf(
            ApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Figlet",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                tool = NameAndVersion("figlet", "1.0.0"),
                info = NameAndVersion("figlet", "1.0.0"),
                invocation = listOf(VariableInvocationParameter(listOf("text"))),
                parameters = listOf(
                    ApplicationParameter.Text(
                        name = "text",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Text",
                        description = "Some text to render with figlet"
                    )
                ),
                outputFileGlobs = listOf("output.txt")
            )
        ),

        "parms" to listOf(
            ApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Parms",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Parms",
                tool = NameAndVersion("parms", "1.0.0"),
                info = NameAndVersion("parms", "1.0.0"),
                invocation = listOf(
                    VariableInvocationParameter(variableNames = listOf("input"), prefixVariable = "-i "),
                    WordInvocationParameter("-o output.json")
                ),
                parameters = listOf(
                    ApplicationParameter.InputFile(
                        name = "input",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Input File",
                        description = ""
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "interval",
                        prettyName = "Interval",
                        description = "duration of interval between results in seconds",
                        optional = true
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

                    ApplicationParameter.FloatingPoint(
                        name = "los_max_duration",
                        prettyName = "Loss of Signal (Max duration)",
                        description = "max amount of time allowed to pass before Loss of Signal is declared",
                        optional = true
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

                    ApplicationParameter.FloatingPoint(
                        name = "max_speed",
                        prettyName = "Max speed",
                        description = "Consider fix invalid if speed is greater than this value (in km/hr)",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "max_ele_change",
                        prettyName = "Maximum elevation change",
                        description = "Consider fix invalid if elevation change is greater than this value (in meters)",
                        optional = true
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

                    ApplicationParameter.FloatingPoint(name = "max_sat_ratio", description = "", optional = true),

                    ApplicationParameter.FloatingPoint(name = "max_SNR_value", description = "", optional = true),

                    ApplicationParameter.FloatingPoint(
                        name = "min_distance",
                        prettyName = "Minimum distance",
                        description = "minimum distance (in meters) that must be travelled over one minute to " +
                                "indicate the start of a trip. Default choosen to be 34 meters which is equal to " +
                                "a typical walking speed of 2KM/hr.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "min_trip_length",
                        prettyName = "Minimum trip length",
                        description = "trips less than this distance (in meters) are not considered trips.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "min_trip_duration",
                        prettyName = "Minimum trip duration",
                        description = "trips less than this duration (in seconds) are not considered trips.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "min_pause_duration",
                        prettyName = "Minimum pause duration",
                        description = "trips less than this duration (in seconds) are not considered trips.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "max_pause_duration",
                        prettyName = "Maximum pause duration",
                        description = " when the duration of a pause exceeds this value, the point is marked as an " +
                                "end point.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "max_percent_single_location",
                        description = "maximum percentage of a trip's fixes that can occur at a single location.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "max_percent_allowed_indoors",
                        description = "maximum percentage of a trip that is allowed indoors.",
                        optional = true
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

                    ApplicationParameter.FloatingPoint(
                        name = "location_radius",
                        description = "defines radius (in meters) of location in which fixes are trapped.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "min_duration_at_location",
                        description = "minimum amount of time (in seconds) that must be spent at a location for it " +
                                "to be considered a location.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "vehicle_cutoff",
                        description = "speeds greater than this value (in KM/hr) will be marked as vehicle.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "bicycle_cutoff",
                        description = "speeds greater than this value (in KM/hr) will be marked as bicycle.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "walk_cutoff",
                        description = "speeds greater than this value (in KM/hr) will be marked as pedestrian.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "percentile_to_sample",
                        description = "speed comparisons are made at this percentile.",
                        optional = true
                    ),

                    ApplicationParameter.FloatingPoint(
                        name = "min_segment_length",
                        description = "minimum length (in meters) of segments used to classify mode of " +
                                "transportation.",
                        optional = true
                    )
                ),
                outputFileGlobs = listOf("output.json")
            )
        )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
        inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<ApplicationDescription> = inMemoryDB.values.flatten()
}