package dk.sdu.cloud.k8

import kotlin.properties.Delegates

typealias MutableBundle = ResourceBundle

class ResourceBundle {
    var name: String by Delegates.notNull()
    var version: String by Delegates.notNull()
    val resources = ArrayList<KubernetesResource>()
}

typealias BundleInit = MutableBundle.(ctx: DeploymentContext) -> Unit
fun bundle(init: BundleInit): ResourceBundle {
    return MutableBundle()
        .also { BundleRegistry.addBundle(it, init) }
}

object BundleRegistry {
    private val bundles = HashMap<String, Pair<ResourceBundle, BundleInit>>()
    val allBundles = ArrayList<Pair<ResourceBundle, BundleInit>>()

    fun addBundle(
        bundle: ResourceBundle,
        init: BundleInit
    ) {
        allBundles.add(Pair(bundle, { ctx ->
            init(ctx)
            bundles[bundle.name] = Pair(bundle, init)
        }))
    }

    fun getBundle(bundleName: String): Pair<ResourceBundle, BundleInit>? {
        return bundles[bundleName]
    }

    fun listBundles(): Collection<Pair<ResourceBundle, BundleInit>> {
        return allBundles
    }
}
