#!/usr/bin/env kotlin
// to use, `brew install kotlin`

import java.io.File

// topJavaMavenProjects.csv taken from public dataset from
// https://github.com/mast-group/mineSStuBs

File("topJavaMavenProjects.csv").readLines().drop(1).forEach { line ->
    val (org, repo) = "https://github.com/([^/]+)/(.*)".toRegex().find(line.substringBefore(','))!!.destructured

    val workflow = File(".github/workflows/${org}_${repo}.yml")

    if(!workflow.exists()) {
        workflow.writeText(
            """
                name: $org/$repo ingest
            
                on:
                  schedule:
                    - cron: "0 7 * * *"
                  push:
                    paths:
                      - 'EMERGENCY-INGEST-0.md'
                  workflow_dispatch: {}
            
                jobs:
                  ingest:
                    timeout-minutes: 60
                    runs-on: ubuntu-latest
                    name: Ingest
                    steps:
                      - uses: actions/checkout@v2
                        with:
                          repository: $org/$repo
                      - name: Ingest
                        uses: docker://moderne/ingest:java11-latest
                        env:
                          MODERNE_API_ACCESS_TOKEN:     ${'$'}{{ secrets.MODERNE_API_ACCESS_TOKEN }}
                      - name: Slack Notify on Failure
                        if:     ${'$'}{{ failure() }}
                        uses: rtCamp/action-slack-notify@v2
                        env:
                          SLACK_WEBHOOK:     ${'$'}{{ secrets.SLACK_ALERTS_WEBHOOK }}
                          SLACK_USERNAME: moderne-ingest
                          SLACK_COLOR: red
                          SLACK_ICON: https://image.pngaaa.com/813/2886813-middle.png
                          SLACK_TITLE: Ingest workflow failure
                          SLACK_MESSAGE: We all have bad days
            """.trimIndent()
        )
    }
}
