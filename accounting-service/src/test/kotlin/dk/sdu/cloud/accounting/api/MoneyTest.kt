package dk.sdu.cloud.accounting.api

import org.junit.Test
import kotlin.test.assertEquals

class MoneyTest{

    @Test
    fun `Money Test`() {
        val money = SerializedMoney(22.4.toBigDecimal(), "DKK")
        assertEquals(22.4.toBigDecimal(), money.amountAsDecimal)
        assertEquals("22.4", money.amount)
        assertEquals("DKK", money.currency)


        val currency = Currencies
        assertEquals("DKK", currency.DKK)
    }
}
