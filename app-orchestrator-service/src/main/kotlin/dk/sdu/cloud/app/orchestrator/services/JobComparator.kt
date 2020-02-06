package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.VerifiedJob

class JobComparator {

    fun jobsEqual(storedJob: VerifiedJob, newJob: VerifiedJob): Boolean {
        //Check time and node settings
        println("checking")
        if (storedJob.maxTime != newJob.maxTime) {
            println("maxTime not same")
            return false
        }
        if (storedJob.nodes != newJob.nodes) {
            println("nodes not the same")
            return false
        }
        if (storedJob.tasksPerNode != newJob.tasksPerNode) {
            println("task per node not the same")
            return false
        }

        //Check mountMode
        if (storedJob.mountMode != null || newJob.mountMode != null) {
            if (storedJob.mountMode?.name != newJob.mountMode?.name) {
                println("mount Mode  (${storedJob.mountMode?.name}, ${newJob.mountMode?.name} )not the same")
                return false
            }
        }
        //Check reservation
        if (storedJob.reservation != newJob.reservation) {
            return false
        }
        // Check workspace and project
        if (storedJob.workspace != newJob.workspace) {
            println("workspace not the same")
            return false
        }
        if (storedJob.project != newJob.project) {
            println("project not the same")
            return false
        }
        println("Checking files")
        //Check files
        if (storedJob.files.isNotEmpty() || newJob.files.isNotEmpty()) {
            if (storedJob.files.size == newJob.files.size) {
                println("files same")
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
        println("Checking mounts")

        //Check Mounts
        if (storedJob.mounts.isNotEmpty() || newJob.mounts.isNotEmpty()) {
            if (storedJob.mounts.size == newJob.mounts.size) {
                println("mounts same")
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
        println("Checking peers")

        //Checking Peers
        if (storedJob.peers.isNotEmpty() || storedJob.peers.isNotEmpty()) {
            if (storedJob.peers.size == newJob.peers.size) {
                println("_peers same")
                storedJob.peers.forEach { applicationPeer ->
                    if (!newJob.peers.contains(applicationPeer)) {
                        return false
                    }
                }
            } else {
                return false
            }
        }
        println("Checking system mounts")

        //Check Shared File System mounts
        if (storedJob.sharedFileSystemMounts.isNotEmpty() || newJob.sharedFileSystemMounts.isNotEmpty()) {
            if (storedJob.sharedFileSystemMounts.size == newJob.sharedFileSystemMounts.size) {
                println("_sharedFile same")
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
        println("Checking backingData")
        if (storedJob.jobInput.backingData != newJob.jobInput.backingData) {
            return false
        }
        return true
    }
}
