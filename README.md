# moderne-ingest-action

## Add repository for ingest in production

See .github/workflows. Most use java 11, some use java 8 (all spring projects). Inspect the project's build to 
determine which java version to use. If in doubt, try java 11 and see if there are any compilation errors.

Copy an existing github actions workflow yml file and modify it to reference the github repository you want to add. 
The "Checkout target" step specifies a github organization/repository that will be used by the action as the repository 
to ingest. The workflow file should be named `{github org}_{github repo}.yml`. The workflow name should 
be `{github_org}/{github_repo} ingest`. 

## Run ingest locally

Make sure you're using the correct java version for the project being ingested. Prior versions of ingest performed the 
git clone for you. Since this is now being handled by a github action, ingest no longer does this, so you'll have to 
git clone yourself before running ingest. 


Run ingest via gradle: `./gradlew --args='{fully-qualified git checkout directory}'`

Run ingest via IntelliJ: Create a launch configuration for main class `io.moderne.ingest.IngestActionApplication`
with program arguments {fully-qualified git checkout directory}.