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
                stage('TruffleHog Scan') {
    steps {
        sh 'rm -f .git/index.lock || true'
        checkout scm
        sh '''
            REPORT_DIR="reports"
            mkdir -p "${REPORT_DIR}"
            docker pull trufflesecurity/trufflehog:latest
            
            set +e
            echo "Scanning working directory in $WORKSPACE ..."
            ls -la "${WORKSPACE}"
            
            docker run --rm \
              -v "${WORKSPACE}:/workspace" \
              -w /workspace \
              trufflesecurity/trufflehog:latest filesystem \
              --json --no-verification --results=verified,unknown,unverified . > reports/trufflehog-report.json
            EXIT_CODE=$?
            set -e
            
            echo "TruffleHog scan results:"
            cat reports/trufflehog-report.json || echo "Report file not found"
            
            if [ "$EXIT_CODE" -ne 0 ]; then
                echo "🛑 TruffleHog scan detected secrets. Please review reports/trufflehog-report.json"
            else
                echo "✅ TruffleHog scan passed with no secrets detected."
            fi
        '''
    }
}
            }
        }

        stage('Upload Scan results to DefectDojo') {
            steps {
                sh '''
                    if [ -f "reports/trufflehog-report.json" ]; then
                        echo "Uploading scan results to DefectDojo..."
                        curl -v -X POST "${DEFECTDOJO_URL}/api/v2/import-scan/" \
                          -H "Authorization: Token ${DEFECTDOJO_TOKEN}" \
                          -H "Content-Type: multipart/form-data" \
                          -F "scan_type=Trufflehog Scan" \
                          -F "file=@reports/trufflehog-report.json" \
                          -F "engagement=${DEFECTDOJO_ENGAGEMENT_ID}" \
                          -F "verified=true" \
                          -F "active=true" || echo "DefectDojo upload failed"
                    else
                        echo "No scan report found to upload"
                    fi
                '''
            }
        }


    }
}