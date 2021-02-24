package dk.sdu.cloud.accounting.utils

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.ProductAvailability
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.services.ProductCategoryTable
import dk.sdu.cloud.accounting.services.ProductTable
import dk.sdu.cloud.accounting.services.WalletTable
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestUsers

suspend fun insertAll(db: DBContext) {
    insertTestProductCategories(db, productCategoryStandard)
    insertTestProductCategories(db, productCategoryGpu)
    insertTestProductCategories(db, productCategoryCephfs)

    insertTestProduct(db, productStandard)
    insertTestProduct(db, productGpu)
    insertTestProduct(db, productCephfs)

    insertTestWallet(db, walletProjectStandard, startBalance = 1000000) // 1.000.000
    insertTestWallet(db, walletProjectGpu, startBalance = 5000000) // 5.000.000
    insertTestWallet(db, walletProjectCephfs, startBalance = 10000000) // 10.000.000

    insertTestWallet(db, walletUserStandard, startBalance = 200000) //200.000
    insertTestWallet(db, walletUserGpu, startBalance = 300000) //300.000
    insertTestWallet(db, walletUserCephfs, startBalance = 5000000) // 5.000.000
}

val projectId = "projectId"
val userId = TestUsers.user.username
val provider = "ucloud"
val productCategoryIdStandard = ProductCategoryId("standard", provider)
val productCategoryIdGpu = ProductCategoryId("gpu", provider)
val productCategoryIdCephfs = ProductCategoryId("cephfs", provider)

val project = Project(
    projectId,
    "A Project",
    null,
    false
)

val productCategoryStandard = ProductCategory(
    productCategoryIdStandard,
    ProductArea.COMPUTE
)
val productCategoryGpu = ProductCategory(
    productCategoryIdGpu,
    ProductArea.COMPUTE
)
val productCategoryCephfs = ProductCategory(
    productCategoryIdCephfs,
    ProductArea.STORAGE
)

val productStandard = Product.Compute(
    "u1-standard-1",
    70000,
    productCategoryIdStandard,
    cpu = 4,
    memoryInGigs = 20
)
val productGpu = Product.Compute(
    "u1-gpu-1",
    150000,
    productCategoryIdGpu,
    cpu = 4,
    memoryInGigs = 60,
    gpu = 2
)
val productCephfs = Product.Storage(
    "u1-cephfs",
    20000,
    productCategoryIdCephfs
)

val walletUserStandard = Wallet(
    userId,
    WalletOwnerType.USER,
    productCategoryIdStandard
)
val walletUserGpu = walletUserStandard.copy(paysFor = productCategoryIdGpu)
val walletUserCephfs = walletUserStandard.copy(paysFor = productCategoryIdCephfs)

val walletProjectStandard = Wallet(
    projectId,
    WalletOwnerType.PROJECT,
    productCategoryIdStandard
)

val walletProjectGpu = walletProjectStandard.copy(paysFor = productCategoryIdGpu)
val walletProjectCephfs = walletProjectStandard.copy(paysFor = productCategoryIdCephfs)

suspend fun insertTestWallet(db: DBContext, wallet: Wallet, startBalance: Long) {
    db.withSession { session ->
        session.insert(WalletTable) {
            set(WalletTable.accountId, wallet.id)
            set(WalletTable.accountType, wallet.type.name)
            set(WalletTable.productCategory, wallet.paysFor.id)
            set(WalletTable.productProvider, wallet.paysFor.provider)
            set(WalletTable.balance, startBalance)
        }
    }
}

suspend fun insertTestProduct(db: DBContext, product: Product) {
    db.withSession { session ->
        session.insert(ProductTable) {
            set(ProductTable.provider, product.category.provider)
            set(ProductTable.category, product.category.id)
            set(ProductTable.area, product.area.name)
            set(ProductTable.pricePerUnit, product.pricePerUnit)
            set(ProductTable.id, product.id)
            set(ProductTable.description, product.description)
            set(ProductTable.priority, product.priority)
            when (val availability = product.availability) {
                is ProductAvailability.Available -> {
                    set(ProductTable.availability, null)
                }

                is ProductAvailability.Unavailable -> {
                    set(ProductTable.availability, availability.reason)
                }
            }

            when (product) {
                is Product.Storage -> {
                    // No more attributes
                }

                is Product.Compute -> {
                    set(ProductTable.cpu, product.cpu)
                    set(ProductTable.gpu, product.gpu)
                    set(ProductTable.memoryInGigs, product.memoryInGigs)
                }
            }
            set(ProductTable.pricePerUnit, product.pricePerUnit)
        }
    }
}

suspend fun insertTestProductCategories(db: DBContext, productCategory: ProductCategory) {
    db.withSession { session ->
        session.insert(ProductCategoryTable) {
            set(ProductCategoryTable.area, productCategory.area.name)
            set(ProductCategoryTable.category, productCategory.id.id )
            set(ProductCategoryTable.provider, productCategory.id.provider)
        }
    }
}
