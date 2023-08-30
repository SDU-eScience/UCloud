package dk.sdu.cloud.config

import kotlin.test.*

class ConfigParserTest {
    @Test
    fun `scaling test`() {
        val parsed = parse(
            // language=yaml
            """
              compute:
                u1-gpu:
                  cost:
                    type: Money
                    currency: DKK
                  template:
                    cpu: 10
                    memory: 80
                    gpu: [1, 2, 3, 4]
                    cpuModel: cpu
                    gpuModel: gpu
                    memoryModel: mem
                    nameSuffix: Gpu
                    description: u1-gpu
                    pricePerHour: 100
            """.trimIndent()
        )

        val category = parsed.compute.singleOrNull() ?: error("Could not find category in output")
        assertEquals("u1-gpu", category.name)
        val products = category.products as List<IndividualProduct<IndividualProduct.ProductSpec.Compute>>
        val cost = category.cost ?: error("Cost should not be null")
        if (cost !is ProductCost.Money) error("cost should be of type money ($cost)")
        assertEquals("DKK", cost.currency)
        assertEquals(4, category.products.size)
        assertEquals(listOf("u1-gpu-1", "u1-gpu-2", "u1-gpu-3", "u1-gpu-4"), category.products.map { it.name })
        assertEquals(listOf(10, 20, 30, 40), products.map { it.spec.cpu })
        assertEquals(listOf(80, 160, 240, 320), products.map { it.spec.memory })
        assertEquals(listOf(1, 2, 3, 4), products.map { it.spec.gpu })
        assertEquals(listOf(100_000_000L, 200_000_000L, 300_000_000L, 400_000_000L), products.map { it.price })
        if (!products.all { it.description == "u1-gpu" }) {
            error("Description is wrong: ${products.map { it.description }}")
        }
        if (!products.all { it.spec.cpuModel == "cpu" }) {
            error("cpu model is wrong: ${products.map { it.spec.cpuModel }}")
        }
        if (!products.all { it.spec.gpuModel == "gpu" }) {
            error("gpu model is wrong: ${products.map { it.spec.gpuModel }}")
        }
        if (!products.all { it.spec.memoryModel == "mem" }) {
            error("mem model is wrong: ${products.map { it.spec.memoryModel }}")
        }
    }

    @Test
    fun `no scaling test`() {
        val parsed = parse(
            // language=yaml
            """
              compute:
                u1-gpu:
                  cost:
                    type: Money
                    currency: DKK
                  template:
                    cpu: [10, 21, 31, 41]
                    memory: [80, 161, 241, 321]
                    gpu: [1, 2, 3, 4]
                    cpuModel: cpu
                    gpuModel: gpu
                    memoryModel: mem
                    nameSuffix: Gpu
                    description: u1-gpu
                    pricePerHour: [101, 201, 301, 400.5]
            """.trimIndent()
        )

        val category = parsed.compute.singleOrNull() ?: error("Could not find category in output")
        assertEquals("u1-gpu", category.name)
        val products = category.products as List<IndividualProduct<IndividualProduct.ProductSpec.Compute>>
        val cost = category.cost ?: error("Cost should not be null")
        if (cost !is ProductCost.Money) error("cost should be of type money ($cost)")
        assertEquals("DKK", cost.currency)
        assertEquals(4, category.products.size)
        assertEquals(listOf("u1-gpu-1", "u1-gpu-2", "u1-gpu-3", "u1-gpu-4"), category.products.map { it.name })
        assertEquals(listOf(10, 21, 31, 41), products.map { it.spec.cpu })
        assertEquals(listOf(80, 161, 241, 321), products.map { it.spec.memory })
        assertEquals(listOf(1, 2, 3, 4), products.map { it.spec.gpu })
        assertEquals(listOf(101_000_000L, 201_000_000L, 301_000_000L, 400_500_000L), products.map { it.price })
        if (!products.all { it.description == "u1-gpu" }) {
            error("Description is wrong: ${products.map { it.description }}")
        }
        if (!products.all { it.spec.cpuModel == "cpu" }) {
            error("cpu model is wrong: ${products.map { it.spec.cpuModel }}")
        }
        if (!products.all { it.spec.gpuModel == "gpu" }) {
            error("gpu model is wrong: ${products.map { it.spec.gpuModel }}")
        }
        if (!products.all { it.spec.memoryModel == "mem" }) {
            error("mem model is wrong: ${products.map { it.spec.memoryModel }}")
        }
    }

    @Test
    fun `no template`() {
        val parsed = parse(
            // language=yaml
            """
                compute:
                  syncthing:
                    cost: { type: Free }
                    syncthing:
                      cpu: 1
                      memory: 2
                      gpu: 0
                      description: syncthingdescription
            """.trimIndent()
        )

        val category = parsed.compute.singleOrNull() ?: error("Could not find category in output")
        assertEquals("syncthing", category.name)
        val products = category.products as List<IndividualProduct<IndividualProduct.ProductSpec.Compute>>
        val cost = category.cost ?: error("Cost should not be null")
        if (cost != ProductCost.Free) error("cost should be of type free ($cost)")

        val product = products.singleOrNull() ?: error("not the correct amount of products: $products")
        assertEquals(0, product.price)
        assertEquals("syncthing", product.name)
        assertEquals("syncthingdescription", product.description)
        assertEquals(1, product.spec.cpu)
        assertEquals(2, product.spec.memory)
        assertEquals(0, product.spec.gpu)
        assertEquals(null, product.spec.cpuModel)
        assertEquals(null, product.spec.gpuModel)
        assertEquals(null, product.spec.memoryModel)
    }

    @Test
    fun `storage product`() {
        val parsed = parse(
            // language=yaml
            """
                storage:
                  u1-storage:
                    cost:
                      type: Money
                      unit: GiB
                    u1-storage:
                      description: storage description
                      pricePerDay: 0.1
            """.trimIndent()
        )

        val category = parsed.storage.singleOrNull() ?: error("Could not find category in output")
        assertEquals("u1-storage", category.name)
        val products = category.products as List<IndividualProduct<IndividualProduct.ProductSpec.Storage>>
        val cost = category.cost ?: error("Cost should not be null")
        if (cost !is ProductCost.Money) error("cost should be of type money ($cost)")

        val product = products.singleOrNull() ?: error("not the correct amount of products: $products")
        assertEquals(100_000, product.price)
        assertEquals("GiB", cost.unit)
        assertEquals("u1-storage", product.name)
        assertEquals("storage description", product.description)
        assertEquals(StorageUnit.GiB, product.spec.unit)
    }
}
