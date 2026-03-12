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

## FaceTec SDK Binaries

The FaceTec SDK binary files are **not included in the repository** (listed in `.gitignore`). You must download them from your [FaceTec account](https://dev.facetec.com/account) and place them manually.

### Where to get the SDK

Download the FaceTec SDK from https://dev.facetec.com/downloads. You will get:
- **Android**: `facetec-sdk-X.X.XX.aar` (inside the Android SDK zip)
- **iOS**: `FaceTecSDK.xcframework` or `FaceTecSDKForDevelopment.xcframework` (inside the iOS SDK zip)

### Where to place the files

The **Android AAR** goes inside the module, while the **iOS xcframework** goes in your **host project's** `ios/` directory (not the module):

```text
your-react-native-project/
├── ios/
│   └── FaceTecSDKForDevelopment.xcframework/   ← iOS: place here (host project)
│       ├── Info.plist
│       ├── ios-arm64/
│       └── ios-arm64_x86_64-simulator/
├── native_modules/
│   └── react-native-facetec/
│       └── android/
│           └── libs/
│               └── facetec-sdk-X.X.XX.aar      ← Android: place here (module)
└── ...
```

> **Note:** If the `android/libs/` directory doesn't exist, create it.

> **Note:** For production, replace `FaceTecSDKForDevelopment.xcframework` with `FaceTecSDK.xcframework` and update the framework search paths in the podspec accordingly.

### Updating the SDK version

When updating the FaceTec SDK version:

1. Replace the `.aar` file in `native_modules/react-native-facetec/android/libs/` with the new version
2. Update the AAR filename in `android/build.gradle`:
   ```gradle
   implementation(name: 'facetec-sdk-X.X.XX', ext: 'aar')
   ```
3. Replace the xcframework directory in your host project's `ios/` with the new version
4. Run `cd ios && pod install` to update the iOS framework reference

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

The podspec configures `FRAMEWORK_SEARCH_PATHS` automatically to find the xcframework in your host project's `ios/` directory. If you use a different xcframework name or location, update the paths in `react-native-facetec.podspec`.

### 3. Add camera permission to Info.plist

```xml
<key>NSCameraUsageDescription</key>
<string>Necesitamos acceso a la cámara para validación facial</string>
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

## Configuration & Initialization

FaceTec SDK requires a **Device Key Identifier** and an **API endpoint**. Initialize the SDK from JavaScript using `FaceTec.initialize()` before the button can be used.

### Basic initialization

```typescript
import { FaceTec } from 'react-native-facetec';

await FaceTec.initialize({
  deviceKeyIdentifier: 'your-device-key-identifier',
  apiEndpoint: 'https://api.facetec.com/api/v4/biometrics/process-request',
});
```

### With environment variables (recommended)

Use `react-native-config` to manage configuration per environment:

**1. Add to your `.env` files:**

```bash
FACETEC_DEVICE_KEY=your-device-key-identifier
FACETEC_API_ENDPOINT=https://api.facetec.com/api/v4/biometrics/process-request
```

**2. Initialize in your app entry point (e.g., `App.tsx`):**

```typescript
import { FaceTec } from 'react-native-facetec';
import Env from 'react-native-config';

await FaceTec.initialize({
  deviceKeyIdentifier: Env.FACETEC_DEVICE_KEY,
  apiEndpoint: Env.FACETEC_API_ENDPOINT,
  headers: { 'Authorization': 'Bearer token' }, // optional custom headers
});
```

### FaceTecInitConfig

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `deviceKeyIdentifier` | `string` | Yes | FaceTec Device Key Identifier |
| `apiEndpoint` | `string` | No | API endpoint URL. Falls back to native Config file value |
| `headers` | `Record<string, string>` | No | Custom headers to include in FaceTec API requests |

### Initialization behavior

- **Idempotent**: Calling `initialize()` when already initialized resolves to `true` immediately.
- **Concurrent-safe**: Calling `initialize()` while initialization is in progress rejects with an error.
- **Thread-safe**: Both iOS and Android use synchronized access to SDK state.

### API endpoint fallback

If `apiEndpoint` is not provided, the SDK falls back to the native Config files:

- **iOS**: `ios/FaceTecLiveness/Config.swift`
- **Android**: `android/src/main/java/com/facetec/Config.java`

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

## Usage

```tsx
import React, { useEffect } from 'react';
import { View, Alert } from 'react-native';
import Env from 'react-native-config';
import { FaceTec, Facetec3DLivenessTestButton } from 'react-native-facetec';

const MyComponent = () => {
  useEffect(() => {
    FaceTec.initialize({
      deviceKeyIdentifier: Env.FACETEC_DEVICE_KEY,
      apiEndpoint: Env.FACETEC_API_ENDPOINT,
    }).catch((err) => console.error('FaceTec init failed:', err));
  }, []);

  const handleResponse = (response) => {
    if (response.success && !response.didError && response.result?.livenessProven) {
      Alert.alert('Success', 'Liveness verified');
      console.log('Server info:', response.serverInfo);
    } else if (response.didError) {
      Alert.alert('Error', 'Server error');
    } else {
      Alert.alert('Failed', 'Liveness not verified');
    }
  };

  const handleError = (error) => {
    switch (error.errorType) {
      case 'permission_denied':
        Alert.alert('Permission denied', 'Camera access is required');
        break;
      case 'session_cancelled':
        console.log('User cancelled the session');
        break;
      case 'init_error':
        Alert.alert('Error', 'SDK initialization failed');
        break;
      case 'device_not_supported':
        Alert.alert('Error', 'Device not supported');
        break;
      case 'network_error':
        Alert.alert('Network error', error.message);
        break;
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
      <Facetec3DLivenessTestButton
        onResponse={handleResponse}
        onError={handleError}
        style={{ width: 250, height: 50, backgroundColor: '#007AFF' }}
        initializingText="Initializing"
        readyText="Start liveness check"
        errorText="Initialization error"
        permissionDeniedText="Camera permission denied"
        errorStyle={{ backgroundColor: '#FF3B30' }}
        initializingStyle={{ backgroundColor: '#808080' }}
      />
    </View>
  );
};

export default MyComponent;
```

> **Note:** The button stays in "initializing" state until `FaceTec.initialize()` completes. You can mount the button and call `initialize()` in any order.

---

## FaceTec API

The `FaceTec` object provides imperative methods for SDK lifecycle management:

```typescript
import { FaceTec } from 'react-native-facetec';
```

| Method | Returns | Description |
|--------|---------|-------------|
| `FaceTec.initialize(config)` | `Promise<boolean>` | Initialize the SDK. Resolves `true` on success |
| `FaceTec.isInitialized()` | `Promise<boolean>` | Check if the SDK is initialized |
| `FaceTec.getSDKVersion()` | `Promise<string>` | Get the FaceTec SDK version string |

---

## FaceTecErrorType

The `FaceTecErrorType` enum provides typed error constants for use in error handlers:

```typescript
import { FaceTecErrorType } from 'react-native-facetec';

// Use in error handling
if (error.errorType === FaceTecErrorType.PERMISSION_DENIED) {
  // handle permission denied
}
```

| Value | String | Description |
|-------|--------|-------------|
| `INIT_ERROR` | `'init_error'` | SDK initialization failed |
| `PERMISSION_DENIED` | `'permission_denied'` | Camera permission was denied |
| `DEVICE_NOT_SUPPORTED` | `'device_not_supported'` | Device doesn't support FaceTec |
| `SESSION_CANCELLED` | `'session_cancelled'` | User cancelled the session |
| `NETWORK_ERROR` | `'network_error'` | Network error or timeout |
| `INTERNAL_ERROR` | `'internal_error'` | Internal error (e.g., no parent view controller) |

---

## Button Props

| Prop | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `onResponse` | `(response: LivenessResponse) => void` | Yes | - | Callback when liveness check completes with server response |
| `onError` | `(error: ErrorEvent) => void` | No | - | Callback for errors without server response (permission denied, cancelled, etc.) |
| `onStateChange` | `(state: ButtonState) => void` | No | - | Callback when button state changes (`'initializing'`, `'ready'`, `'error'`) |
| `style` | `ViewStyle` | No | - | Custom styles for the button (including backgroundColor for ready state) |
| `initializingText` | `string` | No | `"Initializing"` | Text shown while SDK initializes |
| `readyText` | `string` | No | `"Start liveness check"` | Text shown when ready |
| `errorText` | `string` | No | `"Initialization error"` | Text shown on init error |
| `permissionDeniedText` | `string` | No | `"Camera permission denied"` | Text shown when camera permission is denied |
| `errorStyle` | `ViewStyle` | No | - | Custom styles for error state (only backgroundColor is applied) |
| `initializingStyle` | `ViewStyle` | No | Falls back to `style` | Custom styles for initializing state (only backgroundColor is applied) |

---

## Error Event

The error event is emitted when an error occurs without a server response:

```typescript
interface ErrorEvent {
  /** Type of error that occurred */
  errorType: ErrorType;
  /** Descriptive error message */
  message: string;
}

type ErrorType =
  | 'permission_denied'      // Camera permission was denied
  | 'init_error'             // SDK initialization failed
  | 'device_not_supported'   // Device doesn't support FaceTec
  | 'session_cancelled'      // User cancelled the session
  | 'network_error'          // Network error or timeout
  | 'internal_error';        // Internal error (e.g., no parent view controller)
```

### Error Handling

```typescript
import { FaceTecErrorType } from 'react-native-facetec';

const handleError = (error: ErrorEvent) => {
  switch (error.errorType) {
    case FaceTecErrorType.PERMISSION_DENIED:
      // Prompt user to enable camera permission
      break;
    case FaceTecErrorType.SESSION_CANCELLED:
      // User cancelled - usually no action needed
      break;
    case FaceTecErrorType.INIT_ERROR:
      // SDK failed to initialize - check configuration
      break;
    case FaceTecErrorType.DEVICE_NOT_SUPPORTED:
      // Device is not compatible with FaceTec
      break;
    case FaceTecErrorType.NETWORK_ERROR:
      // Network issue - prompt retry
      break;
    case FaceTecErrorType.INTERNAL_ERROR:
      // Unexpected error
      break;
  }
};
```

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
  // Liveness not proven
  else {
    console.log('Liveness not verified');
  }
};

// Note: Cases without server response (network error, user cancelled, permission denied)
// are now handled via the onError callback instead of onResponse
```

---

## Button States

| State | Text | Color | Enabled |
|-------|------|-------|---------|
| Initializing | "Initializing" | Gray (or `initializingStyle.backgroundColor`, or `style.backgroundColor`) | No |
| Ready | "Start liveness check" | Controlled via `style` prop | Yes |
| Error | "Initialization error" | Red (or `errorStyle.backgroundColor`) | No |
| Permission Denied | "Camera permission denied" | Red (or `errorStyle.backgroundColor`) | No |

---

## Troubleshooting

### Error: "FaceTecLivenessButton was not found in UIManager"

Ensure you have:
1. Added the pod to Podfile and run `pod install`
2. Rebuilt the app (not just hot reload)

### Error: "Native module FaceTecLivenessModule tried to override FaceTecLivenessModule"

This happens when the module is registered twice. React Native autolinking handles module registration automatically, so **do NOT manually register** `FaceTecLivenessPackage` in `MainApplication.kt` or `MainApplication.java`. If you added it manually, remove it.

### Error: "Could not find :facetec-sdk-X.X.X"

Make sure you:
1. Placed the `.aar` file in `android/libs/`
2. Added the `flatDir` repository configuration in `android/app/build.gradle` (see step 3 of Android Setup)

### Error: `init_error` with message `REQUEST_ABORTED`

The `DeviceKeyIdentifier` is empty or invalid. Make sure you passed a valid `deviceKeyIdentifier` to `FaceTec.initialize()`.

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
│   ├── FaceTecModule.ts                  ← Imperative JS API (FaceTec.initialize, etc.)
│   └── Facetec3DLivenessTestButton.tsx
├── ios/
│   └── FaceTecLiveness/
│       ├── FaceTecModule.swift            ← Native module (initialization logic)
│       ├── FaceTecModule.m                ← Obj-C bridge
│       ├── FaceTecLivenessViewManager.swift
│       ├── FaceTecLivenessViewManager.m
│       ├── Config.swift
│       ├── SessionRequestProcessor.swift
│       └── SampleAppNetworkingRequest.swift
├── android/
│   ├── libs/
│   │   └── facetec-sdk-X.X.XX.aar        ← Not in git, add manually
│   └── src/main/java/com/facetec/
│       ├── FaceTecLivenessModule.java     ← Native module (initialization logic)
│       ├── FaceTecLivenessViewManager.java
│       ├── FaceTecLivenessPackage.java
│       ├── RNFaceTecLivenessButton.java
│       ├── Config.java
│       ├── SessionRequestProcessor.java
│       └── SampleAppNetworkingRequest.java
├── package.json
├── react-native-facetec.podspec
└── README.md
```

> **Note:** The iOS xcframework is placed in the **host project's** `ios/` directory, not inside this module. See [FaceTec SDK Binaries](#facetec-sdk-binaries) for details.

---

## License

MIT
