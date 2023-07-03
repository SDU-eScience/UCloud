package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceStore
import dk.sdu.cloud.accounting.util.invokeCall
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobStatus
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.app.store.api.WordInvocationParameter
import dk.sdu.cloud.base64Decode
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.ExperimentalTime

data class InternalJobState(
    val specification: JobSpecification,
    val state: JobState,
)

object ResourceOutputPool : DefaultPool<Array<ResourceDocument<Any>>>(128) {
    override fun produceInstance(): Array<ResourceDocument<Any>> = Array(1024) { ResourceDocument() }

    override fun clearInstance(instance: Array<ResourceDocument<Any>>): Array<ResourceDocument<Any>> {
        for (doc in instance) {
            doc.data = null
            doc.createdBy = 0
            doc.createdAt = 0
            doc.project = 0
            doc.id = 0
            doc.providerId = null
        }

        return instance
    }

    inline fun <T, R> withInstance(block: (Array<ResourceDocument<T>>) -> R): R {
        return useInstance {
            @Suppress("UNCHECKED_CAST")
            block(it as Array<ResourceDocument<T>>)
        }
    }
}

class JobResourceService2(
    private val db: DBContext,
    private val _providers: Providers<*>?,
) {
    private val idCards = IdCardServiceImpl(db)
    private val documents = ResourceManagerByOwner(
        "job",
        db,
        ResourceManagerByOwner.Callbacks(
            loadState = { session, count, ids ->
                val state = arrayOfNulls<InternalJobState>(count)

                session.sendPreparedStatement(
                    { setParameter("ids", ids.slice(0 until count)) },
                    """
                        with
                            params as (
                                select param.job_id, jsonb_object_agg(param.name, param.value) as params
                                from app_orchestrator.job_input_parameters param
                                where param.job_id = some(:ids)
                                group by job_id
                            ),
                            resources as (
                                select r.job_id, jsonb_agg(r.resource) as resources
                                from app_orchestrator.job_resources r
                                where r.job_id = some(:ids)
                                group by job_id
                            )
                        select
                            j.resource,
                            j.application_name,
                            j.application_version,
                            j.name,
                            j.replicas,
                            j.time_allocation_millis,
                            j.opened_file,
                            j.restart_on_exit,
                            j.ssh_enabled,
                            j.output_folder,
                            j.current_state,
                            p.params,
                            r.resources
                        from
                            app_orchestrator.jobs j
                            left join params p on j.resource = p.job_id
                            left join resources r on j.resource = r.job_id
                        where
                            j.resource = some(:ids);
                    """
                ).rows.forEach { row ->
                    val id = row.getLong(0)!!

                    val appName = row.getString(1)!!
                    val appVersion = row.getString(2)!!
                    val name = row.getString(3)
                    val replicas = row.getInt(4)!!
                    val timeAllocMillis = row.getLong(5)
                    val openedFile = row.getString(6)
                    val restartOnExit = row.getBoolean(7)
                    val sshEnabled = row.getBoolean(8)
                    val outputFolder = row.getString(9)
                    val currentState = JobState.valueOf(row.getString(10)!!)
                    val params = row.getString(11)?.let { text ->
                        defaultMapper.decodeFromString(
                            MapSerializer(String.serializer(), AppParameterValue.serializer()),
                            text
                        )
                    }
                    val resources = row.getString(12)?.let { text ->
                        defaultMapper.decodeFromString(
                            ListSerializer(AppParameterValue.serializer()),
                            text
                        )
                    }

                    val slot = ids.indexOf(id)
                    state[slot] = InternalJobState(
                        JobSpecification(
                            NameAndVersion(appName, appVersion),
                            ProductReference("", "", ""),
                            name,
                            replicas,
                            false,
                            params,
                            resources,
                            timeAllocMillis?.let { ms -> SimpleDuration.fromMillis(ms) },
                            openedFile,
                            restartOnExit,
                            sshEnabled,
                        ),
                        currentState
                    )
                }

                @Suppress("UNCHECKED_CAST")
                state as Array<InternalJobState>
            }
        )
    )

    val providers get() = _providers!!
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobSpecification>,
    ): BulkResponse<FindByStringId?> {
        val output = ArrayList<FindByStringId>()

        val card = idCards.fetchIdCard(actorAndProject)
        for (job in request.items) {
            val provider = job.product.provider
            validate(actorAndProject, job)

            val doc = ResourceDocument<InternalJobState>()
            val allocatedId = documents.create(
                card,
                findProduct(job.product),
                InternalJobState(job, JobState.IN_QUEUE),
                output = doc
            )

            // TODO(Dan): We need to store the internal state in the database here

            val result = try {
                providers.invokeCall(
                    provider,
                    actorAndProject,
                    { JobsProvider(provider).create },
                    bulkRequestOf(unmarshallDocument(doc)),
                    actorAndProject.signedIntentFromUser,
                )
            } catch (ex: Throwable) {
                // TODO(Dan): This is not guaranteed to run ever. We will get stuck
                //  if never "confirmed" by the provider.
                documents.delete(card, longArrayOf(allocatedId))
                throw ex
                // TODO do we continue or stop here?
            }

            val providerId = result.responses.single()
            if (providerId != null) {
                documents.updateProviderId(allocatedId, providerId.id)
            }

            output.add(FindByStringId(allocatedId.toString()))
        }

        return BulkResponse(output)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<JobIncludeFlags>,
    ): PageV2<Job> {
        println("calling browse")
        val card = idCards.fetchIdCard(actorAndProject)
        println("got card $card")
        val normalizedRequest = request.normalize()
        println("req $normalizedRequest")
        return ResourceOutputPool.withInstance { buffer ->
            // TODO(Dan): Sorting is an issue, especially if we are sorting using a custom property.
            println("about to browse")
            val start = System.nanoTime()
            val result = documents.browse(card, buffer, request.next, request.flags)
            val end = System.nanoTime()
            println("Time was ${end - start}ns")

            val page = ArrayList<Job>(result.count)
            for (idx in 0 until min(normalizedRequest.itemsPerPage, result.count)) {
                page.add(unmarshallDocument(buffer[idx]))
            }

            return PageV2(normalizedRequest.itemsPerPage, page, result.next)
        }
    }

    suspend fun browseV2(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<JobIncludeFlags>,
        channel: ByteWriteChannel,
    ) {
        val globalStart = System.nanoTime()
        val card = idCards.fetchIdCard(actorAndProject)
        val normalizedRequest = request.normalize()
        ResourceOutputPool.withInstance { buffer ->
            // TODO(Dan): Sorting is an issue, especially if we are sorting using a custom property.
            println("about to browse")
            val start = System.nanoTime()
            val result = documents.browse(card, buffer, request.next, request.flags)
            val end = System.nanoTime()
            println("Time was ${end - start}ns")

            val count = min(normalizedRequest.itemsPerPage, result.count)
            channel.writeInt(count)

            for (i in 0 until count) {
                val doc = buffer[i]
                channel.writeLong(doc.id)
                channel.writeLong(doc.createdAt)
                channel.writeInt(doc.createdBy)
                channel.writeInt(doc.project)
                channel.writeInt(doc.product)

                channel.writeInt(doc.product)
                channel.writeText(doc.providerId)

                val job: InternalJobState = doc.data ?: error("")
                channel.writeInt(job.state.ordinal)
                channel.writeText(job.specification.application.name)
                channel.writeText(job.specification.application.version)
                channel.writeText(job.specification.name)
                channel.writeLong(job.specification.timeAllocation?.toMillis() ?: -1L)
            }
        }
        val globalEnd = System.nanoTime()
        println("Internal time was ${globalEnd - globalStart}")
    }

    private suspend fun ByteWriteChannel.writeText(text: String?) {
        if (text == null) {
            writeInt(-1)
            return
        }

        val byteArray = text.encodeToByteArray()
        writeInt(byteArray.size)
        writeFully(byteArray)
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ResourceRetrieveRequest<JobIncludeFlags>,
    ): Job? {
        val longId = request.id.toLongOrNull() ?: return null
        val card = idCards.fetchIdCard(actorAndProject)
        val doc = documents.retrieve(card, longId) ?: return null
        return unmarshallDocument(doc)
    }

    suspend fun terminate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        val allJobs = ResourceOutputPool.withInstance<InternalJobState, List<Job>> { buffer ->

            // TODO(Dan): Wasteful memory allocation and copies
            val count = documents.retrieveBulk(
                card,
                request.items.mapNotNull { it.id.toLongOrNull() }.toLongArray(),
                buffer
            )

            // TODO(Dan): We could skip this step entirely and move directly to the bytes needed to send the request.
            //  No system is going to care about a BulkRequest<Job> when we already have the internal data to inspect.
            //  This step could also do the grouping step below.
            val result = ArrayList<Job>()
            for (idx in 0 until count) {
                result.add(unmarshallDocument(buffer[0]))
            }

            result
        }

        // TODO(Dan): See earlier todo about going from internal to bytes
        val jobsByProvider = allJobs.groupBy { it.specification.product.provider }
        for ((provider, jobs) in jobsByProvider) {
            providers.invokeCall(
                provider,
                actorAndProject,
                { JobsProvider(provider).terminate },
                BulkRequest(jobs),
                actorAndProject.signedIntentFromUser
            )
        }
    }

    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceUpdateAndId<JobUpdate>>
    ) {
        // TODO(Dan): Allocates a lot of memory which isn't needed
        val card = idCards.fetchIdCard(actorAndProject)
        val updatesByJob = request.items.groupBy { it.id }.mapValues { it.value.map { it.update } }

        for ((job, updates) in updatesByJob) {
            documents.addUpdate(
                card,
                job.toLongOrNull() ?: continue,
                updates.map {
                    ResourceStore.Update(
                        it.status,
                        defaultMapper.encodeToJsonElement(JobUpdate.serializer(), it)
                    )
                }
            )
        }
    }

    private suspend fun validate(actorAndProject: ActorAndProject, job: JobSpecification) {
        // TODO do something
    }

    private suspend fun findProduct(ref: ProductReference): Int {
        return 0
    }

    private suspend fun unmarshallDocument(doc: ResourceDocument<InternalJobState>): Job {
        val data = doc.data!!
        return Job(
            doc.id.toString(),
            ResourceOwner(
                "",
                null
            ),
            emptyList(),
            data.specification,
            JobStatus(
                data.state,
            ),
            doc.createdAt,
            permissions = ResourcePermissions(
                listOf(Permission.ADMIN),
                emptyList()
            )
        )
    }
}

fun createDummyJob(): Job {
    return Job(
        Random.nextLong().absoluteValue.toString(),
        ResourceOwner(
            randomString(),
            if (Random.nextBoolean()) randomString() else null
        ),
        emptyList(),
        JobSpecification(
            NameAndVersion(randomString(), randomString()),
            ProductReference(randomString(), randomString(), randomString()),
            parameters = buildMap {
                repeat(Random.nextInt(0, 5)) { idx ->
                    put(randomString(), AppParameterValue.Text(randomString()))
                }
            },
            timeAllocation = SimpleDuration(13, 37, 0)
        ),
        JobStatus(
            JobState.RUNNING,
            startedAt = Random.nextLong().absoluteValue,
            resolvedApplication = Application(
                ApplicationMetadata(
                    randomString(),
                    randomString(),
                    listOf(randomString(), randomString()),
                    randomString(),
                    randomString(),
                    randomString(),
                    true
                ),
                ApplicationInvocationDescription(
                    ToolReference(
                        randomString(),
                        randomString(),
                        Tool(
                            randomString(),
                            Random.nextLong().absoluteValue,
                            Random.nextLong().absoluteValue,
                            NormalizedToolDescription(
                                NameAndVersion(randomString(), randomString()),
                                randomString(),
                                1,
                                SimpleDuration(13, 37, 0),
                                emptyList(),
                                listOf(randomString()),
                                randomString(),
                                randomString(),
                                ToolBackend.DOCKER,
                                randomString(),
                                randomString(),
                                null
                            )
                        )
                    ),
                    listOf(WordInvocationParameter(randomString())),
                    listOf(),
                    listOf()
                )
            ),
        ),
        Random.nextLong().absoluteValue,
    )
}

fun randomString(minSize: Int = 8, maxSize: Int = 16): String {
    return CharArray(Random.nextInt(minSize, maxSize + 1)) { Char(Random.nextInt(48, 91)) }.concatToString()
}

interface BinaryMessage {
    val data: Array<Any?>
    fun schema(): BinarySchema.Record
}

@OptIn(ExperimentalTime::class)
fun main(): Unit = runBlocking {
    /*
    val create = Fie.create(
        byteArrayOf(42),
        listOf(byteArrayOf(0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte())),
        null,
        listOf("fie"),
        listOf(byteArrayOf(13))
    )
     */

    /*
    val create = LinkedList.create(LinkedList.create(null, 10), 42)
    val json = create.encodeToJson()

    println(create)
    println(json)
    println(LinkedList.decodeFromJson(json))

    val channel = ByteChannel()
    create.encode(channel)
    channel.flush()
    println(LinkedList.decode(channel))

    if (true) return@runBlocking Unit
     */

    BinaryRecordDsl(
        """
            record Fie {
                dynamic: binary
                fixed: binary[4123]
                wide: binary[1..4923]
                optional: binary?
                shortStringRepeated: string(0..120)[64]
            }
            
            record LinkedList {
                value: Int
                next: LinkedList?
            }
        """.trimIndent()
    ).parse()
}

class BinaryRecordDsl(val data: String) {
    var cursor = 0
    var line = 1
    var col = 1

    data class Token(val type: TokType, val data: String? = null)

    enum class TokType {
        BRACE_START,
        BRACE_END,
        COLON,
        RECORD,
        IDENTIFIER,
        EOF
    }

    fun isEof(): Boolean = cursor >= data.length

    fun peekChar(): Char {
        return data[cursor]
    }

    fun consumeChar(): Char {
        val peek = peekChar()
        cursor++
        col++

        if (peek == '\n') {
            line++
            col = 1
        }
        return peek
    }

    fun consumeWordIfMatch(word: String): Boolean {
        if (peekWord(word)) {
            repeat(word.length) { consumeChar() }
            return true
        }
        return false
    }

    fun peekWord(word: String): Boolean = data.substring(cursor).startsWith(word)

    fun consumeWhitespace() {
        while (!isEof()) {
            val peek = peekChar()
            if (peek.isWhitespace()) {
                consumeChar()
            } else {
                break
            }
        }
    }

    fun consumeToken(): Token {
        consumeWhitespace()
        if (isEof()) return Token(TokType.EOF)

        return when {
            consumeWordIfMatch("record") -> Token(TokType.RECORD)
            consumeWordIfMatch("{") -> Token(TokType.BRACE_START)
            consumeWordIfMatch("}") -> Token(TokType.BRACE_END)
            consumeWordIfMatch(":") -> Token(TokType.COLON)
            else -> {
                val builder = StringBuilder()
                while (!isEof()) {
                    val peek = peekChar()
                    if (peek.isWhitespace()) break
                    if (!peek.isJavaIdentifierPart() && peek !in setOf('?', '[', ']', '(', ')', '.')) break
                    builder.append(consumeChar())
                }
                Token(TokType.IDENTIFIER, builder.toString())
            }
        }
    }

    fun parse() {
        val records = ArrayList<Record>()
        while (!isEof()) {
            records.add(parseRecord() ?: break)
        }
        println(records)

        for (record in records) {
            generateCode(record).also { println(it) }
        }
    }

    fun generateCode(record: Record): String {
        return buildString {
            append("@JvmInline value class ")
            append(record.name)
            appendLine(" private constructor(")

            append("    ")
            appendLine("override val data: Array<Any?> = arrayOfNulls(globalSchema.fieldTypes.size)")
            appendLine("): BinaryMessage {")
            appendLine("    override inline fun schema() = globalSchema")
            appendLine()

            run {
                var propIdx = 0
                for ((name, type) in record.fields) {
                    generateKtProperty(name, type, propIdx++)
                }
            }

            appendLine()

            appendLine("    suspend fun encode(channel: ByteWriteChannel) {")
            appendLine("        globalSchema.encode(channel, this)")
            appendLine("    }")

            appendLine()
            appendLine("    suspend fun encodeToJson(): JsonElement {")
            appendLine("        return globalSchema.encodeToJson(this)")
            appendLine("    }")

            appendLine()
            appendLine("    companion object {")
            appendLine("    fun create(")
            for ((name, type) in record.fields) {
                append("        ")
                append(name)
                append(": ")
                append(typeToKtType(type))

                appendLine(",")
            }
            appendLine("    ): ${record.name} {")
            appendLine("return ${record.name}().apply {")
            for ((name, _) in record.fields) {
                append("        ")
                append("this.")
                append(name)
                append(" = ")
                appendLine(name)
            }
            appendLine("}")
            appendLine("    }")


            appendLine("        val globalSchema = BinarySchema.Record(")
            appendLine("            \"${record.name}\",")
            appendLine("            arrayOf(")
            for ((name, _) in record.fields) {
                appendLine("                \"${name}\",")
            }
            appendLine("            ),")

            appendLine("            arrayOf(")
            for ((name, type) in record.fields) {
                append(typeToEncoding(record.name, type))
                appendLine(",")
            }
            appendLine("            ),")
            appendLine("            { ${record.name}(it) },")

            appendLine("        )")

            appendLine()
            appendLine("        suspend fun decode(channel: ByteReadChannel): ${record.name} {")
            appendLine("            return globalSchema.decode(channel) as ${record.name}")
            appendLine("        }")

            appendLine()
            appendLine("        suspend fun decodeFromJson(element: JsonElement): ${record.name} {")
            appendLine("            return globalSchema.decodeFromJson(element) as ${record.name}")
            appendLine("        }")

            appendLine("    }")


            appendLine("}")
        }
    }

    private val dslTypes = arrayOf(
        "i8", "i16", "i32", "i64",
        "u8", "u16", "u32", "u64",
        "f32", "f64",
        "bool",
        "string",
        "binary"
    )

    private val ktTypes = arrayOf(
        "Byte", "Short", "Int", "Long",
        "UByte", "UShort", "UInt", "ULong",
        "Float", "Double",
        "Boolean",
        "String",
        "ByteArray"
    )

    fun dslTypeToKtType(type: String): String {
        val idx = dslTypes.indexOf(type)
        if (idx == -1) return type
        return ktTypes[idx]
    }

    private fun typeToKtType(type: Type): String {
        val arrMin = type.arrayMinimum
        val arrMax = type.arrayMaximum

        return if (arrMin != null && arrMax != null) {
            if (arrMin == 0 && arrMax == 1) {
                dslTypeToKtType(type.name) + "?"
            } else {
                val elemType = dslTypeToKtType(type.name)
                "List<$elemType>"
            }
        } else {
            dslTypeToKtType(type.name)
        }
    }

    private fun typeToEncoding(selfRecord: String, type: Type): String {
        var baseType = "BinarySchema." + when (type.name) {
            "i8" -> "I8"
            "i16" -> "I16"
            "i32" -> "I32"
            "i64" -> "I64"

            "u8" -> "U8"
            "u16" -> "U16"
            "u32" -> "U32"
            "u64" -> "U64"

            "f32" -> "F32"
            "f64" -> "F64"

            "bool" -> "Bool"
            "string" -> "Text(minSize = ${type.minimum}, maxSize = ${type.maximum})"
            "binary" -> "Bytes(minSize = ${type.minimum}, maxSize = ${type.maximum})"

            else -> {
                if (selfRecord == type.name) {
                    "Self"
                } else {
                    type.name + ".globalSchema"
                }
            }
        }

        if (baseType.count { it == '.' } > 1) {
            baseType = baseType.substringAfter('.')
        }

        return when {
            type.arrayMinimum != null -> {
                "BinarySchema.Repeated($baseType, ${type.arrayMinimum}, ${type.arrayMaximum})"
            }

            else -> {
                baseType
            }
        }
    }

    private fun typeToStoredKtType(type: Type): String {
        val isOptional = type.arrayMinimum == 0 && type.arrayMaximum == 1

        return if (isOptional) "List<${dslTypeToKtType(type.name)}>?"
        else typeToKtType(type)
    }

    private fun StringBuilder.generateKtProperty(name: String, type: Type, idx: Int) {
        val isOptional = type.arrayMinimum == 0 && type.arrayMaximum == 1
        val isArray = type.arrayMinimum != null && !isOptional

        val ktType = typeToKtType(type)

        append("var ")
        append(name)
        append(": ")
        append(ktType)

        appendLine()
        appendLine("inline get() {")
        append("val b = data[")
        append(idx)
        append("] as ")
        appendLine(typeToStoredKtType(type))
        when {
            isOptional -> {
                appendLine("if (b.isNullOrEmpty()) return null")
                appendLine("return b.single()")
            }

            else -> {
                appendLine("return b")
            }
        }
        appendLine("}")

        appendLine()
        appendLine("inline set(value) {")
        append("data[")
        append(idx)
        append("] = ")
        when {
            isOptional -> {
                appendLine("value?.let { listOf(it) }")
            }

            else -> {
                appendLine("value")
            }
        }
        appendLine("}")
    }

    fun reportError(message: String): Nothing {
        error("Error at line $line:$col: $message")
    }

    data class Record(val name: String, val fields: List<Pair<String, Type>>)

    fun parseRecord(): Record? {
        val next = consumeToken()
        if (next.type == TokType.EOF) return null
        if (next.type != TokType.RECORD) {
            reportError("Expected 'record' or EOF")
        }

        val nameTok = consumeToken()
        if (nameTok.type != TokType.IDENTIFIER) {
            reportError("Expected name of record")
        }

        val name = nameTok.data!!

        if (consumeToken().type != TokType.BRACE_START) {
            reportError("Expected a '{' to start the record")
        }

        val fields = ArrayList<Pair<String, Type>>()
        while (!isEof()) {
            val fieldNameTok = consumeToken()
            if (fieldNameTok.type == TokType.BRACE_END) return Record(name, fields)
            if (fieldNameTok.type != TokType.IDENTIFIER) {
                reportError("Expected an identifier to start a new property")
            }

            val fieldName = fieldNameTok.data!!

            if (consumeToken().type != TokType.COLON) {
                reportError("Expected a colon following the field name ($fieldName) to start the type")
            }

            val typeTok = consumeToken()
            if (typeTok.type != TokType.IDENTIFIER) {
                reportError("Expected a type for the '$fieldName' field")
            }

            val type = typeTok.data!!

            fields.add(fieldName to normalizeType(type))
        }

        reportError("Expected a '}' to end the record")
    }

    data class Type(
        val name: String,
        val minimum: Int,
        val maximum: Int,
        val arrayMinimum: Int? = null,
        val arrayMaximum: Int? = null,
    ) {
        val isOptional = arrayMinimum == 0 && arrayMaximum == 1
    }

    fun normalizeType(type: String): Type {
        var work = type
        var minimum = 0
        var maximum = 1024 * 128
        var arrayMinimum: Int? = null
        var arrayMaximum: Int? = null
        if (work.endsWith("?")) {
            work = work.substring(0, work.length - 1)
            arrayMinimum = 0
            arrayMaximum = 1
        }

        if (work.endsWith("]")) {
            if (arrayMinimum == 0) error("Type cannot be both an array and optional: $type")
            val arrStart = work.indexOf('[')
            if (arrStart != -1) {
                val arrDims = work.substring(arrStart + 1, work.length - 1)

                if (arrDims.contains("..")) {
                    arrayMinimum = arrDims.substringBefore("..").toInt()
                    arrayMaximum = arrDims.substringAfter("..").toInt()
                } else {
                    arrayMinimum = 0
                    arrayMaximum = arrDims.toInt()
                }

                work = work.substring(0, arrStart)
            }
        }

        if (work.endsWith(")")) {
            val arrStart = work.indexOf('(')
            if (arrStart != -1) {
                val arrDims = work.substring(arrStart + 1, work.length - 1)

                if (arrDims.contains("..")) {
                    minimum = arrDims.substringBefore("..").toInt()
                    maximum = arrDims.substringAfter("..").toInt()
                } else {
                    minimum = 0
                    maximum = arrDims.toInt()
                }

                work = work.substring(0, arrStart)
            }
        }

        when (work) {
            "Byte" -> work = "i8"
            "Short" -> work = "i16"
            "Int" -> work = "i32"
            "Long" -> work = "i64"
            "Float" -> work = "f32"
            "Double" -> work = "f64"
            "Boolean" -> work = "bool"
            "String" -> work = "string"
        }

        return Type(work, minimum, maximum, arrayMinimum, arrayMaximum)
    }
}

object BinarySchema {
    sealed interface Type<KtType> {
        suspend fun encode(channel: ByteWriteChannel, value: KtType)
        suspend fun decode(channel: ByteReadChannel): KtType

        suspend fun decodeFromJson(element: JsonElement): KtType
        suspend fun encodeToJson(value: KtType): JsonElement
    }

    data class Record(
        val name: String,
        val fieldNames: Array<String>,
        val fieldTypes: Array<Type<*>>,
        var constructor: (Array<Any?>) -> BinaryMessage,
    ) : Type<BinaryMessage> {
        init {
            require(fieldNames.size == fieldTypes.size) {
                "fieldNames.size (${fieldNames.size}) != fieldTypes.size (${fieldTypes.size})" +
                        "\n\t${fieldNames}" +
                        "\n\t${fieldTypes}"
            }

            for (i in fieldTypes.indices) {
                val field = fieldTypes[i]
                if (field == Self) fieldTypes[i] = this
                if (field is Repeated<*, *> && field.elementType == Self) {
                    @Suppress("UNCHECKED_CAST")
                    val f = field as Repeated<BinaryMessage, Record>
                    f.elementType = this
                }
            }
        }

        override suspend fun encode(channel: ByteWriteChannel, value: BinaryMessage) {
            require(value.data.size == fieldTypes.size) {
                "value.size (${value.data.size}) != fieldTypes.size (${fieldTypes.size})" +
                        "\n\t${value}" +
                        "\n\t${fieldTypes}"
            }

            for (i in fieldTypes.indices) {
                @Suppress("UNCHECKED_CAST") val type = fieldTypes[i] as Type<Any?>
                type.encode(channel, value.data[i])
            }
        }

        override suspend fun decode(channel: ByteReadChannel): BinaryMessage {
            val output = arrayOfNulls<Any>(fieldNames.size)
            for (i in fieldTypes.indices) {
                println("decoding $i $fieldTypes")
                @Suppress("UNCHECKED_CAST") val type = fieldTypes[i] as Type<Any?>
                output[i] = type.decode(channel)
            }
            return constructor(output)
        }

        override suspend fun encodeToJson(value: BinaryMessage): JsonElement {
            val temp = HashMap<String, JsonElement>()
            for (i in fieldTypes.indices) {
                val name = fieldNames[i]
                @Suppress("UNCHECKED_CAST") val type = fieldTypes[i] as Type<Any?>
                temp[name] = type.encodeToJson(value.data[i])
            }
            return JsonObject(temp)
        }

        override suspend fun decodeFromJson(element: JsonElement): BinaryMessage {
            require(element is JsonObject) { "element is not a JsonObject $element" }

            val output = arrayOfNulls<Any>(fieldNames.size)
            for ((key, value) in element) {
                val idx = fieldNames.indexOf(key)
                if (idx == -1) continue

                @Suppress("UNCHECKED_CAST") val type = fieldTypes[idx] as Type<Any?>
                output[idx] = type.decodeFromJson(value)
            }

            return constructor(output)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Record

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String {
            return "Record(name='$name', fieldNames=${fieldNames.contentToString()}, fieldTypes=${fieldTypes.contentToString()})"
        }
    }

    data class Repeated<Kt, T : Type<Kt>>(
        var elementType: T,
        val minSize: Int = 0,
        val maxSize: Int = Int.MAX_VALUE,
    ) : Type<List<Kt>?> {
        init {
            require(minSize <= maxSize) { "minSize ($minSize) > maxSize ($maxSize)" }
        }

        override suspend fun encode(channel: ByteWriteChannel, value: List<Kt>?) {
            val valueSize = value?.size ?: 0
            require(valueSize in minSize..maxSize) {
                "value size is out of bounds! ${valueSize} !in ${minSize}..${maxSize}"
            }

            if (value == null) {
                channel.writeInt(0)
            } else {
                channel.writeInt(value.size)
                for (elem in value) {
                    elementType.encode(channel, elem)
                }
            }
        }

        override suspend fun decode(channel: ByteReadChannel): List<Kt>? {
            val size = channel.readInt()
            require(size in minSize..maxSize) { "received size ($size) is not valid ${minSize}..${maxSize}" }
            if (size < 0) return null

            val output = ArrayList<Kt>()
            for (i in 0 until size) {
                output.add(elementType.decode(channel))
            }
            return output
        }

        override suspend fun encodeToJson(value: List<Kt>?): JsonElement {
            val valueSize = value?.size ?: 0
            require(valueSize in minSize..maxSize) {
                "value size is out of bounds! ${valueSize} !in ${minSize}..${maxSize}"
            }

            if (value == null) return JsonNull
            if (maxSize == 1 && minSize == 0) {
                return elementType.encodeToJson(value.single())
            }

            val temp = ArrayList<JsonElement>()
            for (elem in value) {
                temp.add(elementType.encodeToJson(elem))
            }
            return JsonArray(temp)
        }

        override suspend fun decodeFromJson(element: JsonElement): List<Kt>? {
            if (element == JsonNull) {
                return null
            }

            if (maxSize == 1 && minSize == 0) {
                return listOf(elementType.decodeFromJson(element))
            }

            require(element is JsonArray) { "element is not a JsonArray" }

            val size = element.size
            require(size in minSize..maxSize) { "received size ($size) is not valid ${minSize}..${maxSize}" }

            val output = ArrayList<Kt>()
            for (i in 0 until size) {
                output.add(elementType.decodeFromJson(element[i]))
            }
            return output
        }
    }

    data class Text(
        val minSize: Int = 0,
        val maxSize: Int = 1024 * 1024 * 4
    ) : Type<String?> {
        val elementType = I8

        override suspend fun encode(channel: ByteWriteChannel, value: String?) {
            val valueSize = value?.length ?: 0
            require(valueSize in minSize..maxSize) {
                "value size is out of bounds! ${value?.length} !in ${minSize}..${maxSize}"
            }

            if (value == null) {
                channel.writeInt(0)
            } else {
                val encoded = value.encodeToByteArray()
                channel.writeInt(encoded.size)
                channel.writeFully(encoded)
            }
        }

        override suspend fun decode(channel: ByteReadChannel): String? {
            val size = channel.readInt()
            require(size in minSize..maxSize) { "received size ($size) is not valid ${minSize}..${maxSize}" }
            if (size < 0) return null

            val bytes = ByteArray(size)
            channel.readFully(bytes)
            return bytes.decodeToString()
        }

        override suspend fun encodeToJson(value: String?): JsonElement {
            if (value == null) return JsonNull
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): String? {
            if (element == JsonNull) return null
            require(element is JsonPrimitive && element.isString) { "element is not a string!" }
            return element.content
        }
    }

    data class Bytes(
        val minSize: Int = 0,
        val maxSize: Int = 1024 * 1024 * 4
    ) : Type<ByteArray?> {
        val elementType = I8

        override suspend fun encode(channel: ByteWriteChannel, value: ByteArray?) {
            val valueSize = value?.size ?: 0
            require(valueSize in minSize..maxSize) {
                "value size is out of bounds! ${valueSize} !in ${minSize}..${maxSize}"
            }

            channel.writeInt(valueSize)
            if (value != null) {
                channel.writeFully(value)
            }
        }

        override suspend fun decode(channel: ByteReadChannel): ByteArray? {
            val size = channel.readInt()
            require(size in minSize..maxSize) { "received size ($size) is not valid ${minSize}..${maxSize}" }
            if (size < 0) return null

            val bytes = ByteArray(size)
            channel.readFully(bytes)
            return bytes
        }

        override suspend fun encodeToJson(value: ByteArray?): JsonElement {
            if (value == null) return JsonNull
            return JsonPrimitive(base64Encode(value))
        }

        override suspend fun decodeFromJson(element: JsonElement): ByteArray? {
            if (element == JsonNull) return null
            require(element is JsonPrimitive && element.isString) { "element is not a string!" }
            return base64Decode(element.content)
        }
    }

    object F32 : Type<Float> {
        override suspend fun encode(channel: ByteWriteChannel, value: Float) {
            channel.writeFloat(value)
        }

        override suspend fun decode(channel: ByteReadChannel): Float {
            return channel.readFloat()
        }

        override suspend fun encodeToJson(value: Float): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Float {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.toFloat()
        }
    }

    object F64 : Type<Double> {
        override suspend fun encode(channel: ByteWriteChannel, value: Double) {
            channel.writeDouble(value)
        }

        override suspend fun decode(channel: ByteReadChannel): Double {
            return channel.readDouble()
        }

        override suspend fun encodeToJson(value: Double): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Double {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.toDouble()
        }
    }

    object I8 : Type<Byte> {
        override suspend fun encode(channel: ByteWriteChannel, value: Byte) {
            channel.writeByte(value)
        }

        override suspend fun decode(channel: ByteReadChannel): Byte {
            return channel.readByte()
        }

        override suspend fun encodeToJson(value: Byte): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Byte {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.toByte()
        }
    }

    object I16 : Type<Short> {
        override suspend fun encode(channel: ByteWriteChannel, value: Short) {
            channel.writeShort(value)
        }

        override suspend fun decode(channel: ByteReadChannel): Short {
            return channel.readShort()
        }

        override suspend fun encodeToJson(value: Short): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Short {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.toShort()
        }
    }

    object I32 : Type<Int> {
        override suspend fun encode(channel: ByteWriteChannel, value: Int) {
            channel.writeInt(value)
        }

        override suspend fun decode(channel: ByteReadChannel): Int {
            return channel.readInt()
        }

        override suspend fun encodeToJson(value: Int): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Int {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.toInt()
        }
    }

    object I64 : Type<Long> {
        override suspend fun encode(channel: ByteWriteChannel, value: Long) {
            channel.writeLong(value)
        }

        override suspend fun decode(channel: ByteReadChannel): Long {
            return channel.readLong()
        }

        override suspend fun encodeToJson(value: Long): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Long {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.toLong()
        }
    }

    object Bool : Type<Boolean> {
        override suspend fun encode(channel: ByteWriteChannel, value: Boolean) {
            channel.writeByte(if (value) 1 else 0)
        }

        override suspend fun decode(channel: ByteReadChannel): Boolean {
            return channel.readByte() == 1.toByte()
        }

        override suspend fun encodeToJson(value: Boolean): JsonElement {
            return JsonPrimitive(value)
        }

        override suspend fun decodeFromJson(element: JsonElement): Boolean {
            require(element is JsonPrimitive) { "element is not a primitive" }
            return element.content.equals("true", ignoreCase = true)
        }
    }

    object Self : Type<Unit> {
        override suspend fun decode(channel: ByteReadChannel) {}
        override suspend fun encode(channel: ByteWriteChannel, value: Unit) {}

        override suspend fun decodeFromJson(element: JsonElement) {}
        override suspend fun encodeToJson(value: Unit): JsonElement = JsonNull
    }
}
