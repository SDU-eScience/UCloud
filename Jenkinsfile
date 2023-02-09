properties([
    buildDiscarder(logRotator(numToKeepStr: '30')),
])


// NOTE(Dan): We should definitely not attempt to run these builds on untrusted builds. The Kubernetes cluster we start
// is capable of doing quite a lot of damage. We should at the very least not run Kubernetes based tests on untrusted
// builds.


node {
    sh label: '', script: 'java -version'
    def jobName = "t"+currentBuild.startTimeInMillis

    def compileFail = false
    def testFail = false
    def branchName = ""
    if (env.BRANCH_NAME.startsWith("PR-")) {
        branchName = env.CHANGE_BRANCH
    } else {
        env.BRANCH_NAME
    }
    echo branchName
    echo (jobName)
    //Make check on PR creator and specific branches. master, staging, PRs
    stage('Checkout') {
        checkout(
            [
                $class                           : 'GitSCM',
                branches                         : [
                    [name: branchName]
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

    cleanDocker()

    //Create new environment with providers installed

    try {
        sh script: 'DEBUG_COMMANDS=true ; ./launcher init --all-providers'
        def logArray = currentBuild.rawBuild.getLog(50)
        def log = ""
        for (String s : logArray)
        {
            log += s + " ";
        }
        if (log.contains("BUILD FAILED")) {
            sendAlert("""\
                    :warning: Launcher init on ${env.BRANCH_NAME} failed :warning:
                """.stripIndent()
            )
            currentBuild.result = "FAILURE"
            cleanDocker()
            return
        } 
    }
    catch(Exception e) {
        sendAlert("""\
                :warning: Unknown exception in launcher init :warning:
            """.stripIndent()
        )
        currentBuild.result = "FAILURE"
        cleanDocker()
        throw e
    }

    //Create Snapshot of DB to test purpose. Use "t"+timestamp for UNIQUE ID

    sh script: """
        ./launcher snapshot ${jobName}
    """

    //run test

    try {
        sh script: """
            export UCLOUD_LAUNCHER=\$PWD/launcher
            export UCLOUD_TEST_SNAPSHOT=${jobName} 
            cd integration-test 
            ./gradlew integrationtest
        """
    }
    catch(Exception e) {
        echo 'EX'

        def logArray = currentBuild.rawBuild.getLog(50)
        def log = ""
        for (String s : logArray)
        {
            log += s + " ";
        }
        def startIndex = log.indexOf("FAILURE: Build failed with an exception")
        def endIndex = log.indexOf("* Try:")
        if (startIndex == -1) {
            startIndex = 0
        }
        if (endIndex == -1) {
            endIndex = log.length()-1
        }


        sendAlert("""\
                :warning: Integration Test on ${env.BRANCH_NAME} failed :warning:

                ${log.substring(startIndex, endIndex)}
            """.stripIndent()
        )

        if(log.substring(startIndex, endIndex).contains("Compilation error")) {
            compileFail = true
        } else {
            testFail = true
        }
    }
    finally {
        junit '**/build/test-results/**/*.xml'

        env.WORKSPACE = pwd()
        def workspace = readFile "${env.WORKSPACE}/.compose/current.txt"

        sh script: """
            mkdir -p ./tmp
            docker cp ${workspace}-backend-1:/tmp/service.log ./tmp/service.log
            docker cp ${workspace}-backend-1:/var/log ./tmp/
        """

        archiveArtifacts artifacts: 'tmp/service.log', allowEmptyArchive: true
        archiveArtifacts artifacts: 'tmp/log/ucloud/*.log', allowEmptyArchive: true


        cleanDocker()

        if(compileFail) {
            currentBuild.result = "FAILURE"
        } 
        if(testFail) {
            currentBuild.result = "UNSTABLE"
        }
    }
}

def cleanDocker() {
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

        rm -rf ./tmp
    """
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
