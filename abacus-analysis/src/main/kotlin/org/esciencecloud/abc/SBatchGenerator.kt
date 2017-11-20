package org.esciencecloud.abc

import com.google.common.escape.Escapers

class SBatchGenerator(private val emailForNotifications: String) {
    // We use double-quoted string for the built-in escaping provided by Bash. On top of this we simply need to
    // make sure that user input doesn't escape the string. We should also make sure that variables provided in Bash
    // are not accessible.
    private val bashEscaper =
            Escapers.builder().apply {
                addEscape('\'', "'\"'\"'")
                addEscape('\"', "\\\"")
                addEscape('`', "\\`")
                addEscape('$', "\\$")
            }.build()

    private val compiler = TemplateParser()

    private val TIME_REGEX = Regex("(((\\d{0,2}):)?((\\d{0,2}):))?(\\d{0,2})")

    private fun safeBashArgument(rawArgument: String) = "\"${bashEscaper.escape(rawArgument)}\""

    fun generate(description: ApplicationDescription, parameters: Map<String, Any>, workDir: String) {
        val tool = ToolDAO.findByNameAndVerison(description.tool.name, description.tool.version)!!

        val groupedAppParameters = description.parameters.groupBy { it.name }.mapValues { it.value.single() }
        val actualParameters = groupedAppParameters.mapValues {
            it.value.map(parameters[it.key]!!) // TODO Optional parameters
        }

        fun nodeToString(node: TemplateNode): String = when (node) {
            is WordNode -> node.text
            is VariableNode -> actualParameters[node.parameterName]!!.toString()
            is MixedNode -> node.nodes.joinToString("") { nodeToString(it) }
        }

        fun evaluateTemplate(template: String): String =
                compiler.parseSingleLineTemplate(template).joinToString(" ") { nodeToString(it) }

        fun evaluateTemplateAsBash(template: String): String =
                compiler.parseSingleLineTemplate(template).joinToString(" ") { safeBashArgument(nodeToString(it)) }

        val numberOfNodes = if (description.numberOfNodes != null) {
            evaluateTemplate(description.numberOfNodes).toIntOrNull() ?:
                    throw IllegalStateException("While generation sbatch script - Could not parse number " +
                            "of nodes, value is not integer")
        } else {
            tool.defaultNumberOfNodes
        }

        val tasksPerNode = if (description.tasksPerNode != null) {
            evaluateTemplate(description.tasksPerNode).toLongOrNull() ?:
                    throw IllegalStateException("While generation sbatch script - Could not parse tasks " +
                            "per node, value is not integer")
        } else {
            tool.defaultTasksPerNode
        }

        val maxTime = if (description.maxTime != null) {
            val asString = evaluateTemplate(description.maxTime)
            val match = TIME_REGEX.matchEntire(asString) ?:
                    throw IllegalStateException("Time is not in a valid format. Format is: {HH}:{MM}:{SS}")

            val hoursAsString = match.groupValues[3]
            val minutesAsString = match.groupValues[5]
            val secondsAsString = match.groupValues[6]

            // No need to throw custom exceptions for toInt, should already have been checked with regex
            val hours = if (hoursAsString.isNotEmpty()) hoursAsString.toInt(radix = 10) else 0
            val minutes = if (minutesAsString.isNotEmpty()) minutesAsString.toInt(radix = 10) else 0
            val seconds = if (secondsAsString.isNotEmpty()) secondsAsString.toInt(radix = 10) else 0

            SimpleDuration(hours, minutes, seconds)
        } else {
            tool.defaultMaxTime
        }

        val invocation = evaluateTemplateAsBash(description.invocationTemplate)

        // We should validate at tool level as well, but we do it here as well, just in case
        val modules = tool.requiredModules.joinToString("\n") {
            "module add ${safeBashArgument(it)}"
        }

        // Note: The email is trusted and as a result not escaped

        //
        //
        //
        // NOTE: ALL USER INPUT _MUST_ GO THROUGH SANITIZATION (use safeBashArgument). OTHERWISE USERS WILL BE ABLE
        // TO RUN CODE AS THE ESCIENCE ACCOUNT (AND ACCESS FILES FROM OTHER PROJECTS!)
        //
        //
        //
        val batchJob = """
            #!/bin/bash
            #SBATCH --account sduescience_slim
            #SBATCH --nodes $numberOfNodes
            #SBATCH --ntasks-per-node $tasksPerNode
            #SBATCH --time $maxTime
            #SBATCH --mail-type=ALL
            #SBATCH --mail-user=$emailForNotifications

            module add singularity
            $modules

            srun singularity run -C -H ${safeBashArgument(workDir)} ${safeBashArgument(tool.container)} $invocation
            """.trimIndent()

        println(batchJob)
    }
}

sealed class TemplateNode
data class WordNode(val text: String) : TemplateNode()
data class VariableNode(val parameterName: String) : TemplateNode()
data class MixedNode(val nodes: List<TemplateNode>) : TemplateNode()

class TemplateParser {
    fun parseSingleLineTemplate(inputTemplate: String): ArrayList<TemplateNode> {
        val template = inputTemplate.lines().singleOrNull() ?: throw IllegalArgumentException("Multiple lines in template")
        val output = ArrayList<TemplateNode>()
        var state = 0

        val nodes = ArrayList<TemplateNode>()
        val builder = StringBuilder()

        fun completeToken() {
            when {
                nodes.isEmpty() -> throw IllegalStateException("Need at least one node before completing token")
                nodes.size == 1 -> output.add(nodes[0])
                else -> output.add(MixedNode(nodes.toList()))
            }

            state = 0
            builder.setLength(0)
            nodes.clear()
        }

        template.forEach { char ->
            when (state) {
                0 -> { // Parse word
                    when {
                        char.isWhitespace() -> {
                            nodes.add(WordNode(builder.toString()))
                            completeToken()
                        }

                        char == '$' -> state = 1
                        char == '\\' -> state = 4
                        else -> builder.append(char)
                    }
                }

                1 -> { // Parse variable, first char
                    if (char.isWhitespace()) {
                        builder.append("$")
                        nodes.add(WordNode(builder.toString()))
                        completeToken()
                    } else {
                        if (builder.isNotEmpty()) {
                            nodes.add(WordNode(builder.toString()))
                            builder.setLength(0)
                        }

                        if (char == '{') {
                            state = 3
                        } else {
                            builder.append(char)
                            state = 2
                        }
                    }
                }

                2 -> { // Parse variable, no braces
                    when {
                        char.isWhitespace() -> {
                            nodes.add(VariableNode(builder.toString()))
                            completeToken()
                        }

                        char.isLetterOrDigit() -> builder.append(char)
                        else -> throw IllegalStateException("Illegal char in variable template '$char' ")
                    }
                }

                3 -> { // Parse variable, with braces
                    when {
                        char == '}' -> {
                            nodes.add(VariableNode(builder.toString()))
                            builder.setLength(0)
                            state = 0
                        }

                        char.isLetterOrDigit() -> builder.append(char)
                        else -> throw IllegalStateException("Illegal char in variable template '$char' ")
                    }
                }

                4 -> { // Escape char
                    state = when (char) {
                        '$' -> {
                            builder.append('$')
                            0
                        }

                        '\\' -> {
                            builder.append('\\')
                            0
                        }

                        else -> {
                            throw IllegalStateException("Illegal char in escape '$char'")
                        }
                    }
                }
            }
        }

        when (state) {
            0 -> { // Parse word
                if (builder.isNotEmpty()) {
                    nodes.add(WordNode(builder.toString()))
                }

                if (nodes.isNotEmpty()) {
                    completeToken()
                }
            }

            1 -> { // Parse variable, first char
                nodes.add(WordNode("$"))
                completeToken()
            }

            2 -> { // Parse variables, no braces
                nodes.add(VariableNode(builder.toString()))
                completeToken()
            }

            3 -> { // Parse variable, with braces
                throw IllegalStateException("Unexpected end of template")
            }

            4 -> { // Escape char
                throw IllegalStateException("Unexpected end of template during escaping")
            }
        }

        return output
    }
}