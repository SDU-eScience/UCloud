package dk.sdu.cloud.integration.backend.storage

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.assertThatInstance
import dk.sdu.cloud.integration.backend.initializeResourceTestContext
import dk.sdu.cloud.integration.utils.*
import kotlinx.coroutines.runBlocking

class FileTest : IntegrationTest() {
    data class TestCase(
        val title: String,
        val products: List<Product.Storage>,
    )

    override fun defineTests() {
        val cases: List<TestCase> = runBlocking {
            val allProducts = findProducts()
            val productsByProviders = allProducts.groupBy { it.category.provider }

            productsByProviders.mapNotNull { (provider, products) ->
                val storage = products.filterIsInstance<Product.Storage>()

                if (storage.isNotEmpty()) TestCase(provider, storage)
                else null
            }
        }

        for (case in cases) {
            for (product in case.products) {
                if (product.name == "share" || product.name == "project-home") continue

                val titlePrefix = "Files @ ${case.title} ($product):"
                test<Unit, Unit>("$titlePrefix Test reading") {
                    // In this test we are testing the absolute minimum. We can't really verify the results since this test
                    // doesn't want to assume that any other endpoints are actually usable.

                    // NOTE(Dan): We don't need to check the products since these endpoints are mandatory.

                    execute {
                        with(initializeResourceTestContext(case.products.map { productV1toV2(it) }, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection) = initializeCollection(
                                project = project,
                                rpcClient = rpcClient,
                                provider = product.category.provider,
                                productFilter = product
                            ) ?: error("Could not initialize collection")

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
                        with(initializeResourceTestContext(case.products.map { productV1toV2(it) }, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, _, support) = initializeCollection(
                                project = project,
                                rpcClient = rpcClient,
                                provider = product.category.provider,
                                productFilter = product
                            ) ?: error("Could not initialize collection")

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
                        input(DirectoryInput(listOf(generateId("myfolder"))))
                        check {}
                    }

                    case("Multiple") {
                        input(DirectoryInput((0 until 50).map { generateId(it.toString()) }))
                        check {}
                    }

                    case("UTF8") {
                        input(DirectoryInput(listOf(generateId("\uD83D\uDC36"))))
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
                        with(initializeResourceTestContext(case.products.map { productV1toV2(it) }, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, _, support) = initializeCollection(
                                project = project,
                                rpcClient = rpcClient,
                                provider = product.category.provider,
                                productFilter = product
                            ) ?: error("Could not initialize collection")
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
                        val folder = generateId("myfolder")
                        input(DeleteTest(listOf(folder), listOf(folder)))
                        check {}
                    }

                    case("Recursive") {
                        val topLevel = generateId("top")
                        input(DeleteTest(listOf(topLevel, "$topLevel/2", "$topLevel/2/3"), listOf(topLevel)))
                        check {}
                    }

                    case("UTF8") {
                        val folder = generateId("\uD83D\uDC36")
                        input(DeleteTest(listOf(folder), listOf(folder)))
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
                        with(initializeResourceTestContext(case.products.map { productV1toV2(it) }, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, _, support) = initializeCollection(
                                project = project,
                                rpcClient = rpcClient,
                                provider = product.category.provider,
                                productFilter = product
                            ) ?: error("Could not initialize collection")
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
                        val mainFolder = generateId("myfolder")
                        input(
                            CopyInput(
                                listOf(mainFolder),
                                listOf(SourceAndDestination(mainFolder, generateId("myfolder2")))
                            )
                        )
                        expectSuccess()
                    }

                    case("Recursive") {
                        val topLevel = generateId("top")
                        input(
                            CopyInput(
                                listOf(topLevel, "$topLevel/2", "$topLevel/2/3"),
                                listOf(SourceAndDestination(topLevel, generateId("top2")))
                            )
                        )
                        check {}
                    }

                    case("UTF8") {
                        val folder = generateId("\uD83D\uDC36")
                        input(
                            CopyInput(
                                listOf(folder),
                                listOf(SourceAndDestination(folder, generateId("testing")))
                            )
                        )
                        check {}
                    }

                    case("Malicious") {
                        input(CopyInput(
                            listOf(generateId("folder")),
                            listOf(
                                SourceAndDestination(
                                    "../../../../../../../../../../../etc/passwd",
                                    generateId("password"),
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
                        with(initializeResourceTestContext(case.products.map { productV1toV2(it) }, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, _, support) = initializeCollection(
                                project = project,
                                rpcClient = rpcClient,
                                provider = product.category.provider,
                                productFilter = product
                            ) ?: error("Could not initialize collection")
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
                        val folder = generateId("myfolder")
                        input(MoveInput(
                            listOf(folder),
                            listOf(SourceAndDestination(folder, generateId("myfolder2"))))
                        )
                        check {}
                    }

                    case("Recursive") {
                        val top = generateId("top")
                        input(MoveInput(
                            listOf(top, "$top/2", "$top/2/3"),
                            listOf(SourceAndDestination(top, generateId("2")))
                        ))
                        check {}
                    }

                    case("UTF8") {
                        val folder = generateId("\uD83D\uDC36")
                        input(MoveInput(
                            listOf(folder),
                            listOf(SourceAndDestination(folder, generateId("testing"))))
                        )
                        check {}
                    }

                    case("Malicious") {
                        input(MoveInput(
                            listOf(generateId("folder")),
                            listOf(
                                SourceAndDestination(
                                    "../../../../../../../../../../../etc/passwd",
                                    generateId("password"),
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
                        with(initializeResourceTestContext(case.products.map { productV1toV2(it) }, emptyList())) {
                            val rpcClient = adminClient.withProject(project)
                            val (collection, _, support) = initializeCollection(
                                project = project,
                                rpcClient = rpcClient,
                                provider = product.category.provider,
                                productFilter = product
                            ) ?: error("Could not initialize collection")
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
                        val folder = generateId("myfolder")
                        input(TrashTest(
                            listOf(folder),
                            listOf(ItemToTrash(folder)),
                            clearTrash = false
                        ))
                        check {}
                    }

                    case("Simple") {
                        val folder = generateId("myfolder")
                        input(TrashTest(
                            listOf(folder),
                            listOf(ItemToTrash(folder)),
                            clearTrash = true
                        ))
                        check {}
                    }

                    case("Recursive") {
                        val top = generateId("top")
                        input(TrashTest(
                            listOf(top, "$top/2", "$top/2/3"),
                            listOf(ItemToTrash(top)),
                            clearTrash = true
                        ))
                        check {}
                    }

                    case("UTF8") {
                        val folder =  generateId("\uD83D\uDC36")
                        input(TrashTest(
                            listOf(folder),
                            listOf(ItemToTrash(folder)))
                        )
                        check {}
                    }

                    case("Malicious") {
                        input(TrashTest(
                            listOf(generateId("folder")),
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
