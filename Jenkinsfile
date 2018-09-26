def checkFolderForDiffs(path, numberOfCommits) {
    try {
        // git diff will return 1 for changes (failure) which is caught in catch, or
        // 0 meaning no changes 
        sh "git diff --quiet --exit-code HEAD~${numberOfCommits}..HEAD ${path}"
        return false
    } catch (err) {
        return true
    }
}

def getLastSuccessfulCommit() {
  def lastSuccessfulHash = null
    def lastSuccessfulBuild = currentBuild.rawBuild.getPreviousSuccessfulBuild()
    if ( lastSuccessfulBuild ) {
      lastSuccessfulHash = commitHashForBuild( lastSuccessfulBuild )
    }
  return lastSuccessfulHash
}

@NonCPS
def commitHashForBuild( build ) {
  def scmAction = build?.actions.find { action -> action instanceof jenkins.scm.api.SCMRevisionAction }
  return scmAction?.revision?.hash
}

node{
  if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'JenkinsSetup') {
    String branch = ''
    if (env.BRANCH_NAME == 'master') {
      branch = 'master'
    }
    if (env.BRANCH_NAME == 'JenkinsSetup') {
      branch = 'JenkinsSetup'
    }
    stage('Checkout'){
      checkout(
        [$class: 'GitSCM', 
        branches: [
          [name: branch]
        ], 
        doGenerateSubmoduleConfigurations: false, 
        extensions: [], 
        submoduleCfg: [], 
        userRemoteConfigs: [
          [credentialsId: 'github', 
          url: 'https://github.com/SDU-eScience/SDUCloud.git']
          ]
        ]
      )
    }
    def lastSuccessfulCommit = getLastSuccessfulCommit()
    def currentCommit = commitHashForBuild( currentBuild.rawBuild )
    def numberOfCommits = 0
    if (lastSuccessfulCommit) {
      commits = sh(
        script: "git rev-list $currentCommit \"^$lastSuccessfulCommit\"",
        returnStdout: true
      ).split('\n')
      numberOfCommits = commits.length
    }

    def needToBuild = []
    
    def serviceList = [
      "abc2-sync",
      "client-core",
      "frontend-web",
      "service-common"
    ]

    String ls = sh (script: 'ls', returnStdout: true)
    def list = ls.split("\n")
    for (String item : list){
      if (item.endsWith("-service")) {
          serviceList.add(item)
      }
    }

    stage('Check for') {
      for ( String item : serviceList) {
        if (checkFolderForDiffs(item, numberOfCommits)) {
          needToBuild.add(item+"/Jenkinsfile")
          println(item + " is added to build queue")
        }
        else {
          println('No Changes ' + item +' - Already build')
        }
      }
    }
    String currentResult
    for ( String item : needToBuild) {
      def loaded = load(item)
      if (item != 'frontend-web/Jenkinsfile') {
        currentResult = loaded.initialize()
      }
    }

    println("current result = " + currentResult)
    if (currentResult == 'UNSTABLE') {
      echo "Build is unstable"
      slackSend baseUrl: 'https://sdu-escience.slack.com/services/hooks/jenkins-ci/', message: 'Build Unstable', token: '1cTFN3I0k1rUZ5ByE0Tf15c9'
    }

    if (currentResult == 'SUCCESS') {
      jacoco  execPattern: '**/**.exec',
              exclusionPattern: '**/src/test/**/*.class,**/AuthMockingKt.class,**/DatabaseSetupKt.class', 
              sourcePattern: '**/src/main/kotlin/**'   

    }

    if (currentResult == 'FAILURE') {
      println("FAIL")
      slackSend baseUrl: 'https://sdu-escience.slack.com/services/hooks/jenkins-ci/', message: 'Build FAILED', token: '1cTFN3I0k1rUZ5ByE0Tf15c9'
    }

    if (currentResult == null) {
      currentResult = currentBuild.result ?: 'SUCCESS'
    }

  }
  else {
    println("not master - wont run")
  }
}




