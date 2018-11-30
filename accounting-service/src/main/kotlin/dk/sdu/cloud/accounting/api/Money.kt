package dk.sdu.cloud.accounting.api

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal

/**
 * Represents money when serialized for over the wire transfers.
 */
data class SerializedMoney(
    val amount: String,
    val currency: String
) {
    constructor(amount: BigDecimal, currency: String) : this(amount.toPlainString(), currency)

    @get:JsonIgnore
    val amountAsDecimal: BigDecimal
        get() = BigDecimal(amount)
}

object Currencies {
    const val DKK = "DKK"
}
