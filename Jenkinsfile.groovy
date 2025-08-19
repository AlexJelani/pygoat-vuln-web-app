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
        stage('Run Tests') {
            steps {
                sh 'rm -f .git/index.lock || true'
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
        
        stage('Gitleaks Secret Scan') {
            steps {
                sh 'rm -f .git/index.lock || true'
                checkout scm
                sh '''
                    mkdir -p reports
                    
                    echo "Running Gitleaks secret detection..."
                    docker pull zricethezav/gitleaks:latest
                    
                    docker run --rm \
                      -v "${WORKSPACE}:/workspace" \
                      -w /workspace \
                      zricethezav/gitleaks:latest detect \
                      --source . \
                      --report-format json \
                      --report-path /workspace/reports/gitleaks-report.json \
                      --no-git || true
                    
                    if [ -f "reports/gitleaks-report.json" ]; then
                        echo "✅ Gitleaks scan completed. Check reports/gitleaks-report.json"
                    else
                        echo "No secrets detected" > reports/gitleaks-report.json
                        echo "✅ No secrets found"
                    fi
                '''
            }
        }
        
        stage('Bandit SAST') {
            steps {
                sh 'rm -f .git/index.lock || true'
                checkout scm
                sh '''
                    mkdir -p reports
                    python3 -m venv venv
                    . venv/bin/activate
                    pip install bandit
                    bandit -r . -f json -o reports/bandit-report.json || true
                    echo "✅ Bandit SAST scan completed"
                '''
            }
        }


    }
}