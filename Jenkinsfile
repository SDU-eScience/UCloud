pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        sh '''cd notification-service
./gradlew build -x test'''
      }
    }
  }
}