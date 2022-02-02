properties([
    buildDiscarder(logRotator(numToKeepStr: '30')),
])

def label = "worker-${UUID.randomUUID().toString()}"
def postgresPassword = UUID.randomUUID().toString()

// NOTE(Dan): We should definitely not attempt to run these builds on untrusted builds. The Kubernetes cluster we start
// is capable of doing quite a lot of damage. We should at the very least not run Kubernetes based tests on untrusted
// builds.

podTemplate(
    label: label, 
    containers: [
        containerTemplate(
            name: 'jnlp', 
            image: 'jenkins/jnlp-slave:latest-jdk11', 
            args: '${computer.jnlpmac} ${computer.name}'
        ),
        
        containerTemplate(
            name: 'test', 
            image: 'dreg.cloud.sdu.dk/ucloud/test-runner:2022.1.0', 
            command: 'cat', 
            ttyEnabled: true,
            envVars: [
                containerEnvVar(key: 'POSTGRES_PASSWORD', value: postgresPassword)
            ]
        ),

        containerTemplate(
            name: 'k3s',
            image: 'rancher/k3s:v1.21.6-rc2-k3s1',
            args: 'server --cluster-cidr 10.44.0.0/16 --service-cidr 10.45.0.0/16 --cluster-dns 10.45.0.10 --cluster-domain cluster2.local',
            privileged: true,
            envVars: [
                containerEnvVar(key: 'K3S_KUBECONFIG_OUTPUT', value: '/output/kubeconfig.yaml'),
                containerEnvVar(key: 'K3s_KUBECONFIG_MODE', value: '666'),
            ]
        ),

        containerTemplate(
            name: 'redis',
            image: 'redis:5.0.9'
        ),

        containerTemplate(
            name: 'elastic',
            image: 'docker.elastic.co/elasticsearch/elasticsearch:7.10.2',
            envVars: [
                containerEnvVar(key: 'discovery.type', value: 'single-node')
            ]
        ),

        containerTemplate(
            name: 'postgres',
            image: 'postgres:13.3',
            envVars: [
                containerEnvVar(key: 'POSTGRES_PASSWORD', value: postgresPassword)
            ]
        )
    ],
    volumes: [
      emptyDirVolume(mountPath: '/tmp', memory: false),
      emptyDirVolume(mountPath: '/output', memory: false)
    ]
) {
    node (label) {
        sh label: '', script: 'java -version'
        if (env.BRANCH_NAME == 'master') {
            stage('Checkout') {
                checkout(
                    [
                        $class                           : 'GitSCM',
                        branches                         : [
                            [name: env.BRANCH_NAME]
                        ],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                            [
                                credentialsId: 'github',
                                url          : 'https://github.com/SDU-eScience/SDUCloud.git'
                            ]
                        ]
                    ]
                )
            }

            String frontendResult = runBuild("frontend-web/Jenkinsfile")
            String backendResult = runBuild("backend/Jenkinsfile")
            boolean hasError = false

            if (frontendResult.startsWith("FAILURE")) {
                sendAlert(frontendResult)
                hasError = true
            }

            if (backendResult.startsWith("FAILURE")) {
                sendAlert(backendResult)
                hasError = true
            }

            junit '**/build/test-results/**/*.xml'
            jacoco(
                execPattern: '**/**.exec',
                exclusionPattern: '**/src/test/**/*.class,**/AuthMockingKt.class,**/DatabaseSetupKt.class',
                sourcePattern: '**/src/main/kotlin/**'
            )

            if (hasError) {
                error('Job failed - message have been sent.')
            }
        }
    }
}

String runBuild(String item) {
    def loaded = load(item)
    return loaded.initialize()
}

def sendAlert(String alertMessage) {
    withCredentials(
        [string(credentialsId: "slackToken", variable: "slackToken")]
    ) {
        slackSend(channel: "devalerts", message: alertMessage, tokenCredentialId: 'slackToken')
    }
}
