plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kit.sms2mail"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kit.sms2mail"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = org.jetbrains.kotlin.konan.properties.Properties().apply {
            val localPropertiesFile = project.rootProject.file("local.properties")
            load(localPropertiesFile.inputStream())
        }
        buildConfigField("String", "WEB_CLIENT_SECRET", "${localProperties.getProperty("WEB_CLIENT_SECRET")}")
    }


    signingConfigs {
        getByName("debug") {
            storeFile = file("$rootDir/key.jks")
            keyAlias = "key0"
            storePassword = "yk0000"
            keyPassword = "yk0000"
        }
        create("release") {
            initWith(getByName("debug"))
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/LICENSE.md",
                    "META-INF/NOTICE.md",
                    "META-INF/DEPENDENCIES"
                )
            )
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach {
            val output = it as com.android.build.api.variant.impl.VariantOutputImpl
            val projectName = rootProject.name.replace(" ", "_")
            val version = output.versionName.get()
            val buildType = variant.name
            output.outputFileName = "${projectName}_v$version-$buildType.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    //gsm auth
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.api.services.gmail)
//    implementation(libs.mail)
    implementation(libs.jakarta.mail)
    implementation(libs.play.services.auth)
    implementation(libs.datastore.pref)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bundles.ktor)

    //navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    //material icons
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}