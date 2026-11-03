# Siro Nav for Android (Kotlin)
### نظام الملاحة التوجيهية الحية لمنصة خرائط سيرو لنظام أندرويد (كوتلن)

[![JitPack](https://jitpack.io/v/Hamza-Ayed/siro-nav-android.svg)](https://jitpack.io/#Hamza-Ayed/siro-nav-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.23-orange.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2021%2B-green.svg)](https://developer.android.com)

In-app turn-by-turn navigation on **Siro Maps**, engineered for ride-hailing and delivery apps. The driver never leaves your app: the route, map matching, Arabic voice guidance, and trip meter all run smoothly natively in Kotlin.

---

## 🚀 Key Features / المميزات الرئيسية

1. **Sub-second Map Matching**: High-performance forward-window map snapping onto route polyline with continuous off-route deviation tracking and auto-rerouting.
2. **Offline Bundled Arabic Voice Engine**: 93 pre-baked studio audio prompts in Arabic (`ar`) covering turns, distance alerts, speed limits, fixed speed cameras, and ride phases.
3. **Trip Meter & Tariff Engine**: Built-in meter for distance (in km) and waiting minutes (below speed threshold), auto-calculating fare with custom tariffs (JOD/SAR/USD).
4. **Speed Camera & Radar Warnings**: Lookahead detection (500m / 300m) for fixed and mobile radars with overspeed tolerance enforcement.
5. **Drop-in UI Component (`SiroNavView`)**: Native Android ViewGroup overlay with maneuver banners, distance counters, dynamic speed badge, and ride controls.
6. **Commercial API Licensing**: Enterprise API key gated licensing with automatic telemetry probe collector for fleet intelligence.

---

## 📦 Installation / التثبيت

### 1. Add Repository
In your `settings.gradle.kts` (or root `build.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency
In your app module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Hamza-Ayed:siro-nav-android:0.1.0")
}
```

---

## 🛠️ Quick Start / دليل البدء السريع

### 1. Initialize Controller with API Key & Fare Rules

```kotlin
import com.siro.nav.SiroNavController
import com.siro.nav.models.NavConfig
import com.siro.nav.billing.NavFareConfig

val config = NavConfig(
    apiKey = "YOUR_SIRO_API_KEY",
    baseUrl = "https://map-saas.intaleqapp.com/api"
)
val navController = SiroNavController(context = this, config = config)

// Start Navigation with Ride Fare Config
val fareConfig = NavFareConfig(
    baseFare = 0.50,
    perKilometer = 0.35,
    perWaitingMinute = 0.05,
    currency = "JOD"
)

val started = navController.startNavigation(route = activeRoute, fare = fareConfig)
if (!started) {
    println("Navigation start failed: ${navController.activeError}")
}
```

### 2. Feed GPS Fixes (e.g. from FusedLocationProviderClient)

```kotlin
locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
        result.lastLocation?.let { location ->
            navController.updateLocation(location)
        }
    }
}
```

### 3. Drop-in HUD View in Activity / Fragment XML

```xml
<com.siro.nav.view.SiroNavView
    android:id="@+id/siroNavView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
val navView = findViewById<SiroNavView>(R.id.siroNavView)

navController.listener = object : SiroNavListener {
    override fun onInstructionChanged(instruction: NavEvent.Instruction) {
        navView.updateInstruction(instruction)
    }

    override fun onProgressUpdated(progress: NavEvent.Progress) {
        navView.updateProgress(progress)
    }

    override fun onTripArrived(totals: NavTripTotals) {
        println("Trip arrived! Fare: ${totals.fareLabel}")
    }

    override fun onSpeedExceeded(currentSpeed: Double, speedLimit: Double) {}
    override fun onCameraApproaching(camera: SpeedCamera, distance: Double) {}
    override fun onError(error: String) {}
}

navView.onExitListener = {
    navController.stopNavigation()
}
```

---

## 🧪 Testing

Run test suite:
```bash
./gradlew :siro-nav:testReleaseUnitTest
```

---

## 📄 License
MIT License. © 2026 Siro Platform & Hamza Ayed.
