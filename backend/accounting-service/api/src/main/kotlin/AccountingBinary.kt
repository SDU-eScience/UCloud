// GENERATED CODE - DO NOT MODIFY - See AccountingBinary.msg
// GENERATED CODE - DO NOT MODIFY - See AccountingBinary.msg
// GENERATED CODE - DO NOT MODIFY - See AccountingBinary.msg

package dk.sdu.cloud.accounting.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dk.sdu.cloud.messages.*

enum class ProductTypeB(val encoded: Int, val serialName: String) {
    STORAGE(1, "STORAGE"),
    COMPUTE(2, "COMPUTE"),
    LICENSE(3, "LICENSE"),
    INGRESS(4, "INGRESS"),
    NETWORK_IP(5, "NETWORK_IP"),
    ;companion object {
        fun fromEncoded(encoded: Int): ProductTypeB {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): ProductTypeB {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class AccountingFrequencyB(val encoded: Int, val serialName: String) {
    ONCE(1, "ONCE"),
    PERIODIC_MINUTE(2, "PERIODIC_MINUTE"),
    PERIODIC_HOUR(3, "PERIODIC_HOUR"),
    PERIODIC_DAY(4, "PERIODIC_DAY"),
    ;companion object {
        fun fromEncoded(encoded: Int): AccountingFrequencyB {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): AccountingFrequencyB {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

@JvmInline
value class AccountingUnitB(override val buffer: BufferAndOffset) : BinaryType {
    var _name: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(0 + buffer.offset)))
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }
    val name: String
        inline get() = _name.decode()

    var _namePlural: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(4 + buffer.offset)))
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }
    val namePlural: String
        inline get() = _namePlural.decode()

    var floatingPoint: Boolean
        inline get() = buffer.data.get(8 + buffer.offset) == 1.toByte()
        inline set (value) { buffer.data.put(8 + buffer.offset, value.let { if (it) 1 else 0 }) }

    var displayFrequencySuffix: Boolean
        inline get() = buffer.data.get(9 + buffer.offset) == 1.toByte()
        inline set (value) { buffer.data.put(9 + buffer.offset, value.let { if (it) 1 else 0 }) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "name" to (name.let { JsonPrimitive(it) }),
        "namePlural" to (namePlural.let { JsonPrimitive(it) }),
        "floatingPoint" to (floatingPoint.let { JsonPrimitive(it) }),
        "displayFrequencySuffix" to (displayFrequencySuffix.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<AccountingUnitB> {
        override val size = 10
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = AccountingUnitB(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): AccountingUnitB {
            if (json !is JsonObject) error("AccountingUnitB must be decoded from an object")
            val name = run {
                val element = json["name"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'name' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: name in AccountingUnitB")
            val namePlural = run {
                val element = json["namePlural"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'namePlural' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: namePlural in AccountingUnitB")
            val floatingPoint = run {
                val element = json["floatingPoint"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'floatingPoint' to be a primitive")
                    element.content == "true"
                }
            } ?: error("Missing required property: floatingPoint in AccountingUnitB")
            val displayFrequencySuffix = run {
                val element = json["displayFrequencySuffix"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'displayFrequencySuffix' to be a primitive")
                    element.content == "true"
                }
            } ?: error("Missing required property: displayFrequencySuffix in AccountingUnitB")
            return allocator.AccountingUnitB(
                name = name,
                namePlural = namePlural,
                floatingPoint = floatingPoint,
                displayFrequencySuffix = displayFrequencySuffix,
            )
        }
    }
}


fun BinaryAllocator.AccountingUnitB(
    name: String,
    namePlural: String,
    floatingPoint: Boolean,
    displayFrequencySuffix: Boolean,
): AccountingUnitB {
    val result = this.allocate(AccountingUnitB)
    result._name = name.let { allocateText(it) }
    result._namePlural = namePlural.let { allocateText(it) }
    result.floatingPoint = floatingPoint
    result.displayFrequencySuffix = displayFrequencySuffix
    return result
}

@JvmInline
value class ProductCategoryB(override val buffer: BufferAndOffset) : BinaryType {
    var _name: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(0 + buffer.offset)))
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }
    val name: String
        inline get() = _name.decode()

    var _provider: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(4 + buffer.offset)))
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }
    val provider: String
        inline get() = _provider.decode()

    var productType: ProductTypeB
        inline get() = buffer.data.getShort(8 + buffer.offset).let { ProductTypeB.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(8 + buffer.offset, value.encoded.toShort()) }

    var accountingUnit: AccountingUnitB
        inline get() {
            val offset = buffer.data.getInt(10 + buffer.offset)
            return (if (offset == 0) {
                null
            } else {
                AccountingUnitB(buffer.copy(offset = offset))
            })!!
        }
        inline set(value) {
            buffer.data.putInt(10 + buffer.offset, value?.buffer?.offset ?: 0)
        }

    var accountingFrequency: AccountingFrequencyB
        inline get() = buffer.data.getShort(14 + buffer.offset).let { AccountingFrequencyB.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(14 + buffer.offset, value.encoded.toShort()) }

    var freeToUse: Boolean
        inline get() = buffer.data.get(16 + buffer.offset) == 1.toByte()
        inline set (value) { buffer.data.put(16 + buffer.offset, value.let { if (it) 1 else 0 }) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "name" to (name.let { JsonPrimitive(it) }),
        "provider" to (provider.let { JsonPrimitive(it) }),
        "productType" to (productType.let { JsonPrimitive(it.serialName) }),
        "accountingUnit" to (accountingUnit.let { it.encodeToJson() }),
        "accountingFrequency" to (accountingFrequency.let { JsonPrimitive(it.serialName) }),
        "freeToUse" to (freeToUse.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<ProductCategoryB> {
        override val size = 17
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = ProductCategoryB(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ProductCategoryB {
            if (json !is JsonObject) error("ProductCategoryB must be decoded from an object")
            val name = run {
                val element = json["name"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'name' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: name in ProductCategoryB")
            val provider = run {
                val element = json["provider"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'provider' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: provider in ProductCategoryB")
            val productType = run {
                val element = json["productType"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'productType' to be a primitive")
                    element.content.let { ProductTypeB.fromSerialName(it) }
                }
            } ?: error("Missing required property: productType in ProductCategoryB")
            val accountingUnit = run {
                val element = json["accountingUnit"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    AccountingUnitB.decodeFromJson(allocator, element)
                }
            } ?: error("Missing required property: accountingUnit in ProductCategoryB")
            val accountingFrequency = run {
                val element = json["accountingFrequency"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'accountingFrequency' to be a primitive")
                    element.content.let { AccountingFrequencyB.fromSerialName(it) }
                }
            } ?: error("Missing required property: accountingFrequency in ProductCategoryB")
            val freeToUse = run {
                val element = json["freeToUse"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'freeToUse' to be a primitive")
                    element.content == "true"
                }
            } ?: error("Missing required property: freeToUse in ProductCategoryB")
            return allocator.ProductCategoryB(
                name = name,
                provider = provider,
                productType = productType,
                accountingUnit = accountingUnit,
                accountingFrequency = accountingFrequency,
                freeToUse = freeToUse,
            )
        }
    }
}


fun BinaryAllocator.ProductCategoryB(
    name: String,
    provider: String,
    productType: ProductTypeB,
    accountingUnit: AccountingUnitB,
    accountingFrequency: AccountingFrequencyB,
    freeToUse: Boolean,
): ProductCategoryB {
    val result = this.allocate(ProductCategoryB)
    result._name = name.let { allocateText(it) }
    result._provider = provider.let { allocateText(it) }
    result.productType = productType
    result.accountingUnit = accountingUnit
    result.accountingFrequency = accountingFrequency
    result.freeToUse = freeToUse
    return result
}

@JvmInline
value class WalletAllocationB(override val buffer: BufferAndOffset) : BinaryType {
    var id: Long
        inline get() = buffer.data.getLong(0 + buffer.offset)
        inline set (value) { buffer.data.putLong(0 + buffer.offset, value) }

    var usage: Long
        inline get() = buffer.data.getLong(8 + buffer.offset)
        inline set (value) { buffer.data.putLong(8 + buffer.offset, value) }

    var localUsage: Long
        inline get() = buffer.data.getLong(16 + buffer.offset)
        inline set (value) { buffer.data.putLong(16 + buffer.offset, value) }

    var quota: Long
        inline get() = buffer.data.getLong(24 + buffer.offset)
        inline set (value) { buffer.data.putLong(24 + buffer.offset, value) }

    var startDate: Long
        inline get() = buffer.data.getLong(32 + buffer.offset)
        inline set (value) { buffer.data.putLong(32 + buffer.offset, value) }

    var endDate: Long
        inline get() = buffer.data.getLong(40 + buffer.offset)
        inline set (value) { buffer.data.putLong(40 + buffer.offset, value) }

    var categoryIndex: Int
        inline get() = buffer.data.getInt(48 + buffer.offset)
        inline set (value) { buffer.data.putInt(48 + buffer.offset, value) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "id" to (id.let { JsonPrimitive(it) }),
        "usage" to (usage.let { JsonPrimitive(it) }),
        "localUsage" to (localUsage.let { JsonPrimitive(it) }),
        "quota" to (quota.let { JsonPrimitive(it) }),
        "startDate" to (startDate.let { JsonPrimitive(it) }),
        "endDate" to (endDate.let { JsonPrimitive(it) }),
        "categoryIndex" to (categoryIndex.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<WalletAllocationB> {
        override val size = 52
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = WalletAllocationB(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): WalletAllocationB {
            if (json !is JsonObject) error("WalletAllocationB must be decoded from an object")
            val id = run {
                val element = json["id"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'id' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: id in WalletAllocationB")
            val usage = run {
                val element = json["usage"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'usage' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: usage in WalletAllocationB")
            val localUsage = run {
                val element = json["localUsage"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'localUsage' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: localUsage in WalletAllocationB")
            val quota = run {
                val element = json["quota"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'quota' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: quota in WalletAllocationB")
            val startDate = run {
                val element = json["startDate"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'startDate' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: startDate in WalletAllocationB")
            val endDate = run {
                val element = json["endDate"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'endDate' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: endDate in WalletAllocationB")
            val categoryIndex = run {
                val element = json["categoryIndex"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'categoryIndex' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: categoryIndex in WalletAllocationB")
            return allocator.WalletAllocationB(
                id = id,
                usage = usage,
                localUsage = localUsage,
                quota = quota,
                startDate = startDate,
                endDate = endDate,
                categoryIndex = categoryIndex,
            )
        }
    }
}


fun BinaryAllocator.WalletAllocationB(
    id: Long,
    usage: Long,
    localUsage: Long,
    quota: Long,
    startDate: Long,
    endDate: Long,
    categoryIndex: Int,
): WalletAllocationB {
    val result = this.allocate(WalletAllocationB)
    result.id = id
    result.usage = usage
    result.localUsage = localUsage
    result.quota = quota
    result.startDate = startDate
    result.endDate = endDate
    result.categoryIndex = categoryIndex
    return result
}

@JvmInline
value class UsageOverTimeDataPoint(override val buffer: BufferAndOffset) : BinaryType {
    var usage: Long
        inline get() = buffer.data.getLong(0 + buffer.offset)
        inline set (value) { buffer.data.putLong(0 + buffer.offset, value) }

    var quota: Long
        inline get() = buffer.data.getLong(8 + buffer.offset)
        inline set (value) { buffer.data.putLong(8 + buffer.offset, value) }

    var timestamp: Long
        inline get() = buffer.data.getLong(16 + buffer.offset)
        inline set (value) { buffer.data.putLong(16 + buffer.offset, value) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "usage" to (usage.let { JsonPrimitive(it) }),
        "quota" to (quota.let { JsonPrimitive(it) }),
        "timestamp" to (timestamp.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<UsageOverTimeDataPoint> {
        override val size = 24
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = UsageOverTimeDataPoint(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): UsageOverTimeDataPoint {
            if (json !is JsonObject) error("UsageOverTimeDataPoint must be decoded from an object")
            val usage = run {
                val element = json["usage"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'usage' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: usage in UsageOverTimeDataPoint")
            val quota = run {
                val element = json["quota"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'quota' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: quota in UsageOverTimeDataPoint")
            val timestamp = run {
                val element = json["timestamp"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'timestamp' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: timestamp in UsageOverTimeDataPoint")
            return allocator.UsageOverTimeDataPoint(
                usage = usage,
                quota = quota,
                timestamp = timestamp,
            )
        }
    }
}


fun BinaryAllocator.UsageOverTimeDataPoint(
    usage: Long,
    quota: Long,
    timestamp: Long,
): UsageOverTimeDataPoint {
    val result = this.allocate(UsageOverTimeDataPoint)
    result.usage = usage
    result.quota = quota
    result.timestamp = timestamp
    return result
}

@JvmInline
value class UsageOverTime(override val buffer: BufferAndOffset) : BinaryType {
    var data: BinaryTypeList<UsageOverTimeDataPoint>
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(UsageOverTimeDataPoint, buffer.copy(offset = offset))) ?: error("Missing property data")
        }
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "data" to (data.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<UsageOverTime> {
        override val size = 4
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = UsageOverTime(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): UsageOverTime {
            if (json !is JsonObject) error("UsageOverTime must be decoded from an object")
            val data = run {
                val element = json["data"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(UsageOverTimeDataPoint, allocator, element)
                }
            } ?: error("Missing required property: data in UsageOverTime")
            return allocator.UsageOverTime(
                data = data,
            )
        }
    }
}


fun BinaryAllocator.UsageOverTime(
    data: BinaryTypeList<UsageOverTimeDataPoint>,
): UsageOverTime {
    val result = this.allocate(UsageOverTime)
    result.data = data
    return result
}

@JvmInline
value class BreakdownByProjectPoint(override val buffer: BufferAndOffset) : BinaryType {
    var _title: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(0 + buffer.offset)))
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }
    val title: String
        inline get() = _title.decode()

    var _projectId: Text?
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return if (offset == 0) null else Text(buffer.copy(offset = offset))
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value?.buffer?.offset ?: 0) }
    val projectId: String?
        inline get() = _projectId?.decode()

    var usage: Long
        inline get() = buffer.data.getLong(8 + buffer.offset)
        inline set (value) { buffer.data.putLong(8 + buffer.offset, value) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "title" to (title.let { JsonPrimitive(it) }),
        "projectId" to (projectId?.let { JsonPrimitive(it) } ?: JsonNull),
        "usage" to (usage.let { JsonPrimitive(it) }),
    ))

    companion object : BinaryTypeCompanion<BreakdownByProjectPoint> {
        override val size = 16
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = BreakdownByProjectPoint(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): BreakdownByProjectPoint {
            if (json !is JsonObject) error("BreakdownByProjectPoint must be decoded from an object")
            val title = run {
                val element = json["title"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'title' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: title in BreakdownByProjectPoint")
            val projectId = run {
                val element = json["projectId"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'projectId' to be a primitive")
                    element.content
                }
            }
            val usage = run {
                val element = json["usage"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'usage' to be a primitive")
                    element.content.toLong()
                }
            } ?: error("Missing required property: usage in BreakdownByProjectPoint")
            return allocator.BreakdownByProjectPoint(
                title = title,
                projectId = projectId,
                usage = usage,
            )
        }
    }
}


fun BinaryAllocator.BreakdownByProjectPoint(
    title: String,
    projectId: String? = null,
    usage: Long,
): BreakdownByProjectPoint {
    val result = this.allocate(BreakdownByProjectPoint)
    result._title = title.let { allocateText(it) }
    result._projectId = projectId?.let { allocateText(it) }
    result.usage = usage
    return result
}

@JvmInline
value class BreakdownByProject(override val buffer: BufferAndOffset) : BinaryType {
    var data: BinaryTypeList<BreakdownByProjectPoint>
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(BreakdownByProjectPoint, buffer.copy(offset = offset))) ?: error("Missing property data")
        }
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "data" to (data.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<BreakdownByProject> {
        override val size = 4
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = BreakdownByProject(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): BreakdownByProject {
            if (json !is JsonObject) error("BreakdownByProject must be decoded from an object")
            val data = run {
                val element = json["data"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(BreakdownByProjectPoint, allocator, element)
                }
            } ?: error("Missing required property: data in BreakdownByProject")
            return allocator.BreakdownByProject(
                data = data,
            )
        }
    }
}


fun BinaryAllocator.BreakdownByProject(
    data: BinaryTypeList<BreakdownByProjectPoint>,
): BreakdownByProject {
    val result = this.allocate(BreakdownByProject)
    result.data = data
    return result
}

@JvmInline
value class Charts(override val buffer: BufferAndOffset) : BinaryType {
    var categories: BinaryTypeList<ProductCategoryB>
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(ProductCategoryB, buffer.copy(offset = offset))) ?: error("Missing property categories")
        }
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    var allocations: BinaryTypeList<WalletAllocationB>
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(WalletAllocationB, buffer.copy(offset = offset))) ?: error("Missing property allocations")
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }

    var charts: BinaryTypeList<ChartsForCategory>
        inline get() {
            val offset = buffer.data.getInt(8 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(ChartsForCategory, buffer.copy(offset = offset))) ?: error("Missing property charts")
        }
        inline set(value) { buffer.data.putInt(8 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "categories" to (categories.let { it.encodeToJson() }),
        "allocations" to (allocations.let { it.encodeToJson() }),
        "charts" to (charts.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<Charts> {
        override val size = 12
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = Charts(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): Charts {
            if (json !is JsonObject) error("Charts must be decoded from an object")
            val categories = run {
                val element = json["categories"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(ProductCategoryB, allocator, element)
                }
            } ?: error("Missing required property: categories in Charts")
            val allocations = run {
                val element = json["allocations"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(WalletAllocationB, allocator, element)
                }
            } ?: error("Missing required property: allocations in Charts")
            val charts = run {
                val element = json["charts"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(ChartsForCategory, allocator, element)
                }
            } ?: error("Missing required property: charts in Charts")
            return allocator.Charts(
                categories = categories,
                allocations = allocations,
                charts = charts,
            )
        }
    }
}


fun BinaryAllocator.Charts(
    categories: BinaryTypeList<ProductCategoryB>,
    allocations: BinaryTypeList<WalletAllocationB>,
    charts: BinaryTypeList<ChartsForCategory>,
): Charts {
    val result = this.allocate(Charts)
    result.categories = categories
    result.allocations = allocations
    result.charts = charts
    return result
}

@JvmInline
value class ChartsForCategory(override val buffer: BufferAndOffset) : BinaryType {
    var categoryIndex: Int
        inline get() = buffer.data.getInt(0 + buffer.offset)
        inline set (value) { buffer.data.putInt(0 + buffer.offset, value) }

    var overTime: UsageOverTime
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return (if (offset == 0) {
                null
            } else {
                UsageOverTime(buffer.copy(offset = offset))
            })!!
        }
        inline set(value) {
            buffer.data.putInt(4 + buffer.offset, value?.buffer?.offset ?: 0)
        }

    var breakdownByProject: BreakdownByProject
        inline get() {
            val offset = buffer.data.getInt(8 + buffer.offset)
            return (if (offset == 0) {
                null
            } else {
                BreakdownByProject(buffer.copy(offset = offset))
            })!!
        }
        inline set(value) {
            buffer.data.putInt(8 + buffer.offset, value?.buffer?.offset ?: 0)
        }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "categoryIndex" to (categoryIndex.let { JsonPrimitive(it) }),
        "overTime" to (overTime.let { it.encodeToJson() }),
        "breakdownByProject" to (breakdownByProject.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<ChartsForCategory> {
        override val size = 12
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = ChartsForCategory(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ChartsForCategory {
            if (json !is JsonObject) error("ChartsForCategory must be decoded from an object")
            val categoryIndex = run {
                val element = json["categoryIndex"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'categoryIndex' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: categoryIndex in ChartsForCategory")
            val overTime = run {
                val element = json["overTime"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    UsageOverTime.decodeFromJson(allocator, element)
                }
            } ?: error("Missing required property: overTime in ChartsForCategory")
            val breakdownByProject = run {
                val element = json["breakdownByProject"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BreakdownByProject.decodeFromJson(allocator, element)
                }
            } ?: error("Missing required property: breakdownByProject in ChartsForCategory")
            return allocator.ChartsForCategory(
                categoryIndex = categoryIndex,
                overTime = overTime,
                breakdownByProject = breakdownByProject,
            )
        }
    }
}


fun BinaryAllocator.ChartsForCategory(
    categoryIndex: Int,
    overTime: UsageOverTime,
    breakdownByProject: BreakdownByProject,
): ChartsForCategory {
    val result = this.allocate(ChartsForCategory)
    result.categoryIndex = categoryIndex
    result.overTime = overTime
    result.breakdownByProject = breakdownByProject
    return result
}

