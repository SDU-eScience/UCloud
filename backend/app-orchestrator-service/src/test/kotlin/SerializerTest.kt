package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.defaultMapper
import kotlin.test.Test

class SerializerTest {
    @Test
    fun testSerializationOfJob() {
        val job = """
            {
  "items" : [ {
    "id" : "13",
    "createdAt" : 1769764627664,
    "owner" : {
      "createdBy" : "user",
      "project" : null
    },
    "permissions" : {
      "myself" : [ ],
      "others" : [ ]
    },
    "updates" : [ ],
    "specification" : {
      "product" : {
        "id" : "u1-standard-2",
        "category" : "u1-standard",
        "provider" : "k8s"
      },
      "application" : {
        "name" : "jupyter-all-spark",
        "version" : "4.0.12"
      },
      "replicas" : 1,
      "allowDuplicateJob" : false,
      "parameters" : { },
      "resources" : [ ],
      "timeAllocation" : {
        "hours" : 1,
        "minutes" : 0,
        "seconds" : 0
      }
    },
    "status" : {
      "state" : "IN_QUEUE",
      "jobParametersJson" : {
        "siteVersion" : 3,
        "request" : {
          "application" : {
            "name" : "jupyter-all-spark",
            "version" : "4.0.12"
          },
          "product" : {
            "id" : "u1-standard-2",
            "category" : "u1-standard",
            "provider" : "k8s"
          },
          "name" : "",
          "replicas" : 1,
          "parameters" : { },
          "resources" : [ ],
          "timeAllocation" : {
            "hours" : 1,
            "minutes" : 0,
            "seconds" : 0
          },
          "resolvedProduct" : {
            "type" : "compute",
            "category" : {
              "name" : "u1-standard",
              "provider" : "k8s",
              "productType" : "COMPUTE",
              "accountingUnit" : {
                "name" : "Core",
                "namePlural" : "Core",
                "floatingPoint" : false,
                "displayFrequencySuffix" : true
              },
              "accountingFrequency" : "PERIODIC_MINUTE",
              "allowSubAllocations" : true
            },
            "name" : "u1-standard-2",
            "description" : "A compute product",
            "productType" : "COMPUTE",
            "price" : 2,
            "hiddenInGrantApplications" : false,
            "usage" : null,
            "cpu" : 2,
            "cpuModel" : "Model",
            "memoryInGigs" : 2,
            "memoryModel" : "Model",
            "balance" : 0,
            "maxUsableBalance" : 0,
            "pricePerUnit" : 0
          },
          "resolvedSupport" : {
            "product" : {
              "type" : "compute",
              "category" : {
                "name" : "u1-standard",
                "provider" : "k8s",
                "productType" : "COMPUTE",
                "accountingUnit" : {
                  "name" : "Core",
                  "namePlural" : "Core",
                  "floatingPoint" : false,
                  "displayFrequencySuffix" : true
                },
                "accountingFrequency" : "PERIODIC_MINUTE",
                "allowSubAllocations" : true
              },
              "name" : "u1-standard-2",
              "description" : "A compute product",
              "productType" : "COMPUTE",
              "price" : 2,
              "hiddenInGrantApplications" : false,
              "usage" : null,
              "cpu" : 2,
              "cpuModel" : "Model",
              "memoryInGigs" : 2,
              "memoryModel" : "Model",
              "balance" : 0,
              "maxUsableBalance" : 0,
              "pricePerUnit" : 0
            },
            "support" : {
              "product" : {
                "id" : "u1-standard-2",
                "category" : "u1-standard",
                "provider" : "k8s"
              },
              "docker" : {
                "enabled" : true,
                "web" : true,
                "vnc" : true,
                "logs" : true,
                "terminal" : true,
                "peers" : true,
                "timeExtension" : true
              },
              "virtualMachine" : { },
              "native" : { }
            },
            "features" : [ "jobs.docker.enabled", "jobs.docker.extension", "jobs.docker.logs", "jobs.docker.peers", "jobs.docker.terminal", "jobs.docker.vnc", "jobs.docker.web" ]
          },
          "allowDuplicateJob" : false,
          "sshEnabled" : false
        },
        "resolvedResources" : {
          "ingress" : null
        },
        "machineType" : {
          "cpu" : 2,
          "memoryInGigs" : 2
        }
      },
      "startedAt" : null,
      "expiresAt" : null,
      "resolvedApplication" : {
        "metadata" : {
          "name" : "jupyter-all-spark",
          "version" : "4.0.12",
          "authors" : [ "UCloud" ],
          "title" : "JupyterLab",
          "description" : "JupyterLab ecosystem for Data Science. Installed kernels: Python, R, Scala, Go, Julia, Kotlin, Rust.\n",
          "website" : "https://docs.cloud.sdu.dk/Apps/jupyter-lab.html",
          "public" : true,
          "flavorName" : "Base",
          "group" : {
            "metadata" : {
              "id" : 39
            },
            "specification" : {
              "title" : "",
              "description" : "",
              "defaultFlavor" : "",
              "categories" : null,
              "colorReplacement" : {
                "light" : null,
                "dark" : null
              },
              "logoHasText" : false
            },
            "status" : {
              "applications" : null
            }
          },
          "createdAt" : 1769673447064
        },
        "invocation" : {
          "tool" : {
            "name" : "jupyter-all-spark",
            "version" : "4.0.12",
            "tool" : {
              "owner" : "_ucloud",
              "createdAt" : 1769673447064,
              "modifiedAt" : 1769673447064,
              "description" : {
                "info" : {
                  "name" : "jupyter-all-spark",
                  "version" : "4.0.12"
                },
                "defaultNumberOfNodes" : 1,
                "defaultTimeAllocation" : {
                  "hours" : 1,
                  "minutes" : 0,
                  "seconds" : 0
                },
                "requiredModules" : [ ],
                "authors" : [ "SDU eScience" ],
                "title" : "Jupyter",
                "description" : "JupyterLab ecosystem for Data Science. Installed kernels: Python, R, Scala, Go, Julia, Kotlin, Rust.\n",
                "backend" : "DOCKER",
                "license" : "The 3-Clause BSD License",
                "image" : "dreg.cloud.sdu.dk/ucloud-apps/jupyter-all-spark:4.0.12",
                "container" : "dreg.cloud.sdu.dk/ucloud-apps/jupyter-all-spark:4.0.12",
                "supportedProviders" : null,
                "loadInstructions" : null
              }
            }
          },
          "invocation" : [ {
            "type" : "word",
            "variable" : "",
            "word" : "/sbin/tini",
            "variableNames" : null,
            "prefixGlobal" : "",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          }, {
            "type" : "word",
            "variable" : "",
            "word" : "--",
            "variableNames" : null,
            "prefixGlobal" : "",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          }, {
            "type" : "word",
            "variable" : "",
            "word" : "start-jupyter",
            "variableNames" : null,
            "prefixGlobal" : "",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          }, {
            "type" : "word",
            "variable" : "",
            "word" : "-p",
            "variableNames" : null,
            "prefixGlobal" : "",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          }, {
            "type" : "word",
            "variable" : "",
            "word" : "8888",
            "variableNames" : null,
            "prefixGlobal" : "",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          }, {
            "type" : "var",
            "variable" : "",
            "word" : "",
            "variableNames" : [ "requirements" ],
            "prefixGlobal" : "-r ",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          }, {
            "type" : "var",
            "variable" : "",
            "word" : "",
            "variableNames" : [ "batch" ],
            "prefixGlobal" : "-m ",
            "suffixGlobal" : "",
            "prefixVariable" : "",
            "suffixVariable" : "",
            "isPrefixVariablePartOfArg" : false,
            "isSuffixVariablePartOfArg" : false,
            "variableName" : "",
            "flag" : "",
            "template" : ""
          } ],
          "parameters" : [ {
            "type" : "input_file",
            "name" : "requirements",
            "optional" : true,
            "defaultValue" : null,
            "title" : "Initialization",
            "description" : "File with list of dependencies: .txt, .yml (Conda), and .sh (Bash)\n",
            "min" : null,
            "max" : null,
            "step" : null,
            "unitName" : "",
            "trueValue" : "",
            "falseValue" : "",
            "options" : null,
            "tagged" : null,
            "supportedModules" : null
          }, {
            "type" : "input_file",
            "name" : "batch",
            "optional" : true,
            "defaultValue" : null,
            "title" : "Batch processing",
            "description" : "Submit a Bash script for batch mode execution\n",
            "min" : null,
            "max" : null,
            "step" : null,
            "unitName" : "",
            "trueValue" : "",
            "falseValue" : "",
            "options" : null,
            "tagged" : null,
            "supportedModules" : null
          } ],
          "outputFileGlobs" : [ "*", "stdout.txt", "stderr.txt" ],
          "applicationType" : "WEB",
          "vnc" : null,
          "web" : {
            "port" : 8888
          },
          "ssh" : {
            "mode" : "OPTIONAL"
          },
          "container" : {
            "changeWorkingDirectory" : true,
            "runAsRoot" : true,
            "runAsRealUser" : false
          },
          "environment" : null,
          "allowAdditionalMounts" : true,
          "allowAdditionalPeers" : null,
          "allowMultiNode" : false,
          "allowPublicIp" : false,
          "allowPublicLink" : true,
          "fileExtensions" : [ ".py", ".R", ".jl", ".JL", ".go", ".GO", ".sh", ".java", ".json", ".md", ".rst", ".txt", ".csv", ".xlm", ".js", ".JS", ".kt", ".ktm", ".kts", ".m", ".rs", ".sc", ".sqlite", ".db", ".ipynb" ],
          "licenseServers" : [ ],
          "modules" : {
            "mountPath" : "",
            "optional" : [ ]
          },
          "sbatch" : null
        },
        "favorite" : false,
        "versions" : null
      },
      "resolvedProduct" : {
        "type" : "compute",
        "category" : {
          "name" : "u1-standard",
          "provider" : "k8s",
          "productType" : "COMPUTE",
          "accountingUnit" : {
            "name" : "Core",
            "namePlural" : "Core",
            "floatingPoint" : false,
            "displayFrequencySuffix" : true
          },
          "accountingFrequency" : "PERIODIC_MINUTE",
          "allowSubAllocations" : true
        },
        "name" : "u1-standard-2",
        "description" : "A compute product",
        "productType" : "COMPUTE",
        "price" : 2,
        "hiddenInGrantApplications" : false,
        "usage" : null,
        "cpu" : 2,
        "cpuModel" : "Model",
        "memoryInGigs" : 2,
        "memoryModel" : "Model",
        "balance" : 0,
        "maxUsableBalance" : 0,
        "pricePerUnit" : 0
      },
      "resolvedSupport" : {
        "product" : {
          "type" : "compute",
          "category" : {
            "name" : "u1-standard",
            "provider" : "k8s",
            "productType" : "COMPUTE",
            "accountingUnit" : {
              "name" : "Core",
              "namePlural" : "Core",
              "floatingPoint" : false,
              "displayFrequencySuffix" : true
            },
            "accountingFrequency" : "PERIODIC_MINUTE",
            "allowSubAllocations" : true
          },
          "name" : "u1-standard-2",
          "description" : "A compute product",
          "productType" : "COMPUTE",
          "price" : 2,
          "hiddenInGrantApplications" : false,
          "usage" : null,
          "cpu" : 2,
          "cpuModel" : "Model",
          "memoryInGigs" : 2,
          "memoryModel" : "Model",
          "balance" : 0,
          "maxUsableBalance" : 0,
          "pricePerUnit" : 0
        },
        "support" : {
          "product" : {
            "id" : "u1-standard-2",
            "category" : "u1-standard",
            "provider" : "k8s"
          },
          "docker" : {
            "enabled" : true,
            "web" : true,
            "vnc" : true,
            "logs" : true,
            "terminal" : true,
            "peers" : true,
            "timeExtension" : true
          },
          "virtualMachine" : { },
          "native" : { }
        },
        "features" : [ "jobs.docker.enabled", "jobs.docker.extension", "jobs.docker.logs", "jobs.docker.peers", "jobs.docker.terminal", "jobs.docker.vnc", "jobs.docker.web" ]
      },
      "allowRestart" : false
    },
    "output" : {
      "outputFolder" : null
    }
  } ]
}
        """.trimIndent()

        val decoded = defaultMapper.decodeFromString(BulkRequest.serializer(Job.serializer()), job)
        println(decoded)
    }
}