pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        echo 'Moving to Notifications-service'
        sh 'cd notification-service'
        echo 'Building Gradle'
        sh './gradlew build'
      }
    }
  }
}