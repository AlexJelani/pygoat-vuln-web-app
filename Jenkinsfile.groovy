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
        
        stage('Git-Secret Scan') {
            steps {
                sh 'rm -f .git/index.lock || true'
                checkout scm
                sh '''
                    mkdir -p reports
                    
                    if ! command -v git-secret &> /dev/null; then
                        echo "Installing git-secret..."
                        apt-get update && apt-get install -y git-secret || true
                    fi
                    
                    if command -v git-secret &> /dev/null; then
                        if git-secret list &> /dev/null; then
                            echo "Found encrypted secrets, checking status..."
                            git-secret whoknows > reports/git-secret-report.txt
                            git-secret list >> reports/git-secret-report.txt
                            echo "✅ Git-secret scan completed"
                        else
                            echo "No git-secret configuration found" > reports/git-secret-report.txt
                            echo "✅ No git-secret setup detected"
                        fi
                    else
                        echo "git-secret not available" > reports/git-secret-report.txt
                        echo "⚠️ git-secret installation failed"
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