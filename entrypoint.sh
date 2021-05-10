#!/bin/sh -l

echo $GCR_KEY > /tmp/gcr_key.json

/usr/local/gcloud/google-cloud-sdk/bin/gcloud --quiet auth activate-service-account --key-file=/tmp/gcr_key.json

cd $GITHUB_WORKSPACE/main
./gradlew --no-daemon bootRun --args='--spring.profiles.active=prod'
