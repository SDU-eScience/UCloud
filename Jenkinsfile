properties([
    buildDiscarder(logRotator(numToKeepStr: '30')),
])

def label = "worker-${UUID.randomUUID().toString()}"

podTemplate(label: label, containers: [
containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
containerTemplate(name: 'node', image: 'node:11-alpine', command: 'cat', ttyEnabled: true),
containerTemplate(name: 'centos', image: 'ubuntu', command: 'cat', ttyEnabled: true)
],
volumes: [
  hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
]) {
    node (label) {
        if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'JenkinsSetup') {
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
    withCredentials(
        [usernamePassword(
            credentialsId: "archiva",
            usernameVariable: "ESCIENCE_MVN_USER",
            passwordVariable: "ESCIENCE_MVN_PASSWORD"
        )]
    ) {
        return loaded.initialize()
    }
}

def sendAlert(String alertMessage) {
    withCredentials(
        [string(credentialsId: "slackToken", variable: "slackToken")]
    ) {
        slackSend(channel: "devalerts", message: alertMessage, tokenCredentialId: 'slackToken')
    }
}
