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

config("grafana") {
    configure("enabled", false)
}

config("prometheus") {
    configure("enabled", false)
}

config("auth") {
    configure("scheme", "http")
    configure("trustLocalhost", false)
    configure("host", "ucloud-pilot.aau.dk")
    configure("cert", """
        -----BEGIN CERTIFICATE-----
        MIIDSDCCAjACCQC6eO3nsWJlzDANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJE
        SzETMBEGA1UECAwKU3lkZGFubWFyazELMAkGA1UEBwwCTkExETAPBgNVBAoMCGVT
        Y2llbmNlMREwDwYDVQQLDAhlU2NpZW5jZTEPMA0GA1UEAwwGdWNsb3VkMB4XDTIw
        MDUyNzExMTgwM1oXDTIxMDUyNzExMTgwM1owZjELMAkGA1UEBhMCREsxEzARBgNV
        BAgMClN5ZGRhbm1hcmsxCzAJBgNVBAcMAk5BMREwDwYDVQQKDAhlU2NpZW5jZTER
        MA8GA1UECwwIZVNjaWVuY2UxDzANBgNVBAMMBnVjbG91ZDCCASIwDQYJKoZIhvcN
        AQEBBQADggEPADCCAQoCggEBAM1vGt1ZKYhz9j+wiE1+UaW58SNvlTN+kgOxC1Cr
        gjzRRjPhikqKy0hvhQOEZ5jTw2omynUqu/uqOSI7BEgZ4kxOfxQ/wqgR6oCfvFVw
        lQvyZSxTUD16w8fsPEG8ngkw/mNLBMkDmxRo3Q2Z27YEuNfTOjzqJSVP5+COpMnF
        FIszZ9dboaAI+UiUnniy16CuEPxMhELc8XzCBWko8LZ2BOJBXAwX+sR4KoSyH8E5
        57BzF08L3xGwPQcWhDufOFA7dxwx9kPVWXfyytpYzxckWqMTiUnxLa6yCdgl/4rK
        5qzE69kR2vsfZ8RKZgd4isIzHlU47IHDKfKwCP+RuXRuj/0CAwEAATANBgkqhkiG
        9w0BAQsFAAOCAQEAZ8spJtSEMFkQhu5+o5TqQPVv/m+CUclCALwkF5YQixWUkRYs
        SAWSC4+x+PQvwki6Y9Z/9Z7SXgEn8Lk3WrKhlCQG2U16C/NpYUcu1OjjN5w05NqD
        RQmweTlTSSXbnml952ADXJns6S0INbOsrA8hBvX6m2iNCUHqym8zgnLZ8BH43jm9
        UVs390uDfUA2yIOSoER6SWHHPLH07PmKqDrhLZNkvKtlg1oTl+Fu+uBYbeEZxkry
        COHVZ9LHbjhAvl5JJpIU9dUzsn7zQb9STgo2elxT36RM4BaqqQkGeQcqML2znKFg
        bk3tBXIXMoKNMbsbuY4hxQLdEhZm3SKafqsydA==
        -----END CERTIFICATE-----
    """.trimIndent())
}

config("app-kubernetes") { ctx ->
    configure("prefix", "app-")
    configure("domain", "ucloud-pilot.aau.dk")
}

config("webdav") { ctx ->
    configure("domain", "dav.ucloud-pilot.aau.dk")
}

config("app-orchestrator") { ctx ->
    fun MachineType(name: String, cpu: Int?, memoryInGigs: Int?, gpu: Int? = null) =
        mapOf<String, Any?>("name" to name, "cpu" to cpu, "memoryInGigs" to memoryInGigs, "gpu" to gpu)

    configure("machines", listOf(
        MachineType("Unspecified", null, null),
        MachineType("Small (S)", 1, 4),
        MachineType("Medium (M)", 4, 16),
        MachineType("Large (L)", 16, 32)
    ))
}

config("project") {
    configure("enabled", true)
}
