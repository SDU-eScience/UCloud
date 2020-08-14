config("ceph") { ctx ->
    configure("enabled", false)

    if (ctx.environment in setOf("test", "development")) {
        configure("monitors", "10.135.0.15:6789,10.135.0.16:6789,10.135.0.17:6789")
    } else {
        configure("monitors", "172.26.3.1:6789,172.26.3.2:6789,172.26.3.3:6789")
    }
}

config("stolon") {
    configure("claimStorageClass", "rbd")
}

config("redis") { ctx ->
    configure("claimStorageClass", "rbd")

    if (ctx.environment == "production") {
        configure("claimSize", "5000Gi")

        configure("slaveCpu", "4000m")
        configure("slaveMem", "4096Mi")

        configure("masterCpu", "8000m")
        configure("masterMem", "8192Mi")
    } else {
        configure("claimSize", "1000Gi")
    }
}

config("kibana") { ctx ->
    when (ctx.environment) {
        "production" -> {
            configure("hostname", "elasticsearch-newmaster")
        }

        "test" -> {
            configure("hostname", "elasticsearch-master")
        }

        "development" -> {
            configure("hostname", "elasticsearch-newmaster")
        }
    }
}

config("elasticsearch") { ctx ->
    configure("masterCpu", "2000m")
    configure("clientCpu", "2000m")
    configure("dataCpu", "8000m")

    configure("masterMem", "12Gi")
    configure("dataMem", "30Gi")
    configure("clientMem", "12Gi")

    configure("storageClassName", "rbd")

    when (ctx.environment) {
        "production" -> {
            configure("masterCount", 3)
            configure("clientCount", 2)
            configure("dataCount", 4)
            configure("dataStorage", "5000Gi")
        }

        "test" -> {
            configure("masterCount", 2)
            configure("clientCount", 2)
            configure("dataCount", 2)
            configure("minMasterNodes", 0)
            configure("dataStorage", "500Gi")
        }

        "development" -> {
            configure("masterCount", 3)
            configure("clientCount", 2)
            configure("dataCount", 3)
            configure("dataStorage", "500Gi")
        }
    }
}

config("app-orchestrator") { ctx ->
    fun MachineType(name: String, cpu: Int?, memoryInGigs: Int?, gpu: Int? = null) =
        mapOf<String, Any?>("name" to name, "cpu" to cpu, "memoryInGigs" to memoryInGigs, "gpu" to gpu)

    configure("gpuWhitelist", listOf(
        "marin@imada.sdu.dk",
        "boegebjerg@imada.sdu.dk",
        "tochr15@student.sdu.dk",
        "alaks17@student.sdu.dk",
        "hmoel15@student.sdu.dk",
        "sejr@imada.sdu.dk",
        "ruizhang@imada.sdu.dk",
        "mehrooz@imada.sdu.dk",
        "nomi@imada.sdu.dk",
        "andrea.lekkas@outlook.com",
        "alfal19@student.sdu.dk",
        "fiorenza@imada.sdu.dk",
        "veits@bmb.sdu.dk",
        "petersk@imada.sdu.dk",
        "roettger@imada.sdu.dk",
        "pica@cp3.sdu.dk",
        "konradk@bmb.sdu.dk",
        "vasileios@bmb.sdu.dk",
        "svensson@imada.sdu.dk",
        "dthrane@imada.sdu.dk",
        "jakoj17@student.sdu.dk",
        "alfal19@student.sdu.dk",
        "greisager@imada.sdu.dk"
    ))

    when (ctx.environment) {
        "development", "test" -> {
            configure("machines", listOf(
                MachineType("Unspecified", null, null),
                MachineType("Small (S)", 1, 4),
                MachineType("Medium (M)", 4, 16),
                MachineType("Large (L)", 16, 32)
            ))
        }

        "production" -> {
            configure("machines", listOf(
                MachineType("Unspecified", null, null),
                MachineType("u1-standard-1", 1, 6),
                MachineType("u1-standard-2", 2, 12),
                MachineType("u1-standard-4", 4, 24),
                MachineType("u1-standard-8", 8, 48),
                MachineType("u1-standard-16", 16, 96),
                MachineType("u1-standard-32", 32, 192),
                MachineType("u1-standard-64", 62, 370),
                MachineType("u1-gpu-1", 16, 48, 1),
                MachineType("u1-gpu-2", 32, 96, 2),
                MachineType("u1-gpu-3", 48, 144, 3),
                MachineType("u1-gpu-4", 78, 185, 4)
            ))
        }
    }
}

config("project") { ctx ->
    configure("enabled", ctx.environment != "production")
}

config("webdav") { ctx ->
    when (ctx.environment) {
        "test" -> {
            configure("domain", "davs.dev.cloud.sdu.dk")
        }

        "development" -> {
            configure("domain", "webdav.dev.cloud.sdu.dk")
        }

        "production" -> {
            configure("domain", "dav.cloud.sdu.dk")
        }
    }
}

config("storage") { ctx ->
    when (ctx.environment) {
        "development" -> {
            configure("mountLocation", "dev")
        }

        "test" -> {
            configure("mountLocation", "test")
        }
    }
}

config("app-kubernetes") { ctx ->
    when (ctx.environment) {
        "test" -> {
            configure("prefix", "apps-")
            configure("domain", "dev.cloud.sdu.dk")
        }

        "development" -> {
            configure("prefix", "app-")
            configure("domain", "dev.cloud.sdu.dk")
        }

        "production" -> {
            configure("prefix", "app-")
            configure("domain", "cloud.sdu.dk")
        }
    }

    configure("internalEgressWhitelist", listOf(
        // allow tek-ansys.tek.c.sdu.dk
        "10.144.4.166/32",

        // allow tek-comsol0a.tek.c.sdu.dk
        "10.144.4.169/32",

        // coumputational biology server SDU (requested by Emiliano)
        "10.137.1.93/32"
    ))
}

config("audit-ingestion") { ctx ->
    when (ctx.environment) {
        "development", "production" -> {
            configure("secret", "elasticsearch-logging-cluster-credentials")
        }
    }
}

config("auth") { ctx ->
    configure("scheme", "https")

    when (ctx.environment) {
        "development" -> {
            configure("trustLocalhost", true)
            configure("host", "dev.cloud.sdu.dk")
            configure("cert", """
                -----BEGIN CERTIFICATE-----
                MIIDfzCCAmegAwIBAgIUDlxDPskNFRztsjog68XuA4jjGZkwDQYJKoZIhvcNAQEL
                BQAwTzELMAkGA1UEBhMCREsxDDAKBgNVBAgMA0Z5bjEPMA0GA1UEBwwGT2RlbnNl
                MREwDwYDVQQKDAhlU2NpZW5jZTEOMAwGA1UEAwwFQnJpYW4wHhcNMTkxMDAxMTEz
                NTM0WhcNMjAwOTMwMTEzNTM0WjBPMQswCQYDVQQGEwJESzEMMAoGA1UECAwDRnlu
                MQ8wDQYDVQQHDAZPZGVuc2UxETAPBgNVBAoMCGVTY2llbmNlMQ4wDAYDVQQDDAVC
                cmlhbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMBg0lJG0fpH02pc
                46DEfBm4xe0caQpptxQX4uq8Bl4jW8Lf+ycl2UC3qP/ccCsBmAvmSS1DWRnxSDjc
                eWvSP7oYJarVhPlavj/uNpRDCIs0JBGIRvJB2szriJCwxJXtAyAqTOi9apUQq8we
                9gXB2E48HLo230xnOfUR1++O01aQOVosIrNZvZEwxP6HHXL6TYVRRzlfi0OgYjMs
                by5Jx65l2HVqJZGV/WOfwBYVdaJEJGM3PMXIuZSJmRJX/clgrjCeaQRFMt/BDPnF
                sjfg2xuTZz8dhDpsYel2d9GdDpmI5Yb7bfXaj2AYZ+KXcGIuhNPV8dycvSFgqH4B
                btTrFwUCAwEAAaNTMFEwHQYDVR0OBBYEFJpREMDgQ+CYNKWKE955VW5GtE82MB8G
                A1UdIwQYMBaAFJpREMDgQ+CYNKWKE955VW5GtE82MA8GA1UdEwEB/wQFMAMBAf8w
                DQYJKoZIhvcNAQELBQADggEBAKKhwgVtqPxoAmaKjC/i4KWpYltCBZtQB0NwXRxp
                WlFZ/rnPxA8dCDej1T/dvW3LgCF2su91e44ImH/z+6liJa6O5yHxs/rWT5RsdDNy
                gFMmOBcCHgCS1bcHyz0ZUtOkPvFLODC2vfdxKa3fks7C5O2CKDsBkIMxqu/TMU1S
                /DY5UHyr0nI2jur2M/xcYTEg4RuQRljr4i9vBENdZd/wfKAEPgRDjDVMoxhdi4R6
                zCEdr3vdt8PNI7AbaO7N4znbT8ftmhtxs8+YlgmomSI4vu8FvkDk34xx4T0A6OCC
                dy5pKr7I/0JbWjqjdb26wgDPhAL8Ts6wV6o23xNtAGgJhJ0=
                -----END CERTIFICATE-----
            """.trimIndent())
        }

        "test" -> {
            configure("trustLocalhost", true)
            configure("host", "staging.dev.cloud.sdu.dk")
            configure("cert", """
                -----BEGIN CERTIFICATE-----
                MIIDSDCCAjACCQCOMA9vihmVcDANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJE
                SzETMBEGA1UECAwKU3lkZGFubWFyazELMAkGA1UEBwwCTkExETAPBgNVBAoMCGVT
                Y2llbmNlMREwDwYDVQQLDAhlU2NpZW5jZTEPMA0GA1UEAwwGdWNsb3VkMB4XDTIw
                MDIxODEzNDYyNloXDTIxMDIxNzEzNDYyNlowZjELMAkGA1UEBhMCREsxEzARBgNV
                BAgMClN5ZGRhbm1hcmsxCzAJBgNVBAcMAk5BMREwDwYDVQQKDAhlU2NpZW5jZTER
                MA8GA1UECwwIZVNjaWVuY2UxDzANBgNVBAMMBnVjbG91ZDCCASIwDQYJKoZIhvcN
                AQEBBQADggEPADCCAQoCggEBAK8TIpxSyi1BwiiAFTDTHFVXSa/nHT882s22hikk
                6mljQohCqHXGpSTuINMM5ma5wdsqFfYb/nIz7fXdDktW/hAbhDIkoOLGxb4BJx+S
                /Ce3LZXSKlT8CxJ+Ayw66APG2ntksqQVkKvPD+HUpSEV5mXR+E+3uzj8Vd8e1SYi
                h/423zfJ8bJA7TSripi85BWzwMbWJYbLT4wW1PwOhNpwhqqClTjcnlfeqBb3SMmj
                pgKg5bM6YuZKyoSrKMF2WjzBxw1aOwBRKbO8Z12I8noFeGDw9+w/caYG+ZvusIEy
                oTk/+zhG8hRRyNa2RCAZspz06jaCUV1aFxX7Hl/yvRQAu6sCAwEAATANBgkqhkiG
                9w0BAQsFAAOCAQEAAmgal9lScwRkLMV2CSBhCcnog/PL5bIiQj0HieRkHb8gUiwk
                OGbOxDH2P/Gn/4WDO6SwQDXb8L/Kk3e+jDD8snt2n1Pqmw/7WgemTZoEQMVKuWGM
                TseyfMEA6PFA7OZTi4CMtfq/Qh0rlPN72wA3fxjOS8upku9X0S2gsLuNQorj8R/5
                iAHo/fPNAmAHVI8cyQRWbcP5K7TvEf3Ij9yBByas50AHoMdjCoSFqoIVtiT8JlMa
                0iRq2Uj2uVqPocy2tJ2K27FHKRR3H6YSdjzZ/Ys+9kxc22I40nLVLfCdnBV9/GGU
                a/e5GT6FiQPV79ntyelUEQCkqT7Xr0uSt4Cc2g==
                -----END CERTIFICATE-----
            """.trimIndent())
        }

        "production" -> {
            configure("host", "cloud.sdu.dk")
            configure("enablyWayf", true)
            configure("cert", """
                    -----BEGIN CERTIFICATE-----
                    MIIDNDCCAhwCCQCshN+wCG6lEzANBgkqhkiG9w0BAQsFADBcMQswCQYDVQQGEwJE
                    SzEdMBsGA1UECgwUU3lkZGFuc2sgVW5pdmVyc2l0ZXQxFzAVBgNVBAsMDmVTY2ll
                    bmNlQ2VudGVyMRUwEwYDVQQDDAxjbG91ZC5zZHUuZGswHhcNMTgxMTE1MTMwNjU2
                    WhcNMTkxMTE1MTMwNjU2WjBcMQswCQYDVQQGEwJESzEdMBsGA1UECgwUU3lkZGFu
                    c2sgVW5pdmVyc2l0ZXQxFzAVBgNVBAsMDmVTY2llbmNlQ2VudGVyMRUwEwYDVQQD
                    DAxjbG91ZC5zZHUuZGswggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCf
                    ZMpChrOcUx4sPr3FXrpJ6n77H0ueHXzMIGKyh7+JZuScB77eMwWa6MCeo58byzPu
                    1u6Je6W3QWt/vWYNFwj+yAFv9FRjh67mpB7v4Tew6P1HcIrSBE6P+cOdtDO1augf
                    fAI8K77FuVC7ZVlTWwP2wjOQIvBTOEtoTN1IOAlmbFwRkX+rRwZ1U53ZNo17PW0T
                    QHxdD90NbYqx/wuFus1UdgBrI0uVTOmJG7ohiWt8bpW5mz+et4SGgFGl2LD6mv4l
                    etHzhOJgMMVEAA8o5TwwxCYw5QaGdLtZ1jPTfWj3w0wJxPTcPj39unI/ztfrW+OG
                    efHsK02igOfRKv8rbKcJAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAG4KYesD3ZM/
                    fh5lHFd6BcCCC6n9+TkeeFqNoWnmeQN6HZAv7X4OdQ3wyMV2fbeGWRG23OjqcTud
                    5Z1NfXZPoVCq+PeG1PcUsgA5iTSNpPENGEJHp1fVFJIrRjaaVUYa5mB58M8x29Hi
                    52DnIKUth4svRL5JtikuNEtFWOUmoX4QNrgxPGyRaqGwWNXD5EUMRgVeaq97rBB1
                    euWW6VhEvo5S5p64K0E1EjGHv3N384/Nu8+P7sKX3vQorNiidnSJlMl+VARcV6k9
                    eWK+YvfER32gylkRqG56k2oC9AuRKV88mLVCV7HcpA2Q1gDIqRVXhMavgFZ+Mxh+
                    Ms12PEWBG3Q=
                    -----END CERTIFICATE-----               
            """.trimIndent())
        }
    }
}

config("indexing") { ctx ->
    when (ctx.environment) {
        "development" -> {
            configure("numberOfShards", 5)
            configure("numberOfReplicas", 2)
        }

        "test" -> {
            configure("numberOfShards", 2)
            configure("numberOfReplicas", 1)
        }

        "production" -> {
            configure("numberOfShards", 5)
            configure("numberOfReplicas", 2)
        }
    }
}

