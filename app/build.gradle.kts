import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "ir.naghshechi.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "ir.naghshechi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        javaCompileOptions.annotationProcessorOptions.arguments["room.schemaLocation"] = "$projectDir/schemas"
    }
    val keyPath = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (!keyPath.isNullOrBlank()) create("distribution") {
            storeFile = file(keyPath)
            storePassword = System.getenv("ANDROID_STORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-preview" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!keyPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("distribution")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    testOptions.unitTests.isIncludeAndroidResources = true
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    maxHeapSize = "2g"
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
tasks.register("runtimeInventory") {
    doLast {
        val items = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }.distinctBy { "$it" }.sortedBy { "$it" }
            .map { mapOf("group" to it.group, "name" to it.name, "version" to it.version) }
        val output = layout.buildDirectory.file("reports/runtime-dependencies.json").get().asFile
        output.parentFile.mkdirs()
        output.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(items)))
    }
}
