plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

android {
    namespace = "com.siro.nav"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Hamza-Ayed"
                artifactId = "siro-nav-android"
                version = "0.1.0"

                pom {
                    name.set("Siro Nav Android SDK")
                    description.set("In-app Turn-by-Turn Navigation SDK for Android (Kotlin) on Siro Maps")
                    url.set("https://github.com/Hamza-Ayed/siro-nav-android")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("Hamza-Ayed")
                            name.set("Hamza Ayed")
                            email.set("hamzaayedflutter@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/Hamza-Ayed/siro-nav-android.git")
                        developerConnection.set("scm:git:ssh://github.com:Hamza-Ayed/siro-nav-android.git")
                        url.set("https://github.com/Hamza-Ayed/siro-nav-android")
                    }
                }
            }
        }
    }
}
