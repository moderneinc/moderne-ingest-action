#!/bin/sh -l


printf '%s' "$INPUT_GCP_SERVICE_ACCOUNT_KEY" > /tmp/gcp_service_account_key.json
export GOOGLE_APPLICATION_CREDENTIALS="/tmp/gcp_service_account_key.json"

cd $GITHUB_WORKSPACE/main
./gradlew --no-daemon bootRun --args='--spring.profiles.active=prod'
