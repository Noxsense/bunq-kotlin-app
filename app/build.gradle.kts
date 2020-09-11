plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android-extensions")
}

android {
    compileSdkVersion(Sdk.COMPILE_SDK_VERSION)

    defaultConfig {
        minSdkVersion(Sdk.MIN_SDK_VERSION)
        targetSdkVersion(Sdk.TARGET_SDK_VERSION)

        applicationId = AppCoordinates.APP_ID
        versionCode = AppCoordinates.APP_VERSION_CODE
        versionName = AppCoordinates.APP_VERSION_NAME
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lintOptions {
        isWarningsAsErrors = true
        isAbortOnError = true
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk7"))

    // "install" the bunq java sdk
    // Duplicate class X.Y.Z found in modules jetified-jakarta.activation-1.2.2.jar (com.sun.activation:jakarta.activation:1.2.2) and jetified-javax.activation-api-1.2.0.jar (javax.activation:javax.activation-api:1.2.0)
    // Duplicate class X.Y.Z found in modules jetified-jakarta.xml.bind-api-2.3.3.jar (jakarta.xml.bind:jakarta.xml.bind-api:2.3.3) and jetified-jaxb-api-2.3.1.jar (javax.xml.bind:jaxb-api:2.3.1)
    // Duplicate class X.Y.Z found in modules jetified-jaxb-core-2.3.0.1.jar (com.sun.xml.bind:jaxb-core:2.3.0.1) and jetified-jaxb-impl-2.3.3.jar (com.sun.xml.bind:jaxb-impl:2.3.3)
    implementation("com.github.bunq:sdk_java:1.14.1") {
        exclude(group = "com.sun.xml.bind", module = "jaxb-core")
        exclude(group = "jakarta.xml.bind", module = "jakarta.xml.bind-api")
        exclude(group = "com.sun.activation", module = "jakarta.activation")
    }

    // https://github.com/Kotlin/kotlinx.coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.9")

    implementation("com.google.code.gson:gson:2.8.5")

    implementation(project(":library-kotlin"))

    implementation(SupportLibs.ANDROIDX_APPCOMPAT)
    implementation(SupportLibs.ANDROIDX_CONSTRAINT_LAYOUT)
    implementation(SupportLibs.ANDROIDX_CORE_KTX)

    testImplementation(TestingLib.JUNIT)

    androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_EXT_JUNIT)
    androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_RULES)
    androidTestImplementation(AndroidTestingLib.ESPRESSO_CORE)
}
