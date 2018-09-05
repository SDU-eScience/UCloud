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

//Finds the current branch name
def local() {
  String shell = sh returnStdout: true, script: "git branch | rev | cut -d ')' -f1 | rev | xargs"
  return shell
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
  branch = local()
  println(branch.length())
  if(branch.equals("")){
    println("DEBUG: hit equals")
    branch = "master"
  }
  println("This is the current branch: " + branch)

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

  stage('Check') {
    for ( String item : serviceList) {
      if (checkFolderForDiffs(item, numberOfCommits)) {
        needToBuild.add(item+"/Jenkinsfile")
      }
      else {
        echo 'No Changes ' + item +' - Already build'
      }
    }
  }
  for ( String item : needToBuild) {
    def loaded = load(item)
    loaded.initialize()
  }
  
}




