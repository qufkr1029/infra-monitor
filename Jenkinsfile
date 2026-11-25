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
                    # The host bind directory must be writable by the collector's UID.
                    docker run --rm --network none --read-only --user 0:0 \
                        --cap-drop ALL --cap-add CHOWN \
                        -v "$DEPLOY_DIR/collector_logs:/logs" \
                        infra-monitor-collector:latest \
                        python -c 'import os; os.chown("/logs", 65534, 65534)'
                    docker compose up -d --no-build --force-recreate
                    docker compose ps
                '''
            }
        }
    }
}
