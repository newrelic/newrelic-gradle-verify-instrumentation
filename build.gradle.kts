repositories {
    mavenCentral()
}

plugins {
    id("groovy")
    id("java-gradle-plugin")
    id("maven-publish")
    id("signing")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.apache.maven:maven-resolver-provider:3.6.1")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.4.2")
    implementation("org.apache.maven.resolver:maven-resolver-transport-file:1.4.2")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.4.2")
    implementation("commons-codec:commons-codec:1.14")

    // test deps
    testImplementation("org.codehaus.groovy:groovy:2.5.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.1")
}

tasks.test {
    useJUnitPlatform()
}

group = "com.newrelic.agent.java"
version = "3.1-SNAPSHOT"

tasks {
    val taskScope = this
    val jar: Jar by taskScope
    jar.apply {
        manifest.attributes["Implementation-Vendor"] = "New Relic, Inc"
        manifest.attributes["Implementation-Version"] = project.version
    }
}

gradlePlugin {
    plugins {
        create("gradle-verify-instrumentation-plugin") {
            id = "com.newrelic.gradle-verify-instrumentation-plugin"
            implementationClass = "com.newrelic.agent.instrumentation.verify.VerificationPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }
}

signing {
//    val signingKey: String? by project
//    val signingPassword: String? by project
//    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}
