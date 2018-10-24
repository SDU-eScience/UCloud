node {
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
            "abc2-sync",
            "client-core",
//            "frontend-web",
            "service-common"
        ]

        String ls = sh(script: 'ls', returnStdout: true)
        def list = ls.split("\n")
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
        for (String item : needToBuild) {
            def loaded = load(item)
            if (item == 'frontend-web/Jenkinsfile') continue

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
                    withCredentials([string(credentialsId: "slackToken", variable: "slackToken")]) {
                        slackSend(
                            baseUrl: 'https://sdu-escience.slack.com/services/hooks/jenkins-ci/',
                            message: 'Build Unstable',
                            token: "$slackToken"
                        )
                    }
                    error('Aborting due to caught error - marked as unstable')

                }

                if (currentResult == 'FAILURE') {
                    println("FAIL")
                    allSucceed = false
                    withCredentials([string(credentialsId: "slackToken", variable: "slackToken")]) {
                        withCredentials([string(credentialsId: "slackToken", variable: "slackToken")]) {
                            slackSend(
                                baseUrl: 'https://sdu-escience.slack.com/services/hooks/jenkins-ci/',
                                message: 'Build FAILED',
                                token: "$slackToken"
                            )
                        }
                    }
                    error('Aborting due to caught error - marked as failure')
                }
            }
        }

        if (allSucceed) {
            junit '**/build/test-results/**/*.xml'      
            jacoco(
                execPattern: '**/**.exec',
                exclusionPattern: '**/src/test/**/*.class,**/AuthMockingKt.class,**/DatabaseSetupKt.class',
                sourcePattern: '**/src/main/kotlin/**'
            )
        }
    }
}