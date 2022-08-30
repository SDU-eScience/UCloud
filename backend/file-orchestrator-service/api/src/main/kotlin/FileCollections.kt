package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ChargeType
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.accounting.api.providers.SupportByProvider
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import dk.sdu.cloud.provider.api.Resources
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

typealias FileCollectionsRenameRequest = BulkRequest<FileCollectionsRenameRequestItem>

@Serializable
data class FileCollectionsRenameRequestItem(
    val id: String,
    val newTitle: String,
) {
    init {
        checkSingleLine(::newTitle, newTitle)
    }
}
typealias FileCollectionsRenameResponse = Unit

typealias FileCollectionsProviderRenameRequest = BulkRequest<FileCollectionsProviderRenameRequestItem>

@Serializable
data class FileCollectionsProviderRenameRequestItem(
    val id: String,
    val newTitle: String,
) {
    init {
        checkSingleLine(::newTitle, newTitle)
    }
}
typealias FileCollectionsProviderRenameResponse = Unit

@Serializable
data class FileCollectionsAclUpdateRequestItem(
    val id: String,
)

typealias FileCollectionsProviderAclUpdateRequest = BulkRequest<FileCollectionsProviderAclUpdateRequestItem>

@Serializable
data class FileCollectionsProviderAclUpdateRequestItem(
    val id: String,
)

// ---

object FileCollections : ResourceApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        FileCollection.serializer(),
        typeOfIfPossible<FileCollection>(),
        FileCollection.Spec.serializer(),
        typeOfIfPossible<FileCollection.Spec>(),
        FileCollection.Update.serializer(),
        typeOfIfPossible<FileCollection.Update>(),
        FileCollectionIncludeFlags.serializer(),
        typeOfIfPossible<FileCollectionIncludeFlags>(),
        FileCollection.Status.serializer(),
        typeOfIfPossible<FileCollection.Status>(),
        FSSupport.serializer(),
        typeOfIfPossible<FSSupport>(),
        Product.Storage.serializer(),
        typeOfIfPossible<Product.Storage>(),
    )

    init {
        description = """
A FileCollection is an entrypoint to a user's files

${Resources.readMeFirst}

This entrypoint allows the user to access all the files they have access to within a single project. It is important to
note that a file collection is not the same as a directory! Common real-world examples of a file collection is listed
below:

| Name              | Typical path                | Comment                                                     |
|-------------------|-----------------------------|-------------------------------------------------------------|
| Home directory    | `/home/${"$"}username/`     | The home folder is typically the main collection for a user |
| Work directory    | `/work/${"$"}projectId/`    | The project 'home' folder                                   |
| Scratch directory | `/scratch/${"$"}projectId/` | Temporary storage for a project                             |

The provider of storage manages a 'database' of these file collections and who they belong to. The file collections also
play an important role in accounting and resource management. A file collection can have a quota attached to it and
billing information is also stored in this object. Each file collection can be attached to a different product type, and
as a result, can have different billing information attached to it. This is, for example, useful if a storage provider
has both fast and slow tiers of storage, which is typically billed very differently.

All file collections additionally have a title. This title can be used for a user-friendly version of the folder. This
title does not have to be unique, and can with great benefit choose to not reference who it belongs to. For example,
if every user has exactly one home directory, then it would make sense to give this collection `"Home"` as its title.

---

__📝 Provider Note:__ This is the API exposed to end-users. See the table below for other relevant APIs.

| End-User | Provider (Ingoing) | Control (Outgoing) |
|----------|--------------------|--------------------|
| [`FileCollections`](/docs/developer-guide/orchestration/storage/filecollections.md) | [`FileCollectionsProvider`](/docs/developer-guide/orchestration/storage/providers/drives/ingoing.md) | [`FileCollectionsControl`](/docs/developer-guide/orchestration/storage/providers/drives/outgoing.md) |

---
""".trimIndent()
    }

    private const val retrieveUseCase = "retrieve"
    private const val renameUseCase = "rename"

    override fun documentation() {
        useCase(
            retrieveUseCase,
            "An example collection",
            flow = {
                val user = basicUser()
                comment("In this example we will see a simple collection. This collection models the 'home' " +
                        "directory of a user.")

                comment("📝 NOTE: Collections are identified by a unique (UCloud provided) ID")

                success(
                    retrieve,
                    ResourceRetrieveRequest(FileCollectionIncludeFlags(), "54123"),
                    FileCollection(
                        "54123",
                        FileCollection.Spec(
                            "Home",
                            ProductReference("example-ssd", "example-ssd", "example")
                        ),
                        1635151675465L,
                        FileCollection.Status(),
                        emptyList(),
                        ResourceOwner("user", null),
                        ResourcePermissions(listOf(Permission.ADMIN), emptyList()),
                        null
                    ),
                    user
                )
            }
        )

        useCase(
            renameUseCase,
            "Renaming a collection",
            flow = {
                val user = basicUser()
                comment("In this example, we will see how a user can rename one of their collections.")
                comment("📝 NOTE: Renaming must be supported by the provider")

                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "ucloud" to listOf(
                                ResolvedSupport(
                                    Product.Storage(
                                        "example-ssd",
                                        1,
                                        ProductCategoryId("example-ssd", "example"),
                                        "Fast storage",
                                        unitOfPrice = ProductPriceUnit.PER_UNIT,
                                        chargeType = ChargeType.DIFFERENTIAL_QUOTA
                                    ),
                                    FSSupport(
                                        ProductReference("example-ssd", "example-ssd", "example"),
                                        collection = FSCollectionSupport(
                                            usersCanCreate = true,
                                            usersCanRename = true,
                                            usersCanDelete = true,
                                        ),
                                    )
                                )
                            )
                        )
                    ),
                    user
                )

                comment("As we can see, the provider does support the rename operation. We now look at our collections.")
                success(
                    browse,
                    ResourceBrowseRequest(FileCollectionIncludeFlags()),
                    PageV2(
                        50,
                        listOf(
                            FileCollection(
                                "54123",
                                FileCollection.Spec(
                                    "Home",
                                    ProductReference("example-ssd", "example-ssd", "example")
                                ),
                                1635151675465L,
                                FileCollection.Status(),
                                emptyList(),
                                ResourceOwner("user", null),
                                ResourcePermissions(listOf(Permission.ADMIN), emptyList()),
                                null
                            )
                        ),
                        null
                    ),
                    user
                )

                comment("Using the unique ID, we can rename the collection")

                success(
                    rename,
                    FileCollectionsRenameRequest(
                        listOf(
                            FileCollectionsRenameRequestItem(
                            "54123",
                            "My Awesome Drive"
                        )
                        )
                    ),
                    Unit,
                    user
                )

                comment("The new title is observed when we browse the collections one more time")

                success(
                    browse,
                    ResourceBrowseRequest(FileCollectionIncludeFlags()),
                    PageV2(
                        50,
                        listOf(
                            FileCollection(
                                "54123",
                                FileCollection.Spec(
                                    "My Awesome Drive",
                                    ProductReference("example-ssd", "example-ssd", "example")
                                ),
                                1635151675465L,
                                FileCollection.Status(),
                                emptyList(),
                                ResourceOwner("user", null),
                                ResourcePermissions(listOf(Permission.ADMIN), emptyList()),
                                null
                            )
                        ),
                        null
                    ),
                    user
                )
            }
        )
    }

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!

    val rename = call("rename", BulkRequest.serializer(FileCollectionsRenameRequestItem.serializer()), FileCollectionsRenameResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "rename")
    }
}

object FileCollectionsControl : ResourceControlApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        FileCollection.serializer(),
        typeOfIfPossible<FileCollection>(),
        FileCollection.Spec.serializer(),
        typeOfIfPossible<FileCollection.Spec>(),
        FileCollection.Update.serializer(),
        typeOfIfPossible<FileCollection.Update>(),
        FileCollectionIncludeFlags.serializer(),
        typeOfIfPossible<FileCollectionIncludeFlags>(),
        FileCollection.Status.serializer(),
        typeOfIfPossible<FileCollection.Status>(),
        FSSupport.serializer(),
        typeOfIfPossible<FSSupport>(),
        Product.Storage.serializer(),
        typeOfIfPossible<Product.Storage>(),
    )
}

open class FileCollectionsProvider(
    provider: String,
) : ResourceProviderApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections", provider) {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        FileCollection.serializer(),
        typeOfIfPossible<FileCollection>(),
        FileCollection.Spec.serializer(),
        typeOfIfPossible<FileCollection.Spec>(),
        FileCollection.Update.serializer(),
        typeOfIfPossible<FileCollection.Update>(),
        FileCollectionIncludeFlags.serializer(),
        typeOfIfPossible<FileCollectionIncludeFlags>(),
        FileCollection.Status.serializer(),
        typeOfIfPossible<FileCollection.Status>(),
        FSSupport.serializer(),
        typeOfIfPossible<FSSupport>(),
        Product.Storage.serializer(),
        typeOfIfPossible<Product.Storage>(),
    )

    val rename = call("rename", BulkRequest.serializer(FileCollectionsProviderRenameRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "rename", roles = Roles.SERVICE)
    }

    override val delete get() = super.delete!!
}
