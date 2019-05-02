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

            String currentResult
            Boolean allSucceed = true
            int size = needToBuild.size()
            int jumpsize = 4
            int i = 0

            while (true) {
                stage("building and testing ${serviceList[i]}, ${serviceList[i+1]}, ${serviceList[i+2]}, ${serviceList[i+3]}") {
                    parallel (
                        (serviceList[i]): {
                            println("running " + i)
                        },
                        (serviceList[i+1]): {
                            println("running " + i+1)
                        },
                        (serviceList[i+2]): {
                            println("running " + i+2)
                        },
                        (serviceList[i+3]): {
                            println("running " + i+3)
                        }
                    )
                }
                i = i+jumpsize
                if (i >= size-jumpsize) {
                    println("BREAKS")
                    break
                }
            }
            println("OUT of while")
            i = i-4
            for (i; i < needToBuild.size(); i++) {
                stage("building and testing ${serviceList[i]}"){
                    println("running last")
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
