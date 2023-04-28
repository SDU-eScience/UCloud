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

private val creditsPerMinuteNames = listOf("uc-general","uc-t4","u1-fat","u2-gpu","u1-standard","u1-gpu","uc-a10","syncthing")


fun translateToChargeType(productCategory: ProductCategory): ChargeType {
    return when(productCategory.accountingFrequency) {
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
fun translateToProductPriceUnit(productCategory: ProductCategory):ProductPriceUnit {
    return when(productCategory.productType) {
        ProductType.STORAGE,
        ProductType.INGRESS,
        ProductType.LICENSE,
        ProductType.NETWORK_IP-> {
            ProductPriceUnit.PER_UNIT
        }
        ProductType.COMPUTE -> {
            if (creditsPerMinuteNames.contains(productCategory.name)) {
                ProductPriceUnit.CREDITS_PER_MINUTE
            } else if (productCategory.name == "sophia-slim") {
                ProductPriceUnit.UNITS_PER_HOUR
            } else {
                ProductPriceUnit.UNITS_PER_MINUTE
            }
        }
    }
}

fun checkDeicReferenceFormat(referenceId: String?) {
    if (referenceId != null) {
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
                    "$errorMessage Uni value is not listed in DeiC. If you think this is a mistake, please contact UCloud"
                )
            }

            splitId[2].length != 2 -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    errorMessage + " Allocation category wrong fornat"
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
                    errorMessage + " Only supports numeric local ids"
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
