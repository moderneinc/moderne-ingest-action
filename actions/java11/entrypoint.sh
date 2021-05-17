#!/bin/sh -l

printf '%s' "${INPUT_GCPKEY}" > /tmp/gcp_key.json
export GOOGLE_APPLICATION_CREDENTIALS="/tmp/gcp_key.json"

cd $GITHUB_WORKSPACE/main
./gradlew --no-daemon bootRun --args="--spring.profiles.active=prod --styleName=${INPUT_STYLENAME}"
