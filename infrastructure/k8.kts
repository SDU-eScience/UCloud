//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

import java.io.File
import java.nio.file.Files
import java.util.*

bundle { ctx ->
    name = "ceph"
    version = "1"

    val cephFsSubFolder = when (ctx.environment) {
        Environment.DEVELOPMENT -> ""
        Environment.PRODUCTION -> ""
        Environment.TEST -> "staging"
    }

    withConfigMap("ceph-fs-config", version = "3") {
        addConfig(
            "config.yml",
            """
                ceph:
                  subfolder: "$cephFsSubFolder"
                  useCephDirectoryStats: true
            """.trimIndent()
        )
    }

    if (ctx.environment != Environment.PRODUCTION) {
        val cephMonitors = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "10.135.0.15:6789,10.135.0.16:6789,10.135.0.17:6789"
            Environment.PRODUCTION -> "172.26.3.1:6789,172.26.3.2:6789,172.26.3.3:6789"
        }

        val cephFsUser = when (ctx.environment) {
            Environment.PRODUCTION -> {
                TODO()
            }

            Environment.TEST, Environment.DEVELOPMENT -> {
                "snaptest"
            }
        }

        val cephFsSecret = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "snaptest"
            Environment.PRODUCTION -> TODO()
        }

        withSecret(name = cephFsSecret, namespace = "default") {
            val scanner = Scanner(System.`in`)
            println("Please enter ceph fs key: ")
            val adminKey = scanner.nextLine()
            secret.stringData = mapOf("key" to adminKey)
        }

        withSecret(name = "ceph-secret", namespace = "kube-system") {
            val scanner = Scanner(System.`in`)
            println("Please enter ceph admin key: ")
            val adminKey = scanner.nextLine()
            secret.stringData = mapOf("key" to adminKey)
        }

        withSecret(name = "ceph-secret-kube", namespace = "kube-system") {
            val scanner = Scanner(System.`in`)
            println("Please enter key for ceph user (kube pool): ")
            val userKey = scanner.nextLine()
            secret.stringData = mapOf("key" to userKey)
        }

        val cephfsVolumes = mapOf(
            "app-cephfs" to "app-kubernetes/cephfs",
            "app-fs-cephfs" to "default/app-fs-k8s",
            "cephfs" to "default/cephfs"
        )
        cephfsVolumes.forEach { (volName, pvc) ->
            withPersistentVolume(volName) {
                resource.spec.cephfs = CephFSPersistentVolumeSource().apply {
                    this.monitors = cephMonitors.split(",")
                    this.user = cephFsUser
                    this.secretRef = SecretReference().apply {
                        this.name = cephFsSecret
                        this.namespace = "default"
                    }
                }
                resource.spec.accessModes = listOf("ReadWriteMany")
                resource.spec.capacity = mapOf("storage" to Quantity("9223372036854775807"))
                resource.spec.persistentVolumeReclaimPolicy = "Retain"
            }

            val (ns, pvcName) = pvc.split("/")
            withPersistentVolumeClaim(pvcName) {
                resource.metadata.namespace = ns
                resource.spec.accessModes = listOf("ReadWriteMany")
                resource.spec.volumeName = volName
            }
        }

        resources.add(
            object : KubernetesResource {
                override val phase = DeploymentPhase.DEPLOY
                override fun toString() = "RBD-Provisioner"
                val cm = ConfigMapResource("rbd-provisioner-version", "1")

                override fun DeploymentContext.create() {
                    YamlResource(
                        """
                            apiVersion: apps/v1
                            kind: Deployment
                            metadata:
                              annotations:
                                deployment.kubernetes.io/revision: "1"
                              labels:
                                app: rbd-provisioner
                              name: rbd-provisioner
                              namespace: kube-system
                            spec:
                              progressDeadlineSeconds: 2147483647
                              replicas: 1
                              revisionHistoryLimit: 2147483647
                              selector:
                                matchLabels:
                                  app: rbd-provisioner
                              strategy:
                                type: Recreate
                              template:
                                metadata:
                                  creationTimestamp: null
                                  labels:
                                    app: rbd-provisioner
                                spec:
                                  containers:
                                  - env:
                                    - name: PROVISIONER_NAME
                                      value: ceph.com/rbd
                                    image: quay.io/external_storage/rbd-provisioner:latest
                                    imagePullPolicy: Always
                                    name: rbd-provisioner
                                    resources: {}
                                    securityContext:
                                      allowPrivilegeEscalation: true
                                    terminationMessagePath: /dev/termination-log
                                    terminationMessagePolicy: File
                                  dnsPolicy: ClusterFirst
                                  imagePullSecrets:
                                  - name: esci-docker
                                  restartPolicy: Always
                                  schedulerName: default-scheduler
                                  securityContext: {}
                                  serviceAccount: rbd-provisioner
                                  serviceAccountName: rbd-provisioner
                                  terminationGracePeriodSeconds: 30
                                       
                        """.trimIndent()
                    ).apply { create() }

                    YamlResource(
                        """
                            apiVersion: storage.k8s.io/v1
                            kind: StorageClass
                            metadata:
                              name: rbd
                            parameters:
                              adminId: admin
                              adminSecretName: ceph-secret
                              adminSecretNamespace: kube-system
                              imageFeatures: layering
                              imageFormat: "2"
                              monitors: $cephMonitors
                              pool: kube
                              userId: kube
                              userSecretName: ceph-secret-kube
                              userSecretNamespace: kube-system
                            provisioner: ceph.com/rbd
                            reclaimPolicy: Delete
                            volumeBindingMode: Immediate

                        """.trimIndent()
                    ).apply { create() }

                    YamlResource(
                        """
                            apiVersion: rbac.authorization.k8s.io/v1
                            kind: ClusterRole
                            metadata:
                              name: rbd-provisioner
                            rules:
                            - apiGroups:
                              - ""
                              resources:
                              - secrets
                              verbs:
                              - get
                              - list
                            - apiGroups:
                              - ""
                              resources:
                              - persistentvolumes
                              verbs:
                              - get
                              - list
                              - watch
                              - create
                              - delete
                            - apiGroups:
                              - ""
                              resources:
                              - persistentvolumeclaims
                              verbs:
                              - get
                              - list
                              - watch
                              - update
                            - apiGroups:
                              - storage.k8s.io
                              resources:
                              - storageclasses
                              verbs:
                              - get
                              - list
                              - watch
                            - apiGroups:
                              - ""
                              resources:
                              - events
                              verbs:
                              - get
                              - create
                              - list
                              - update
                              - patch
                            - apiGroups:
                              - ""
                              resourceNames:
                              - kube-dns
                              - coredns
                              resources:
                              - services
                              verbs:
                              - list
                              - get
                            - apiGroups:
                              - ""
                              resources:
                              - endpoints
                              verbs:
                              - get
                              - list
                              - watch
                              - create
                              - update
                              - patch

                        """.trimIndent()
                    ).apply { create() }

                    YamlResource(
                        """
                            apiVersion: rbac.authorization.k8s.io/v1
                            kind: ClusterRoleBinding
                            metadata:
                              name: rbd-provisioner
                            roleRef:
                              apiGroup: rbac.authorization.k8s.io
                              kind: ClusterRole
                              name: rbd-provisioner
                            subjects:
                            - kind: ServiceAccount
                              name: rbd-provisioner
                              namespace: kube-system

                        """.trimIndent()
                    ).apply { create() }

                    YamlResource(
                        """
                            apiVersion: v1
                            kind: ServiceAccount
                            metadata:
                              name: rbd-provisioner
                              namespace: kube-system

                        """.trimIndent()
                    ).apply { create() }

                    with(cm) { create() }
                }

                override fun DeploymentContext.delete() {
                    // Not implemented
                }

                override fun DeploymentContext.isUpToDate(): Boolean = with(cm) { isUpToDate() }
            }
        )
    }
}

bundle { ctx ->
    name = "stolon"
    version = "1"

    withConfigMap("pgaudit") {
        configMap.metadata.namespace = "stolon"
        configMap.binaryData = mapOf(
            "pgaudit.so" to String(
                Base64.getEncoder().encode(
                    File(
                        ctx.repositoryRoot,
                        "infrastructure/pgaudit.so"
                    ).readBytes()
                )
            )
        )
    }

    withService(name = "postgres") {
        service.spec.apply {
            type = "ExternalName"
            externalName = "stolon-proxy.stolon.svc.cluster.local"
        }
    }

    withHelmChart("stolon") {
        chartVersion = "1.5.6"
        val size = when (ctx.environment) {
            Environment.TEST, Environment.DEVELOPMENT -> "250Gi"
            Environment.PRODUCTION -> "250Gi" // For some reason this is also the production value
        }

        val suPassword = ctx.client
            .secrets()
            .inNamespace("stolon")
            .withName("stolon")
            .get()
            ?.data
            ?.get("pg_su_password")
            ?.let { Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }
            ?: UUID.randomUUID().toString()

        val replPassword = ctx.client
            .secrets()
            .inNamespace("stolon")
            .withName("stolon")
            .get()
            ?.data
            ?.get("pg_repl_password")
            ?.let { Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }
            ?: UUID.randomUUID().toString()

        //language=yml
        valuesAsString = """
           superuserPassword: $suPassword
           replicationPassword: $replPassword
           image:
             tag: v0.14.0-pg10
           keeper:
             volumeMounts:
             - mountPath: /usr/lib/postgresql/10/lib/pgaudit.so
               name: pgaudit
               subPath: pgaudit.so
             volumes:
             - configMap:
                 name: pgaudit
               name: pgaudit
           persistence:
             size: $size
             storageClassName: rbd
           pgParameters:
             datestyle: iso, mdy
             default_text_search_config: pg_catalog.english
             dynamic_shared_memory_type: posix
             lc_messages: en_US.utf8
             lc_monetary: en_US.utf8
             lc_numeric: en_US.utf8
             lc_time: en_US.utf8
             log_connections: "true"
             log_destination: stderr
             log_disconnections: "true"
             log_statement: mod
             log_timezone: UTC
             logging_collector: "true"
             maintenance_work_mem: 250MB
             max_connections: "2000"
             shared_buffers: 4000MB
             shared_preload_libraries: pgaudit
             ssl: false
             timezone: UTC
             wal_level: replica 
        """.trimIndent()
    }
}

bundle {
    name = "ambassador"
    version = "1"

    withHelmChart("ambassador", namespace = "default") {
        chartVersion = "5.3.1"
        //language=yml
        valuesAsString = """
            adminService:
              create: false
            daemonSet: true
            replicaCount: 1
            service:
              enableHttps: false
              http:
                port: 8888
              type: NodePort            
        """.trimIndent()
    }
}

bundle {
    name = "grafana"
    version = "1"

    withHelmChart("grafana") {
        chartVersion = "4.6.3"
        //language=yml
        valuesAsString = """
            image:
              tag: 6.5.0
            persistence:
              accessModes:
              - ReadWriteOnce
              enabled: true
              size: 100Gi
              storageClassName: rbd
            sidecar:
              dashboards:
                enabled: true
                label: grafana_dashboard
              datasources:
                enabled: true
                label: grafana_datasource            
        """.trimIndent()
    }
}

bundle { ctx ->
    name = "kibana"
    version = "2"

    withHelmChart("kibana", namespace = "elasticsearch") {
        chartVersion = "7.6.0"
        repo = HelmRepo("elastic", "https://helm.elastic.co")
        val hostname = when (ctx.environment) {
            Environment.TEST -> "elasticsearch-master"
            Environment.PRODUCTION, Environment.DEVELOPMENT -> "elasticsearch-newmaster"
        }

        //language=yml
        valuesAsString = """
            elasticsearchHosts: http://$hostname:9200
            extraEnvs:
            - name: ELASTICSEARCH_USERNAME
              valueFrom:
                secretKeyRef:
                  key: username
                  name: elasticsearch-kibana-credentials
            - name: ELASTICSEARCH_PASSWORD
              valueFrom:
                secretKeyRef:
                  key: password
                  name: elasticsearch-kibana-credentials
            kibanaConfig:
              kibana.yml: |
                elasticsearch.ssl:
                  certificateAuthorities: /usr/share/kibana/config/certs/elastic-node.p12
                  verificationMode: certificate
            protocol: http
            secretMounts:
            - name: elastic-certificates
              path: /usr/share/kibana/config/certs
              secretName: elastic-certificates            
        """.trimIndent()
    }
}

bundle { ctx ->
    name = "redis"
    version = "1"

    withHelmChart("redis") {
        chartVersion = "10.2.1"
        val rbdSize = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "1000Gi"
            Environment.PRODUCTION -> "5000Gi"
        }

        val masterCpuRequest = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "4000m"
            Environment.PRODUCTION -> "8000m"
        }

        val masterMemRequest = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "4096Mi"
            Environment.PRODUCTION -> "8192Mi"
        }

        val slaveCpuRequest = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "2000m"
            Environment.PRODUCTION -> "4000m"
        }

        val slaveMemRequest = when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> "2048Mi"
            Environment.PRODUCTION -> "4096Mi"
        }

        //language=yml
        valuesAsString = """
            cluster:
              enabled: true
              slaveCount: 3
            configmap: |-
              # Enable AOF https://redis.io/topics/persistence#append-only-file
              appendonly yes
              # Disable RDB persistence, AOF persistence already enabled.
              save ""
            image:
              pullPolicy: IfNotPresent
              registry: docker.io
              repository: bitnami/redis
              tag: 5.0.5
            master:
              disableCommands:
              - FLUSHDB
              - FLUSHALL
              persistence:
                accessModes:
                - ReadWriteOnce
                enabled: true
                size: $rbdSize
                storageClass: rbd
              resources:
                requests:
                  cpu: $masterCpuRequest
                  memory: $masterMemRequest
            metrics:
              enabled: true
            networkPolicy:
              enabled: false
            persistence:
              enabled: true
              size: $rbdSize
              storageClass: rbd
            rbac:
              create: false
            redisPort: 6379
            securityContext:
              enabled: true
              fsGroup: 1001
              runAsUser: 1001
            sentinel:
              enabled: false
            serviceAccount:
              create: false
            slave:
              disableCommands:
              - FLUSHDB
              - FLUSHALL
              persistence:
                accessModes:
                - ReadWriteOnce
                enabled: true
                size: $rbdSize
                storageClass: rbd
              resources:
                requests:
                  cpu: $slaveCpuRequest
                  memory: $slaveMemRequest
            usePassword: false
            
        """.trimIndent()
    }

    withService(name = "redis") {
        service.spec.apply {
            type = "ExternalName"
            externalName = "redis-master.redis.svc.cluster.local"
        }
    }
}

bundle {
    name = "prometheus"
    version = "1"

    withHelmChart("prometheus") {
        chartVersion = "10.4.0"

        //language=yml
        valuesAsString = """
            alertmanager:
              enabled: false
            nodeExporter:
              image:
                repository: quay.io/prometheus/node-exporter
                tag: v0.15.0
              prometheus.yml:
                rule_files:
                - /etc/config/rules
                - /etc/config/alerts
                scrape_configs:
                - job_name: prometheus
                  static_configs:
                  - targets:
                    - localhost:9090
                - bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
                  job_name: kubernetes-apiservers
                  kubernetes_sd_configs:
                  - role: endpoints
                  relabel_configs:
                  - action: keep
                    regex: default;kubernetes;https
                    source_labels:
                    - __meta_kubernetes_namespace
                    - __meta_kubernetes_service_name
                    - __meta_kubernetes_endpoint_port_name
                  scheme: https
                  tls_config:
                    ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
                    insecure_skip_verify: true
                - bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
                  job_name: kubernetes-nodes
                  kubernetes_sd_configs:
                  - role: node
                  relabel_configs:
                  - action: labelmap
                    regex: __meta_kubernetes_node_label_(.+)
                  - replacement: kubernetes.default.svc:443
                    target_label: __address__
                  - regex: (.+)
                    replacement: /api/v1/nodes/${'$'}1/proxy/metrics
                    source_labels:
                    - __meta_kubernetes_node_name
                    target_label: __metrics_path__
                  scheme: https
                  tls_config:
                    ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
                    insecure_skip_verify: true
                - bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
                  job_name: kubernetes-nodes-cadvisor
                  kubernetes_sd_configs:
                  - role: node
                  relabel_configs:
                  - action: labelmap
                    regex: __meta_kubernetes_node_label_(.+)
                  - replacement: kubernetes.default.svc:443
                    target_label: __address__
                  - regex: (.+)
                    replacement: /api/v1/nodes/${'$'}1/proxy/metrics/cadvisor
                    source_labels:
                    - __meta_kubernetes_node_name
                    target_label: __metrics_path__
                  scheme: https
                  tls_config:
                    ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
                    insecure_skip_verify: true
                - job_name: kubernetes-service-endpoints
                  kubernetes_sd_configs:
                  - role: endpoints
                  relabel_configs:
                  - action: keep
                    regex: true
                    source_labels:
                    - __meta_kubernetes_service_annotation_prometheus_io_scrape
                  - action: replace
                    regex: (https?)
                    source_labels:
                    - __meta_kubernetes_service_annotation_prometheus_io_scheme
                    target_label: __scheme__
                  - action: replace
                    regex: (.+)
                    source_labels:
                    - __meta_kubernetes_service_annotation_prometheus_io_path
                    target_label: __metrics_path__
                  - action: replace
                    regex: ([^:]+)(?::\d+)?;(\d+)
                    replacement: ${'$'}1:${'$'}2
                    source_labels:
                    - __address__
                    - __meta_kubernetes_service_annotation_prometheus_io_port
                    target_label: __address__
                  - action: labelmap
                    regex: __meta_kubernetes_service_label_(.+)
                  - action: replace
                    source_labels:
                    - __meta_kubernetes_namespace
                    target_label: kubernetes_namespace
                  - action: replace
                    source_labels:
                    - __meta_kubernetes_service_name
                    target_label: kubernetes_name
                  - action: replace
                    source_labels:
                    - __meta_kubernetes_pod_node_name
                    target_label: nodename
                - honor_labels: true
                  job_name: prometheus-pushgateway
                  kubernetes_sd_configs:
                  - role: service
                  relabel_configs:
                  - action: keep
                    regex: pushgateway
                    source_labels:
                    - __meta_kubernetes_service_annotation_prometheus_io_probe
                - job_name: kubernetes-services
                  kubernetes_sd_configs:
                  - role: service
                  metrics_path: /probe
                  params:
                    module:
                    - http_2xx
                  relabel_configs:
                  - action: keep
                    regex: true
                    source_labels:
                    - __meta_kubernetes_service_annotation_prometheus_io_probe
                  - source_labels:
                    - __address__
                    target_label: __param_target
                  - replacement: blackbox
                    target_label: __address__
                  - source_labels:
                    - __param_target
                    target_label: instance
                  - action: labelmap
                    regex: __meta_kubernetes_service_label_(.+)
                  - source_labels:
                    - __meta_kubernetes_namespace
                    target_label: kubernetes_namespace
                  - source_labels:
                    - __meta_kubernetes_service_name
                    target_label: kubernetes_name
                - job_name: kubernetes-pods
                  kubernetes_sd_configs:
                  - role: pod
                  relabel_configs:
                  - action: keep
                    regex: true
                    source_labels:
                    - __meta_kubernetes_pod_annotation_prometheus_io_scrape
                  - action: replace
                    regex: (.+)
                    source_labels:
                    - __meta_kubernetes_pod_annotation_prometheus_io_path
                    target_label: __metrics_path__
                  - action: replace
                    regex: ([^:]+)(?::\d+)?;(\d+)
                    replacement: ${'$'}1:${'$'}2
                    source_labels:
                    - __address__
                    - __meta_kubernetes_pod_annotation_prometheus_io_port
                    target_label: __address__
                  - action: labelmap
                    regex: __meta_kubernetes_pod_label_(.+)
                  - action: replace
                    source_labels:
                    - __meta_kubernetes_namespace
                    target_label: kubernetes_namespace
                  - action: replace
                    source_labels:
                    - __meta_kubernetes_pod_name
                    target_label: kubernetes_pod_name
            pushgateway:
              enabled: false
            server:
              persistentVolume:
                replicaCount: 2
                size: 25Gi
                storageClass: rbd
                        
        """.trimIndent()
    }
}

enum class ElasticRole {
    MASTER,
    CLIENT,
    DATA
}

bundle { ctx ->
    name = "elasticsearch"
    version = "1"

    fun roleSpecificConfiguration(role: ElasticRole): Map<String, Any?> {
        val result = HashMap<String, Any?>()
        when (role) {
            ElasticRole.MASTER -> {

            }

            ElasticRole.CLIENT -> {
                result["persistence"] = mapOf(
                    "enabled" to false
                )
                result["service"] to mapOf(
                    "type" to "NodePort"
                )
            }

            ElasticRole.DATA -> {

            }
        }
        return result
    }

    fun configuration(role: ElasticRole): Map<String, Any?> =
        roleSpecificConfiguration(role) +
                mapOf<String, Any?>(
                    "clusterName" to "elasticsearch",
                    "nodeGroup" to when (role) {
                        ElasticRole.MASTER -> "master"
                        ElasticRole.CLIENT -> "client"
                        ElasticRole.DATA -> "data"
                    },
                    "roles" to mapOf(
                        "master" to (role == ElasticRole.MASTER).toString(),
                        "ingest" to "false",
                        "data" to (role == ElasticRole.DATA).toString()
                    ),
                    "replicas" to when (role) {
                        ElasticRole.MASTER -> {
                            when (ctx.environment) {
                                Environment.DEVELOPMENT -> 3
                                Environment.PRODUCTION -> 3
                                Environment.TEST -> 2
                            }
                        }
                        ElasticRole.CLIENT -> 2
                        ElasticRole.DATA -> when (ctx.environment) {
                            Environment.DEVELOPMENT -> 3
                            Environment.PRODUCTION -> 4
                            Environment.TEST -> 2
                        }
                    },
                    "esJavaOpts" to when (role) {
                        ElasticRole.MASTER -> "-Xmx8g -Xms8g"
                        ElasticRole.CLIENT -> "-Xmx8g -Xms8g"
                        ElasticRole.DATA -> "-Xmx20g -Xms20g"
                    },
                    "resources" to mapOf(
                        "limits" to mapOf(
                            "cpu" to when (role) {
                                ElasticRole.MASTER -> "2000m"
                                ElasticRole.CLIENT -> "2000m"
                                ElasticRole.DATA -> 8
                            },
                            "memory" to when (role) {
                                ElasticRole.MASTER -> "12Gi"
                                ElasticRole.CLIENT -> "12Gi"
                                ElasticRole.DATA -> "30Gi"
                            }
                        ),
                        "requests" to mapOf(
                            "cpu" to when (role) {
                                ElasticRole.MASTER -> "2000m"
                                ElasticRole.CLIENT -> "2000m"
                                ElasticRole.DATA -> 4
                            },
                            "memory" to when (role) {
                                ElasticRole.MASTER -> "12Gi"
                                ElasticRole.CLIENT -> "12Gi"
                                ElasticRole.DATA -> "30Gi"
                            }
                        )
                    ),
                    "masterService" to "elasticsearch-master",
                    "volumeClaimTemplate" to mapOf(
                        "accessModes" to listOf("ReadWriteOnce"),
                        "storageClassName" to "rbd",
                        "resources" to mapOf(
                            "requests" to mapOf(
                                "storage" to when (role) {
                                    ElasticRole.MASTER -> {
                                        "4Gi"
                                    }

                                    ElasticRole.CLIENT -> {
                                        "1Gi"
                                    }

                                    ElasticRole.DATA -> {
                                        if (ctx.environment == Environment.TEST) {
                                            "500Gi"
                                        } else {
                                            "5000Gi"
                                        }
                                    }
                                }
                            )
                        )
                    )
                )

    val security = mapOf(
        "protocol" to "http",
        "esConfig" to mapOf(
            "elasticsearch.yml" to """
               xpack.security.enabled: true
               xpack.security.transport.ssl.enabled: true
               xpack.security.transport.ssl.verification_mode: certificate
               xpack.security.transport.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
               xpack.security.transport.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
               xpack.security.http.ssl.enabled: false
               xpack.security.http.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
               xpack.security.http.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
               http.max_content_length: 500mb
               indices.memory.index_buffer_size: 40%
               ${if (ctx.environment == Environment.TEST) "discovery.zen.minimum_master_nodes: 2" else ""}
 
            """.trimIndent()
        ),
        "extraEnvs" to listOf(
            mapOf(
                "name" to "ELASTIC_PASSWORD",
                "valueFrom" to mapOf(
                    "secretKeyRef" to mapOf(
                        "name" to "elastic-credentials",
                        "key" to "password"
                    )
                )
            ),
            mapOf(
                "name" to "ELASTIC_USERNAME",
                "valueFrom" to mapOf(
                    "secretKeyRef" to mapOf(
                        "name" to "elastic-credentials",
                        "key" to "username"
                    )
                )
            )
        ),
        "secretMounts" to listOf(
            mapOf(
                "name" to "elastic-certificates",
                "secretName" to "elastic-certificates",
                "path" to "/usr/share/elasticsearch/config/certs"
            )
        )
    )

    fun temporaryFile(contents: String): String {
        return Files.createTempFile("file", ".yaml").toFile().also { it.writeText(contents) }.absolutePath
    }

    resources.add(object : KubernetesResource {
        override val phase = DeploymentPhase.DEPLOY
        override fun toString() = "ElasticSearchResource()"
        val cm = ConfigMapResource("elasticsearch-version", "1")

        fun install(role: ElasticRole, withSecurity: Boolean) {
            val args = ArrayList<String>()
            args.addAll(
                listOf(
                    "helm",
                    "--kube-context",
                    ctx.environment.name.toLowerCase(),
                    "upgrade",
                    "--install",
                    "-f",
                    temporaryFile(yamlMapper.writeValueAsString(configuration(role)))
                )
            )

            if (withSecurity) args.addAll(listOf("-f", temporaryFile(yamlMapper.writeValueAsString(security))))

            args.addAll(
                listOf(
                    "--namespace",
                    "elasticsearch",
                    "elasticsearch-${role.name.toLowerCase()}",
                    "elastic/elasticsearch"
                )
            )

            Process.runAndPrint(*args.toTypedArray())
        }

        override fun DeploymentContext.create() {
            Helm.addRepo("elastic", "https://helm.elastic.co")
            Helm.updateRepo()

            println("Generating certificates...")
            Process.runAndPrint(
                "bash",
                File(ctx.repositoryRoot, "infrastructure/elk/elasticsearch/generate_ca.sh").absolutePath
            )

            client.secrets().inNamespace("elasticsearch").createOrReplace(Secret().apply {
                metadata = ObjectMeta()
                metadata.name = "elastic-certificates"
                val caFile = File("./elastic-node.p12")
                data = mapOf(
                    "elastic-node.p12" to String(
                        Base64.getEncoder().encode(
                            caFile.readBytes()
                        )
                    )
                )
                caFile.delete()
                File("elastic-ca.p12").delete()
            })

            client.secrets().inNamespace("elasticsearch").createOrReplace(Secret().apply {
                metadata = ObjectMeta()
                metadata.name = "elastic-credentials"
                stringData = mapOf("username" to "elastic", "password" to "empty")
            })

            install(ElasticRole.MASTER, withSecurity = true)
            install(ElasticRole.CLIENT, withSecurity = true)
            install(ElasticRole.DATA, withSecurity = true)

            println("Configuring passwords (Waiting for cluster ready first)")
            while (true) {
                try {
                    val isReady = client
                        .pods()
                        .inNamespace("elasticsearch")
                        .list()
                        .items
                        .filter { it.metadata.name.contains("elastic") }
                        .all { pod ->
                            pod.status.conditions.find { it.type == "Ready" }?.status == "True"
                        }

                    if (isReady) break
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                }

                Thread.sleep(2000)
            }

            while (true) {
                println("Configuring passwords...")
                val elasticClient = client.pods().inNamespace("elasticsearch").list().items
                    .find { it.metadata.name.contains("client") } ?: continue

                val exec = client.pods()
                    .inNamespace("elasticsearch")
                    .withName(elasticClient.metadata.name)
                    .execWithDefaultListener(
                        listOf(
                            "elasticsearch-setup-passwords",
                            "auto",
                            "-b"
                        ),
                        attachStdout = true,
                        attachStderr = true
                    )

                try {
                    val stdout = exec.stdout!!
                    val stderr = exec.stderr!!

                    val output = stdout.readBytes().toString(Charsets.UTF_8)
                    val errput = stderr.readBytes().toString(Charsets.UTF_8)
                    stdout.close()
                    stderr.close()

                    if (!output.contains("Changed password") && !errput.contains("Changed password")) {
                        println(errput)
                        continue
                    }

                    val passwords = output.lines()
                        .filter { it.startsWith("PASSWORD") }
                        .map { passwordLine ->
                            val username = passwordLine.substringBefore('=').removePrefix("PASSWORD ").trim()
                            val password = passwordLine.substringAfter("= ").trim()

                            Pair(username, password)
                        }
                        .toMap()


                    passwords.forEach { (username, password) ->
                        client.secrets().inNamespace("elasticsearch").createOrReplace(Secret().apply {
                            metadata = ObjectMeta()
                            metadata.name = "${username.replace('_', '-')}-credentials"
                            stringData = mapOf("username" to username, "password" to password)
                        })
                    }

                    client.secrets().inNamespace("elasticsearch").createOrReplace(Secret().apply {
                        metadata = ObjectMeta()
                        metadata.name = "elasticsearch-kibana-credentials"
                        stringData = mapOf("username" to "elastic", "password" to passwords.getValue("elastic"))
                    })

                    client.secrets().inNamespace("default").createOrReplace(Secret().apply {
                        metadata = ObjectMeta()
                        metadata.name = "elasticsearch-credentials"
                        stringData = mapOf("elk.yml" to """
                            elk:
                                elasticsearch:
                                    credentials:
                                        username: elastic
                                        password: ${passwords.getValue("elastic")}
                        """.trimIndent())
                    })

                    println("Passwords configured! Restarting all Elasticsearch pods")

                    // Restart all pods
                    client.pods().inNamespace("elasticsearch").delete()
                    break
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    continue
                }
            }

            // Write that we are done
            with(cm) { create() }
        }

        override fun DeploymentContext.delete() {
            // Not implemented
        }

        override fun DeploymentContext.isUpToDate(): Boolean = with(cm) { isUpToDate() }
    })

    withService {
        service.spec.apply {
            type = "ExternalName"
            externalName = "elasticsearch-client.elasticsearch.svc.cluster.local"
        }
    }
}
