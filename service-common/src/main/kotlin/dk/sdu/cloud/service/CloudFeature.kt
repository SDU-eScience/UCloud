package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.ServiceDescription

class CloudFeature : MicroFeature {
    private lateinit var ctx: Micro
    internal var cloud: AuthenticatedCloud? = null
    private var maxWeight: Int = Int.MIN_VALUE

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx
    }

    fun addAuthenticatedCloud(weight: Int, cloud: AuthenticatedCloud) {
        if (weight > maxWeight) this.cloud = cloud
    }

    companion object Feature : MicroFeatureFactory<CloudFeature, Unit> {
        override val key: MicroAttributeKey<CloudFeature> = MicroAttributeKey("cloud-feature")
        override fun create(config: Unit): CloudFeature = CloudFeature()
    }
}

val Micro.authenticatedCloud: AuthenticatedCloud
    get() = feature(CloudFeature).cloud
        ?: throw IllegalStateException("No cloud configured. Use addAuthenticatedCloud()")
