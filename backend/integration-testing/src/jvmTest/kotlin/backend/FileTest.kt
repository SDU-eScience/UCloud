package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.retrySection
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.*


suspend fun initializeCollection(
    project: String,
    rpcClient: AuthenticatedClient,
    product: Product.Storage
): Pair<FileCollection, FSSupport> {
    // NOTE(Dan): Don't throw since we didn't even check if we should do this.
    // At least one of these should initialize the system for us. If we still end up with no
    // collection, then something is wrong in the system.
    FileCollections.init.call(Unit, rpcClient)
    Files.init.call(Unit, rpcClient)
    FileCollections.create.call(
        bulkRequestOf(FileCollection.Spec("Test", product.toReference())),
        rpcClient
    )

    val collection = FileCollections.browse.call(
        ResourceBrowseRequest(FileCollectionIncludeFlags()),
        rpcClient
    ).orThrow().items.find { it.specification.product == product.toReference() }
        ?: throw IllegalStateException("No collection found for: $project of $product")

    val support = FileCollections.retrieveProducts.call(Unit, rpcClient).orThrow()
        .productsByProvider.values.flatten().find { it.product == product }
        ?: throw IllegalStateException("$product has no support")

    return Pair(collection, support.support)
}

class FileTest : IntegrationTest() {
    data class TestCase(
        val title: String,
        val initialization: suspend () -> Unit,
        val products: List<Product.Storage>,
    )

    override fun defineTests() {
        val cases = listOf(
            run {
                val product = Product.Storage(
                    "u1-cephfs",
                    1L,
                    ProductCategoryId("u1-cephfs", UCLOUD_PROVIDER),
                    "storage"
                )

                val projectHome = Product.Storage(
                    "project-home",
                    1L,
                    ProductCategoryId("u1-cephfs", UCLOUD_PROVIDER),
                    "home"
                )

                TestCase(
                    "UCloud/Storage",
                    { Products.create.call(bulkRequestOf(product, projectHome), serviceClient).orThrow() },
                    listOf(product),
                )
            },
        )

        for (case in cases) {
            for (product in case.products) {
                val titlePrefix = "Files @ ${case.title} ($product):"
                test<Unit, Unit>("$titlePrefix Test reading") {
                    // In this test we are testing the absolute minimum. We can't really verify the results since this test
                    // doesn't want to assume that any other endpoints are actually usable.

                    // NOTE(Dan): We don't need to check the products since these endpoints are mandatory.

                    execute {
                        case.initialization()
                        with(initializeResourceTestContext(case.products, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection) = initializeCollection(project, rpcClient, product)

                            val browseResult = Files.browse.call(
                                ResourceBrowseRequest(UFileIncludeFlags(path = "/${collection.id}")),
                                rpcClient
                            ).orThrow()

                            if (browseResult.items.isNotEmpty()) {
                                Files.retrieve.call(
                                    ResourceRetrieveRequest(UFileIncludeFlags(), browseResult.items[0].id),
                                    rpcClient
                                ).orThrow()
                            }
                        }
                    }

                    case("No input") {
                        input(Unit)
                        check {  }
                    }
                }

                data class DirectoryInput(val subpaths: List<String>)
                test<DirectoryInput, Unit>("$titlePrefix Test creation of directories") {
                    execute {
                        case.initialization()
                        with(initializeResourceTestContext(case.products, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, support) = initializeCollection(project, rpcClient, product)
                            if (support.files.isReadOnly) return@execute

                            val inputPaths = input.subpaths.map { subpath ->
                                "/${collection.id}/${subpath}"
                            }

                            val normalizedPaths = inputPaths.map { it.normalize() }

                            inputPaths.zip(normalizedPaths).forEach { (input, normalized) ->
                                val isValid = normalized.startsWith("/${collection.id}/")
                                val createdFolder = Files.createFolder.call(
                                    bulkRequestOf(FilesCreateFolderRequestItem(input, WriteConflictPolicy.REJECT)),
                                    rpcClient
                                )

                                if (isValid) {
                                    createdFolder.orThrow()
                                    val result = Files.retrieve.call(
                                        ResourceRetrieveRequest(UFileIncludeFlags(), normalized),
                                        rpcClient
                                    )

                                    assertThatInstance(result, "$input -> $normalized should be a directory") {
                                        it.orNull()?.status?.type == FileType.DIRECTORY
                                    }
                                } else {
                                    assertThatInstance(createdFolder, "$input -> $normalized should fail gracefully") {
                                        it.statusCode.value in 400..499
                                    }
                                }
                            }
                        }
                    }

                    case("Simple") {
                        input(DirectoryInput(listOf("myfolder")))
                        check {}
                    }

                    case("Multiple") {
                        input(DirectoryInput((0 until 50).map { it.toString() }))
                        check {}
                    }

                    case("UTF8") {
                        input(DirectoryInput(listOf("\uD83D\uDC36")))
                        check {}
                    }

                    case("Malicious") {
                        input(DirectoryInput(listOf("../../../../../../../../../../../etc/passwd")))
                        check {}
                    }
                }

                data class DeleteTest(val directoriesToCreate: List<String>, val directoriesToDelete: List<String>)
                test<DeleteTest, Unit>("$titlePrefix Test deletion of directory") {
                    execute {
                        case.initialization()
                        with(initializeResourceTestContext(case.products, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, support) = initializeCollection(project, rpcClient, product)
                            if (support.files.isReadOnly) return@execute

                            input.directoriesToCreate.forEach { subpath ->
                                Files.createFolder.call(
                                    bulkRequestOf(
                                        FilesCreateFolderRequestItem(
                                            "/${collection.id}/${subpath}",
                                            WriteConflictPolicy.REJECT
                                        )
                                    ),
                                    rpcClient
                                ).orThrow()
                            }

                            val inputPaths = input.directoriesToDelete.map { subpath ->
                                "/${collection.id}/${subpath}"
                            }

                            val normalizedPaths = inputPaths.map { it.normalize() }

                            inputPaths.zip(normalizedPaths).forEach { (input, normalized) ->
                                val isValid = normalized.startsWith("/${collection.id}/")
                                val deleteResult = Files.delete.call(
                                    bulkRequestOf(FindByStringId(input)),
                                    rpcClient
                                )

                                if (isValid) {
                                    deleteResult.orThrow()
                                    retrySection {
                                        val result = Files.retrieve.call(
                                            ResourceRetrieveRequest(UFileIncludeFlags(), normalized),
                                            rpcClient
                                        )

                                        assertThatInstance(result, "$input -> $normalized should not exist") {
                                            it.statusCode == HttpStatusCode.NotFound
                                        }
                                    }
                                } else {
                                    assertThatInstance(deleteResult, "$input -> $normalized should fail gracefully") {
                                        it.statusCode.value in 400..499
                                    }
                                }
                            }
                        }
                    }

                    case("Simple") {
                        input(DeleteTest(listOf("myfolder"), listOf("myfolder")))
                        check {}
                    }

                    case("Recursive") {
                        input(DeleteTest(listOf("1", "1/2", "1/2/3"), listOf("1")))
                        check {}
                    }

                    case("UTF8") {
                        input(DeleteTest(listOf("\uD83D\uDC36"), listOf("\uD83D\uDC36")))
                        check {}
                    }

                    case("Malicious") {
                        input(DeleteTest(
                            listOf(),
                            listOf("../../../../../../../../../../../etc/passwd")
                        ))
                        check {}
                    }
                }

                data class SourceAndDestination(
                    val source: String,
                    val destination: String,
                    val isValid: Boolean = true,
                )
                data class CopyInput(
                    val directoriesToCreate: List<String>,
                    val copiesToMake: List<SourceAndDestination>
                )
                test<CopyInput, Unit>("$titlePrefix Test copy") {
                    execute {
                        case.initialization()
                        with(initializeResourceTestContext(case.products, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, support) = initializeCollection(project, rpcClient, product)
                            if (support.files.isReadOnly) return@execute

                            input.directoriesToCreate.forEach { subpath ->
                                Files.createFolder.call(
                                    bulkRequestOf(
                                        FilesCreateFolderRequestItem(
                                            "/${collection.id}/${subpath}",
                                            WriteConflictPolicy.REJECT
                                        )
                                    ),
                                    rpcClient
                                ).orThrow()
                            }

                            input.copiesToMake.forEach { copy ->
                                val result = Files.copy.call(
                                    bulkRequestOf(
                                        FilesCopyRequestItem(
                                            "/${collection.id}/${copy.source}",
                                            "/${collection.id}/${copy.destination}",
                                            WriteConflictPolicy.REJECT
                                        ),
                                    ),
                                    rpcClient
                                )

                                if (copy.isValid) {
                                    result.orThrow()

                                    retrySection {
                                        val retrieved = Files.retrieve.call(
                                            ResourceRetrieveRequest(
                                                UFileIncludeFlags(),
                                                "/${collection.id}/${copy.destination}"
                                            ),
                                            rpcClient
                                        )

                                        assertThatInstance(retrieved, "destination should also be a directory") {
                                            it.orNull()?.status?.type == FileType.DIRECTORY
                                        }
                                    }
                                } else {
                                    assertThatInstance(result, "should fail gracefully") {
                                        it.statusCode.value in 400..499
                                    }
                                }
                            }
                        }
                    }

                    case("Simple") {
                        input(CopyInput(
                            listOf("myfolder"),
                            listOf(SourceAndDestination("myfolder", "myfolder2")))
                        )
                        check {}
                    }

                    case("Recursive") {
                        input(CopyInput(
                            listOf("1", "1/2", "1/2/3"),
                            listOf(SourceAndDestination("1", "2"))
                        ))
                        check {}
                    }

                    case("UTF8") {
                        input(CopyInput(
                            listOf("\uD83D\uDC36"),
                            listOf(SourceAndDestination("\uD83D\uDC36", "testing")))
                        )
                        check {}
                    }

                    case("Malicious") {
                        input(CopyInput(
                            listOf("folder"),
                            listOf(
                                SourceAndDestination(
                                    "../../../../../../../../../../../etc/passwd",
                                    "password",
                                    isValid = false
                                )
                            )
                        ))
                        check {}
                    }
                }

                data class MoveInput(
                    val directoriesToCreate: List<String>,
                    val movesToMake: List<SourceAndDestination>
                )
                test<MoveInput, Unit>("$titlePrefix Test move") {
                    execute {
                        case.initialization()
                        with(initializeResourceTestContext(case.products, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, support) = initializeCollection(project, rpcClient, product)
                            if (support.files.isReadOnly) return@execute

                            input.directoriesToCreate.forEach { subpath ->
                                Files.createFolder.call(
                                    bulkRequestOf(
                                        FilesCreateFolderRequestItem(
                                            "/${collection.id}/${subpath}",
                                            WriteConflictPolicy.REJECT
                                        )
                                    ),
                                    rpcClient
                                ).orThrow()
                            }

                            input.movesToMake.forEach { copy ->
                                val result = Files.move.call(
                                    bulkRequestOf(
                                        FilesMoveRequestItem(
                                            "/${collection.id}/${copy.source}",
                                            "/${collection.id}/${copy.destination}",
                                            WriteConflictPolicy.REJECT
                                        ),
                                    ),
                                    rpcClient
                                )

                                if (copy.isValid) {
                                    result.orThrow()

                                    retrySection {
                                        val retrievedOld = Files.retrieve.call(
                                            ResourceRetrieveRequest(
                                                UFileIncludeFlags(),
                                                "/${collection.id}/${copy.source}"
                                            ),
                                            rpcClient
                                        )

                                        assertThatInstance(retrievedOld, "source should not exist") {
                                            it.statusCode == HttpStatusCode.NotFound
                                        }

                                        val retrievedNew = Files.retrieve.call(
                                            ResourceRetrieveRequest(
                                                UFileIncludeFlags(),
                                                "/${collection.id}/${copy.destination}"
                                            ),
                                            rpcClient
                                        )

                                        assertThatInstance(retrievedNew, "destination should also be a directory") {
                                            it.orNull()?.status?.type == FileType.DIRECTORY
                                        }
                                    }
                                } else {
                                    assertThatInstance(result, "should fail gracefully") {
                                        it.statusCode.value in 400..499
                                    }
                                }
                            }
                        }
                    }

                    case("Simple") {
                        input(MoveInput(
                            listOf("myfolder"),
                            listOf(SourceAndDestination("myfolder", "myfolder2")))
                        )
                        check {}
                    }

                    case("Recursive") {
                        input(MoveInput(
                            listOf("1", "1/2", "1/2/3"),
                            listOf(SourceAndDestination("1", "2"))
                        ))
                        check {}
                    }

                    case("UTF8") {
                        input(MoveInput(
                            listOf("\uD83D\uDC36"),
                            listOf(SourceAndDestination("\uD83D\uDC36", "testing")))
                        )
                        check {}
                    }

                    case("Malicious") {
                        input(MoveInput(
                            listOf("folder"),
                            listOf(
                                SourceAndDestination(
                                    "../../../../../../../../../../../etc/passwd",
                                    "password",
                                    isValid = false
                                )
                            )
                        ))
                        check {}
                    }
                }

                data class ItemToTrash(val subpath: String, val isValid: Boolean = true)
                data class TrashTest(
                    val directoriesToCreate: List<String>,
                    val itemsToTrash: List<ItemToTrash>,
                    val clearTrash: Boolean = true,
                )
                test<TrashTest, Unit>("$titlePrefix Test trash") {
                    execute {
                        case.initialization()
                        with(initializeResourceTestContext(case.products, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, support) = initializeCollection(project, rpcClient, product)
                            if (support.files.isReadOnly) return@execute
                            if (!support.files.trashSupported) return@execute

                            input.directoriesToCreate.forEach { subpath ->
                                Files.createFolder.call(
                                    bulkRequestOf(
                                        FilesCreateFolderRequestItem(
                                            "/${collection.id}/${subpath}",
                                            WriteConflictPolicy.REJECT
                                        )
                                    ),
                                    rpcClient
                                ).orThrow()
                            }

                            input.itemsToTrash.forEach { trash ->
                                val result = Files.trash.call(
                                    bulkRequestOf(FindByPath("/${collection.id}/${trash.subpath}")),
                                    rpcClient
                                )

                                if (trash.isValid) {
                                    result.orThrow()

                                    retrySection {
                                        val retrievedOld = Files.retrieve.call(
                                            ResourceRetrieveRequest(
                                                UFileIncludeFlags(),
                                                "/${collection.id}/${trash.subpath}"
                                            ),
                                            rpcClient
                                        )

                                        assertThatInstance(retrievedOld, "source should not exist") {
                                            it.statusCode == HttpStatusCode.NotFound
                                        }
                                    }
                                } else {
                                    assertThatInstance(result, "should fail gracefully") {
                                        it.statusCode.value in 400..499
                                    }
                                }
                            }

                            if (input.clearTrash) {
                                val allCollections = FileCollections.browse.call(
                                    ResourceBrowseRequest(
                                        FileCollectionIncludeFlags(),
                                        itemsPerPage = 250
                                    ),
                                    rpcClient
                                ).orThrow().items

                                var foundTrash = false
                                for (coll in allCollections) {
                                    val items = Files.browse.call(
                                        ResourceBrowseRequest(
                                            UFileIncludeFlags(path = "/${coll.id}"),
                                            itemsPerPage = 250
                                        ),
                                        rpcClient
                                    ).orThrow().items

                                    val trashFolder = items.find { it.status.icon == FileIconHint.DIRECTORY_TRASH }
                                    if (trashFolder != null) {
                                        foundTrash = true

                                        Files.emptyTrash.call(
                                            bulkRequestOf(FindByPath(trashFolder.id)),
                                            rpcClient
                                        ).orThrow()
                                    }
                                }

                                if (!foundTrash) {
                                    throw IllegalStateException("Found no trash folder in any collection: $allCollections")
                                }
                            }
                        }
                    }

                    case("Simple without clear") {
                        input(TrashTest(
                            listOf("myfolder"),
                            listOf(ItemToTrash("myfolder")),
                            clearTrash = false
                        ))
                        check {}
                    }

                    case("Simple") {
                        input(TrashTest(
                            listOf("myfolder"),
                            listOf(ItemToTrash("myfolder")),
                            clearTrash = true
                        ))
                        check {}
                    }

                    case("Recursive") {
                        input(TrashTest(
                            listOf("1", "1/2", "1/2/3"),
                            listOf(ItemToTrash("1")),
                            clearTrash = true
                        ))
                        check {}
                    }

                    case("UTF8") {
                        input(TrashTest(
                            listOf("\uD83D\uDC36"),
                            listOf(ItemToTrash("\uD83D\uDC36")))
                        )
                        check {}
                    }

                    case("Malicious") {
                        input(TrashTest(
                            listOf("folder"),
                            listOf(
                                ItemToTrash("../../../../../../../../../../../etc/passwd", isValid = false),
                            ),
                            clearTrash = false
                        ))
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Test search") {
                    execute {}
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Test uploading and downloading") {
                    execute {}
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }
            }
        }
    }
}
