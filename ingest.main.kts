#!/usr/bin/env kotlin
// to use, `brew install kotlin`

@file:DependsOn("commons-cli:commons-cli:1.4")

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import java.io.File

val options = Options()
options.addOption(
    Option.builder("java")
        .longOpt("java-version")
        .argName("java")
        .desc("A java version. Either 11 or 8.")
        .hasArg(true)
        .required(false)
        .build()
)

val (org, repo) = "https://github.com/([^/]+)/(.*)".toRegex().find(args[0])!!.destructured

val commandLine: CommandLine = DefaultParser().parse(options, args)
val javaVersion: String = commandLine.getOptionValue("java-version", "11")

File(".github/workflows/${org}_${repo}.yml").writeText("""
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
            uses: docker://moderne/ingest:java${javaVersion}-latest
            env:
              MODERNE_API_ACCESS_TOKEN:     ${'$'}{{ secrets.MODERNE_API_ACCESS_TOKEN }}
""".trimIndent())
