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
./gradlew build -x test
'''
          }
        }
        stage('Build abc2-sync') {
          steps {
            sh '''cd abc2-sync
./gradlew build -x test
'''
          }
        }
        stage('Build Auth-service') {
          steps {
            sh '''cd auth-service
./gradlew build -x test
'''
          }
        }
        stage('Build Client-Core') {
          steps {
            sh '''cd client-core
./gradlew build -x test
'''
          }
        }
        stage('Build Indexing-service') {
          steps {
            sh '''cd indexing-service
./gradlew build -x test
'''
          }
        }
        stage('Build Metadata-service') {
          steps {
            sh '''cd metadata-service
./gradlew build -x test
'''
          }
        }
        stage('Build Service-common') {
          steps {
            sh '''cd service-common
./gradlew build -x test
'''
          }
        }
        stage('Build Zenodo-service') {
          steps {
            sh '''cd  zenodo-service
./gradlew build -x test
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