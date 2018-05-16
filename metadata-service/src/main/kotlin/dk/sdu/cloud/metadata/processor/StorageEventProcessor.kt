package dk.sdu.cloud.metadata.processor

import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
import dk.sdu.cloud.metadata.api.ProjectEvent
import dk.sdu.cloud.metadata.api.ProjectEventConsumer
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.services.MetadataCommandService
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.storage.api.StorageEvent
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

typealias StorageEventConsumer = KStream<String, StorageEvent>

interface Stream<K : Any, V : Any> {
    fun forEach(consumer: (K, V) -> Unit)
    fun <A : Any, B : Any> map(mapper: (K, V) -> Pair<A, B>): Stream<A, B>
    fun <B : Any> mapValues(mapper: (V) -> B): Stream<K, B>
    fun filter(predicate: (K, V) -> Boolean): Stream<K, V>
}

class KafkaStream<K : Any, V : Any>(private val delegate: KStream<K, V>) : Stream<K, V> {
    override fun forEach(consumer: (K, V) -> Unit) {
        delegate.foreach(consumer)
    }

    override fun <A : Any, B : Any> map(mapper: (K, V) -> Pair<A, B>): Stream<A, B> {
        return KafkaStream(delegate.map { key, value -> mapper(key, value).let { KeyValue(it.first, it.second) } })
    }

    override fun <B : Any> mapValues(mapper: (V) -> B): Stream<K, B> {
        return KafkaStream(delegate.mapValues { mapper(it) })
    }

    override fun filter(predicate: (K, V) -> Boolean): Stream<K, V> {
        return KafkaStream(delegate.filter { key, value -> predicate(key, value) })
    }
}

class StorageEventProcessor(
    private val storageEvents: StorageEventConsumer,
    private val projectEvents: ProjectEventConsumer,

    private val metadataCommandService: MetadataCommandService,
    private val projectService: ProjectService
) {
    fun init() {
        log.info("Initializing storage event processor")

        KafkaStream(storageEvents)
            .mapValues { event ->
                // Map by project to guarantee ordering of project events
                val project = projectService.findBestMatchingProjectByPath(event.path)
                        ?: return@mapValues Pair<StorageEvent, Project?>(event, null)
                project.id ?: return@mapValues Pair<StorageEvent, Project?>(event, null)

                Pair(event, project)
            }
            .filter { _, eventWithProject -> eventWithProject.second != null }
            .map { _, (event, project) -> project!! to event }
            .forEach { project, event ->
                // Then process the actual events
                val projectId = project.id!!

                when (event) {
                    is StorageEvent.CreatedOrModified -> {
                        metadataCommandService.addFiles(
                            projectId,
                            setOf(FileDescriptionForMetadata(event.id, event.type, event.path))
                        )
                    }

                    is StorageEvent.Moved -> {
                        metadataCommandService.updatePathOfFile(projectId, event.id, event.path)
                    }

                    is StorageEvent.Deleted -> {
                        metadataCommandService.removeFilesById(projectId, setOf(event.id))
                    }
                }
            }

        KafkaStream(projectEvents).forEach { _, event ->
            // NOTE(Dan): we want to keep the project and metadata abstractions very separate. A project does not need
            // a description. We can easily fill in dummy values for the people who don't want any thing special.
            // We can likely also source some of this information from their project application.

            when (event) {
                is ProjectEvent.Created -> {
                    metadataCommandService.create(
                        ProjectMetadata(
                            sduCloudRoot = event.project.fsRoot,
                            title = "Untitled project",
                            files = event.initialFiles,
                            creators = emptyList(),
                            description = "",
                            license = ""
                        )
                    )
                }
            }
        }

        log.info("Done")
    }

    companion object {
        private val log = LoggerFactory.getLogger(StorageEventProcessor::class.java)
    }
}

