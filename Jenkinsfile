pipeline {
  agent any
  stages {
    stage('test') {
      steps {
        echo 'Moving to Notifications-service'
        sh 'cd notifications-service'
        echo 'Building Gradle'
        sh './gradlew build'
      }
    }
  }
}