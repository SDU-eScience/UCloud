def label = "worker-${UUID.randomUUID().toString()}"

podTemplate(label: label, containers: [
containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
containerTemplate(name: 'node', image: 'node:11-alpine', command: 'cat', ttyEnabled: true)
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
            String currentResult5
            Boolean allSucceed = true
            int size = needToBuild.size()
            int jumpsize = 5
            int i = 0

            def resultList = [""] * size

            while (true) {
                stage("building and testing ${serviceList[i]}, ${serviceList[i+1]}, ${serviceList[i+2]}, ${serviceList[i+3]}, ${serviceList[i+4]}") {
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
                        },
                        (serviceList[i+4]): {
                            currentResult5 = runBuild(needToBuild[i+4])
                        }
                    )
                }
                resultList[i] = currentResult1
                resultList[i+1] = currentResult2
                resultList[i+2] = currentResult3
                resultList[i+3] = currentResult4
                resultList[i+4] = currentResult5
                println("STATUS OF RUNS: ${currentResult1}, ${currentResult2}, ${currentResult3}, ${currentResult4}, ${currentResult5}")
                println(resultList)
                i = i+jumpsize
                if (i >= size-jumpsize) {
                    break
                }
            }

            for (i; i < needToBuild.size(); i++) {
                stage("building and testing ${serviceList[i]}"){
                    String currentResult = runBuild(needToBuild[i])
                    resultList[i] = currentResult
                    println ("THIS IS A RESULT: ${currentResult}")
                    println(resultList)
                }
            }
        /*
            String currentResult
            Boolean allSucceed = true
            for (String item : needToBuild) {
                def loaded = load(item)
                withCredentials([usernamePassword(
                    credentialsId: "archiva",
                    usernameVariable: "ESCIENCE_MVN_USER",
                    passwordVariable: "ESCIENCE_MVN_PASSWORD"
                )]) {
                    currentResult = loaded.initialize()

                    println("current result = " + currentResult)
                    currentResult = currentResult ?: 'SUCCESS'
                    println("current result after ?: = " + currentResult)

                    if (currentResult == 'UNSTABLE') {
                        echo "Build is unstable"
                        allSucceed = false
                        sendAlert("Build Unstable")
                        error('Aborting due to caught error - marked as unstable')

                    }

                    if (currentResult == 'FAILURE') {
                        println("FAIL")
                        allSucceed = false
                        sendAlert("Build FAILED")
                        error('Aborting due to caught error - marked as failure')
                    }
                }
            }
    */
    //        if (allSucceed) {
    //            junit '**/build/test-results/**/*.xml'      
    //            jacoco(
    //                execPattern: '**/**.exec',
    //                exclusionPattern: '**/src/test/**/*.class,**/AuthMockingKt.class,**/DatabaseSetupKt.class',
    //                sourcePattern: '**/src/main/kotlin/**'
    //            )
    //        }
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
