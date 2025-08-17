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
                            // Ensure full git history and run scan
                            sh 'git fetch --unshallow || echo "Already full clone"'
                            sh 'ls -la .git || echo "No .git directory"'
                            
                            def scanResult = sh(
                                script: """
                                docker run --rm \\
                                    -v "${WORKSPACE}:/repo" \\
                                    -w /repo \\
                                    ghcr.io/gitleaks/gitleaks:latest \\
                                    detect --source=. \\
                                    --report-path=gitleaks-report.json \\
                                    --report-format=json \\
                                    --verbose
                                exit \$?
                                """,
                                returnStatus: true
                            )
                            
                            // Process results based on the exit code
                            if (scanResult != 0) {
                                currentBuild.result = 'UNSTABLE'
                                echo '🛑 Gitleaks scan detected potential secrets! Review the report.'
                                
                                if (fileExists('gitleaks-report.json')) {
                                    archiveArtifacts artifacts: 'gitleaks-report.json'
                                    sh 'cat gitleaks-report.json'
                                } else {
                                    echo 'Report file not found'
                                }
                            } else {
                                echo '✅ Gitleaks scan passed with no secrets detected.'
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