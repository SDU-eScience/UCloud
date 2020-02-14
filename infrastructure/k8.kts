//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

import java.io.File
import java.util.*

bundle {
    name = "stolon"
    version = "1"

    withConfigMap("pgaudit") { ctx ->
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

    withHelmChart("stable/stolon-1.5.0") { ctx ->
        val size = when (ctx.environment) {
            Environment.TEST, Environment.DEVELOPMENT -> "250Gi"
            Environment.PRODUCTION -> "250Gi" // For some reason this is also the production value
        }

        //language=yml
        valuesAsString = """
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

    withHelmChart("stable/ambassador-5.1.0") {
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

    withHelmChart("stable/grafana-3.8.3") {
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

bundle {
    name = "kibana"
    version = "1"

    withHelmChart("stable/kibana-7.5.0") {
        //language=yml
        valuesAsString = """
            elasticsearchHosts: http://elasticsearch-newmaster:9200
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

bundle {
    name = "redis"
    version = "1"

    withHelmChart("stable/redis-10.2.1") { ctx ->
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
}

bundle {
    name = "prometheus"
    version = "1"

    withHelmChart("stable/prometheus-9.1.0") {
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

bundle {
    name = "elasticsearch"
    version = "1"

    val chart = "stable/elasticsearch-7.5.0"
    val namespace = "elasticsearch"

    withHelmChart(chart, namespace = namespace) { ctx ->
        name = when (ctx.environment) {
            Environment.PRODUCTION -> "helm-es-migration-client"
            Environment.DEVELOPMENT, Environment.TEST -> "elasticsearch-client"
        }

        //language=yml
        valuesAsString = """
            clusterName: elasticsearch
            esConfig:
              elasticsearch.yml: |
                xpack.security.enabled: true
                xpack.security.transport.ssl.enabled: true
                xpack.security.transport.ssl.verification_mode: certificate
                xpack.security.transport.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.transport.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.http.ssl.enabled: false
                xpack.security.http.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.http.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12

                http.max_content_length: 500mb
                indices.memory.index_buffer_size: 30%
            esJavaOpts: -Xmx8g -Xms8g
            extraEnvs:
            - name: ELASTIC_PASSWORD
              valueFrom:
                secretKeyRef:
                  key: password
                  name: elastic-credentials
            - name: ELASTIC_USERNAME
              valueFrom:
                secretKeyRef:
                  key: username
                  name: elastic-credentials
            masterService: elasticsearch-newmaster
            nodeGroup: newclient
            persistence:
              enabled: false
            protocol: http
            replicas: 2
            resources:
              limits:
                cpu: 2000m
                memory: 14Gi
              requests:
                cpu: 2000m
                memory: 14Gi
            roles:
              data: "false"
              ingest: "false"
              master: "false"
            secretMounts:
            - name: elastic-certificates
              path: /usr/share/elasticsearch/config/certs
              secretName: elastic-certificates
            service:
              type: NodePort
            volumeClaimTemplate:
              accessModes:
              - ReadWriteOnce
              resources:
                requests:
                  storage: 1Gi
              storageClassName: rbc # Note: This is wrong (TODO What will happen if we fix it)
                        
        """.trimIndent()
    }

    withHelmChart(chart, namespace = namespace) { ctx ->
        name = when (ctx.environment) {
            Environment.PRODUCTION -> "helm-es-migration-client"
            Environment.DEVELOPMENT, Environment.TEST -> "elasticsearch-client"
        }

        //language=yml
        valuesAsString = """
            clusterName: elasticsearch
            esConfig:
              elasticsearch.yml: |
                xpack.security.enabled: true
                xpack.security.transport.ssl.enabled: true
                xpack.security.transport.ssl.verification_mode: certificate
                xpack.security.transport.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.transport.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.http.ssl.enabled: false
                xpack.security.http.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.http.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12

                http.max_content_length: 500mb
                indices.memory.index_buffer_size: 30%
            esJavaOpts: -Xmx20g -Xms20g
            extraEnvs:
            - name: ELASTIC_PASSWORD
              valueFrom:
                secretKeyRef:
                  key: password
                  name: elastic-credentials
            - name: ELASTIC_USERNAME
              valueFrom:
                secretKeyRef:
                  key: username
                  name: elastic-credentials
            masterService: elasticsearch-newmaster
            nodeGroup: newdata
            protocol: http
            replicas: 3
            resources:
              limits:
                cpu: 8
                memory: 30Gi
              requests:
                cpu: 4
                memory: 30Gi
            roles:
              data: "true"
              ingest: "false"
              master: "false"
            secretMounts:
            - name: elastic-certificates
              path: /usr/share/elasticsearch/config/certs
              secretName: elastic-certificates
            volumeClaimTemplate:
              accessModes:
              - ReadWriteOnce
              resources:
                requests:
                  storage: 5000Gi
              storageClassName: rbd
            
        """.trimIndent()
    }

    withHelmChart(chart, namespace = namespace) { ctx ->
        name = when (ctx.environment) {
            Environment.PRODUCTION -> "helm-es-migration-master"
            Environment.DEVELOPMENT, Environment.TEST -> "elasticsearch-master"
        }

        //language=yml
        valuesAsString = """
            clusterName: elasticsearch
            esConfig:
              elasticsearch.yml: |
                xpack.security.enabled: true
                xpack.security.transport.ssl.enabled: true
                xpack.security.transport.ssl.verification_mode: certificate
                xpack.security.transport.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.transport.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.http.ssl.enabled: false
                xpack.security.http.ssl.truststore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12
                xpack.security.http.ssl.keystore.path: /usr/share/elasticsearch/config/certs/elastic-node.p12

                http.max_content_length: 500mb
                indices.memory.index_buffer_size: 30%
            esJavaOpts: -Xmx8g -Xms8g
            extraEnvs:
            - name: ELASTIC_PASSWORD
              valueFrom:
                secretKeyRef:
                  key: password
                  name: elastic-credentials
            - name: ELASTIC_USERNAME
              valueFrom:
                secretKeyRef:
                  key: username
                  name: elastic-credentials
            imageTag: 7.5.0 # TODO This is only present in the master's copy
            masterService: elasticsearch-newmaster
            nodeGroup: newmaster
            protocol: http
            replicas: 3
            resources:
              limits:
                cpu: 2000m
                memory: 14Gi
              requests:
                cpu: 2000m
                memory: 14Gi
            roles:
              data: "false"
              ingest: "false"
              master: "true"
            secretMounts:
            - name: elastic-certificates
              path: /usr/share/elasticsearch/config/certs
              secretName: elastic-certificates
            volumeClaimTemplate:
              accessModes:
              - ReadWriteOnce
              resources:
                requests:
                  storage: 4Gi
              storageClassName: rbd            
        """.trimIndent()
    }
}
