// GENERATED CODE - DO NOT MODIFY - See StatisticsModel.msg
// GENERATED CODE - DO NOT MODIFY - See StatisticsModel.msg
// GENERATED CODE - DO NOT MODIFY - See StatisticsModel.msg

package dk.sdu.cloud.app.orchestrator.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dk.sdu.cloud.messages.*

import dk.sdu.cloud.accounting.api.*

@JvmInline
value class JobStatistics(override val buffer: BufferAndOffset) : BinaryType {
    var categories: BinaryTypeList<ProductCategoryB>
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(ProductCategoryB, buffer.copy(offset = offset))) ?: error("Missing property categories")
        }
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    var usageByUser: BinaryTypeList<JobUsageByUser>
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(JobUsageByUser, buffer.copy(offset = offset))) ?: error("Missing property usageByUser")
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }

    var mostUsedApplications: BinaryTypeList<MostUsedApplications>
        inline get() {
            val offset = buffer.data.getInt(8 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(MostUsedApplications, buffer.copy(offset = offset))) ?: error("Missing property mostUsedApplications")
        }
        inline set(value) { buffer.data.putInt(8 + buffer.offset, value.buffer.offset) }

    var jobSubmissionStatistics: BinaryTypeList<JobSubmissionStatistics>
        inline get() {
            val offset = buffer.data.getInt(12 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(JobSubmissionStatistics, buffer.copy(offset = offset))) ?: error("Missing property jobSubmissionStatistics")
        }
        inline set(value) { buffer.data.putInt(12 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "categories" to (categories.let { it.encodeToJson() }),
        "usageByUser" to (usageByUser.let { it.encodeToJson() }),
        "mostUsedApplications" to (mostUsedApplications.let { it.encodeToJson() }),
        "jobSubmissionStatistics" to (jobSubmissionStatistics.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<JobStatistics> {
        override val size = 16
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = JobStatistics(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): JobStatistics {
            if (json !is JsonObject) error("JobStatistics must be decoded from an object")
            val categories = run {
                val element = json["categories"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(ProductCategoryB, allocator, element)
                }
            } ?: error("Missing required property: categories in JobStatistics")
            val usageByUser = run {
                val element = json["usageByUser"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(JobUsageByUser, allocator, element)
                }
            } ?: error("Missing required property: usageByUser in JobStatistics")
            val mostUsedApplications = run {
                val element = json["mostUsedApplications"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(MostUsedApplications, allocator, element)
                }
            } ?: error("Missing required property: mostUsedApplications in JobStatistics")
            val jobSubmissionStatistics = run {
                val element = json["jobSubmissionStatistics"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(JobSubmissionStatistics, allocator, element)
                }
            } ?: error("Missing required property: jobSubmissionStatistics in JobStatistics")
            return allocator.JobStatistics(
                categories = categories,
                usageByUser = usageByUser,
                mostUsedApplications = mostUsedApplications,
                jobSubmissionStatistics = jobSubmissionStatistics,
            )
        }
    }
}


fun BinaryAllocator.JobStatistics(
    categories: BinaryTypeList<ProductCategoryB>,
    usageByUser: BinaryTypeList<JobUsageByUser>,
    mostUsedApplications: BinaryTypeList<MostUsedApplications>,
    jobSubmissionStatistics: BinaryTypeList<JobSubmissionStatistics>,
): JobStatistics {
    val result = this.allocate(JobStatistics)
    result.categories = categories
    result.usageByUser = usageByUser
    result.mostUsedApplications = mostUsedApplications
    result.jobSubmissionStatistics = jobSubmissionStatistics
    return result
}

@JvmInline
value class JobUsageByUser(override val buffer: BufferAndOffset) : BinaryType {
    var categoryIndex: Int
        inline get() = buffer.data.getInt(0 + buffer.offset)
        inline set (value) { buffer.data.putInt(0 + buffer.offset, value) }

    var dataPoints: BinaryTypeList<JobUsageByUserDataPoint>
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(JobUsageByUserDataPoint, buffer.copy(offset = offset))) ?: error("Missing property dataPoints")
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "categoryIndex" to (categoryIndex.let { JsonPrimitive(it) }),
        "dataPoints" to (dataPoints.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<JobUsageByUser> {
        override val size = 8
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = JobUsageByUser(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): JobUsageByUser {
            if (json !is JsonObject) error("JobUsageByUser must be decoded from an object")
            val categoryIndex = run {
                val element = json["categoryIndex"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'categoryIndex' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: categoryIndex in JobUsageByUser")
            val dataPoints = run {
                val element = json["dataPoints"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(JobUsageByUserDataPoint, allocator, element)
                }
            } ?: error("Missing required property: dataPoints in JobUsageByUser")
            return allocator.JobUsageByUser(
                categoryIndex = categoryIndex,
                dataPoints = dataPoints,
            )
        }
    }
}


fun BinaryAllocator.JobUsageByUser(
    categoryIndex: Int,
    dataPoints: BinaryTypeList<JobUsageByUserDataPoint>,
): JobUsageByUser {
    val result = this.allocate(JobUsageByUser)
    result.categoryIndex = categoryIndex
    result.dataPoints = dataPoints
    return result
}

@JvmInline
value class JobUsageByUserDataPoint(override val buffer: BufferAndOffset) : BinaryType {
    var _username: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(0 + buffer.offset)))
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }
    val username: String
        inline get() = _username.decode()

    var usage: Long
        inline get() = buffer.data.getLong(4 + buffer.offset)
        inline set (value) { buffer.data.putLong(4 + buffer.offset, value) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "username" to (username.let { JsonPrimitive(it) }),
        "usage" to (usage.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<JobUsageByUserDataPoint> {
        override val size = 12
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = JobUsageByUserDataPoint(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): JobUsageByUserDataPoint {
            if (json !is JsonObject) error("JobUsageByUserDataPoint must be decoded from an object")
            val username = run {
                val element = json["username"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'username' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: username in JobUsageByUserDataPoint")
            val usage = run {
                val element = json["usage"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'usage' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: usage in JobUsageByUserDataPoint")
            return allocator.JobUsageByUserDataPoint(
                username = username,
                usage = usage,
            )
        }
    }
}


fun BinaryAllocator.JobUsageByUserDataPoint(
    username: String,
    usage: Long,
): JobUsageByUserDataPoint {
    val result = this.allocate(JobUsageByUserDataPoint)
    result._username = username.let { allocateText(it) }
    result.usage = usage
    return result
}

@JvmInline
value class MostUsedApplications(override val buffer: BufferAndOffset) : BinaryType {
    var categoryIndex: Int
        inline get() = buffer.data.getInt(0 + buffer.offset)
        inline set (value) { buffer.data.putInt(0 + buffer.offset, value) }

    var dataPoints: BinaryTypeList<MostUsedApplicationsDataPoint>
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(MostUsedApplicationsDataPoint, buffer.copy(offset = offset))) ?: error("Missing property dataPoints")
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "categoryIndex" to (categoryIndex.let { JsonPrimitive(it) }),
        "dataPoints" to (dataPoints.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<MostUsedApplications> {
        override val size = 8
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = MostUsedApplications(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): MostUsedApplications {
            if (json !is JsonObject) error("MostUsedApplications must be decoded from an object")
            val categoryIndex = run {
                val element = json["categoryIndex"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'categoryIndex' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: categoryIndex in MostUsedApplications")
            val dataPoints = run {
                val element = json["dataPoints"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(MostUsedApplicationsDataPoint, allocator, element)
                }
            } ?: error("Missing required property: dataPoints in MostUsedApplications")
            return allocator.MostUsedApplications(
                categoryIndex = categoryIndex,
                dataPoints = dataPoints,
            )
        }
    }
}


fun BinaryAllocator.MostUsedApplications(
    categoryIndex: Int,
    dataPoints: BinaryTypeList<MostUsedApplicationsDataPoint>,
): MostUsedApplications {
    val result = this.allocate(MostUsedApplications)
    result.categoryIndex = categoryIndex
    result.dataPoints = dataPoints
    return result
}

@JvmInline
value class MostUsedApplicationsDataPoint(override val buffer: BufferAndOffset) : BinaryType {
    var _applicationName: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(0 + buffer.offset)))
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }
    val applicationName: String
        inline get() = _applicationName.decode()

    var numberOfJobs: Int
        inline get() = buffer.data.getInt(4 + buffer.offset)
        inline set (value) { buffer.data.putInt(4 + buffer.offset, value) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "applicationName" to (applicationName.let { JsonPrimitive(it) }),
        "numberOfJobs" to (numberOfJobs.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<MostUsedApplicationsDataPoint> {
        override val size = 8
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = MostUsedApplicationsDataPoint(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): MostUsedApplicationsDataPoint {
            if (json !is JsonObject) error("MostUsedApplicationsDataPoint must be decoded from an object")
            val applicationName = run {
                val element = json["applicationName"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'applicationName' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: applicationName in MostUsedApplicationsDataPoint")
            val numberOfJobs = run {
                val element = json["numberOfJobs"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'numberOfJobs' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: numberOfJobs in MostUsedApplicationsDataPoint")
            return allocator.MostUsedApplicationsDataPoint(
                applicationName = applicationName,
                numberOfJobs = numberOfJobs,
            )
        }
    }
}


fun BinaryAllocator.MostUsedApplicationsDataPoint(
    applicationName: String,
    numberOfJobs: Int,
): MostUsedApplicationsDataPoint {
    val result = this.allocate(MostUsedApplicationsDataPoint)
    result._applicationName = applicationName.let { allocateText(it) }
    result.numberOfJobs = numberOfJobs
    return result
}

@JvmInline
value class JobSubmissionStatistics(override val buffer: BufferAndOffset) : BinaryType {
    var categoryIndex: Int
        inline get() = buffer.data.getInt(0 + buffer.offset)
        inline set (value) { buffer.data.putInt(0 + buffer.offset, value) }

    var dataPoints: BinaryTypeList<JobSubmissionStatisticsDataPoint>
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(JobSubmissionStatisticsDataPoint, buffer.copy(offset = offset))) ?: error("Missing property dataPoints")
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "categoryIndex" to (categoryIndex.let { JsonPrimitive(it) }),
        "dataPoints" to (dataPoints.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<JobSubmissionStatistics> {
        override val size = 8
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = JobSubmissionStatistics(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): JobSubmissionStatistics {
            if (json !is JsonObject) error("JobSubmissionStatistics must be decoded from an object")
            val categoryIndex = run {
                val element = json["categoryIndex"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'categoryIndex' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: categoryIndex in JobSubmissionStatistics")
            val dataPoints = run {
                val element = json["dataPoints"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(JobSubmissionStatisticsDataPoint, allocator, element)
                }
            } ?: error("Missing required property: dataPoints in JobSubmissionStatistics")
            return allocator.JobSubmissionStatistics(
                categoryIndex = categoryIndex,
                dataPoints = dataPoints,
            )
        }
    }
}


fun BinaryAllocator.JobSubmissionStatistics(
    categoryIndex: Int,
    dataPoints: BinaryTypeList<JobSubmissionStatisticsDataPoint>,
): JobSubmissionStatistics {
    val result = this.allocate(JobSubmissionStatistics)
    result.categoryIndex = categoryIndex
    result.dataPoints = dataPoints
    return result
}

@JvmInline
value class JobSubmissionStatisticsDataPoint(override val buffer: BufferAndOffset) : BinaryType {
    var day: Byte
        inline get() = buffer.data.get(0 + buffer.offset)
        inline set (value) { buffer.data.put(0 + buffer.offset, value) }

    var hourOfDayStart: Byte
        inline get() = buffer.data.get(1 + buffer.offset)
        inline set (value) { buffer.data.put(1 + buffer.offset, value) }

    var hourOfDayEnd: Byte
        inline get() = buffer.data.get(2 + buffer.offset)
        inline set (value) { buffer.data.put(2 + buffer.offset, value) }

    var reserved1: Byte
        inline get() = buffer.data.get(3 + buffer.offset)
        inline set (value) { buffer.data.put(3 + buffer.offset, value) }

    var numberOfJobs: Int
        inline get() = buffer.data.getInt(4 + buffer.offset)
        inline set (value) { buffer.data.putInt(4 + buffer.offset, value) }

    var averageDurationInSeconds: Int
        inline get() = buffer.data.getInt(8 + buffer.offset)
        inline set (value) { buffer.data.putInt(8 + buffer.offset, value) }

    var averageQueueInSeconds: Int
        inline get() = buffer.data.getInt(12 + buffer.offset)
        inline set (value) { buffer.data.putInt(12 + buffer.offset, value) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "day" to (day.let { JsonPrimitive(it) }),
        "hourOfDayStart" to (hourOfDayStart.let { JsonPrimitive(it) }),
        "hourOfDayEnd" to (hourOfDayEnd.let { JsonPrimitive(it) }),
        "reserved1" to (reserved1.let { JsonPrimitive(it) }),
        "numberOfJobs" to (numberOfJobs.let { JsonPrimitive(it) }),
        "averageDurationInSeconds" to (averageDurationInSeconds.let { JsonPrimitive(it) }),
        "averageQueueInSeconds" to (averageQueueInSeconds.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<JobSubmissionStatisticsDataPoint> {
        override val size = 16
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = JobSubmissionStatisticsDataPoint(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): JobSubmissionStatisticsDataPoint {
            if (json !is JsonObject) error("JobSubmissionStatisticsDataPoint must be decoded from an object")
            val day = run {
                val element = json["day"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'day' to be a primitive")
                    element.content.toByte()
                }
            } ?: error("Missing required property: day in JobSubmissionStatisticsDataPoint")
            val hourOfDayStart = run {
                val element = json["hourOfDayStart"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'hourOfDayStart' to be a primitive")
                    element.content.toByte()
                }
            } ?: error("Missing required property: hourOfDayStart in JobSubmissionStatisticsDataPoint")
            val hourOfDayEnd = run {
                val element = json["hourOfDayEnd"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'hourOfDayEnd' to be a primitive")
                    element.content.toByte()
                }
            } ?: error("Missing required property: hourOfDayEnd in JobSubmissionStatisticsDataPoint")
            val reserved1 = run {
                val element = json["reserved1"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'reserved1' to be a primitive")
                    element.content.toByte()
                }
            } ?: error("Missing required property: reserved1 in JobSubmissionStatisticsDataPoint")
            val numberOfJobs = run {
                val element = json["numberOfJobs"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'numberOfJobs' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: numberOfJobs in JobSubmissionStatisticsDataPoint")
            val averageDurationInSeconds = run {
                val element = json["averageDurationInSeconds"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'averageDurationInSeconds' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: averageDurationInSeconds in JobSubmissionStatisticsDataPoint")
            val averageQueueInSeconds = run {
                val element = json["averageQueueInSeconds"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'averageQueueInSeconds' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: averageQueueInSeconds in JobSubmissionStatisticsDataPoint")
            return allocator.JobSubmissionStatisticsDataPoint(
                day = day,
                hourOfDayStart = hourOfDayStart,
                hourOfDayEnd = hourOfDayEnd,
                reserved1 = reserved1,
                numberOfJobs = numberOfJobs,
                averageDurationInSeconds = averageDurationInSeconds,
                averageQueueInSeconds = averageQueueInSeconds,
            )
        }
    }
}


fun BinaryAllocator.JobSubmissionStatisticsDataPoint(
    day: Byte,
    hourOfDayStart: Byte,
    hourOfDayEnd: Byte,
    reserved1: Byte,
    numberOfJobs: Int,
    averageDurationInSeconds: Int,
    averageQueueInSeconds: Int,
): JobSubmissionStatisticsDataPoint {
    val result = this.allocate(JobSubmissionStatisticsDataPoint)
    result.day = day
    result.hourOfDayStart = hourOfDayStart
    result.hourOfDayEnd = hourOfDayEnd
    result.reserved1 = reserved1
    result.numberOfJobs = numberOfJobs
    result.averageDurationInSeconds = averageDurationInSeconds
    result.averageQueueInSeconds = averageQueueInSeconds
    return result
}

