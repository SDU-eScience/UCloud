package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.snapshot.*
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.repositories.fs.FsRepository
import org.slf4j.Logger
import java.time.LocalDate

private const val REPO_NAME = "backup"


class BackupService(private val elastic: ElasticsearchClient, private val mountLocation: String) {

    fun start() {
        val exists = try {
            elastic.snapshot().verifyRepository(
                VerifyRepositoryRequest.Builder()
                    .name(REPO_NAME)
                    .build()
            ).nodes().isNotEmpty()
        } catch (ex: ElasticsearchStatusException) {
            log.warn(ex.stackTraceToString())
            false
        }
        if (!exists) {
            log.info("Repository does not exist. Creating backup Repo.")
            setupRepo()
        }
        val date = LocalDate.now().toString().replace("-","." )

        val snapshotName = "snapshot_$date"

        val request = CreateSnapshotRequest.Builder()
            .repository(REPO_NAME)
            .snapshot(snapshotName)
            .build()

        elastic.snapshot().create(request)
    }

    private fun setupRepo() {
        val request = CreateRepositoryRequest.Builder()
            .name(REPO_NAME)
            .type(FsRepository.TYPE)
            .settings(
                RepositorySettings.Builder()
                    .location(mountLocation)
                    .compress(true)
                    .build()
            )
            .build()

        elastic.snapshot().createRepository(request)
    }

    fun deleteBackup() {
        //Always generate a date in the format YYYY.MM.dd
        val date = LocalDate.now().toString().replace("-","." )

        val request = DeleteSnapshotRequest.Builder()
            .repository(REPO_NAME)
            .snapshot("snapshot_$date")
            .build()
        elastic.snapshot().delete(request)
    }


    companion object : Loggable {
        override val log: Logger = ExpiredEntriesDeleteService.logger()
    }
}
