provider {
    id = "foobar"

    host "ucloud" {
        address = "xxxxxxxx"
        port = 443
    }

    host "self" {
        address = "xxxxxxxx"
        port = 443
    }

    ipc {
        directory = "/var/run/ucloud"
    }

    logs {
        directory = "/var/log/ucloud"
        rotation {
            enabled = true
            retentionDays = 180
        }
    }
}

services {
    type = "Slurm"

    identityManagement {
        type = "FreeIPA"
        config = "/etc/ucloud/ipa.hcl" # Contains URL, username and password
    }

    fileSystem "u1-ess" {
        management {
            type = "ESS"
            config = "/etc/ucloud/ess.hcl"
        }

        payment {
            type = "Resource"
            unit = "TB"
        }

        driveLocator "home" {
            entity = "User"
            pattern = "/home/#{username}"
        }

        driveLocator "projects" {
            entity = "Project"
            pattern = "/work/#{title}"
        }

        driveLocator "collections" {
            entity = "Collection"
            pattern = "/collections/#{id}"
        }

        driveLocator "memberFiles" {
            entity = "MemberFiles"
            pattern = "/projects/#{project}/#{username}"
        }
    }

    fileSystem "u1-generic-storage" {
        management {
            type = "Scripted"

            walletUpdated = "/opt/ucloud/on-storage-wallet-updated"
            fetchUsage = "/opt/ucloud/fetch-storage-usage"
        }

        payment {
            type = "Money"
            currency = "DKK"
            unit = "TB"
            interval = "Monthly"
            price = 123.456 # 123.456 DKK per TB paid monthly
        }

        driveLocator "projects" {
            entity = "Project"
            script = "/opt/ucloud/drive-locator"
        }
    }

    ssh {
        enabled = true
        installKeys = true
        hostname = "frontend.example.com"
        port = 22
    }

    licenses {
        enabled = false
    }

    slurm {
        /*
        # Fully automatic, few options to customize.
        accountManagement {
            type = "Automatic"
        }
        */

        # Fully manual, fully customizable.
        accountManagement {
            type = "Scripted"

            walletUpdated = "/opt/ucloud/on-compute-wallet-updated"
            fetchUsage = "/opt/ucloud/fetch-compute-usage"
            accountMapper = "/opt/ucloud/account-mapper"
            // ...
        }

        machine "u1-standard" {
            partition = "standard"
            constraint = "standard"

            nameSuffix = "Cpu"

            cpu    = [1, 2,  4,  8, 16,  32,  64]
            memory = [4, 8, 16, 32, 64, 128, 256]

            cpuModel = "Model"
            memoryModel = "Model"

            payment {
                type = "Resource"
                unit = "Cpu"
                interval = "Hourly"
            }
        }

        machine "u1-gpu" {
            partition = "gpu"
            constraint = "gpu"

            nameSuffix = "Gpu"

            cpu    = [   1,    2,    4,    8,   16,   32,   64]
            gpu    = [   1,    2,    3,    4,    5,    6,    7]
            memory = [   4,    8,   16,   32,   64,  128,  256]
            price  = [10.1, 20.2, 30.3, 40.4, 50.5, 60.6, 70.7]

            cpuModel = "Model"
            memoryModel = "Model"
            gpuModel = "Model"

            payment {
                type = "Money"
                currency = "DKK"
                interval = "Hourly"
            }
        }

        machine "hippo-hm" {
            payment {
                type = "Resource"
                unit = "Cpu"
                interval = "Hourly"
            }

            group "hippo-hm1" {
                partition = "standard"
                constraint = "hm1"

                nameSuffix = "Cpu"

                cpu    = [1, 2,  4,  8, 16,  32,  64]
                memory = [4, 8, 16, 32, 64, 128, 256]

                cpuModel = "Model 1"
                memoryModel = "Model 1"
            }

            group "hippo-hm2" {
                partition = "standard"
                constraint = "hm2"

                nameSuffix = "Cpu"

                cpu    = [1, 2,  4,  8, 16,  32,  64]
                memory = [2, 4,  8, 16, 32,  64, 128]

                cpuModel = "Model"
                memoryModel = "Model"
            }

            group "hippo-hm3" {
                partition = "standard"
                constraint = "hm3"

                nameSuffix = "Cpu"

                cpu    = [1, 2,  4,  8, 16,  32,  64]
                memory = [4, 8, 16, 32, 64, 128, 256]

                cpuModel = "Model 3"
                memoryModel = "Model 3"
            }
        }
    }
}
