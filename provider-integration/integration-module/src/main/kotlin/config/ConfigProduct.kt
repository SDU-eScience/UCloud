package dk.sdu.cloud.config

import dk.sdu.cloud.accounting.api.ChargeType
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ConfigProduct<T : Product> {
    abstract val name: String
    abstract val description: String
    abstract val cost: ConfigProductCost

    abstract fun toProduct(category: String, provider: String): T

    protected fun category(category: String, provider: String): ProductCategoryId {
        return ProductCategoryId(category, provider)
    }

    protected fun pricePerUnit(): Long {
        if (cost.quota) {
            if (cost.price != null) throw IllegalArgumentException("Price not supported when quota = true for $name")
            return 1
        }

        if (cost.currency == ConfigProductCost.Currency.FREE) {
            if (cost.price != null) throw IllegalArgumentException("Price not supported when cost.currency = FREE for $name")
            return 1
        }

        if (cost.currency == ConfigProductCost.Currency.UNITS) {
            if (cost.price != null) throw IllegalArgumentException("Price not supported when cost.currency = UNITS for $name")
            return 1
        }

        val price = cost.price ?: throw IllegalArgumentException("Price is required when currency = DKK for $name")

        val beforeDecimal = price.substringBefore('.').toIntOrNull() ?:
            throw IllegalArgumentException("Invalid price supplied for $name")
        val afterDecimal = price.substringAfter('.', "").padEnd(6, '0').toIntOrNull() ?:
            throw IllegalArgumentException("Invalid price supplied for $name")

        if (afterDecimal > 999_999) throw IllegalArgumentException("Unsupported level of precision for price in $name")

        return ((beforeDecimal * 1_000_000L) + afterDecimal) / pricePerUnitMultiplier()
    }

    protected open fun pricePerUnitMultiplier(): Long = 1L

    protected fun chargeType(): ChargeType {
        return if (cost.quota) ChargeType.DIFFERENTIAL_QUOTA else ChargeType.ABSOLUTE
    }

    protected fun unitOfPrice(): ProductPriceUnit {
        if (cost.quota) return ProductPriceUnit.PER_UNIT
        return when (cost.currency) {
            ConfigProductCost.Currency.FREE -> {
                ProductPriceUnit.PER_UNIT
            }

            ConfigProductCost.Currency.DKK -> {
                when (cost.frequency) {
                    ConfigProductCost.Frequency.ONE_TIME -> ProductPriceUnit.CREDITS_PER_UNIT
                    ConfigProductCost.Frequency.MINUTE -> ProductPriceUnit.CREDITS_PER_MINUTE
                    ConfigProductCost.Frequency.HOUR -> ProductPriceUnit.CREDITS_PER_HOUR
                    ConfigProductCost.Frequency.DAY -> ProductPriceUnit.CREDITS_PER_DAY
                    null -> throw IllegalArgumentException("Invalid cost.frequency for $name")
                }
            }

            ConfigProductCost.Currency.UNITS -> {
                when (cost.frequency) {
                    ConfigProductCost.Frequency.ONE_TIME -> ProductPriceUnit.PER_UNIT
                    ConfigProductCost.Frequency.MINUTE -> ProductPriceUnit.UNITS_PER_MINUTE
                    ConfigProductCost.Frequency.HOUR -> ProductPriceUnit.UNITS_PER_HOUR
                    ConfigProductCost.Frequency.DAY -> ProductPriceUnit.UNITS_PER_DAY
                    null -> throw IllegalArgumentException("Invalid cost.frequency for $name")
                }
            }

            null -> throw IllegalArgumentException("Missing currency for $name (did you intend to use `quota: true`?)")
        }
    }

    protected fun isFree() = cost.currency == ConfigProductCost.Currency.FREE

    @Serializable
    @SerialName("storage")
    data class Storage(
        override val name: String,
        override val description: String,
        override val cost: ConfigProductCost,
    ) : ConfigProduct<Product.Storage>() {
        override fun toProduct(category: String, provider: String): Product.Storage {
            return Product.Storage(
                name = name,
                pricePerUnit = pricePerUnit(),
                category = category(category, provider),
                description = description,
                unitOfPrice = unitOfPrice(),
                chargeType = chargeType(),
                freeToUse = isFree(),
            )
        }
    }

    @Serializable
    @SerialName("ingress")
    data class Ingress(
        override val name: String,
        override val description: String,
        override val cost: ConfigProductCost,
    ) : ConfigProduct<Product.Ingress>() {
        override fun toProduct(category: String, provider: String): Product.Ingress {
            return Product.Ingress(
                name = name,
                pricePerUnit = pricePerUnit(),
                category = category(category, provider),
                description = description,
                unitOfPrice = unitOfPrice(),
                chargeType = chargeType(),
                freeToUse = isFree(),
            )
        }
    }

    @Serializable
    @SerialName("networkip")
    data class NetworkIP(
        override val name: String,
        override val description: String,
        override val cost: ConfigProductCost,
    ) : ConfigProduct<Product.NetworkIP>() {
        override fun toProduct(category: String, provider: String): Product.NetworkIP {
            return Product.NetworkIP(
                name = name,
                pricePerUnit = pricePerUnit(),
                category = category(category, provider),
                description = description,
                unitOfPrice = unitOfPrice(),
                chargeType = chargeType(),
                freeToUse = isFree(),
            )
        }
    }

    @Serializable
    @SerialName("license")
    data class License(
        override val name: String,
        override val description: String,
        override val cost: ConfigProductCost,
        val tags: List<String>
    ) : ConfigProduct<Product.License>() {
        override fun toProduct(category: String, provider: String): Product.License {
            return Product.License(
                name = name,
                pricePerUnit = pricePerUnit(),
                category = category(category, provider),
                description = description,
                unitOfPrice = unitOfPrice(),
                chargeType = chargeType(),
                freeToUse = isFree(),
                tags = tags,
            )
        }
    }

    @Serializable
    @SerialName("compute")
    data class Compute(
        override val name: String,
        override val description: String,
        override val cost: ConfigProductCost,
        val cpu: Int,
        val memoryInGigs: Int,
        val gpu: Int? = null,
        val cpuModel: String? = null,
        val gpuModel: String? = null,
        val memoryModel: String? = null,
    ) : ConfigProduct<Product.Compute>() {
        override fun toProduct(category: String, provider: String): Product.Compute {
            return Product.Compute(
                name = name,
                pricePerUnit = pricePerUnit(),
                category = category(category, provider),
                description = description,
                unitOfPrice = unitOfPrice(),
                chargeType = chargeType(),
                freeToUse = isFree(),

                cpu = cpu,
                memoryInGigs = memoryInGigs,
                gpu = gpu,

                cpuModel = cpuModel,
                gpuModel = gpuModel,
                memoryModel = memoryModel,
            )
        }

        override fun pricePerUnitMultiplier(): Long = cpu.toLong()
    }
}

@Serializable
data class ConfigProductCost(
    val currency: Currency? = null,
    val frequency: Frequency? = null,
    val price: String? = null,
    val quota: Boolean = false,
) {
    enum class Currency {
        DKK,
        UNITS,
        FREE,
    }

    enum class Frequency {
        ONE_TIME,
        MINUTE,
        HOUR,
        DAY,
    }
}
