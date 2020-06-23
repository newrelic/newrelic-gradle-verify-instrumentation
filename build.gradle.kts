repositories {
    mavenCentral()
}

plugins {
    id("groovy")
    id("java-gradle-plugin")
    id("maven-publish")
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
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.4.1")
    implementation("org.apache.maven.resolver:maven-resolver-transport-file:1.4.1")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.4.1")

    // test deps
    testImplementation("org.codehaus.groovy:groovy:2.5.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.1")
}

tasks.test {
    useJUnitPlatform()
}

group = "com.newrelic.agent.java"
version = "3.0"

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
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }
}