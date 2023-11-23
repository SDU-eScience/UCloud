package dk.sdu.cloud.config

import com.charleskorn.kaml.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.providerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.ceil

// Core model
// =====================================================================================================================
@Serializable
data class ConfigProductSchema(
    val compute: List<Category>,
    val storage: List<Category>,
    val publicLinks: List<Category>,
    val publicIps: List<Category>,
    val licenses: List<Category>,
) {
    var productsUnknownToUCloud: Collection<ProductV2> = emptyList()

    val allCategories: Sequence<Category>
        get() = sequence {
            yieldAll(compute)
            yieldAll(storage)
            yieldAll(publicLinks)
            yieldAll(publicIps)
            yieldAll(licenses)
        }

    val allProducts: List<ProductV2>
        get() = allCategories.flatMap { it.coreProducts }.toList()

    fun findCategoryAndProduct(ref: ProductReferenceV2): Pair<Category, IndividualProduct<*>>? {
        val category = allCategories.find { it.name == ref.category } ?: return null
        val product = category.products.find { it.name == ref.id } ?: return null
        return category to product
    }

    fun findCategory(category: String): Category? {
        return allCategories.find { it.name == category }
    }
}

@Serializable
data class Category(
    val name: String,
    val type: ProductType,
    var cost: ProductCost,
    val products: List<IndividualProduct<*>> = ArrayList(),
) {
    lateinit var category: ProductCategory
        private set
    lateinit var coreProducts: List<ProductV2>
        private set

    internal fun init() {
        var c = cost ?: error("The category must have a cost at this point")
        if (c is ProductCost.Resource && c.unit == null && type == ProductType.COMPUTE) {
            cost = c.copy(unit = ComputeResourceType.Cpu.name)
            c = cost
        }
        category = ProductCategory(
            name,
            providerId,
            type,
            when (c) {
                ProductCost.Free -> accUnit("Free")
                is ProductCost.Money -> accUnit(
                    buildString {
                        append(c.currency)
                        if (c.unit != null) {
                            append(" per ")
                            append(c.prettyUnit ?: c.unit)
                        }
                    },
                    floatingPoint = true,
                    displaySuffix = true,
                )
                is ProductCost.Resource -> accUnit(
                    c.selectPrettyUnitOrDefault(type),
                    displaySuffix = c.accountingInterval != null,
                )
            },
            when (c) {
                ProductCost.Free -> AccountingFrequency.ONCE
                is ProductCost.Money -> c.interval.toAccountingFrequency()
                is ProductCost.Resource -> c.accountingInterval.toAccountingFrequency()
            }
        )

        coreProducts = products.map { p ->
            when (p.spec) {
                is IndividualProduct.ProductSpec.Compute -> {
                    ProductV2.Compute(
                        p.name, p.price, category, p.description,
                        p.spec.cpu,
                        p.spec.memory,
                        p.spec.gpu,
                        p.spec.cpuModel,
                        p.spec.memoryModel,
                        p.spec.gpuModel,
                        hiddenInGrantApplications = false,
                    )
                }

                is IndividualProduct.ProductSpec.License -> {
                    ProductV2.License(
                        p.name, p.price, category, p.description,
                        p.spec.tags,
                        hiddenInGrantApplications = false,
                    )
                }

                is IndividualProduct.ProductSpec.PublicIp -> {
                    ProductV2.NetworkIP(
                        p.name, p.price, category, p.description,
                        hiddenInGrantApplications = false,
                    )
                }
                is IndividualProduct.ProductSpec.PublicLink -> {
                    ProductV2.Ingress(
                        p.name, p.price, category, p.description,
                        hiddenInGrantApplications = false,
                    )
                }
                is IndividualProduct.ProductSpec.Storage -> {
                    ProductV2.Storage(
                        p.name, p.price, category, p.description,
                        hiddenInGrantApplications = false,
                    )
                }
            }.also {
                require(type == it.productType) { "mismatch in product types" }
            }
        }
    }
}

private fun ProductCost.AccountingInterval?.toAccountingFrequency(): AccountingFrequency {
    return when (this) {
        ProductCost.AccountingInterval.Minutely -> AccountingFrequency.PERIODIC_MINUTE
        ProductCost.AccountingInterval.Hourly -> AccountingFrequency.PERIODIC_HOUR
        ProductCost.AccountingInterval.Daily -> AccountingFrequency.PERIODIC_DAY
        null -> AccountingFrequency.ONCE
    }
}

private fun accUnit(
    name: String,
    namePlural: String? = null,
    floatingPoint: Boolean = false,
    displaySuffix: Boolean = false
) = AccountingUnit(name, namePlural ?: name, floatingPoint, displaySuffix)

enum class StorageUnit(val bytes: Long) {
    MB(1000 * 1000L),
    MiB(1024 * 1024L),
    GB(1000 * 1000 * 1000L),
    GiB(1024 * 1024 * 1024L),
    TB(1000 * 1000 * 1000 * 1000L),
    TiB(1024 * 1024 * 1024 * 1024L),
    ;

    fun convertToThisUnitFromBytes(bytes: Long): Long {
        return ceil(bytes.toDouble() / this.bytes).toLong()
    }

    fun convertFromThisUnitToBytes(amount: Long): Long {
        return amount * bytes
    }
}

enum class ComputeResourceType {
    Cpu,
    Memory,
    Gpu
}

@Serializable
data class IndividualProduct<out S : IndividualProduct.ProductSpec>(
    val spec: S,

    var name: String = "",
    var description: String = "",
    var price: Long = 0L,
) {
    @Serializable
    sealed class ProductSpec {
        @Serializable
        @SerialName("Compute")
        data class Compute(
            var cpu: Int = 0,
            var memory: Int = 0,
            var gpu: Int = 0,
            var cpuModel: String? = null,
            var memoryModel: String? = null,
            var gpuModel: String? = null,
        ) : ProductSpec() {
            fun getResource(resource: ComputeResourceType): Int {
                return when (resource) {
                    ComputeResourceType.Cpu -> cpu
                    ComputeResourceType.Memory -> memory
                    ComputeResourceType.Gpu -> gpu
                }
            }
        }

        @Serializable
        @SerialName("PublicLink")
        class PublicLink() : ProductSpec()
        @Serializable
        @SerialName("PublicIp")
        class PublicIp() : ProductSpec()
        @Serializable
        @SerialName("License")
        data class License(val tags: List<String>) : ProductSpec()
        @Serializable
        @SerialName("Storage")
        data class Storage(val unit: StorageUnit?) : ProductSpec()
    }
}

@Serializable
sealed class ProductCost {
    abstract val type: Type

    @Serializable
    @SerialName("Money")
    data class Money(
        val currency: String,
        override val unit: String? = null,
        override var prettyUnit: String? = null,
    ) : ProductCost(), WithUnit {
        @Transient override val type = Type.Money
        var interval: AccountingInterval? = null

        // This cost model uses money. If no unit is specified, then the cost is "price per interval of use".
        // If the unit is specified, then the cost is "price per unit per interval of use". A price is always defined
        // per product. If interval is null, then the product is considered a one-time payment as opposed to a
        // recurring one.

        // Examples of this model include:
        // - DKK (currency = DKK, unit = null, interval = null)
        // - DKK/hour (currency = DKK, unit = null, interval = Hourly)
        // - EUR per GiB/day (currency = EUR, unit = GiB, interval = Daily)

        fun updateInterval(node: YamlNode, interval: AccountingInterval) {
            if (this.interval != null && this.interval != interval) {
                parserError(node, "You must supply pricing intervals consistently within a single category")
            }

            this.interval = interval
        }
    }

    interface WithUnit {
        val unit: String?
        var prettyUnit: String?

        fun selectPrettyUnitOrDefault(type: ProductType): String {
            return prettyUnit ?: unit ?: when (type) {
                ProductType.STORAGE -> "GB"
                ProductType.COMPUTE -> "Core"
                ProductType.INGRESS -> "Link"
                ProductType.LICENSE -> "License"
                ProductType.NETWORK_IP -> "IP address"
            }
        }
    }

    @Serializable
    @SerialName("Resource")
    data class Resource(
        override val unit: String? = null,
        override var prettyUnit: String? = null,
        val accountingInterval: AccountingInterval? = null,
    ) : ProductCost(), WithUnit {
        @Transient override val type = Type.Resource
    }

    @Serializable
    @SerialName("Free")
    object Free : ProductCost() {
        @Transient override val type = Type.Free
    }

    enum class Type {
        Money,
        Resource,
        Free,
    }

    enum class AccountingInterval(val minutes: Long) {
        Minutely(1),
        Hourly(60),
        Daily(60 * 24),
        ;

        data class ConversionResult(val wholePart: Long, val remainingInMilliseconds: Long)

        fun convertFromMillis(millis: Long): ConversionResult {
            val targetInMillis = minutes * 1_000 * 60
            val wholePart = millis / targetInMillis
            val remaining = millis % targetInMillis
            return ConversionResult(wholePart, remaining)
        }
    }
}

// General product parsing
// =====================================================================================================================
fun parseProductConfiguration(data: String): ConfigProductSchema {
    val root = assertNodeType<YamlMap>(yamlMapper.parseToYamlNode(data))
    val compute = root.getChildNode<YamlMap>("compute")
    val storage = root.getChildNode<YamlMap>("storage")
    val publicLinks = root.getChildNode<YamlMap>("publicLinks")
    val publicIps = root.getChildNode<YamlMap>("publicIps")
    val licenses = root.getChildNode<YamlMap>("licenses")

    fun parseType(type: ProductType, section: YamlMap?): List<Category> {
        return ArrayList<Category>().apply {
            section?.entries?.forEach { (key, value) ->
                add(parseCategory(key.content, assertNodeType<YamlMap>(value), type))
            }
        }
    }

    return ConfigProductSchema(
        parseType(ProductType.COMPUTE, compute),
        parseType(ProductType.STORAGE, storage),
        parseType(ProductType.INGRESS, publicLinks),
        parseType(ProductType.NETWORK_IP, publicIps),
        parseType(ProductType.LICENSE, licenses),
    )
}

private fun parseCategory(
    categoryName: String,
    node: YamlMap,
    type: ProductType,
): Category {
    val costNode = node.getChildNode<YamlMap>("cost") ?: parserError(node, "missing 'cost' in category")
    val cost = parseCost(costNode)

    val allProducts = ArrayList<IndividualProduct<*>>()

    for ((sectionName, section) in node.entries) {
        if (sectionName.content == "cost") continue
        val isTemplated = sectionName.content == "template"

        val sectionMap = assertNodeType<YamlMap>(section)

        val subsection = when (type) {
            ProductType.COMPUTE -> parseComputeProducts(sectionMap, cost, costNode)
            ProductType.INGRESS -> parsePublicLinkProducts(sectionMap, cost, costNode)
            ProductType.LICENSE -> parseLicenseProducts(sectionMap, cost, costNode)
            ProductType.NETWORK_IP -> parsePublicIpProducts(sectionMap, cost, costNode)
            ProductType.STORAGE -> parseStorageProducts(sectionMap, cost, costNode)
        }

        // Determine product names and description
        val description = sectionMap.getChildNode<YamlNode>("description")?.asString()
            ?: parserError(sectionMap, "Missing description")

        for (product in subsection.products) {
            product.description = description
        }

        if (isTemplated) {
            val namePrefix = sectionMap.getChildNode<YamlNode>("namePrefix")?.asString() ?: categoryName
            val nameSuffix = sectionMap.getChildNode<YamlNode>("nameSuffix")

            for (product in subsection.products) {
                product.name = buildString {
                    append(namePrefix)
                    append('-')
                    append(subsection.nameSuffixFunction(product, nameSuffix))
                }
            }
        } else {
            for (product in subsection.products) {
                product.name = sectionName.asString()
            }
        }

        // Determine product prices
        val pricePerMinute = sectionMap.getChildNode<YamlNode>("pricePerMinute")
        val pricePerHour = sectionMap.getChildNode<YamlNode>("pricePerHour")
        val pricePerDay = sectionMap.getChildNode<YamlNode>("pricePerDay")
        val price = sectionMap.getChildNode<YamlNode>("price")

        val interval = when {
            price == null && pricePerMinute != null && pricePerHour == null && pricePerDay == null -> ProductCost.AccountingInterval.Minutely
            price == null && pricePerMinute == null && pricePerHour != null && pricePerDay == null -> ProductCost.AccountingInterval.Hourly
            price == null && pricePerMinute == null && pricePerHour == null && pricePerDay != null -> ProductCost.AccountingInterval.Daily
            price != null && pricePerMinute == null && pricePerHour == null && pricePerDay == null -> null
            else -> null
        }

        val priceNode = when (interval) {
            ProductCost.AccountingInterval.Minutely -> pricePerMinute
            ProductCost.AccountingInterval.Hourly -> pricePerHour
            ProductCost.AccountingInterval.Daily -> pricePerDay
            null -> price
        }

        when (cost) {
            ProductCost.Free -> {
                if (interval != null) {
                    parserError(
                        sectionMap,
                        "You cannot supply a price in this cost mode. Did you mean to use `type: Money`?"
                    )
                }
                for (product in subsection.products) product.price = 0
            }

            is ProductCost.Resource -> {
                if (interval != null) {
                    parserError(
                        sectionMap,
                        "You cannot supply a price in this cost mode. Did you mean to use `type: Money`?"
                    )
                }
                for (product in subsection.products) {
                    product.price = subsection.resourcesUsedPerProduct(product)
                        ?: parserError(sectionMap, "Price was not correctly supplied.")
                }
            }

            is ProductCost.Money -> {
                if (priceNode == null) {
                    parserError(
                        sectionMap,
                        "You must supply a price when using `type: Money` as the cost model. Use one of the " +
                                "following properties: price, pricePerMinute, pricePerHour, pricePerDay."
                    )
                }

                if (interval != null) cost.updateInterval(priceNode, interval)

                if (priceNode is YamlList) {
                    if (priceNode.items.size != subsection.products.size) {
                        parserError(priceNode, "Inconsistent size of price. It should match the number of products.")
                    }

                    for (i in 0 until subsection.products.size) {
                        subsection.products[i].price = priceNode.items[i].asAccountingFloat()
                    }
                } else {
                    val asFloat = priceNode.asAccountingFloat()
                    for (product in subsection.products) product.price = asFloat * subsection.scalingFunction(product)
                }
            }
        }

        if (!isTemplated && subsection.products.size != 1) {
            parserError(sectionMap, "Only the 'template' section can produce multiple products!")
        }

        allProducts.addAll(subsection.products)
    }

    return Category(categoryName, type, cost, allProducts)
}

private fun parseCost(node: YamlMap): ProductCost {
    val type = node.getChildNode<YamlNode>("type")?.asEnum<ProductCost.Type>()
        ?: parserError(node, "Missing 'type' in cost")

    return when (type) {
        ProductCost.Type.Free -> ProductCost.Free

        ProductCost.Type.Money -> {
            val currency = node.getChildNode<YamlNode>("currency")?.asString() ?: "DKK"
            val unit = node.getChildNode<YamlNode>("unit")?.asString()
            ProductCost.Money(currency, unit)
        }

        ProductCost.Type.Resource -> {
            val unit = node.getChildNode<YamlNode>("unit")?.asString()
            val interval = node.getChildNode<YamlNode>("interval")?.asEnum<ProductCost.AccountingInterval>()
            ProductCost.Resource(unit, null, interval)
        }
    }
}

private class ProductSubSection(
    val products: List<IndividualProduct<*>>,
    val scalingFunction: (IndividualProduct<*>) -> Int,
    val resourcesUsedPerProduct: (IndividualProduct<*>) -> Long?,
    val nameSuffixFunction: (product: IndividualProduct<*>, nameSuffix: YamlNode?) -> String,
)

// Parsing related to specific product types
// =====================================================================================================================
private fun parseStorageProducts(
    template: YamlMap,
    cost: ProductCost,
    costNode: YamlMap,
): ProductSubSection {
    val storageUnit = if (cost is ProductCost.WithUnit) {
        val unit = cost.unit ?: parserError(costNode, "Missing 'unit' specified. Example: GB.")
        YamlScalar(unit, costNode.path).asEnum<StorageUnit>()
    } else {
        null
    }

    if (cost is ProductCost.WithUnit && storageUnit != null) {
        cost.prettyUnit = storageUnit.name
    }

    return ProductSubSection(
        listOf(IndividualProduct(IndividualProduct.ProductSpec.Storage(storageUnit))),
        scalingFunction = { 1 },
        resourcesUsedPerProduct = { 1 },
        nameSuffixFunction = { p, s -> "" }
    )
}

private fun parseLicenseProducts(
    template: YamlMap,
    cost: ProductCost,
    costNode: YamlMap,
): ProductSubSection {
    val tagNode = template.getChildNode<YamlList>("tags") ?: parserError(template, "Missing tags")
    val tags = tagNode.items.map { it.asString() }
    return ProductSubSection(
        listOf(IndividualProduct(IndividualProduct.ProductSpec.License(tags))),
        scalingFunction = { 1 },
        resourcesUsedPerProduct = { 1 },
        nameSuffixFunction = { p, s -> "" }
    )
}

private fun parsePublicIpProducts(
    template: YamlMap,
    cost: ProductCost,
    costNode: YamlMap,
): ProductSubSection {
    return ProductSubSection(
        listOf(IndividualProduct(IndividualProduct.ProductSpec.PublicIp())),
        scalingFunction = { 1 },
        resourcesUsedPerProduct = { 1 },
        nameSuffixFunction = { p, s -> "" }
    )
}

private fun parsePublicLinkProducts(
    template: YamlMap,
    cost: ProductCost,
    costNode: YamlMap,
): ProductSubSection {
    return ProductSubSection(
        listOf(IndividualProduct(IndividualProduct.ProductSpec.PublicLink())),
        scalingFunction = { 1 },
        resourcesUsedPerProduct = { 1 },
        nameSuffixFunction = { p, s -> "" }
    )
}

private fun parseComputeProducts(
    template: YamlMap,
    cost: ProductCost,
    costNode: YamlMap,
): ProductSubSection {
    val products = ArrayList<IndividualProduct<IndividualProduct.ProductSpec.Compute>>()
    val accountingResource = when (cost) {
        ProductCost.Free -> null
        is ProductCost.Money -> null
        is ProductCost.Resource -> {
            val unit = cost.unit ?: ComputeResourceType.Cpu.name
            val scalar = YamlScalar(unit, (costNode.getChildNode<YamlNode>("unit") ?: costNode).path)
            scalar.asEnum<ComputeResourceType>()
        }
    }

    if (accountingResource != null && cost is ProductCost.WithUnit) {
        when (accountingResource) {
            ComputeResourceType.Cpu -> cost.prettyUnit = "Core"
            ComputeResourceType.Memory -> cost.prettyUnit = "GB of memory"
            ComputeResourceType.Gpu -> cost.prettyUnit = "GPU"
        }
    }

    // Determine product dimensions

    val cpu = template.getChildNode<YamlNode>("cpu") ?: parserError(template, "Missing 'cpu' in product")
    val memory =
        template.getChildNode<YamlNode>("memory") ?: parserError(template, "Missing 'memory' in product")
    val gpu = template.getChildNode<YamlNode>("gpu")

    val scalingFactor = when {
        cpu is YamlList && memory is YamlList && gpu is YamlList -> null
        cpu !is YamlList && memory !is YamlList && gpu !is YamlList -> null
        cpu is YamlList && memory !is YamlList && gpu !is YamlList -> ComputeResourceType.Cpu
        cpu !is YamlList && memory is YamlList && gpu !is YamlList -> ComputeResourceType.Memory
        cpu !is YamlList && memory !is YamlList && gpu is YamlList -> ComputeResourceType.Gpu

        else -> {
            parserError(
                template,
                "Either all resources must be specified as a list or only a single resource is specified " +
                        "via a list (see cpu, memory and gpu)"
            )
        }
    }

    val productCount = when (scalingFactor) {
        ComputeResourceType.Cpu -> (cpu as YamlList).items.size
        ComputeResourceType.Memory -> (memory as YamlList).items.size
        ComputeResourceType.Gpu -> (gpu as YamlList).items.size
        null -> (cpu as? YamlList)?.items?.size ?: 1
    }

    repeat(productCount) { products.add(IndividualProduct(IndividualProduct.ProductSpec.Compute())) }

    if (cpu is YamlList) {
        if (cpu.items.size != productCount) parserError(cpu, "Inconsistent size between resources")
        for (i in 0 until productCount) {
            products[i].spec.cpu = cpu.items[i].asInt()
        }
    } else {
        val cpuInt = cpu.asInt()
        for (i in 0 until productCount) {
            products[i].spec.cpu = cpuInt
        }
    }

    if (memory is YamlList) {
        if (memory.items.size != productCount) parserError(memory, "Inconsistent size between resources")
        for (i in 0 until productCount) {
            products[i].spec.memory = memory.items[i].asInt()
        }
    } else {
        val asInt = memory.asInt()
        for (i in 0 until productCount) {
            products[i].spec.memory = asInt
        }
    }

    if (gpu is YamlList) {
        if (gpu.items.size != productCount) parserError(gpu, "Inconsistent size between resources")
        for (i in 0 until productCount) {
            products[i].spec.gpu = gpu.items[i].asInt()
        }
    } else if (gpu != null) {
        val asInt = gpu.asInt()
        for (i in 0 until productCount) {
            products[i].spec.gpu = asInt
        }
    }

    if (scalingFactor != null) {
        if (scalingFactor != ComputeResourceType.Cpu) {
            for (product in products) product.spec.cpu *= product.spec.getResource(scalingFactor)
        }

        if (scalingFactor != ComputeResourceType.Memory) {
            for (product in products) product.spec.memory *= product.spec.getResource(scalingFactor)
        }

        if (scalingFactor != ComputeResourceType.Gpu) {
            for (product in products) product.spec.gpu *= product.spec.getResource(scalingFactor)
        }
    }

    // Determine model names
    val cpuModel = template.getChildNode<YamlNode>("cpuModel")?.asString()
    val gpuModel = template.getChildNode<YamlNode>("gpuModel")?.asString()
    val memoryModel = template.getChildNode<YamlNode>("memoryModel")?.asString()
    for (product in products) {
        product.spec.cpuModel = cpuModel
        product.spec.gpuModel = gpuModel
        product.spec.memoryModel = memoryModel
    }

    return ProductSubSection(
        products,
        scalingFunction = { p ->
            if (scalingFactor == null) 1
            else (p.spec as IndividualProduct.ProductSpec.Compute).getResource(scalingFactor)
        },
        resourcesUsedPerProduct = { p ->
            if (accountingResource == null) null
            else (p.spec as IndividualProduct.ProductSpec.Compute).getResource(accountingResource).toLong()
        },
        nameSuffixFunction = { p, suffix ->
            val suffixResource = suffix?.asEnum<ComputeResourceType>() ?: ComputeResourceType.Cpu
            (p.spec as IndividualProduct.ProductSpec.Compute).getResource(suffixResource).toString()
        }
    )
}

// Utilities
// =====================================================================================================================
fun ProductV2.explainPricing(): String {
    if (price == 0L) return "Free"

    if (price == 1L && category.accountingFrequency == AccountingFrequency.ONCE) {
        return "Quota based (${category.accountingUnit.name})"
    } else if (category.accountingFrequency == AccountingFrequency.ONCE) {
        return if (category.accountingUnit.floatingPoint) {
            val mc = MathContext.DECIMAL128
            val price = BigDecimal(price, mc).divide(BigDecimal(1_000_000L, mc), mc).toPlainString()
            "$price ${category.accountingUnit.name}"
        } else {
            "$price ${category.accountingUnit.name}"
        }
    }

    return buildString {
        append(if (category.accountingUnit.floatingPoint) {
            val mc = MathContext.DECIMAL128
            BigDecimal(price, mc).divide(BigDecimal(1_000_000L, mc), mc).toPlainString()
        } else {
            price.toString()
        })
        append(" ")
        append(category.accountingUnit.name)
        if (category.accountingUnit.displayFrequencySuffix) {
            append("/")
            append(when (category.accountingFrequency) {
                AccountingFrequency.ONCE -> ""
                AccountingFrequency.PERIODIC_MINUTE -> "minute"
                AccountingFrequency.PERIODIC_HOUR -> "hour"
                AccountingFrequency.PERIODIC_DAY -> "day"
            })
        }
    }
}

fun ProductV2.toReference(): ProductReference = ProductReference(name, category.name, category.provider)

// "Low-level" YAML parsing
// =====================================================================================================================
private fun YamlNode.asAccountingFloat(): Long {
    val stringValue = asString()
    val before = stringValue.substringBefore('.').toLongOrNull() ?: parserError(this, "Invalid number supplied.")
    val afterText = stringValue.substringAfter('.', "").padEnd(6, '0')
    val after = afterText.toLongOrNull() ?: parserError(this, "Invalid number supplied.")
    if (after < 0L) parserError(this, "Invalid number supplied.")

    if (before < 0L) parserError(this, "This value cannot be negative")
    if (before >= Long.MAX_VALUE / 1_000_000L) {
        parserError(this, "This value is too large. We will not be able to precisely represent this value.")
    }

    if (after >= 1_000_000L) {
        parserError(this, "Too many digits after the decimal point. We cannot represent such a number precisely.")
    }

    return before * 1_000_000L + after
}

private inline fun <reified T : Enum<T>> YamlNode.asEnum(): T {
    val stringValue = asString()
    val values = enumValues<T>()
    return values.find { it.name == stringValue }
        ?: parserError(this, "Invalid option supplied. Valid options are: ${values.joinToString(", ") { it.name }}")
}

private inline fun <reified T : YamlNode> assertNodeType(node: YamlNode): T {
    return (node as? T) ?: parserError(node, "Invalid type")
}

private inline fun <reified T : YamlNode> YamlMap.getChildNode(child: String): T? {
    return entries.entries.find { it.key.content == child }?.value?.let { assertNodeType(it) }
}

private fun YamlNode.asString(): String {
    if (this is YamlList) {
        val single = items.singleOrNull() ?: parserError(this, "Expected a string")
        return single.asString()
    }
    val scalar = assertNodeType<YamlScalar>(this)
    return scalar.content
}

private fun YamlNode.asBoolean(): Boolean {
    if (this is YamlList) {
        val single = items.singleOrNull() ?: parserError(this, "Expected a boolean")
        return single.asBoolean()
    }
    val scalar = assertNodeType<YamlScalar>(this)
    if (scalar.content.equals("true", ignoreCase = true)) return true
    else if (scalar.content.equals("false", ignoreCase = true)) return false
    parserError(this, "Expected a boolean")
}

private fun YamlNode.asDouble(): Double {
    if (this is YamlList) {
        val single = items.singleOrNull() ?: parserError(this, "Expected a floating point number")
        return single.asDouble()
    }
    val scalar = assertNodeType<YamlScalar>(this)
    return scalar.content.toDoubleOrNull() ?: parserError(this, "Expected a floating point number")
}

private fun YamlNode.asLong(): Long {
    if (this is YamlList) {
        val single = items.singleOrNull() ?: parserError(this, "Expected an integer")
        return single.asLong()
    }
    val scalar = assertNodeType<YamlScalar>(this)
    return scalar.content.toLongOrNull() ?: parserError(this, "Expected an integer")
}

private fun YamlNode.asInt(): Int {
    if (this is YamlList) {
        val single = items.singleOrNull() ?: parserError(this, "Expected an integer")
        return single.asInt()
    }
    val scalar = assertNodeType<YamlScalar>(this)
    return scalar.content.toIntOrNull() ?: parserError(this, "Expected an integer")
}

private fun parserError(node: YamlNode, message: String): Nothing {
    error("Error ${node.location.line}:${node.location.column}: $message")
}

private val yamlMapper = Yaml(
    configuration = YamlConfiguration(
        polymorphismStyle = PolymorphismStyle.Property,
        strictMode = false,
    )
)
