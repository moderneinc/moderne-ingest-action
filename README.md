# moderne-ingest-action

Just github actions workflows that leverage moderne-ingest-container

## Add repository for ingest in production

See .github/workflows. Most use java 11, some use java 8 (all spring projects). Inspect the project's build to 
determine which java version to use. If in doubt, try java 11 and see if there are any compilation errors.

Copy an existing github actions workflow yml file and modify it to reference the github repository you want to add. 
The "Checkout target" step specifies a github organization/repository that will be used by the action as the repository 
to ingest. The workflow file should be named `{github org}_{github repo}.yml`. The workflow name should 
be `{github_org}/{github_repo} ingest`. 

## Run ingest locally

See [moderne-ingest-container](https://github.com/moderneinc/moderne-ingest-container)
