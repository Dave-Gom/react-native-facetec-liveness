# react-native-facetec

React Native component for FaceTec 3D Liveness verification.

## Installation (Local Module)

This module is designed to be used as a local native module within your React Native project.

### Step 1: Copy the module

Copy the `react-native-facetec` folder to your project:

```text
your-react-native-project/
├── native_modules/
│   └── react-native-facetec/   # Copy here
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
    "react-native-facetec": "file:./native_modules/react-native-facetec"
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
  pod 'react-native-facetec', :path => '../native_modules/react-native-facetec'
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
include ':react-native-facetec'
project(':react-native-facetec').projectDir = new File(rootProject.projectDir, '../native_modules/react-native-facetec/android')
```

### 2. Add dependency to app/build.gradle

In `android/app/build.gradle`, add in the `dependencies` block:

```gradle
dependencies {
    // ... other dependencies
    implementation project(':react-native-facetec')
}
```

### 3. Add flatDir repository for FaceTec SDK AAR

In `android/app/build.gradle`, add at the end of the file (after the `apply from` statements):

```gradle
repositories {
    // FaceTec SDK AAR
    flatDir {
        dirs '../../native_modules/react-native-facetec/android/libs'
    }
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
import { Facetec3DLivenessTestButton } from 'react-native-facetec';

const MyComponent = () => {
  const handleResponse = (response) => {
    if (response.success && !response.didError && response.result?.livenessProven) {
      Alert.alert('Exito', 'Verificacion de vida completada');
      console.log('Server info:', response.serverInfo);
    } else if (response.didError) {
      Alert.alert('Error', 'Error en el servidor');
    } else if (response.success === undefined) {
      // Sin respuesta del servidor (error de red o usuario cancelo)
      console.log('Sin respuesta del servidor');
    } else {
      Alert.alert('Fallido', 'Liveness no verificado');
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
      <Facetec3DLivenessTestButton
        onResponse={handleResponse}
        style={{ width: 250, height: 50, backgroundColor: 'blue' }}
        initializingText="Iniciando"
        readyText="Iniciar prueba de vida"
        errorText="Error de inicializacion"
        permissionDeniedText="Permiso de camara denegado"
        errorStyle={{ backgroundColor: 'red' }}
        initializingStyle={{ backgroundColor: 'gray' }}
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
| `style` | `ViewStyle` | No | - | Custom styles for the button (including backgroundColor for ready state) |
| `initializingText` | `string` | No | `"Iniciando"` | Text shown while SDK initializes |
| `readyText` | `string` | No | `"Iniciar prueba de vida"` | Text shown when ready |
| `errorText` | `string` | No | `"Error de inicializacion"` | Text shown on init error |
| `permissionDeniedText` | `string` | No | `"Permiso de camara denegado"` | Text shown when camera permission is denied |
| `errorStyle` | `ViewStyle` | No | - | Custom styles for error state (only backgroundColor is applied) |
| `initializingStyle` | `ViewStyle` | No | Falls back to `style` | Custom styles for initializing state (only backgroundColor is applied) |

---

## Response Object

The response object contains data directly from the FaceTec server:

```typescript
interface LivenessResponse {
  /** Indicates general success from server */
  success?: boolean;
  /** Indicates if there was a server error */
  didError?: boolean;
  /** Encrypted response blob */
  responseBlob?: string;
  /** Liveness result */
  result?: LivenessResult;
  /** Server information */
  serverInfo?: ServerInfo;
  /** Additional session data */
  additionalSessionData?: AdditionalSessionData;
  /** HTTP call information */
  httpCallInfo?: HttpCallInfo;
}

interface LivenessResult {
  /** Estimated age group */
  ageV2GroupEnumInt?: number;
  /** Indicates if liveness was proven */
  livenessProven?: boolean;
}

interface ServerInfo {
  /** Server SDK version */
  coreServerSDKVersion?: string;
  /** Server mode (Development Only, Production, etc.) */
  mode?: string;
  /** Security notice */
  notice?: string;
}

interface AdditionalSessionData {
  /** Application ID */
  appID?: string;
  /** Device model */
  deviceModel?: string;
  /** Device SDK version */
  deviceSDKVersion?: string;
  /** Installation ID */
  installationID?: string;
  /** Platform (ios, android) */
  platform?: string;
}

interface HttpCallInfo {
  /** Call date */
  date?: string;
  /** Epoch in seconds */
  epochSecond?: number;
  /** Call path */
  path?: string;
  /** HTTP method */
  requestMethod?: string;
  /** Transaction ID */
  tid?: string;
}
```

### Response Handling

```typescript
const handleResponse = (response: LivenessResponse) => {
  // Success case: liveness verified
  if (response.success && !response.didError && response.result?.livenessProven) {
    console.log('Liveness verified!');
  }
  // Server error
  else if (response.didError) {
    console.log('Server error');
  }
  // No response (network error or user cancelled)
  else if (response.success === undefined) {
    console.log('No server response');
  }
  // Liveness not proven
  else {
    console.log('Liveness not verified');
  }
};
```

---

## Button States

| State | Text | Color | Enabled |
|-------|------|-------|---------|
| Initializing | "Iniciando" | Gray (or `initializingStyle.backgroundColor`, or `style.backgroundColor`) | No |
| Ready | "Iniciar prueba de vida" | Controlled via `style` prop | Yes |
| Error | "Error de inicializacion" | Red (or `errorStyle.backgroundColor`) | No |
| Permission Denied | "Permiso de camara denegado" | Red (or `errorStyle.backgroundColor`) | No |

---

## Configuration

To configure the FaceTec SDK, modify these files:

- **iOS**: `ios/FaceTecLiveness/Config.swift`
- **Android**: `android/src/main/java/com/facetec/Config.java`

### Required Configuration

```text
DeviceKeyIdentifier = "your-device-key"
YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT = "your-endpoint-url"
```

### API Endpoint Configuration

| Environment | Endpoint | Notes |
|-------------|----------|-------|
| **Testing/Dev** | `https://api.facetec.com/api/v4/biometrics/process-request` | FaceTec's public testing server |
| **Production** | `https://your-server.com/facetec/process` | YOUR OWN backend server |

**IMPORTANT**: In production, you MUST use your own backend server that proxies requests to FaceTec. Calling FaceTec's API directly from the app is NOT allowed in production.

See:
- [Security Best Practices](https://dev.facetec.com/security-best-practices#server-rest-endpoint-security)
- [Architecture Diagram](https://dev.facetec.com/configuration-options#zoom-architecture-and-data-flow)

---

## Troubleshooting

### Error: "FaceTecLivenessButton was not found in UIManager"

Ensure you have:
1. Added the pod to Podfile and run `pod install`
2. Rebuilt the app (not just hot reload)

### Error: "Native module FaceTecLivenessModule tried to override FaceTecLivenessModule"

This happens when the module is registered twice. React Native autolinking handles module registration automatically, so **do NOT manually register** `FaceTecLivenessPackage` in `MainApplication.kt` or `MainApplication.java`. If you added it manually, remove it.

### Error: "Could not find :facetec-sdk-X.X.X"

Make sure you added the `flatDir` repository configuration in `android/app/build.gradle` (see step 3 of Android Setup).

### Error: "Using bridging headers with framework targets is unsupported"

This has been fixed in the module. If you encounter it, ensure the podspec does not have `SWIFT_OBJC_BRIDGING_HEADER` in `pod_target_xcconfig`.

### Dependency conflicts with npm install

Always use `yarn install` instead of `npm install` to respect the existing `yarn.lock` file and avoid version conflicts.

---

## Project Structure

```text
react-native-facetec/
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
│   └── src/main/java/com/facetec/
│       ├── FaceTecLivenessViewManager.java
│       ├── FaceTecLivenessPackage.java
│       ├── FaceTecLivenessModule.java
│       ├── RNFaceTecLivenessButton.java
│       ├── Config.java
│       ├── SessionRequestProcessor.java
│       └── SampleAppNetworkingRequest.java
├── package.json
├── react-native-facetec.podspec
└── README.md
```

---

## License

MIT
