#!/bin/sh -l

cd $GITHUB_WORKSPACE/main
./gradlew --no-daemon bootRun --args='--spring.profiles.active=prod'
