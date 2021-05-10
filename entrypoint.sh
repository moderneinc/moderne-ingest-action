#!/bin/sh -l

echo $GCP_SERVICE_ACCOUNT_KEY > /tmp/gcp_service_account_key.json

/usr/local/gcloud/google-cloud-sdk/bin/gcloud --quiet auth activate-service-account --key-file=/tmp/gcp_service_account_key.json

cd $GITHUB_WORKSPACE/main
./gradlew --no-daemon bootRun --args='--spring.profiles.active=prod'
