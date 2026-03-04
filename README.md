# react-native-facetec-liveness

React Native component for FaceTec 3D Liveness verification.

## Installation (Local Module)

This module is designed to be used as a local native module within your React Native project.

### Step 1: Copy the module

Copy the `react-native-facetec-liveness` folder to your project:

```
your-react-native-project/
├── native_modules/
│   └── react-native-facetec-liveness/   # Copy here
├── android/
├── ios/
├── src/
└── package.json
```

### Step 2: Add dependency to package.json

Add the local module to your `package.json` dependencies:

```json
{
  "dependencies": {
    "react-native-facetec-liveness": "file:./native_modules/react-native-facetec-liveness"
  }
}
```

### Step 3: Install dependencies

**IMPORTANT:** Use `yarn install` (not `npm install`) to respect the existing lock file and avoid dependency conflicts:

```bash
yarn install
```

---

## iOS Setup

### 1. Add pod to Podfile

In `ios/Podfile`, add inside the main target:

```ruby
target 'YourApp' do
  # ... other pods
  pod 'react-native-facetec-liveness', :path => '../native_modules/react-native-facetec-liveness'
end
```

### 2. Install pods

```bash
cd ios && pod install && cd ..
```

### 3. Add camera permission to Info.plist

```xml
<key>NSCameraUsageDescription</key>
<string>Necesitamos acceso a la camara para validacion facial</string>
```

---

## Android Setup

### 1. Add module to settings.gradle

In `android/settings.gradle`, add at the beginning:

```gradle
include ':react-native-facetec-liveness'
project(':react-native-facetec-liveness').projectDir = new File(rootProject.projectDir, '../native_modules/react-native-facetec-liveness/android')
```

### 2. Add dependency to app/build.gradle

In `android/app/build.gradle`, add in the `dependencies` block:

```gradle
dependencies {
    // ... other dependencies
    implementation project(':react-native-facetec-liveness')
}
```

### 3. Register package in MainApplication

**For Kotlin (`MainApplication.kt`):**

```kotlin
import com.facetecliveness.FaceTecLivenessPackage

class MainApplication : Application(), ReactApplication {
    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> =
                PackageList(this).packages.apply {
                    add(FaceTecLivenessPackage())
                }
            // ... rest of config
        }
}
```

**For Java (`MainApplication.java`):**

```java
import com.facetecliveness.FaceTecLivenessPackage;

@Override
protected List<ReactPackage> getPackages() {
    List<ReactPackage> packages = new PackageList(this).getPackages();
    packages.add(new FaceTecLivenessPackage());
    return packages;
}
```

### 4. Camera permissions (AndroidManifest.xml)

These are already included in the module, but ensure your app has:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Usage

```tsx
import React from 'react';
import { View, Alert } from 'react-native';
import { Facetec3DLivenessTestButton } from 'react-native-facetec-liveness';

const MyComponent = () => {
  const handleResponse = (response) => {
    if (response.success) {
      Alert.alert('Exito', 'Verificacion de vida completada');
      console.log('Status:', response.status);
    } else {
      Alert.alert('Error', response.message);
      console.log('Error status:', response.status);
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
      <Facetec3DLivenessTestButton
        onResponse={handleResponse}
        style={{ width: 250, height: 50 }}
        initializingText="Iniciando"
        readyText="Iniciar prueba de vida"
        errorText="Error de inicializacion"
      />
    </View>
  );
};

export default MyComponent;
```

---

## Props

| Prop | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `onResponse` | `(response: LivenessResponse) => void` | Yes | - | Callback when liveness check completes |
| `style` | `ViewStyle` | No | - | Custom styles for the button |
| `initializingText` | `string` | No | `"Iniciando"` | Text shown while SDK initializes |
| `readyText` | `string` | No | `"Iniciar prueba de vida"` | Text shown when ready |
| `errorText` | `string` | No | `"Error de inicializacion"` | Text shown on init error |

---

## Response Object

```typescript
interface LivenessResponse {
  success: boolean;
  status: LivenessStatus;
  message: string;
}

type LivenessStatus =
  | 'success'
  | 'error'
  | 'initError'
  | 'cancelled'
  | 'SESSION_COMPLETED'
  | 'USER_CANCELLED_FACE_SCAN'
  | 'REQUEST_ABORTED';
```

---

## Button States

| State | Text | Color | Enabled |
|-------|------|-------|---------|
| Initializing | "Iniciando" | Gray | No |
| Ready | "Iniciar prueba de vida" | Blue | Yes |
| Error | "Error de inicializacion" | Red | No |

---

## Configuration

To change the FaceTec API configuration, modify:

- **iOS**: `ios/FaceTecLiveness/Config.swift`
- **Android**: `android/src/main/java/com/facetecliveness/Config.java`

Update these values with your production credentials:

```
DeviceKeyIdentifier = "your-device-key"
YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT = "https://your-api-endpoint.com"
```

---

## Troubleshooting

### Error: "FaceTecLivenessButton was not found in UIManager"

Ensure you have:
1. Added the pod to Podfile and run `pod install`
2. Registered `FaceTecLivenessPackage` in MainApplication
3. Rebuilt the app (not just hot reload)

### Error: "Using bridging headers with framework targets is unsupported"

This has been fixed in the module. If you encounter it, ensure the podspec does not have `SWIFT_OBJC_BRIDGING_HEADER` in `pod_target_xcconfig`.

### Dependency conflicts with npm install

Always use `yarn install` instead of `npm install` to respect the existing `yarn.lock` file and avoid version conflicts.

---

## Project Structure

```
react-native-facetec-liveness/
├── src/
│   ├── index.ts
│   └── Facetec3DLivenessTestButton.tsx
├── ios/
│   ├── FaceTecSDK.xcframework/
│   └── FaceTecLiveness/
│       ├── FaceTecLivenessViewManager.swift
│       ├── FaceTecLivenessViewManager.m
│       ├── Config.swift
│       ├── SessionRequestProcessor.swift
│       └── SampleAppNetworkingRequest.swift
├── android/
│   ├── libs/
│   │   └── facetec-sdk-10.0.38.aar
│   └── src/main/java/com/facetecliveness/
│       ├── FaceTecLivenessViewManager.java
│       ├── FaceTecLivenessPackage.java
│       ├── FaceTecLivenessModule.java
│       ├── RNFaceTecLivenessButton.java
│       ├── Config.java
│       ├── SessionRequestProcessor.java
│       └── SampleAppNetworkingRequest.java
├── package.json
├── react-native-facetec-liveness.podspec
└── README.md
```

---

## License

MIT
