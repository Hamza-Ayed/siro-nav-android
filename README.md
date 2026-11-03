# Siro Nav for Android (Kotlin)
### Native Turn-by-Turn In-App Navigation SDK for Android (Kotlin • SDK 21+)

[![JitPack](https://jitpack.io/v/Hamza-Ayed/siro-nav-android.svg)](https://jitpack.io/#Hamza-Ayed/siro-nav-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-orange.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2021%2B-green.svg)](https://developer.android.com)
[![Maps](https://img.shields.io/badge/Maps-Siro%20Limestone%20Vector-purple.svg)](https://map.intaleqapp.com)

A high-performance, native **Kotlin** navigation SDK engineered for ride-hailing platforms, courier fleets, and mobility operators across MENA. Delivers an end-to-end sovereign navigation stack directly inside Android apps—eliminating reliance on third-party map providers (Google Maps, Waze) and driving down operational map unit costs by over $0.30 per completed trip.

---

## 📸 Live Visuals & Navigation HUD

<div align="center">
  <img src="docs/media/screen_android_roundabout_polyline.png" width="370" alt="Siro Nav Android Roundabout Polyline" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/media/screen_android_fare_hud.png" width="370" alt="Siro Nav Android Fare Progression HUD" />
</div>

- **Road-Adherent Route Polyline**: Multi-layer vector rendering with high-contrast halo casing (`12.0px`) and navigation blue core (`7.5px`) strictly adhering to real road topology and complex roundabout corridors.
- **Intelligent Roundabout Maneuvers**: Automatically resolves rotaries to specific exit numbers (`Exit 1..6`) with offline studio Arabic audio instructions and localized street names.
- **High-Precision Vehicle Marker**: Dual-layer disc marker with drop shadow and sharp directional chevron aligned with live GPS bearing and trajectory.
- **Developer Tariff & Dynamic Metering**: Sub-second meter calculation for base fare, distance traveled, and waiting minutes (speed < 5 km/h) in custom currencies (`JOD`, `SAR`, `AED`, `USD`).
- **Elevated Speedometer**: Floating circular speed gauge with red warning perimeter for clear driver glanceability.

---

## 📦 Installation

### 1. Configure Maven Repository
Add JitPack to your `settings.gradle.kts` (or root `build.gradle.kts`):

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

## ⚡ Quick Start (5 Lines of Code)

### 1. Initialize Controller & Configure Fare Tariff

```kotlin
import com.siro.nav.SiroNavController
import com.siro.nav.models.NavConfig
import com.siro.nav.billing.NavFareConfig

// Initialize controller with Siro Maps API Key
val config = NavConfig(
    apiKey = "YOUR_SIRO_API_KEY",
    baseUrl = "https://map-saas.intaleqapp.com/api"
)
val navController = SiroNavController(context = this, config = config)

// Developer billing rules & custom currency
val fareConfig = NavFareConfig(
    baseFare = 0.50,            // Base starting price
    perKilometer = 0.35,        // Rate per kilometer
    perWaitingMinute = 0.05,    // Rate per waiting minute (speed < 5 km/h)
    currency = "JOD",           // Supported: JOD, SAR, AED, USD
    waitingSpeedKmh = 5.0
)

// Start turn-by-turn navigation
navController.startNavigation(route = activeRoute, fare = fareConfig)
```

### 2. Embed Navigation View in XML Layout

```xml
<com.siro.nav.view.SiroNavView
    android:id="@+id/siroNavView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 3. Connect Listeners & Feed GPS Fixes

```kotlin
val navView = findViewById<SiroNavView>(R.id.siroNavView)
navView.showFareMetrics = true

navController.listener = object : SiroNavListener {
    override fun onInstructionChanged(instruction: NavEvent.Instruction) {
        navView.updateInstruction(instruction)
    }

    override fun onProgressUpdated(progress: NavEvent.Progress) {
        navView.updateProgress(progress)
    }

    override fun onTripArrived(totals: NavTripTotals) {
        println("Trip arrived! Total Fare: ${totals.fareLabel}")
        println("Distance: ${totals.totalDistanceKm} km")
        println("Waiting: ${totals.waitingMinutes} min")
    }

    override fun onSpeedExceeded(currentSpeed: Double, speedLimit: Double) {}
    override fun onCameraApproaching(camera: SpeedCamera, distance: Double) {}
    override fun onError(error: String) {}
}

// Forward raw GPS fixes from FusedLocationProviderClient
fun onLocationReceived(location: Location) {
    navController.updateLocation(location)
}
```

---

## 🧪 Automated Testing

Execute unit test suite:
```bash
./gradlew :siro-nav:testReleaseUnitTest
```

---

## 📄 License
MIT License. © 2026 Siro Platform & Hamza Ayed.
