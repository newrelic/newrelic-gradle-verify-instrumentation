[![Community Project header](https://github.com/newrelic/open-source-office/raw/master/examples/categories/images/Community_Project.png)](https://github.com/newrelic/open-source-office/blob/master/examples/categories/index.md#community-project)

gradle-verify-instrumentation-plugin
====================================

This plugin provides support for `verifyInstrumentation` DSL in New Relic instrumentation gradle build files. Using this plugin, you can figure out what range of versions for a library are supported by your instrumentation.  

:warning: This plugin has a very niche use case for the New Relic Java Agent. 
It is not intended to be used or modified for any other environment.

Open source license
====================================

This project is distributed under the Apache 2 license.

What do you need to make this work?
====================================

Required: 
* Gradle, minimum 7.2
* Java 17

Java Runtime
====================================

Most Java agent instrumentation modules are compiled to target Java 8+. However, some modules are compiled to target Java 11 and others require Java 17. 
The verifier must be run with a minimum Java runtime of 17 because it will fail when trying to verify instrumentation 
targeting Java 17 when running all modules from the same runtime. Of course, when running individual modules the runtime can be whatever version the given module requires.

Start using the plugin
====================================

To use the plugin, update your buildscript dependencies in settings.gradle:

```gradle
pluginManagement {
    repositories {
      mavenLocal()
      mavenCentral()
      gradlePluginPortal()
    }
  }
```

Update your build.gradle:

```gradle
buildscript {
    dependencies {
        classpath "com.newrelic.agent.java:gradle-verify-instrumentation-plugin:3.1"
    }
}

apply plugin "com.newrelic.gradle-verify-instrumentation-plugin"
```

Or:

```gradle
 plugins {
   id("com.newrelic.gradle-verify-instrumentation-plugin") version "3.1"
 }
```

**Note** For instrumentation bundled with the New Relic Java agent, this is already configured and these steps are not required.

## Configuring the plugin

To configure the plugin for a specific weave instrumentation library, in each instrumentation's `build.gradle`, you'll need to configure a `verifyInstrumentation` block. Within this block, specify maven ranges that your module should pass or fail against.

The task downloads all versions (and all required dependencies) within the testing ranges. Each version downloaded is then checked to make sure the instrumentation in this module applies as expected:

* If the target code is covered by `passes` or `passesOnly`, then the module must apply successfully.
* If the target code is covered by fails or is outside of a `passesOnly` range, then the module must not apply successfully.
* If the condition above is not met, the gradle task fails with the reason.

```gradle
verifyInstrumentation {
    fails("com.typesafe.play:anorm_2.11:[1.0,2.0)")
    passes("com.typesafe.play:anorm_2.11:[2.0,2.5)")
    fails("com.typesafe.play:anorm_2.11:[2.5,)")
}
```

In this example, we are saying that versions 2.0 (inclusive) through versions 2.5 (exclusive) of the "anorm_2.11" library should instrument correctly. We are also saying that versions 1.0 (inclusive) through versions 2.0 (exclusive) should fail. We also assert that versions greater than or equal to 2.5 fail instrumentation. This ensures that our instrumentation only works with the range specified in passes.

There are several options to use to configure the range support. In no particular order:
* `passes` specifies the range that _should_ be able to be instrumented. It does not perform any checks on versions outside the range.
* `fails` specifies the range that _should_ fail to be instrumented.
* `passesOnly` specifies the range that _should_ be able to be instrumented. It then checks all versions outside the range (for the same group:name) to ensure those versions fail.
* `exclude` specifies versions (can be a range) to exclude
* `excludeRegex` specifies versions to exclude using a regex, useful for excluding all snapshot builds, for example.
* `[]` configures an inclusive match
* `()` configures an exclusive match

For more information on range syntax, see [Maven version syntax](https://cwiki.apache.org/confluence/display/MAVENOLD/Dependency+Mediation+and+Conflict+Resolution#DependencyMediationandConflictResolution-DependencyVersionRanges).

**Note** In general, using `passesOnly` is preferable, as it ensures that only the given range works against your instrumentation, and not anything else. We can rewrite our above example with one line using `passesOnly`:

```gradle
verifyInstrumentation {
    passesOnly  'com.typesafe.play:anorm_2.11:[2.0,2.5)'
}
```

### Configuring the plugin outside the `java_agent` repo

The `java_agent` repo configuration includes several values you must set if using `verifyInstrumentation` outside the `java_agent` repo.

* `nrAgent` must be a reference to the newrelic.jar fat jar. You can specify a `String` to use a maven dependency, or a `File` to reference a local file.
* `passesFileName` is a file name as a `String` if you want all successful verifications to log to the same file. That is, when the package fails to apply when it should fail, or applies successfully when it should apply successfully. 
* `verifyClasspath` can be used to verify that the jar successfully applies when loading exactly the `compile` and `implementation` dependencies specified for the implementation jar. 

## Running the plugin

To verify all instrumentation libraries, simply invoke:
```gradle
.../java_agent/$ ./gradlew verifyInstrumentation
```
...and then go for a fresh cup of coffee. You have enough time to get get a really good cup a couple blocks away.

To verify a specific instrumentation module, invoke:

```gradle
...cd instrumentation/moduleToVerify
.../moduleToVerify/$ ../../gradlew verifyInstrumentation
```
Or:

```gradle
.../java_agent/$ ./gradlew :instrumentation:moduleToVerify:verifyInstrumentation
```

## Additional Dependencies

By default, the Maven library is verified with its transitive dependencies. To specify additional dependencies while verifying, add a configuration closure and use 'implementation'.

```gradle
verifyInstrumentation {
  passesOnly("io.spray:spray-routing_2.11:[1.3.1,)") {
    implementation("com.typesafe.akka:akka-actor_2.11:2.3.14") // akka is not explicitly listed as a spray dependency so we have to tell the verifier to include it.
  }
}
```

## Choosing versions

The versions to include and exclude are entirely dependent upon the target library being instrumented. 

Once you have the general range of supported versions (something like `[2.0,2.5)`), you need to verify the whole range and make note of any failures. If you are maintaining an instrumentation module with an open-ended list (such as `[2.0,)`), then you need to run `verifyInstrumentation` periodically to ensure the version range is still correct.

You'll probably see that just one build here or there fails. With that, you should add an exception, as it's probably just a problem with a specific build of the target library. If you see a whole string of failures, it might indicate a more fundamental change in the library as of a specific version. In that case, you might need to cap the version support, and create a new instrumentation to handle the newer versions. 


Support
====================================

New Relic has open-sourced this project. This project is provided AS-IS WITHOUT WARRANTY OR DEDICATED SUPPORT. Report issues and contributions to the project here on GitHub.

We encourage you to bring your experiences and questions to the [Explorers Hub](https://discuss.newrelic.com/) where our community members collaborate on solutions and new ideas.

Community
====================================
New Relic hosts and moderates an online forum where customers can interact with New Relic employees as well as other customers to get help and share best practices. Like all official New Relic open source projects, there's a related Community topic in the New Relic Explorers Hub. You can find this project's topic/threads here:

https://discuss.newrelic.com/c/support-products-agents/java-agent

Issues / enhancement requests
====================================
Issues and enhancement requests can be submitted in 
the [issues tab of this repository.](https://github.com/newrelic/newrelic-gradle-verify-instrumentation/issues) 
Please search for and review the existing open issues before submitting a new issue.

Contributing
====================================
We encourage your contributions to improve this project! Keep in mind when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project. If you have any questions, or to execute our corporate CLA, required if your contribution is on behalf of a company, please drop us an email at opensource@newrelic.com.
