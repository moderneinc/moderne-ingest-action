import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "2.4.3"
    java
}

group = "io.moderne"

repositories {
    mavenLocal()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        url = uri("https://repo.gradle.org/gradle/libs-releases")
    }
    mavenCentral()
}

extra["jackson-bom.version"] = "2.12.2"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

configurations {
    all {
        resolutionStrategy.eachDependency {
            if(requested.group == "org.openrewrite") {
                useVersion("latest.integration")
                because("I said so")
            }
            if(requested.group == "io.micrometer") {
                useVersion("1.5.+")
                because("Two argument Timer.Sample#stop(..) was removed in Micrometer 1.6")
            }
        }
        resolutionStrategy {
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
            cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
        }
    }
}

dependencies {

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.google.cloud:spring-cloud-gcp-starter-storage:latest.release")

    implementation("org.apache.commons:commons-compress:latest.release")
    implementation("org.tukaani:xz:latest.release")

    implementation("org.rocksdb:rocksdbjni:latest.release")

    implementation("org.openrewrite:rewrite-maven:latest.integration")
    implementation("org.openrewrite:rewrite-java:latest.integration")
    implementation("org.openrewrite:rewrite-yaml:latest.integration")
    implementation("org.openrewrite:rewrite-properties:latest.integration")
    runtimeOnly("org.openrewrite:rewrite-java-11:latest.integration")
    runtimeOnly("org.openrewrite:rewrite-java-8:latest.integration")

    implementation("org.gradle:gradle-tooling-api:6.+")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

springBoot {
    mainClass.set("io.moderne.ingest.IngestActionApplication")
}

tasks.getByName<BootRun>("bootRun") {
    jvmArgs!!.add("-XX:MaxRAMPercentage=75.0")
}
