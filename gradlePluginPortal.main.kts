#!/usr/bin/env kotlin
// to use, `brew install kotlin`

import java.io.File

// repositories contributing to gradle plugin portal

val reader = File("plugins-with-urls.txt").bufferedReader()

reader.readLine() // skip headers
reader.readLine() // skip headers

var partitionCount = 0
var partition = 8

reader.lineSequence().forEach { l ->
    val line = l.substringAfterLast('|').trim()
    println(line)
    if (line.startsWith("https://github.com/")) {
        val parts = line.substringAfter("https://github.com/").split("/")
        if (parts.size == 2) {
            val (org, repo) = parts

            val workflow = File(".github/workflows/${org}_${repo}.yml")

            if (!workflow.exists()) {
                if(partitionCount.inc() > 800) {
                    partitionCount = 0
                    partition.inc()
                }

                workflow.writeText(
                    """
                        name: $org/$repo ingest
                    
                        on:
                          schedule:
                            - cron: "0 $partition * * *"
                          push:
                            paths:
                              - 'EMERGENCY-INGEST-${partition}.md'
                          workflow_dispatch: {}
                    
                        jobs:
                          ingest:
                            timeout-minutes: 60
                            runs-on: ubuntu-latest
                            name: Ingest
                            steps:
                              - uses: actions/checkout@v3
                                with:
                                  repository: $org/$repo
                              - name: Ingest
                                uses: docker://moderne/ingest:java11-latest
                                env:
                                  MODERNE_API_ACCESS_TOKEN:     ${'$'}{{ secrets.MODERNE_API_ACCESS_TOKEN }}
                    """.trimIndent()
                )
            }
        }
    }
}
