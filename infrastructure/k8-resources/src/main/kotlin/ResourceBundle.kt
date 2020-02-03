package dk.sdu.cloud.k8

import kotlin.properties.Delegates

interface ResourceBundle {
    val name: String
    val version: String
    val resources: List<KubernetesResource>
}

class MutableBundle : ResourceBundle {
    override var name: String by Delegates.notNull()
    override var version: String by Delegates.notNull()
    override val resources = ArrayList<KubernetesResource>()
}

fun bundle(init: MutableBundle.() -> Unit): ResourceBundle {
    return MutableBundle()
        .apply(init)
        .also { BundleRegistry.addBundle(it) }
}

object BundleRegistry {
    private val bundles = HashMap<String, ResourceBundle>()

    fun addBundle(bundle: ResourceBundle) {
        bundles[bundle.name] = bundle
    }

    fun getBundle(bundleName: String): ResourceBundle? {
        return bundles[bundleName]
    }

    fun listBundles(): Collection<ResourceBundle> {
        return bundles.values
    }
}
