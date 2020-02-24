package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.VerifiedJob

class JobComparator {

    fun jobsEqual(storedJob: VerifiedJob, newJob: VerifiedJob): Boolean {
        //Check time and node settings
        if (storedJob.maxTime != newJob.maxTime) {
            return false
        }
        if (storedJob.nodes != newJob.nodes) {
            return false
        }
        if (storedJob.tasksPerNode != newJob.tasksPerNode) {
            return false
        }

        //Check mountMode
        if (storedJob.mountMode != null || newJob.mountMode != null) {
            if (storedJob.mountMode?.name != newJob.mountMode?.name) {
                return false
            }
        }
        //Check reservation
        if (storedJob.reservation != newJob.reservation) {
            return false
        }
        // Check project
        if (storedJob.project != newJob.project) {
            return false
        }
        //Check files
        if (storedJob.files.isNotEmpty() || newJob.files.isNotEmpty()) {
            if (storedJob.files.size == newJob.files.size) {
                val storedJobFilesId = storedJob.files.map { it.id }
                val newJobFilesId = newJob.files.map { it.id }
                storedJobFilesId.forEach { id ->
                    if (!newJobFilesId.contains(id)) {
                        return false
                    }
                }
            }
            else {
                return false
            }
        }
        //Check Mounts
        if (storedJob.mounts.isNotEmpty() || newJob.mounts.isNotEmpty()) {
            if (storedJob.mounts.size == newJob.mounts.size) {
                val storedJobMountsId = storedJob.mounts.map { it.id }
                val newJobMountsId = newJob.mounts.map { it.id }
                storedJobMountsId.forEach { id ->
                    if (!newJobMountsId.contains(id)) {
                        return false
                    }
                }
            } else {
                return false
            }
        }

        //Checking Peers
        if (storedJob.peers.isNotEmpty() || storedJob.peers.isNotEmpty()) {
            if (storedJob.peers.size == newJob.peers.size) {
                storedJob.peers.forEach { applicationPeer ->
                    if (!newJob.peers.contains(applicationPeer)) {
                        return false
                    }
                }
            } else {

                return false
            }
        }

        //Check Shared File System mounts
        if (storedJob.sharedFileSystemMounts.isNotEmpty() || newJob.sharedFileSystemMounts.isNotEmpty()) {
            if (storedJob.sharedFileSystemMounts.size == newJob.sharedFileSystemMounts.size) {
                val storedJobSharedFileSystemID = storedJob.sharedFileSystemMounts.map { it.sharedFileSystem.id }
                val newJobSharedFileSystemID = newJob.sharedFileSystemMounts.map { it.sharedFileSystem.id }
                storedJobSharedFileSystemID.forEach { id ->
                    if (!newJobSharedFileSystemID.contains(id)) {
                        return false
                    }
                }
            } else {
                return false
            }
        }

        //Checking jobInput
        if (storedJob.jobInput.backingData != newJob.jobInput.backingData) {
            return false
        }
        return true
    }
}
