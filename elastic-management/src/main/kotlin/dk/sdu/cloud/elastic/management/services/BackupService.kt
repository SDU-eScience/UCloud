package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest
import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryRequest
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.repositories.fs.FsRepository
import org.slf4j.Logger
import java.time.LocalDate

private const val REPO_NAME = "backup"


class BackupService(private val elastic: RestHighLevelClient, private val mountLocation: String) {

    fun start() {
        val exists = try {
            !elastic.snapshot().verifyRepository(
                VerifyRepositoryRequest(REPO_NAME), RequestOptions.DEFAULT).nodes.isEmpty()
        } catch (ex: ElasticsearchStatusException) {
            log.warn(ex.stackTraceToString())
            false
        }
        if (!exists) {
            log.info("Repository does not exist. Creating backup Repo.")
            setupRepo()
        }
        val date = LocalDate.now().toString().replace("-","." )

        val request = CreateSnapshotRequest()
        request.repository(REPO_NAME)

        val snapshotName = "snapshot_$date"
        request.snapshot(snapshotName)

        elastic.snapshot().create(request, RequestOptions.DEFAULT)
    }

    private fun setupRepo() {
        val request = PutRepositoryRequest()

        request.name(REPO_NAME)
        request.type(FsRepository.TYPE)

        val locationKey = FsRepository.LOCATION_SETTING.key
        val locationValue = mountLocation
        val compressKey = FsRepository.COMPRESS_SETTING.key
        val compressValue = true

        val settings = Settings.builder()
            .put(locationKey, locationValue)
            .put(compressKey, compressValue)
            .build()

        request.settings(settings)

        elastic.snapshot().createRepository(request, RequestOptions.DEFAULT)
    }

    fun deleteBackup() {
        //Always generate a date in the format YYYY.MM.dd
        val date = LocalDate.now().toString().replace("-","." )

        val request = DeleteSnapshotRequest(REPO_NAME, "snapshot_$date")
        elastic.snapshot().delete(request, RequestOptions.DEFAULT)
    }


    companion object : Loggable {
        override val log: Logger = ExpiredEntriesDeleteService.logger()
    }
}
