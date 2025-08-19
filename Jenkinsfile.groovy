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
              zricethezav/gitleaks:latest detect \
              --source=/workspace \
              --verbose \
              --report-format=json > reports/gitleaks-report.json
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