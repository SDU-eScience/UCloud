package dk.sdu.cloud.app.kubernetes.services

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.DoneablePod
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.PodSpecBuilder
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder
import io.fabric8.kubernetes.api.model.SecurityContext
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeBuilder
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import io.fabric8.kubernetes.api.model.batch.DoneableJob
import io.fabric8.kubernetes.api.model.batch.JobSpecBuilder
import kotlinx.coroutines.delay


fun PodTemplateSpecBuilder.metadata(builder: ObjectMetaBuilder.() -> Unit): PodTemplateSpecBuilder {
    val objectMetaBuilder = ObjectMetaBuilder()
    objectMetaBuilder.builder()
    withMetadata(objectMetaBuilder.build())
    return this
}

fun DoneableJob.metadata(builder: ObjectMetaBuilder.() -> Unit): DoneableJob {
    val objectMetaBuilder = ObjectMetaBuilder()
    objectMetaBuilder.builder()
    withMetadata(objectMetaBuilder.build())
    return this
}


fun DoneablePod.metadata(builder: ObjectMetaBuilder.() -> Unit): DoneablePod {
    val objectMetaBuilder = ObjectMetaBuilder()
    objectMetaBuilder.builder()
    withMetadata(objectMetaBuilder.build())
    return this
}

fun DoneableJob.spec(builder: JobSpecBuilder.() -> Unit): DoneableJob {
    val podBuilder = JobSpecBuilder()
    podBuilder.builder()
    withSpec(podBuilder.build())
    return this
}

fun PodTemplateSpecBuilder.spec(builder: PodSpecBuilder.() -> Unit): PodTemplateSpecBuilder {
    val podBuilder = PodSpecBuilder()
    podBuilder.builder()
    withSpec(podBuilder.build())
    return this
}

fun DoneablePod.spec(builder: PodSpecBuilder.() -> Unit): DoneablePod {
    val podBuilder = PodSpecBuilder()
    podBuilder.builder()
    withSpec(podBuilder.build())
    return this
}

fun container(builder: ContainerBuilder.() -> Unit): Container {
    val containerBuilder = ContainerBuilder()
    containerBuilder.builder()
    return containerBuilder.build()
}

fun simpleContainer(builder: Container.() -> Unit): Container {
    return Container().apply(builder)
}

fun volume(builder: Volume.() -> Unit): Volume {
    return Volume().apply(builder)
}

fun securityContext(builder: SecurityContext.() -> Unit): SecurityContext = SecurityContext().apply(builder)

fun volumeMount(builder: VolumeMount.() -> Unit): VolumeMount {
    val myBuilder = VolumeMount()
    myBuilder.apply(builder)
    return myBuilder
}

suspend fun await(retries: Int = 50, time: Long = 100, condition: () -> Boolean) {
    for (attempt in 0 until retries) {
        if (condition()) return
        delay(time)
    }

    throw IllegalStateException("Condition failed!")
}

suspend fun awaitCatching(retries: Int = 50, time: Long = 100, condition: () -> Boolean) {
    for (attempt in 0 until retries) {
        if (runCatching(condition).getOrNull() == true) return
        delay(time)
    }

    throw IllegalStateException("Condition failed!")
}

