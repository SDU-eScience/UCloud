package dk.sdu.cloud.share.services.db

import com.github.jasync.sql.db.QueryResult
import java.time.LocalDateTime

class EnhancedPreparedStatement(statement: String) {
    private val parameterNamesToIndex: Map<String, List<Int>>
    private val boundValues = HashSet<String>()
    private val preparedStatement: String
    private val parameters: Array<Any?>

    init {
        val parameterNamesToIndex = HashMap<String, List<Int>>()

        val queryBuilder = StringBuilder()
        var parameterIndex = 0
        var stringIndex = 0
        while (stringIndex < statement.length) {
            val nextParameter = statement.indexOf('?', stringIndex)
            if (nextParameter == -1) {
                queryBuilder.append(statement.substring(stringIndex))
                break
            }

            queryBuilder.append(statement.substring(stringIndex, nextParameter + 1)) // include '?'

            val endOfParameterName = statement.substring(nextParameter + 1)
                .indexOfFirst { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '_' }
                .takeIf { it != -1 }
                ?.let { it + nextParameter + 1 }
                ?: statement.length

            val parameterName = statement.substring(nextParameter + 1, endOfParameterName)
            stringIndex = endOfParameterName

            parameterNamesToIndex[parameterName] =
                (parameterNamesToIndex[parameterName] ?: emptyList()) + listOf(parameterIndex)

            parameterIndex++
        }

        this.parameterNamesToIndex = parameterNamesToIndex
        preparedStatement = queryBuilder.toString()
        parameters = Array(parameterIndex) { null }
    }

    fun setParameterAsNull(name: String) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = null
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: String?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Boolean?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Byte?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Short?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Int?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Long?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Float?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: List<Any?>?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: Double?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: ByteArray?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameter(name: String, value: LocalDateTime?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    fun setParameterUntyped(name: String, value: Any?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    suspend fun sendPreparedStatement(session: AsyncDBConnection, release: Boolean = false): QueryResult =
        session.sendPreparedStatement(preparedStatement, parameters.toList(), release)
}

suspend inline fun AsyncDBConnection.sendPreparedStatement(
    block: EnhancedPreparedStatement.() -> Unit,
    query: String,
    release: Boolean = false
): QueryResult {
    return EnhancedPreparedStatement(query).also(block).sendPreparedStatement(this, release)
}
