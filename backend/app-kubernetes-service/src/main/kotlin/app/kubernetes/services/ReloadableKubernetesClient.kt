package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.Doneable
import io.fabric8.kubernetes.api.model.DoneableSecret
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretList
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.ExtensionsAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.KubernetesListMixedOperation
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable
import io.fabric8.kubernetes.client.dsl.NetworkAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable
import io.fabric8.kubernetes.client.dsl.PolicyAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.RbacAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.SchedulingAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.SettingsAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.SubjectAccessReviewDSL
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.informers.SharedInformerFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.ExecutorService

class ReloadableKubernetesClient(
    val allowReloading: Boolean,
    val configFiles: List<File>
) : KubernetesClient {
    lateinit var delegate: KubernetesClient

    init {
        reload()
    }

    fun reload() {
        if (allowReloading) {
            for (file in configFiles) {
                if (file.exists()) {
                    log.info("Reloading Kubernetes client to use $file")
                    val config = Config.fromKubeconfig(file.readText())
                    delegate = DefaultKubernetesClient(config)
                    return
                }
            }
        }

        log.info("Using default Kubernetes configuration")
        delegate = DefaultKubernetesClient()
    }

    companion object : Loggable {
        override val log = logger()
    }

    override fun lists(): KubernetesListMixedOperation = delegate.lists()
    override fun apps(): AppsAPIGroupDSL = delegate.apps()
    override fun resourceQuotas() = delegate.resourceQuotas()
    override fun policy(): PolicyAPIGroupDSL = delegate.policy()
    override fun informers(): SharedInformerFactory = delegate.informers()
    override fun informers(executorService: ExecutorService?): SharedInformerFactory =
        delegate.informers(executorService)
    override fun extensions(): ExtensionsAPIGroupDSL = delegate.extensions()
    override fun subjectAccessReviewAuth(): SubjectAccessReviewDSL = delegate.subjectAccessReviewAuth()
    override fun batch(): BatchAPIGroupDSL = delegate.batch()
    override fun services() = delegate.services()
    override fun limitRanges() = delegate.limitRanges()
    override fun supportsApiPath(path: String?) = delegate.supportsApiPath(path)
    override fun getConfiguration(): Config = delegate.configuration
    override fun getApiVersion(): String = delegate.apiVersion
    override fun getVersion() = delegate.version
    override fun rbac(): RbacAPIGroupDSL = delegate.rbac()
    override fun network(): NetworkAPIGroupDSL = delegate.network()
    override fun close() = delegate.close()
    override fun persistentVolumes() = delegate.persistentVolumes()
    override fun getMasterUrl() = delegate.masterUrl
    override fun configMaps() = delegate.configMaps()
    override fun componentstatuses() = delegate.componentstatuses()
    override fun customResourceDefinitions() = delegate.customResourceDefinitions()
    override fun resourceList(s: String?) = delegate.resourceList(s)
    override fun resourceList(list: KubernetesResourceList<*>?) = delegate.resourceList(list)
    override fun resourceList(vararg items: HasMetadata?) = delegate.resourceList(*items)
    override fun resourceList(items: MutableCollection<HasMetadata>?) = delegate.resourceList(items)
    override fun settings(): SettingsAPIGroupDSL = delegate.settings()
    override fun bindings() = delegate.bindings()
    override fun autoscaling() = delegate.autoscaling()
    override fun <C : Any?> adapt(type: Class<C>?): C = delegate.adapt(type)
    override fun namespaces() = delegate.namespaces()
    override fun scheduling(): SchedulingAPIGroupDSL = delegate.scheduling()
    override fun pods() = delegate.pods()
    override fun nodes() = delegate.nodes()
    override fun rootPaths() = delegate.rootPaths()
    override fun <C : Any?> isAdaptable(type: Class<C>?): Boolean = delegate.isAdaptable(type)
    override fun getNamespace(): String = delegate.namespace
    override fun events() = delegate.events()
    override fun endpoints() = delegate.endpoints()
    override fun serviceAccounts() = delegate.serviceAccounts()
    override fun replicationControllers() = delegate.replicationControllers()
    override fun <T : HasMetadata?, L : KubernetesResourceList<*>?, D : Doneable<T>?> customResources(
        crd: CustomResourceDefinition?,
        resourceType: Class<T>?,
        listClass: Class<L>?,
        doneClass: Class<D>?
    ) = delegate.customResources(crd, resourceType, listClass, doneClass)
    override fun persistentVolumeClaims() = delegate.persistentVolumeClaims()
    override fun storage() = delegate.storage()
    override fun <T : HasMetadata?, L : KubernetesResourceList<*>?, D : Doneable<T>?> customResource(
        crd: CustomResourceDefinition?,
        resourceType: Class<T>?,
        listClass: Class<L>?,
        doneClass: Class<D>?
    ) = delegate.customResources(crd, resourceType, listClass, doneClass)
    override fun customResource(customResourceDefinition: CustomResourceDefinitionContext?) =
        delegate.customResource(customResourceDefinition)
    override fun <T : HasMetadata?> resource(`is`: T) = delegate.resource(`is`)
    override fun resource(s: String?) = delegate.resource(s)
    override fun load(`is`: InputStream?) = delegate.load(`is`)
    override fun secrets() = delegate.secrets()
}
