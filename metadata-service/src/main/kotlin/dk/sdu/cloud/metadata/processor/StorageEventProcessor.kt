package dk.sdu.cloud.metadata.processor

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.metadata.api.ProjectEvent
import dk.sdu.cloud.metadata.api.ProjectEventConsumer
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.services.MetadataCommandService
import dk.sdu.cloud.metadata.services.ProjectException
import dk.sdu.cloud.metadata.services.ProjectService
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

typealias StorageEventConsumer = KStream<String, StorageEvent>

class StorageEventProcessor(
    private val storageEvents: StorageEventConsumer,
    private val projectEvents: ProjectEventConsumer,

    private val metadataCommandService: MetadataCommandService,
    private val projectService: ProjectService<*>,
    private val cloud: AuthenticatedCloud
) {
    fun init() {
        log.info("Initializing storage event processor")

        storageEvents
            .filter { _, event -> event is StorageEvent.Deleted || event is StorageEvent.Invalidated }
            .foreach { _, event ->
                // TODO Technically we should not assume that Invalidated means deleted.
                // It is valid for the storage-service to emit Invalidated followed by a refresh of the real file.
                // We should instead enqueue an internal delete if we don't receive a refresh of the real file within,
                // say, 48 hours.

                log.debug("Handling StorageEvent: $event")

                try {
                    val project = projectService.findByFSRoot(event.path)
                    metadataCommandService.delete(project.owner, project.id!!)
                    projectService.deleteProjectByRoot(event.path)
                } catch (e: ProjectException.NotFound) {
                    // Do nothing
                    log.debug("No project for ${event.path}")
                }
            }

        projectEvents.foreach { _, event ->
            // NOTE(Dan): we want to keep the project and metadata abstractions very separate. A project does not need
            // a description. We can easily fill in dummy values for the people who don't want any thing special.
            // We can likely also source some of this information from their project application.

            when (event) {
                is ProjectEvent.Created -> {
                    metadataCommandService.create(
                        ProjectMetadata(
                            sduCloudRoot = event.project.fsRoot,
                            sduCloudRootId = event.project.fsRootId,
                            title = "Untitled project",
                            creators = emptyList(),
                            description = "Project description goes here...",
                            license = null,
                            id = event.projectId.toString()
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

