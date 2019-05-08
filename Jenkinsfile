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

            def needToBuild = []

            def serviceList = [
                "frontend-web",
                "service-common"
            ]

            def list = sh(script: 'ls', returnStdout: true).split("\n")
            for (String item : list) {
                if (item.endsWith("-service")) {
                    serviceList.add(item)
                }
            }
            for (String item : serviceList) {
                needToBuild.add(item + "/Jenkinsfile")
            } 

            String currentResult1
            String currentResult2
            String currentResult3
            String currentResult4
            int size = needToBuild.size()
            int jumpsize = 4
            int i = 0

            def resultList = [""] * size
            
            while (true) {
                stage("building and testing ${serviceList[i]}, ${serviceList[i+1]}, ${serviceList[i+2]}, ${serviceList[i+3]}") {
                    parallel (
                        (serviceList[i]): {
                            currentResult1 = runBuild(needToBuild[i])
                        },
                        (serviceList[i+1]): {
                            currentResult2 = runBuild(needToBuild[i+1])
                        },
                        (serviceList[i+2]): {
                            currentResult3 = runBuild(needToBuild[i+2])
                        },
                        (serviceList[i+3]): {
                            currentResult4 = runBuild(needToBuild[i+3])
                        }
                    )
                }
                resultList[i] = currentResult1
                resultList[i+1] = currentResult2
                resultList[i+2] = currentResult3
                resultList[i+3] = currentResult4
                i = i+jumpsize
                if (i >= size-jumpsize) {
                    break
                }
            }

            for (i; i < needToBuild.size(); i++) {
                stage("building and testing ${serviceList[i]}"){
                    String currentResult = runBuild(needToBuild[i])
                    resultList[i] = currentResult
                }
            }
            println(resultList)
            if (resultList.contains("FAILURE") || resultList.contains("UNSTABLE")) {
                String message = "Following services are marked UNSTABLE due to failing tests:\n"
                for (int k = 0; k < resultList.size(); k++) {
                    if (resultList[k] == "UNSTABLE") {
                        message = message + "${serviceList[k]}\n"
                    }
                }
                message = message + "\nFollowing services have FAILED during builds:\n"
                for (int k = 0; k < resultList.size(); k++) {
                    if (resultList[k] == "FAILED") {
                        message = message + "${serviceList[k]}\n"
                    }
                }
                sendAlert(message)
                error('Job failed - message have been sent. JobInfo: $resultList \n Message: $message')
            }

            currentBuild.rawBuild.@result = hudson.model.Result.SUCCESS            

            junit '**/build/test-results/**/*.xml'      
            jacoco(
                execPattern: '**/**.exec',
                exclusionPattern: '**/src/test/**/*.class,**/AuthMockingKt.class,**/DatabaseSetupKt.class',
                sourcePattern: '**/src/main/kotlin/**'
            )
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
        slackSend(
            baseUrl: 'https://sdu-escience.slack.com/services/hooks/jenkins-ci/',
            message: alertMessage,
            token: "$slackToken"
        )
    }
}
