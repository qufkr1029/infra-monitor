pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        skipDefaultCheckout()
    }

    triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'ref', value: '$.ref']
            ],
            tokenCredentialId: 'infra-monitor-webhook-token',
            causeString: 'Triggered by GitHub push to $ref',
            regexpFilterText: '$ref',
            regexpFilterExpression: '^refs/heads/main$',
            printContributedVariables: false,
            printPostContent: false
        )
    }

    environment {
        DEPLOY_DIR = '/home/ubuntu/docker/projects/infra-monitor'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Image') {
            steps {
                sh 'docker build -t infra-monitor-collector:latest ./collector'
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    set -eu
                    mkdir -p "$DEPLOY_DIR"
                    cp docker-compose.yml "$DEPLOY_DIR/docker-compose.yml"
                    cd "$DEPLOY_DIR"
                    docker compose config --quiet
                    docker compose up -d --no-build --force-recreate
                    docker compose ps
                '''
            }
        }
    }
}
