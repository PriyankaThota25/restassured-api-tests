pipeline {
    agent any

    tools {
        maven 'Maven-3'   // name configured in Manage Jenkins > Tools
        jdk 'JDK-21'
    }

    parameters {
        string(name: 'BASE_URI', defaultValue: 'https://jsonplaceholder.typicode.com', description: 'API under test')
        string(name: 'AUTH_TOKEN_URL', defaultValue: 'https://auth.example.com/oauth/token', description: 'OAuth token endpoint')
    }

    stages {
        stage('Test') {
            steps {
                // Credential of kind "Username with password": id = client id, password = client secret.
                // Jenkins masks the secret in the console log.
                withCredentials([usernamePassword(credentialsId: 'api-client-credentials',
                                                  usernameVariable: 'AUTH_CLIENT_ID',
                                                  passwordVariable: 'AUTH_CLIENT_SECRET')]) {
                    sh 'mvn -B clean test -Dbase.uri=$BASE_URI -Dauth.token.url=$AUTH_TOKEN_URL'
                }
            }
        }
    }

    post {
        always {
            junit 'target/surefire-reports/*.xml'
            // Needs the "HTML Publisher" plugin. Jenkins' default Content-Security-Policy blocks the
            // report's inline CSS, so it shows unstyled until you relax it (Manage Jenkins > Script
            // Console): System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "default-src 'self'; style-src 'self' 'unsafe-inline';")
            publishHTML(target: [reportDir: 'target/custom-report', reportFiles: 'index.html',
                                 reportName: 'API Test Report', keepAll: true,
                                 alwaysLinkToLastBuild: true, allowMissing: false])
        }
    }
}
