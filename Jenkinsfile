pipeline {
  agent any
  stages {
    stage('Build') {
      parallel {
        stage('Build Notification-service') {
          steps {
            sh '''cd notification-service
./gradlew build -x test'''
          }
        }
        stage('Build App-service') {
          steps {
            sh '''cd app-service
ls
gradlew build -x test
'''
          }
        }
      }
    }
    stage('Test') {
      parallel {
        stage('Test Notification-service') {
          steps {
            sh '''cd notification-service
./gradlew test'''
          }
        }
        stage('Test App-service') {
          steps {
            sh '''cd app-service
gradlew build test'''
          }
        }
      }
    }
  }
}