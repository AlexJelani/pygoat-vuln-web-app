pipeline {
    agent any

    environment {
        DOCKER_CREDENTIALS_ID = 'dockerhub-credentials'
        DOCKER_USER           = 'alexjelani13'
        IMAGE_TAG             = 'latest'
    }

    stages {
        stage('Quality Checks') {
            parallel {
                stage('Run Tests') {
                    steps {
                        checkout scm
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
        sh 'rm -f .git/index.lock || true'
        checkout scm
        sh '''
            REPORT_DIR="reports"
            mkdir -p "${REPORT_DIR}"
            docker pull ghcr.io/gitleaks/gitleaks:latest
            
            set +e
            echo "Scanning working directory in $WORKSPACE ..."
            ls -lah "${WORKSPACE}"
            
            docker run --rm \
              -v "${WORKSPACE}:/workspace" \
              -w /workspace \
              ghcr.io/gitleaks/gitleaks:latest dir \
              --source=/workspace \
              --verbose \
              --report-path=/workspace/reports/gitleaks-report.json \
              --report-format=json
            EXIT_CODE=$?
            set -e
            
            if [ "$EXIT_CODE" -ne 0 ]; then
                echo "🛑 GitLeaks scan detected secrets. Please review reports/gitleaks-report.json"
                exit 1
            else
                echo "✅ GitLeaks scan passed with no secrets detected."
            fi
        '''
    }
}
            }
        }

        stage('Build and Push Docker Image') {
            steps {
                sh 'rm -f .git/index.lock || true'
                checkout scm
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