package dk.sdu.cloud.ucloud.data.extraction.api

data class WalletInfo(
    val accountId: String,
    val allocated: Long,
    val productType: ProductType
)
