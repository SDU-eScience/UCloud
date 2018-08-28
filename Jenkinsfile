pipeline {
  agent any
  stages {
    stage('test') {
      steps {
        echo 'Hello World'
        sh '''echo "Moving to notification service"
cd notifications
ls'''
      }
    }
  }
}