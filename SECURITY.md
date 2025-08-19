# Security Notes for PyGoat

## About PyGoat
PyGoat is an **intentionally vulnerable web application** designed for security training and education. It contains deliberate security flaws to help users learn about web application security.

## GitLeaks Secret Scanning
This repository uses GitLeaks for secret scanning in the CI/CD pipeline. However, since PyGoat is a training application, it contains intentional "secrets" that are part of the learning exercises.

### Handling Intentional Test Secrets
- Test password hashes and API keys are used in security labs
- These are documented in `.gitleaksignore` to prevent false positives
- All intentional secrets are clearly marked with comments in the code
- These values are NOT real production secrets

### Real Security Considerations
While PyGoat contains intentional vulnerabilities for training:
- Never use this application in production
- Always use proper secret management in real applications
- Follow security best practices when building production systems

## CI/CD Security Pipeline
The Jenkins pipeline includes:
- GitLeaks secret scanning
- Test execution
- DefectDojo integration for vulnerability management
- Docker image building and deployment