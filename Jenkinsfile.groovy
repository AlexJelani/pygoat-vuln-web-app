pipeline {
    agent any

    environment {
        DOCKER_CREDENTIALS_ID = 'dockerhub-credentials'
        DOCKER_USER           = 'alexjelani13'
        IMAGE_TAG             = 'latest'
        DEFECTDOJO_URL        = 'http://131.186.56.105:8083'
        DEFECTDOJO_TOKEN      = credentials('defectdojo-api-token')
        DEFECTDOJO_ENGAGEMENT_ID = '1'
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
            docker pull zricethezav/gitleaks:latest
            
            set +e
            echo "Scanning working directory in $WORKSPACE ..."
            
            docker run --rm \
              -v "${WORKSPACE}:/workspace" \
              -w /workspace \
              zricethezav/gitleaks:latest dir \
              --source=/workspace \
              --verbose \
              --report-path=/workspace/reports/gitleaks-report.json \
              --report-format=json
            EXIT_CODE=$?
            set -e
            
            if [ "$EXIT_CODE" -ne 0 ]; then
                echo "🛑 GitLeaks scan detected secrets. Please review reports/gitleaks-report.json"
                cat reports/gitleaks-report.json || echo "Report file not found"
            else
                echo "✅ GitLeaks scan passed with no secrets detected."
            fi
        '''
    }
}
            }
        }

        stage('Upload Scan results to DefectDojo') {
            steps {
                sh '''
                    if [ -f "reports/gitleaks-report.json" ]; then
                        echo "Uploading scan results to DefectDojo..."
                        curl -v -X POST "${DEFECTDOJO_URL}/api/v2/import-scan/" \
                          -H "Authorization: Token ${DEFECTDOJO_TOKEN}" \
                          -H "Content-Type: multipart/form-data" \
                          -F "scan_type=JSON" \
                          -F "file=@reports/gitleaks-report.json" \
                          -F "engagement=${DEFECTDOJO_ENGAGEMENT_ID}" \
                          -F "verified=true" \
                          -F "active=true" || echo "DefectDojo upload failed"
                    else
                        echo "No scan report found to upload"
                    fi
                '''
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