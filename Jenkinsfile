properties([
    buildDiscarder(logRotator(numToKeepStr: '30')),
])


// NOTE(Dan): We should definitely not attempt to run these builds on untrusted builds. The Kubernetes cluster we start
// is capable of doing quite a lot of damage. We should at the very least not run Kubernetes based tests on untrusted
// builds.


node {
    sh label: '', script: 'java -version'
    if (env.BRANCH_NAME == 'devel-test') {
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
                            url          : 'https://github.com/SDU-eScience/UCloud.git'
                        ]
                    ]
                ]
            )
        }

        sendAlert("Hello from Jenkins")

        /*String frontendResult = runBuild("frontend-web/Jenkinsfile")
        String backendResult = runBuild("backend/Jenkinsfile")
        boolean hasError = false

        if (frontendResult.startsWith("FAILURE")) {
            sendAlert(frontendResult)
            hasError = true
        }

        if (backendResult.startsWith("FAILURE")) {
            sendAlert(backendResult)
            hasError = true
        }*/

        //junit '**/build/test-results/**/*.xml'
        //jacoco(
        //    execPattern: '**/**.exec',
        //    exclusionPattern: '**/src/test/**/*.class,**/AuthMockingKt.class,**/DatabaseSetupKt.class',
        //    sourcePattern: '**/src/main/kotlin/**'
        //)

        //if (hasError) {
        //    error('Job failed - message have been sent.')
        //}
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
