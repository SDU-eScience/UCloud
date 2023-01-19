properties([
    buildDiscarder(logRotator(numToKeepStr: '30')),
])


// NOTE(Dan): We should definitely not attempt to run these builds on untrusted builds. The Kubernetes cluster we start
// is capable of doing quite a lot of damage. We should at the very least not run Kubernetes based tests on untrusted
// builds.


node {
    sh label: '', script: 'java -version'
    def jobName = "t"+currentBuild.startTimeInMillis
    echo (jobName)
    //Make check on PR creator and specific branches. master, staging, PRs
    if (env.BRANCH_NAME == 'jenkinsSetup') {
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

        //Delete current environment if any

        sh script: """
            docker rm -f \$(docker ps -q) || true
            docker volume rm -f \$(docker volume ls -q) || true
            docker network rm  \$(docker network ls -q) || true
            
            docker rm -f \$(docker ps -q) || true
            docker volume rm -f \$(docker volume ls -q) || true
            docker network rm  \$(docker network ls -q) || true
            
            docker volume prune || true
            docker network prune || true
            docker run --rm -v \$PWD:/mnt/folder ubuntu:22.04 bash -c 'rm -rf /mnt/folder/.compose/*'
        """

        //Create new environment with providers installed

        sh script: 'DEBUG_COMMANDS=true ; ./launcher init --all-providers'

        //Create Snapshot of DB to test purpose. Use "t"+timestamp for UNIQUE ID

        sh script: """
            ./launcher snapshot ${jobName}
        """

        //run test
        sh script: """
            cd integration-test 
            export UCLOUD_LAUNCHER=\$PWD/launcher 
            export UCLOUD_TEST_SNAPSHOT=${jobName} 
            ./gradlew test GiftTest
        """

        //Save log files from UCLoud and gradle build report

        junit '**/build/test-results/**/*.xml'

        //Delete current environment

        sh script: """
            docker rm -f \$(docker ps -q) || true
            docker volume rm -f \$(docker volume ls -q) || true
            docker network rm  \$(docker network ls -q) || true
            
            docker rm -f \$(docker ps -q) || true
            docker volume rm -f \$(docker volume ls -q) || true
            docker network rm  \$(docker network ls -q) || true
            
            docker volume prune || true
            docker network prune || true
            docker run --rm -v \$PWD:/mnt/folder ubuntu:22.04 bash -c 'rm -rf /mnt/folder/.compose/*'
        """

        //sendAlert("Hello from Jenkins")

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
