package dk.sdu.cloud.provider.api

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.withHooks
import io.ktor.client.request.*
import java.util.Calendar

fun getTime(atStartOfDay: Boolean): Long {
    if (atStartOfDay) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.time.time
    }
    return System.currentTimeMillis()
}

private val creditsPerMinuteNames = listOf(
    "uc-general",
    "uc-t4",
    "u1-fat",
    "u2-gpu",
    "u1-standard",
    "u1-gpu",
    "uc-a10",
    "syncthing",
    "cpu",
    "standard-cpu-dkk"
)

fun basicTranslationToAccountingUnit(productPriceUnit: ProductPriceUnit, productType: ProductType): AccountingUnit {
    return when (productType) {
        ProductType.STORAGE -> AccountingUnit(
            "Gigabyte",
            "Gigabytes",
            floatingPoint = false,
            displayFrequencySuffix = false
        )

        ProductType.COMPUTE -> {
            if (productPriceUnit.name == "PER_UNIT") {
                AccountingUnit(
                    "Syncthing",
                    "Syncthing",
                    false,
                    false
                )
            } else if (productPriceUnit.name.contains("UNITS_PER")) {
                AccountingUnit(
                    "Core hour",
                    "Core hours",
                    floatingPoint = false,
                    displayFrequencySuffix = true
                )
            } else if (productPriceUnit.name.contains("CREDITS_PER")) {
                AccountingUnit(
                    "DKK",
                    "DKK",
                    floatingPoint = true,
                    displayFrequencySuffix = false
                )
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Missing type to translate")
            }
        }

        ProductType.INGRESS -> AccountingUnit(
            "Link",
            "Links",
            floatingPoint = false,
            displayFrequencySuffix = false
        )

        ProductType.LICENSE -> AccountingUnit(
            "License",
            "Licenses",
            floatingPoint = false,
            displayFrequencySuffix = false
        )

        ProductType.NETWORK_IP -> AccountingUnit(
            "IP",
            "IPs",
            floatingPoint = false,
            displayFrequencySuffix = false
        )
    }
}

fun translateToAccountingFrequency(productPriceUnit: ProductPriceUnit): AccountingFrequency {
    return when (productPriceUnit) {
        ProductPriceUnit.CREDITS_PER_UNIT -> AccountingFrequency.ONCE
        ProductPriceUnit.PER_UNIT -> AccountingFrequency.ONCE
        ProductPriceUnit.CREDITS_PER_MINUTE -> AccountingFrequency.PERIODIC_MINUTE
        ProductPriceUnit.CREDITS_PER_HOUR -> AccountingFrequency.PERIODIC_HOUR
        ProductPriceUnit.CREDITS_PER_DAY -> AccountingFrequency.PERIODIC_DAY
        ProductPriceUnit.UNITS_PER_MINUTE -> AccountingFrequency.PERIODIC_MINUTE
        ProductPriceUnit.UNITS_PER_HOUR -> AccountingFrequency.PERIODIC_HOUR
        ProductPriceUnit.UNITS_PER_DAY -> AccountingFrequency.PERIODIC_DAY
    }
}

fun translateToChargeType(productCategory: ProductCategory): ChargeType {
    return when (productCategory.accountingFrequency) {
        AccountingFrequency.PERIODIC_MINUTE,
        AccountingFrequency.PERIODIC_HOUR,
        AccountingFrequency.PERIODIC_DAY -> {
            ChargeType.ABSOLUTE
        }

        AccountingFrequency.ONCE -> {
            ChargeType.DIFFERENTIAL_QUOTA
        }
    }
}

fun translateToProductPriceUnit(productType: ProductType, productCategoryName: String): ProductPriceUnit {
    return when (productType) {
        ProductType.STORAGE,
        ProductType.INGRESS,
        ProductType.LICENSE,
        ProductType.NETWORK_IP -> {
            ProductPriceUnit.PER_UNIT
        }

        ProductType.COMPUTE -> {
            if (creditsPerMinuteNames.contains(productCategoryName)) {
                ProductPriceUnit.CREDITS_PER_MINUTE
            } else if (productCategoryName == "sophia-slim") {
                ProductPriceUnit.UNITS_PER_HOUR
            } else {
                ProductPriceUnit.UNITS_PER_MINUTE
            }
        }
    }
}

fun checkDeicReferenceFormat(referenceId: String?) {
    if (referenceId != null && referenceId.startsWith("deic-", ignoreCase = true)) {
        val errorMessage = "Error in DeiC reference id:"
        val deicUniList = listOf("KU", "DTU", "AU", "SDU", "AAU", "RUC", "ITU", "CBS")
        val splitId = referenceId.split("-")
        when {
            splitId.size != 4 -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "$errorMessage It seems like you are not following request format. DeiC-XX-YY-NUMBER"
                )
            }

            splitId.first() != "DeiC" -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "$errorMessage First part should be DeiC."
                )
            }

            !deicUniList.contains(splitId[1]) -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "$errorMessage Could not recognize university."
                )
            }

            splitId[2].length != 2 -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    errorMessage + " Allocation category uses the wrong format."
                )
            }

            !splitId[2].contains(Regex("""[LNSI][1-5]$""")) -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    errorMessage + " Allocation category has wrong format."
                )
            }

            !splitId[3].contains(Regex("""^\d+$""")) ->
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    errorMessage + " Only supports numeric local ids."
                )
        }
    }
}


fun AuthenticatedClient.withProxyInfo(username: String?, signedIntent: String?): AuthenticatedClient {
    return withHooks(
        beforeHook = {
            if (username != null) {
                when (it) {
                    is OutgoingHttpCall -> {
                        it.builder.header(
                            IntegrationProvider.UCLOUD_USERNAME_HEADER,
                            base64Encode(username.encodeToByteArray())
                        )

                        if (signedIntent != null) {
                            it.builder.header(
                                IntegrationProvider.UCLOUD_SIGNED_INTENT,
                                signedIntent,
                            )
                        }
                    }

                    is OutgoingWSCall -> {
                        it.attributes[OutgoingWSCall.proxyAttribute] = username
                        if (signedIntent != null) {
                            it.attributes[OutgoingWSCall.signedIntentAttribute] = signedIntent
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Cannot attach proxy info to this client $it")
                    }
                }
            }
        }
    )
}
