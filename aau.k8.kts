config("ceph") { ctx ->
    configure("enabled", false)
}

config("stolon") { ctx ->
    configure("claimStorageClass", "nfs")
}

config("redis") { ctx ->
    configure("claimStorageClass", "nfs")

    configure("claimSize", "10Gi")

    configure("slaveCpu", "2000m")
    configure("slaveMem", "2096Mi")

    configure("masterCpu", "4000m")
    configure("masterMem", "4096Mi")
}

config("kibana") {
    configure("enabled", false)
}

config("elasticsearch") { ctx ->
    configure("storageClassName", "nfs")
    configure("dataStorage", "200Gi")

    configure("masterCpu", "2000m")
    configure("clientCpu", "2000m")
    configure("dataCpu", "4000m")

    configure("masterMem", "4Gi")
    configure("dataMem", "8Gi")
    configure("clientMem", "8Gi")

    configure("masterCount", 2)
    configure("clientCount", 2)
    configure("dataCount", 2)
    configure("minMasterNodes", 0)
}
