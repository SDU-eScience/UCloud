import { Application } from "Applications";
import { Page } from "Types";

export const applicationsPage: Page<Application> = {
    itemsInTotal: 8,
    itemsPerPage: 25,
    pageNumber: 0,
    items: [
        {
            favorite: false,
            owner: "jonas@hinchely.dk",
            createdAt: 1531306829400,
            modifiedAt: 1531306829400,
            imageUrl: "",
            description: {
                resources: { multiNodeSupport: false },
                info: { name: "palms", version: "1.0.0" },
                tool: { name: "palms", version: "1.0.0" },
                authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                title: "PALMS",
                description: "The central role of places in which physical activity (PA) is done is now widely recognized, so it is important to measure both activity and its location. At present, other than using the very expensive method of doubly-labeled water, if a researcher wants to measure PA in free living humans the most accurate technologies are either combined heart rate and motion (HR+M) sensors or accelerometers. But these devices do not collect data on where the activity occurs. If a researcher wants to know where a person performs physical activity, this information must be collected by means of self report after the PA has occurred. More recently, strategies utilizing ecological momentary assessment (EMA) have been used to sample behavioral experiences, including physical activity, in free living humans while they occur. However, this approach depends upon time- or event-critical sampling of self-reported information and thus continues to depend on self-report from the user to enter the information. Objective measurement of walking and cycling using portable global positioning system (GPS) devices has been successful but GPS data have yet to be combined with highly accurate PA measurement in a way that can be used across settings and populations.\n",
                invocation: [
                    {
                        type: "word",
                        word: "parms"
                    },
                    {
                        type: "var",
                        variableNames: ["input"],
                        prefixGlobal: "",
                        suffixGlobal: "",
                        prefixVariable: "-i ",
                        suffixVariable: "",
                        variableSeparator: " "
                    },
                    {
                        type: "word",
                        word: "-o output.json"
                    },
                    {
                        type: "var",
                        variableNames: ["interval"],
                        prefixGlobal: "",
                        suffixGlobal: "",
                        prefixVariable: "--interval ",
                        suffixVariable: "",
                        variableSeparator: " "
                    },
                    { type: "var", variableNames: ["insert_missing"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--insert_missing ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["insert_until"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--insert_until ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["insert_max_seconds"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--insert_max_seconds ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["los_max_duration"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--los_max_duration ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["remove_lone"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--remove_lone ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["filter_invalid"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--filter_invalid ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["max_speed"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--max_speed ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["max_ele_change"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--max_ele_change ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_change_3_fixes"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_change_3_fixes ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["detect_indoors"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--detect_indoors ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_distance"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_distance ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_trip_length"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_trip_length ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_trip_duration"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_trip_duration ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_pause_duration"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_pause_duration ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["max_pause_duration"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--max_pause_duration ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["max_percent_single_location"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--max_percent_single_location ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["max_percent_allowed_indoors"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--max_percent_allowed_indoors ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["remove_indoor_fixes"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--remove_indoor_fixes ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["include_trip_pauses"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--include_trip_pauses ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["trap_indoor_fixes"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--trap_indoor_fixes ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["trap_outdoor_fixes"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--trap_outdoor_fixes ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["trap_trip_fixes"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--trap_trip_fixes ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["allow_non_trips"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--allow_non_trips ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["location_radius"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--location_radius ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_duration_at_location"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_duration_at_location ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["vehicle_cutoff"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--vehicle_cutoff ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["bicycle_cutoff"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--bicycle_cutoff ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["walk_cutoff"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--walk_cutoff ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["percentile_to_sample"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--percentile_to_sample ", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["min_segment_length"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "--min_segment_length ", suffixVariable: "", variableSeparator: " " }
                ],
                parameters: [
                    {
                        name: "input",
                        optional: false,
                        defaultValue: null,
                        title: "Input File",
                        description: "",
                        type: "input_file"
                    },
                    {
                        name: "interval",
                        optional: true,
                        defaultValue: null,
                        title: "Interval",
                        description: "duration of interval between results in seconds",
                        min: null,
                        max: null,
                        step: null,
                        unitName: "seconds",
                        type: "integer"
                    },
                    {
                        name: "insert_missing",
                        optional: true,
                        defaultValue: null,
                        title: "Insert missing",
                        description: "if true, gaps in GPS fixes are replaced by the last valid fix",
                        trueValue: "true",
                        falseValue: "false",
                        type: "boolean"
                    },
                    {
                        name: "insert_until",
                        optional: true,
                        defaultValue: null,
                        title: "Insert until",
                        description: "if true, inserts until a max time is reached",
                        trueValue: "true",
                        falseValue: "false",
                        type: "boolean"
                    },
                    {
                        name: "insert_max_seconds", optional: true, defaultValue: null, title: "Insert max seconds", description: "max number of seconds to replace missing fixes with last valid fix", "trueValue": "true", "falseValue": "false", "type": "boolean"
                    },
                    {
                        name: "los_max_duration", optional: true, defaultValue: null, title: "Loss of Signal (Max duration)", description: "max amount of time allowed to pass before Loss of Signal is declared", "min": null, "max": null, "step": null, "unitName": "seconds", "type": "integer"
                    }, { name: "remove_lone", "optional": true, defaultValue: null, "title": "Remove lone", "description": "if true, removes lone fixes", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "filter_invalid", "optional": true, "defaultValue": null, "title": "Filter invalid", "description": "if true, removes invalid fixes", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "max_speed", "optional": true, "defaultValue": null, "title": "Max speed", "description": "Consider fix invalid if speed is greater than this value (in km/hr)", "min": null, "max": null, "step": null, "unitName": "km/hr", "type": "integer" }, { "name": "max_ele_change", "optional": true, "defaultValue": null, "title": "Maximum elevation change", "description": "Consider fix invalid if elevation change is greater than this value (in meters)", "min": null, "max": null, "step": null, "unitName": "meters", "type": "integer" }, { "name": "min_change_3_fixes", "optional": true, "defaultValue": null, "title": "min_change_3_fixes", "description": "Consider fix invalid if change in distance between fix 1 and 3 is less than this value (in meters). This helps remove GPS jitter.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "detect_indoors", "optional": true, "defaultValue": null, "title": "Detect indoors", "description": "If true, mark position as indoors, outdoors or in-vehicle", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "max_sat_ratio", "optional": true, "defaultValue": null, "title": "max_sat_ratio", "description": "", "min": null, "max": null, "step": null, "unitName": null, "type": "integer" }, { "name": "max_SNR_value", "optional": true, "defaultValue": null, "title": "max_SNR_value", "description": "", "min": null, "max": null, "step": null, "unitName": null, "type": "integer" }, { "name": "min_distance", "optional": true, "defaultValue": null, "title": "Minimum distance", "description": "minimum distance (in meters) that must be travelled over one minute to indicate the start of a trip. Default chosen to be 34 meters which is equal to a typical walking speed of 2KM/hr.", "min": null, "max": null, "step": null, "unitName": "km/hr", "type": "integer" }, { "name": "min_trip_length", "optional": true, "defaultValue": null, "title": "Minimum trip length", "description": "trips less than this distance (in meters) are not considered trips.", "min": null, "max": null, "step": null, "unitName": "meters", "type": "integer" }, { "name": "min_trip_duration", "optional": true, "defaultValue": null, "title": "Minimum trip duration", "description": "trips less than this duration (in seconds) are not considered trips.", "min": null, "max": null, "step": null, "unitName": "seconds", "type": "integer" }, { "name": "min_pause_duration", "optional": true, "defaultValue": null, "title": "Minimum pause duration", "description": "trips less than this duration (in seconds) are not considered trips.", "min": null, "max": null, "step": null, "unitName": "seconds", "type": "integer" }, { "name": "max_pause_duration", "optional": true, "defaultValue": null, "title": "Maximum pause duration", "description": " when the duration of a pause exceeds this value, the point is marked as an end point.", "min": null, "max": null, "step": null, "unitName": null, "type": "integer" }, { "name": "max_percent_single_location", "optional": true, "defaultValue": null, "title": "max_percent_single_location", "description": "maximum percentage of a trip's fixes that can occur at a single location.", "min": 0, "max": 100, "step": null, "unitName": "%", "type": "integer" }, { "name": "max_percent_allowed_indoors", "optional": true, "defaultValue": null, "title": "max_percent_allowed_indoors", "description": "maximum percentage of a trip that is allowed indoors.", "min": 0, "max": 100, "step": null, "unitName": "%", "type": "integer" }, { "name": "remove_indoor_fixes", "optional": true, "defaultValue": null, "title": "remove_indoor_fixes", "description": "if true, points at the start and end of a trip that are marked indoors are removed from the trip.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "include_trip_pauses", "optional": true, "defaultValue": null, "title": "include_trip_pauses", "description": "if true, include trip pause points as locations.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "trap_indoor_fixes", "optional": true, "defaultValue": null, "title": "trap_indoor_fixes", "description": "if true, stationary indoor fixes within a given radius of the location will be set to the center of the location.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "trap_outdoor_fixes", "optional": true, "defaultValue": null, "title": "trap_outdoor_fixes", "description": "if true, stationary outdoor fixes will be set to the location center.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "trap_trip_fixes", "optional": true, "defaultValue": null, "title": "trap_trip_fixes", "description": "if true, also include fixes that are part of trips.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "allow_non_trips", "optional": true, "defaultValue": null, "title": "allow_non_trips", "description": "if true, locations may be included that are not part of a trip.", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "location_radius", "optional": true, "defaultValue": null, "title": "location_radius", "description": "defines radius (in meters) of location in which fixes are trapped.", "min": null, "max": null, "step": null, "unitName": "meters", "type": "integer" }, { "name": "min_duration_at_location", "optional": true, "defaultValue": null, "title": "min_duration_at_location", "description": "minimum amount of time (in seconds) that must be spent at a location for it to be considered a location.", "min": null, "max": null, "step": null, "unitName": "seconds", "type": "integer" }, { "name": "vehicle_cutoff", "optional": true, "defaultValue": null, "title": "vehicle_cutoff", "description": "speeds greater than this value (in KM/hr) will be marked as vehicle.", "min": null, "max": null, "step": null, "unitName": "km/hr", "type": "integer" }, { "name": "bicycle_cutoff", "optional": true, "defaultValue": null, "title": "bicycle_cutoff", "description": "speeds greater than this value (in KM/hr) will be marked as bicycle.", "min": null, "max": null, "step": null, "unitName": "km/hr", "type": "integer" }, { "name": "walk_cutoff", "optional": true, "defaultValue": null, "title": "walk_cutoff", "description": "speeds greater than this value (in KM/hr) will be marked as pedestrian.", "min": null, "max": null, "step": null, "unitName": "km/hr", "type": "integer" }, { "name": "percentile_to_sample", "optional": true, "defaultValue": null, "title": "percentile_to_sample", "description": "speed comparisons are made at this percentile.", "min": null, "max": null, "step": null, "unitName": null, "type": "integer" }, { "name": "min_segment_length", "optional": true, "defaultValue": null, "title": "min_segment_length", "description": "minimum length (in meters) of segments used to classify mode of transportation.", "min": null, "max": null, "step": null, "unitName": "meters", "type": "integer" }],
                outputFileGlobs: ["stdout.txt", "stderr.txt"]
            },
            tool: {
                owner: "jonas@hinchely.dk",
                createdAt: 1531304428595,
                modifiedAt: 1531304428595,
                description: {
                    info: {
                        name: "palms",
                        version: "1.0.0"
                    },
                    container: "parms.simg",
                    defaultNumberOfNodes: 1,
                    defaultTasksPerNode: 1,
                    defaultMaxTime: {
                        hours: 0,
                        minutes: 10,
                        seconds: 0
                    },
                    requiredModules: [],
                    authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                    title: "PALMS",
                    description: "Tools for running PALMS",
                    backend: "SINGULARITY"
                }
            }
        }, {
            imageUrl: "",
            favorite: false,
            owner: "jonas@hinchely.dk", createdAt: 1531308013745, modifiedAt: 1531308013745, description: {
                resources: { multiNodeSupport: false },
                info: { name: "bwa-mem", version: "1.0.0" }, tool: { name: "bwa-sambamba", version: "3.3.0" },
                authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                title: "BWA-MEM", description: "BWA-MEM", invocation: [{ type: "word", word: "bwa-mem" }, { type: "var", variableNames: ["index_base_main"], prefixGlobal: "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }, { "type": "var", "variableNames": ["R1"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }, { "type": "var", "variableNames": ["R2"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }], "parameters": [{ "name": "index_base_main", "optional": false, "defaultValue": null, "title": "Index Resources", "description": "", "type": "input_file" }, { "name": "base_dict", "optional": false, "defaultValue": null, "title": ".fasta.dict", "description": "", "type": "input_file" }, { "name": "base_amb", "optional": false, "defaultValue": null, "title": ".fasta.amb", "description": "", "type": "input_file" }, { "name": "base_ann", "optional": false, "defaultValue": null, "title": ".fasta.ann", "description": "", "type": "input_file" }, { "name": "base_bwt", "optional": false, "defaultValue": null, "title": ".fasta.bwt", "description": "", "type": "input_file" }, { "name": "base_fai", "optional": false, "defaultValue": null, "title": ".fasta.fai", "description": "", "type": "input_file" }, { "name": "base_pac", "optional": false, "defaultValue": null, "title": ".fasta.pac", "description": "", "type": "input_file" }, { "name": "base_sa", "optional": false, "defaultValue": null, "title": ".fasta.sa", "description": "", "type": "input_file" }, { "name": "R1", "optional": false, "defaultValue": null, "title": "R1", "description": "", "type": "input_file" }, { "name": "R2", "optional": false, "defaultValue": null, "title": "R2", "description": "", "type": "input_file" }], "outputFileGlobs": ["sample.bam*", "stdout.txt", "stderr.txt"]
            }, tool: { owner: "jonas@hinchely.dk", createdAt: 1531304635371, modifiedAt: 1531304635371, description: { info: { name: "bwa-sambamba", version: "3.3.0" }, container: "bwa-sambamba", defaultNumberOfNodes: 1, defaultTasksPerNode: 1, defaultMaxTime: { hours: 1, minutes: 0, seconds: 0 }, requiredModules: [], authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"], title: "bwa-sambamba", description: "Tools for running bwa-sambamba", backend: "UDOCKER" } }
        }, {
            imageUrl: "",
            favorite: false,
            owner: "jonas@hinchely.dk", createdAt: 1531305600165, modifiedAt: 1531305600165, description: {
                resources: { multiNodeSupport: false },
                info: { name: "figlet-count", version: "1.0.0" },
                tool: { name: "figlet", version: "1.0.0" },
                authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                title: "Figlet Counter",
                description: "Count with Figlet!\n",
                invocation: [{ "type": "word", "word": "figlet-count" }, { "type": "var", "variableNames": ["n"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }],
                parameters: [{ name: "n", optional: false, defaultValue: { value: 100, type: "int" }, title: "Count", description: "How much should we count to?", min: null, "max": null, "step": null, "unitName": null, "type": "integer" }], "outputFileGlobs": ["stdout.txt", "stderr.txt"]
            },
            tool: {
                owner: "jonas@hinchely.dk",
                createdAt: 1531303905548,
                modifiedAt: 1531303905548,
                description: {
                    info: { name: "figlet", version: "1.0.0" },
                    container: "figlet.simg",
                    defaultNumberOfNodes: 1,
                    defaultTasksPerNode: 1,
                    defaultMaxTime: { hours: 0, minutes: 1, seconds: 0 },
                    requiredModules: [],
                    authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                    title: "Figlet",
                    description: "Tool for rendering text.", "backend": "SINGULARITY"
                }
            }
        }, {
            favorite: false,
            imageUrl: "",
            owner: "jonas@hinchely.dk", createdAt: 1531307297437, modifiedAt: 1531307297437, description: {
                resources: { multiNodeSupport: false },
                info: { "name": "tqdist_triplet", "version": "1.0.0" },
                tool: { "name": "tqdist", "version": "1.0.0" },
                authors: ["Andreas Sand", "Morten K. Holt", "Jens Johansen", "Gerth StÃ¸lting Brodal", "Thomas Mailund", "Christian N.S. Pedersen"], "title": "tqDist: Triplet Distance", "description": "Distance measures between trees are useful for comparing trees in a systematic manner and several different distance measures have been proposed. The triplet and quartet distances, for rooted and unrooted trees, are defined as the number of subsets of three or four leaves, respectively, where the topologies of the induced sub-trees differ. These distances can trivially be computed by explicitly enumerating all sets of three or four leaves and testing if the topologies are different, but this leads to time complexities at least of the order n^3 or n^4 just for enumerating the sets. The different topologies can be counted implicitly, however, and using this tqDist computes the triplet distance between rooted trees in O(n log n) time and the quartet distance between unrooted trees in O(dn log n) time, where d degree of the tree with the smallest degree.\n", "invocation": [{ "type": "word", "word": "triplet_dist" }, { "type": "bool_flag", "variableName": "verbose", "flag": "-v" }, { "type": "var", "variableNames": ["tree_one", "tree_two"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }], "parameters": [{ "name": "verbose", "optional": false, "defaultValue": null, "title": "Verbose", "description": "If the -v option is used, the following numbers will be reported  (in this order):\n- The number of leaves in the trees (should be the same for both). - The number of triplets in the two trees (n choose 3). - The triplet distance between the two trees. - The normalized triplet distance between the two trees. - The number of resolved triplets that agree in the two trees. - The normalized number of resolved triplets that agree in the two trees. - The number triplets that are unresolved in both trees. - The normalized number triplets that are unresolved in both trees.\n", "trueValue": "true", "falseValue": "false", "type": "boolean" }, { "name": "tree_one", "optional": false, "defaultValue": null, "title": "Tree One", "description": "", "type": "input_file" }, { "name": "tree_two", "optional": false, "defaultValue": null, "title": "Tree Two", "description": "", "type": "input_file" }], "outputFileGlobs": ["stdout.txt", "stderr.txt"]
            }, tool: { owner: "jonas@hinchely.dk", createdAt: 1531304469178, modifiedAt: 1531304469178, description: { info: { "name": "tqdist", "version": "1.0.0" }, "container": "tqdist.simg", defaultNumberOfNodes: 1, defaultTasksPerNode: 1, defaultMaxTime: { hours: 0, minutes: 10, seconds: 0 }, requiredModules: [], authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"], "title": "tqDist", "description": "Tools for running tqDist", "backend": "SINGULARITY" } }
        }, {
            favorite: false,
            imageUrl: "",
            owner: "jonas@hinchely.dk",
            createdAt: 1531305389134,
            modifiedAt: 1531305389134,
            description: {
                resources: { multiNodeSupport: false },
                info: { name: "figlet", version: "1.0.0" },
                tool: { name: "figlet", version: "1.0.0" },
                authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                title: "Figlet", description: "Render some text!\n",
                invocation: [
                    { type: "word", word: "figlet" },
                    { type: "var", variableNames: ["text"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "", suffixVariable: "", variableSeparator: " " }
                ],
                parameters: [
                    {
                        name: "text",
                        optional: false, defaultValue: null, title: "Some text to render with figlet", description: "", type: "text"
                    }],
                outputFileGlobs: ["stdout.txt", "stderr.txt"]
            },
            tool: {
                owner: "jonas@hinchely.dk",
                createdAt: 1531303905548,
                modifiedAt: 1531303905548,
                description: {
                    info: { name: "figlet", version: "1.0.0" },
                    container: "figlet.simg",
                    defaultNumberOfNodes: 1,
                    defaultTasksPerNode: 1,
                    defaultMaxTime: { hours: 0, minutes: 1, seconds: 0 },
                    requiredModules: [],
                    authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                    title: "Figlet",
                    description: "Tool for rendering text.",
                    backend: "SINGULARITY"
                }
            }
        }, {
            favorite: false,
            imageUrl: "",
            owner: "jonas@hinchely.dk", createdAt: 1531307717715, modifiedAt: 1531307717715, description: {
                resources: { multiNodeSupport: false },
                info: { name: "rapidnj", version: "2.3.2" },
                tool: { name: "rapidnj", version: "2.3.2" },
                authors: ["Martin Simonsen", "Thomas Mailund", "Christian N. S. Pedersen"],
                title: "RapidNJ", description: "RapidNJ is an algorithmic engineered implementation of canonical neighbour-joining. It uses an efficient search heuristic to speed-up the core computations of the neighbour-joining method that enables RapidNJ to outperform other state-of-the-art neighbour-joining implementations\n",
                invocation: [
                    { type: "word", word: "rapidnj" },
                    { type: "var", variableNames: ["file"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "", suffixVariable: "", variableSeparator: " " },
                    { type: "var", variableNames: ["format"], prefixGlobal: "", suffixGlobal: "", prefixVariable: "-i ", suffixVariable: "", variableSeparator: " " }],
                parameters: [{ name: "file", optional: false, defaultValue: null, title: "Input File", description: "", type: "input_file" }, { name: "format", optional: true, defaultValue: null, title: "Input Format", description: "The program can usually guess the input format, otherwise this option can be used to choose between different formats. To infer a tree from an alignment in Stockholm format use 'sth'.", "type": "text" }], "outputFileGlobs": ["stdout.txt", "stderr.txt"]
            }, tool: { owner: "jonas@hinchely.dk", createdAt: 1531304528792, modifiedAt: 1531304528792, description: { info: { name: "rapidnj", version: "2.3.2" }, container: "rapidnj.simg", defaultNumberOfNodes: 1, defaultTasksPerNode: 1, defaultMaxTime: { hours: 0, minutes: 10, seconds: 0 }, requiredModules: [], authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"], title: "RapidNJ", description: "Tools for running rapidnj", "backend": "SINGULARITY" } }
        }, {
            favorite: false,
            imageUrl: "",
            owner: "jonas@hinchely.dk",
            createdAt: 1531304975016,
            modifiedAt: 1531304975016,
            description: {
                resources: { multiNodeSupport: false },
                info: { name: "searchgui_msgf", version: "3.3.0" },
                tool: { name: "searchgui", version: "3.3.0" },
                authors: ["Vaudel M", "Barsnes H", "Berven FS", "Sickmann A", "Martens L."],
                title: "SearchCLI: MS-GF+",
                description: "SearchGUI is a user-friendly open-source graphical user interface for configuring and running proteomics identification search engines and de novo sequencing algorithms, currently supporting  [X! Tandem](http://www.thegpm.org/tandem), [MS-GF+](http://www.ncbi.nlm.nih.gov/pubmed/?term=25358478),  [MS Amanda](http://ms.imp.ac.at/?goto#msamanda), [MyriMatch](http://www.ncbi.nlm.nih.gov/pubmed/?term=17269722),  [Comet](http://comet-ms.sourceforge.net/), [Tide](http://cruxtoolkit.sourceforge.net),  [Andromeda](http://www.coxdocs.org/doku.php?id=maxquant:andromeda:start), [OMSSA](http://www.ncbi.nlm.nih.gov/pubmed/15473683),  [Novor](http://rapidnovor.com) and  [DirecTag](http://fenchurch.mc.vanderbilt.edu/bumbershoot/directag/).\n",
                invocation: [{ "type": "word", "word": "java" }, { "type": "word", "word": "-Xmx4G" }, { "type": "word", "word": "-cp" }, { "type": "word", "word": "/opt/sgui/SearchGUI-3.2.20.jar" }, { "type": "word", "word": "eu.isas.searchgui.cmd.SearchCLI" }, { "type": "word", "word": "-spectrum_files" }, { "type": "word", "word": "./" }, { "type": "word", "word": "-output_folder" }, { "type": "word", "word": "./" }, { "type": "word", "word": "-xtandem" }, { "type": "word", "word": "0" }, { "type": "word", "word": "-msgf" }, { "type": "word", "word": "1" }, { "type": "word", "word": "-comet" }, { "type": "word", "word": "0" }, { "type": "word", "word": "-myrimatch" }, { "type": "word", "word": "0" }, { "type": "word", "word": "-omssa" }, { "type": "word", "word": "0" }, { "type": "word", "word": "-tide" }, { "type": "word", "word": "0" }, { "type": "word", "word": "-andromeda" }, { "type": "word", "word": "0" }, { "type": "var", "variableNames": ["threads"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "-threads ", "suffixVariable": "", "variableSeparator": " " }, { "type": "var", "variableNames": ["id_params"], prefixGlobal: "", "suffixGlobal": "", "prefixVariable": "-id_params ", "suffixVariable": "", "variableSeparator": " " }],
                parameters: [{ "name": "id_params", "optional": false, "defaultValue": null, "title": ".par file", "description": "", "type": "input_file" }, { "name": "fasta", "optional": false, "defaultValue": null, "title": ".fasta file", "description": "", "type": "input_file" }, { "name": "mgf", "optional": false, "defaultValue": null, "title": ".mgf file", "description": "", "type": "input_file" }, { "name": "threads", "optional": false, "defaultValue": null, "title": "Number of threads", "description": "", "min": null, "max": null, "step": null, "unitName": null, "type": "integer" }],
                outputFileGlobs: ["*.html", "*.zip", "stdout.txt", "stderr.txt"]
            }, tool: {
                owner: "jonas@hinchely.dk",
                createdAt: 1531304587935,
                modifiedAt: 1531304587935,
                description: {
                    info: { name: "searchgui", version: "3.3.0" },
                    container: "sgui",
                    defaultNumberOfNodes: 1,
                    defaultTasksPerNode: 1,
                    defaultMaxTime: { hours: 3, minutes: 0, seconds: 0 },
                    requiredModules: [],
                    authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                    title: "SearchGUI",
                    description: "Tools for running SearchGUI",
                    backend: "UDOCKER"
                }
            }
        }, {
            favorite: false,
            imageUrl: "",
            owner: "jonas@hinchely.dk", createdAt: 1531307353519, modifiedAt: 1531307353519, description: {
                resources: { multiNodeSupport: false },
                info: { name: "tqdist_quartet", version: "1.0.0" },
                tool: { name: "tqdist", version: "1.0.0" },
                authors: ["Andreas Sand", "Morten K. Holt", "Jens Johansen", "Gerth StÃ¸lting Brodal", "Thomas Mailund", "Christian N.S. Pedersen"],
                title: "tqDist: Quartet Distance", description: "Distance measures between trees are useful for comparing trees in a systematic manner and several different distance measures have been proposed. The triplet and quartet distances, for rooted and unrooted trees, are defined as the number of subsets of three or four leaves, respectively, where the topologies of the induced sub-trees differ. These distances can trivially be computed by explicitly enumerating all sets of three or four leaves and testing if the topologies are different, but this leads to time complexities at least of the order n^3 or n^4 just for enumerating the sets. The different topologies can be counted implicitly, however, and using this tqDist computes the triplet distance between rooted trees in O(n log n) time and the quartet distance between unrooted trees in O(dn log n) time, where d degree of the tree with the smallest degree.\n", "invocation": [{ "type": "word", "word": "quartet_dist" }, { "type": "bool_flag", "variableName": "verbose", "flag": "-v" }, { "type": "var", "variableNames": ["tree_one", "tree_two"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }], "parameters": [{ "name": "verbose", "optional": false, "defaultValue": null, "title": "Verbose", "description": "If the -v option is used, the following numbers will be reported  (in this order):\n- The number of leaves in the trees (should be the same for both). - The number of triplets in the two trees (n choose 3). - The triplet distance between the two trees. - The normalized triplet distance between the two trees. - The number of resolved triplets that agree in the two trees. - The normalized number of resolved triplets that agree in the two trees. - The number triplets that are unresolved in both trees. - The normalized number triplets that are unresolved in both trees.\n", trueValue: "true", falseValue: "false", type: "boolean" }, { name: "tree_one", optional: false, defaultValue: null, title: "Tree One", description: "", type: "input_file" }, { name: "tree_two", optional: false, defaultValue: null, title: "Tree Two", description: "", type: "input_file" }], outputFileGlobs: ["stdout.txt", "stderr.txt"]
            }, tool: {
                owner: "jonas@hinchely.dk",
                createdAt: 1531304469178,
                modifiedAt: 1531304469178,
                description: {
                    info: { name: "tqdist", version: "1.0.0" },
                    container: "tqdist.simg", defaultNumberOfNodes: 1, defaultTasksPerNode: 1, defaultMaxTime: { hours: 0, minutes: 10, seconds: 0 }, requiredModules: [], authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"], title: "tqDist", description: "Tools for running tqDist", backend: "SINGULARITY"
                }
            }
        }],
    pagesInTotal: 0
};

export const detailedApplication = {
    owner: "jonas@hinchely.dk",
    createdAt: 1531305600165,
    modifiedAt: 1531305600165,
    description: {
        info: {
            name: "figlet-count",
            version: "1.0.0"
        },
        tool: {
            name: "figlet",
            version: "1.0.0"
        },
        authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
        title: "Figlet Counter",
        description: "Count with Figlet!\n",
        invocation: [
            { "type": "word", "word": "figlet-count" },
            { "type": "var", "variableNames": ["n"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }
        ],
        parameters: [
            { "name": "n", "optional": false, "defaultValue": 100, "title": "Count", "description": "How much should we count to?", "min": null, "max": null, "step": null, "unitName": null, "type": "integer" }
        ],
        outputFileGlobs: ["stdout.txt", "stderr.txt"]
    },
    tool: {
        owner: "jonas@hinchely.dk",
        createdAt: 1531303905548,
        modifiedAt: 1531303905548,
        description: {
            info: {
                name: "figlet",
                version: "1.0.0"
            },
            container: "figlet.simg",
            defaultNumberOfNodes: 1,
            defaultTasksPerNode: 1,
            defaultMaxTime: {
                hours: 0,
                minutes: 1,
                seconds: 0
            },
            requiredModules: [],
            authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
            title: "Figlet",
            description: "Tool for rendering text.",
            backend: "SINGULARITY"
        }
    }
}

test("Error silencer", () =>
    expect(1).toBe(1)
);