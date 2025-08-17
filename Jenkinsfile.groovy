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

                        // Install python3-venv if needed and create virtual environment
                        sh '''
                            apt-get update && apt-get install -y python3-venv || true
                            python3 -m venv venv
                            . venv/bin/activate
                            pip install -r requirements.txt
                            python manage.py test
                        '''
                    }
                }
                stage('Gitleaks Scan') {
                    steps {
                        // Checkout code with full history for a complete scan.
                        // The 'unshallow' command is needed because Jenkins often does a shallow clone.
                        checkout scm
                        sh 'git fetch --unshallow || echo "Already a full clone"'

                        // Run Gitleaks using its Docker image.
                        // 'catchError' mimics 'continue-on-error: true'. If leaks are found,
                        // the stage is marked as UNSTABLE, but the pipeline continues.
                        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            sh "docker run --rm -v ${pwd()}:/repo ghcr.io/gitleaks/gitleaks:v8.18.2 detect --source /repo --verbose --report-path gitleaks-report.json"
                        }

                        // Save the gitleaks report as a build artifact.
                        archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
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