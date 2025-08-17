pipeline {
    // 1. Agent Setup:
    // This pipeline can run on any agent that has Docker, Git, and Python 3 + pip installed.
    // The user running the Jenkins agent process needs to be part of the 'docker' group.
    agent any

    // 2. Environment Variables:
    // Define environment variables for the pipeline.
    environment {
        // The ID of your "Username with password" credential in Jenkins for Docker Hub.
        DOCKER_CREDENTIALS_ID = 'dockerhub-credentials'
        // Your public Docker Hub username/organization name.
        DOCKER_USER           = 'alexjelani13' // <-- IMPORTANT: Change this value
        IMAGE_TAG             = 'latest'
    }

    stages {
        // 3. Parallel Stages for Testing and Scanning:
        // These stages run in parallel, just like the 'test' and 'gitleaks' jobs in GitHub Actions.
        stage('Quality Checks') {
            parallel {
                stage('Run Tests') {
                    steps {
                        // Checkout the source code
                        checkout scm

                        // Install required system packages and create virtual environment
                        sh '''
                            apt-get update && apt-get install -y python3-venv libpq-dev python3-dev gcc || true
                            python3 -m venv venv
                            . venv/bin/activate
                            pip install -r requirements.txt
                            python manage.py test
                        '''
                    }
                }
                stage('Gitleaks Scan') {
                    steps {
                        checkout scm
                        script {
                            sh 'git fetch --unshallow || echo "Already a full clone"'
                            sh 'docker pull ghcr.io/gitleaks/gitleaks:latest'
                            
                            try {
                                // Fixed command with proper Git repo mounting
                                sh """
                                docker run --rm --user "\$(id -u):\$(id -g)" \
                                -v "${pwd()}:/repo" \
                                -v "${pwd()}/.git:/repo/.git" \
                                -e GIT_DISCOVERY_ACROSS_FILESYSTEM=true \
                                ghcr.io/gitleaks/gitleaks:latest \
                                detect --source /repo --verbose --report-format json --report-path /repo/gitleaks-report.json
                                """
                                echo '✅ No secrets detected by Gitleaks.'
                            } catch (any) {
                                currentBuild.result = 'UNSTABLE'
                                echo "🛑 Secrets detected! Review the Gitleaks report artifact. 🛑"
                                archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
                                sh 'cat gitleaks-report.json | python3 -m json.tool'
                                publishHTML([
                                    allowMissing: false,
                                    alwaysLinkToLastBuild: true,
                                    keepAll: true,
                                    reportDir: '.',
                                    reportFiles: 'gitleaks-report.json',
                                    reportName: 'Gitleaks Security Report',
                                    reportTitles: 'Gitleaks Scan Results'
                                ])
                            }
                        }
                    }
                }
            }
        }

        // 4. Build and Push Stage:
        // This stage runs after the parallel checks are successful.
        stage('Build and Push Docker Image') {
            steps {
                script {
                    def imageName = "${env.DOCKER_USER}/pygoat"
                    def customImage = docker.build("${imageName}:${env.IMAGE_TAG}", '.')
                    docker.withRegistry("https://registry.hub.docker.com", env.DOCKER_CREDENTIALS_ID) {
                        customImage.push()
                    }
                }
            }
        }
    }
}