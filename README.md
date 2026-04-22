# react-native-facetec

React Native native module for FaceTec 3D Liveness verification.

Provides `FaceTec.initialize()` and `FaceTec.startLivenessCheck()` as Promise-based APIs. Camera permissions are **not** handled by the native module — they must be requested from JavaScript before calling these methods.

## Installation (Local Module)

### Step 1: Copy the module

```text
your-react-native-project/
├── native_modules/
│   └── react-native-facetec/   # Copy here
├── android/
├── ios/
└── package.json
```

### Step 2: Add dependency to package.json

```json
{
  "dependencies": {
    "react-native-facetec": "file:./native_modules/react-native-facetec"
  }
}
```

### Step 3: Install dependencies

**IMPORTANT:** Use `yarn install` (not `npm install`) to respect the existing lock file.

```bash
yarn install
```

---

## FaceTec SDK Binaries

The FaceTec SDK binary files are **not included in the repository** (listed in `.gitignore`). Download them from your [FaceTec account](https://dev.facetec.com/downloads).

### Where to place the files

```text
your-react-native-project/
├── android/
│   └── libs/
│       └── facetec-sdk-X.X.XX.aar              ← Android SDK (single AAR, dev/prod via device key)
├── ios/
│   ├── FaceTecSDKForDevelopment.xcframework/    ← iOS SDK (development + simulator)
│   │   ├── Info.plist
│   │   ├── ios-arm64/
│   │   └── ios-arm64_x86_64-simulator/
│   └── FaceTecSDK.xcframework/                  ← iOS SDK (production, device only)
│       ├── Info.plist
│       └── ios-arm64/
├── native_modules/
│   └── react-native-facetec/                    ← Module code (no binaries)
└── ...
```

> **Note:** If the `android/libs/` directory doesn't exist, create it.

> **iOS (Xcode):** After placing each `.xcframework` in `ios/`, add them to the **Frameworks** group in the Xcode project navigator. The Podfile `post_install` hook handles linking the correct one per build configuration.

### Platform differences

- **Android**: A single AAR is used for all environments. Dev vs production mode is determined by the `deviceKeyIdentifier` passed at initialization (via `FACETEC_DEVICE_KEY` env var).
- **iOS**: FaceTec provides separate xcframeworks for development and production. The Podfile automatically selects the correct one per build configuration:

| Build configuration | xcframework used |
| ------------------- | ---------------- |
| `Prod.Release` | `FaceTecSDK.xcframework` (production) |
| All others (`Debug`, `QA.*`, `Next.*`, etc.) | `FaceTecSDKForDevelopment.xcframework` |
| Simulator (any config) | Always `FaceTecSDKForDevelopment.xcframework` (production SDK has no simulator slice) |

### Updating the SDK version

1. Replace the `.aar` file in `android/libs/` with the new version
2. Update the AAR filename in `native_modules/react-native-facetec/android/build.gradle`
3. Replace both `.xcframework` directories in `ios/` with the new versions
4. Run `cd ios && pod install`

---

## iOS Setup

### 1. Add pod to Podfile

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
<string>Necesitamos acceso a la cámara para validación facial</string>
```

---

## Android Setup

### 1. Add module to settings.gradle

```gradle
include ':react-native-facetec'
project(':react-native-facetec').projectDir = new File(rootProject.projectDir, '../native_modules/react-native-facetec/android')
```

### 2. Add dependency to app/build.gradle

```gradle
dependencies {
    implementation project(':react-native-facetec')
}
```

### 3. Add flatDir repository for FaceTec SDK AAR

In `android/app/build.gradle`, add at the end:

```gradle
repositories {
    flatDir {
        dirs '../libs'
    }
}
```

### 4. Camera permissions (AndroidManifest.xml)

Already included in the module manifest, but ensure your app has:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Environment Variables

FaceTec credentials must be configured via environment variables (using `react-native-config`), **never hardcoded**:

```bash
# .env.dev / .env.qa / .env.production
FACETEC_DEVICE_KEY=your-device-key-identifier
FACETEC_API_ENDPOINT=https://your-api.com/facetec/process-request
```

Declare the types in `env.d.ts`:

```typescript
declare module 'react-native-config' {
  export interface NativeConfig {
    FACETEC_DEVICE_KEY: string;
    FACETEC_API_ENDPOINT: string;
  }
}
```

---

## Usage

The module exposes a `FaceTec` object with Promise-based methods. Camera permissions must be handled in JavaScript before calling `initialize()` or `startLivenessCheck()`.

### Basic flow

```typescript
import { FaceTec } from 'react-native-facetec';
import { Camera } from 'react-native-vision-camera';
import Config from 'react-native-config';

const handleLivenessCheck = async () => {
  // 1. Request camera permission (not handled by native module)
  const permission = await Camera.requestCameraPermission();
  if (permission !== 'granted') {
    // Handle denied permission (navigate to settings screen, etc.)
    return;
  }

  // 2. Initialize the SDK (safe to call multiple times — reuses instance if config unchanged)
  await FaceTec.initialize({
    deviceKeyIdentifier: Config.FACETEC_DEVICE_KEY,
    apiEndpoint: Config.FACETEC_API_ENDPOINT,
    headers: { Authorization: 'Bearer token' }, // optional
  });

  // 3. Start liveness check
  const response = await FaceTec.startLivenessCheck({
    frameColor: '#ffffff',
    readyScreenHeaderText: 'Place your face in the frame',
    actionButtonText: 'I AM READY',
  });

  if (response.result?.livenessProven) {
    // Verification successful
  }
};
```

### Using F3DLivenessButton (app atom)

The project provides an `F3DLivenessButton` atom in `app/atoms/Facetec/` that wraps the full flow (permission request → initialize → liveness check):

```tsx
import { F3DLivenessButton } from '@atoms';

<F3DLivenessButton
  initConfig={{
    deviceKeyIdentifier: Config.FACETEC_DEVICE_KEY,
    apiEndpoint: Config.FACETEC_API_ENDPOINT,
  }}
  onResponse={(response) => {
    if (response.result?.livenessProven) {
      // Navigate to next screen
    }
  }}
  onError={(error) => {
    if (error.errorType === 'permission_denied') {
      navigation.navigate('CameraPermission');
    }
  }}
  customization={{
    frameColor: '#ffffff',
    actionButtonText: 'ESTOY LISTO',
  }}>
  Verify Identity
</F3DLivenessButton>
```

The button handles:
1. Requests camera permission via `Camera.requestCameraPermission()` (VisionCamera)
2. Shows a loader while initializing and running the session
3. Merges custom props with `defaultCustomization` from `constants.ts`
4. Emits `permission_denied` to `onError` if camera access is denied

---

## API Reference

### `FaceTec.initialize(config)`

Initializes the FaceTec SDK. Safe to call multiple times — returns immediately if already initialized with the same config. If called while a previous initialization is in progress, the previous one is cancelled and its promise is rejected with `init_cancelled`.

```typescript
interface FaceTecInitConfig {
  /** FaceTec Device Key Identifier (required) */
  deviceKeyIdentifier: string;
  /** API endpoint URL (optional, falls back to native Config value) */
  apiEndpoint?: string;
  /** Custom headers for API requests (optional) */
  headers?: Record<string, string>;
}
```

**Returns:** `Promise<boolean>` — resolves `true` on success.

### `FaceTec.isInitialized()`

**Returns:** `Promise<boolean>`

### `FaceTec.getInitializationStatus()`

**Returns:** `Promise<{ status: 'idle' | 'initializing' | 'initialized' | 'error', error?: string }>`

### `FaceTec.getSDKVersion()`

**Returns:** `Promise<string>`

### `FaceTec.startLivenessCheck(customization?)`

Starts a liveness session. Applies customization and launches the FaceTec UI.

- **Resolves** with `LivenessResponse` on server response
- **Rejects** with `{ code: ErrorType, message: string }` on failure

---

## Customization

The `customization` argument controls all visual aspects of the FaceTec SDK UI. All fields are optional — omitted fields fall back to native Config defaults.

### Frame & Overlay

| Property               | Type     | Description                 |
| ---------------------- | -------- | --------------------------- |
| `outerBackgroundColor` | `string` | Background behind the frame |
| `frameColor`           | `string` | Frame background color      |
| `borderColor`          | `string` | Frame border color          |

### Oval

| Property           | Type     | Description                 |
| ------------------ | -------- | --------------------------- |
| `ovalColor`        | `string` | Oval stroke color           |
| `dualSpinnerColor` | `string` | Oval spinner/progress color |

### Ready Screen

| Property                   | Type                | Description                                  |
| -------------------------- | ------------------- | -------------------------------------------- |
| `readyScreenHeaderText`    | `string`            | Header text (supports `\n` for line breaks)  |
| `readyScreenHeaderStyles`  | `FaceTecTextStyles` | `{ color?, fontFamily? }`                    |
| `readyScreenSubtext`       | `string`            | Subtext below the oval. Set to `' '` to hide |
| `readyScreenSubtextStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }`                    |

### Action Button

| Property             | Type                  | Description   |
| -------------------- | --------------------- | ------------- |
| `actionButtonText`   | `string`              | Button label  |
| `actionButtonStyles` | `FaceTecButtonStyles` | See below     |

```typescript
interface FaceTecButtonStyles {
  fontFamily?: string;
  textColor?: string;
  backgroundColor?: string;
  highlightBackgroundColor?: string;
  disabledBackgroundColor?: string;
  cornerRadius?: number;
}
```

### Retry Screen

| Property                   | Type                | Description                 |
| -------------------------- | ------------------- | --------------------------- |
| `retryScreenHeaderText`    | `string`            | Header text on retry screen |
| `retryScreenHeaderStyles`  | `FaceTecTextStyles` | `{ color?, fontFamily? }`   |
| `retryScreenSubtext`       | `string`            | Subtext on retry screen     |
| `retryScreenSubtextStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }`   |

### Feedback Bar

| Property            | Type                       | Description |
| ------------------- | -------------------------- | ----------- |
| `feedbackBarStyles` | `FaceTecFeedbackBarStyles` | See below   |

```typescript
interface FaceTecFeedbackBarStyles {
  fontFamily?: string;
  textColor?: string;
  backgroundColor?: string;
  cornerRadius?: number;
}
```

### Feedback Texts

Override the texts shown during the liveness scan. All fields are optional.

```typescript
interface FaceTecFeedbackTexts {
  moveCloser?: string;
  moveAway?: string;
  centerFace?: string;
  faceNotFound?: string;
  holdSteady?: string;
  faceNotUpright?: string;
  faceNotLookingStraight?: string;
  useEvenLighting?: string;
  moveToEyeLevel?: string;
  presessionFrameYourFace?: string;
  presessionLookStraight?: string;
  presessionNeutralExpression?: string;
  presessionRemoveDarkGlasses?: string;
  presessionConditionsTooBright?: string;
  presessionBrightenEnvironment?: string;
}
```

### Result Screen

| Property               | Type                      | Description                         |
| ---------------------- | ------------------------- | ----------------------------------- |
| `resultMessageStyles`  | `FaceTecTextStyles`       | `{ color?, fontFamily? }`           |
| `resultScreenTexts`    | `FaceTecResultScreenTexts`| Upload/success message overrides    |

```typescript
interface FaceTecResultScreenTexts {
  uploadMessage?: string;
  uploadMessageStillUploading?: string;
  successMessage?: string;
  successEnrollmentMessage?: string;
  successReverificationMessage?: string;
  successLivenessAndIdMessage?: string;
}
```

### Font notes

- **iOS**: Use the PostScript name (e.g., `Montserrat-SemiBold`). Must be in `Info.plist` under `UIAppFonts`.
- **Android**: Use the font file name without extension (e.g., `Montserrat-SemiBold`). Must be in `assets/fonts/`. Uses `ReactFontManager` to resolve.
- **Color format**: Hex strings — `#RRGGBB` or `#RRGGBBAA`.

---

## Error Types

Errors are returned as promise rejections with `{ code: ErrorType, message: string }`.

```typescript
type ErrorType =
  | 'permission_denied'   // Camera permission denied (emitted by F3DLivenessButton)
  | 'init_error'          // SDK initialization failed
  | 'init_cancelled'      // Initialization cancelled by a newer initialize() call
  | 'device_not_supported'// Device not supported by FaceTec
  | 'session_cancelled'   // User cancelled the liveness session
  | 'network_error'       // Network error or session timeout
  | 'internal_error';     // Unexpected internal error
```

> **Note:** `permission_denied` is emitted by the `F3DLivenessButton` atom in JavaScript, not by the native module. The native module does not check or request camera permissions.

---

## Response Object

```typescript
interface LivenessResponse {
  success?: boolean;
  didError?: boolean;
  responseBlob?: string;
  result?: {
    livenessProven?: boolean;
    ageV2GroupEnumInt?: number;
  };
  serverInfo?: {
    coreServerSDKVersion?: string;
    mode?: string;
    notice?: string;
  };
  additionalSessionData?: {
    appID?: string;
    deviceModel?: string;
    deviceSDKVersion?: string;
    installationID?: string;
    platform?: string;
  };
  httpCallInfo?: {
    date?: string;
    epochSecond?: number;
    path?: string;
    requestMethod?: string;
    tid?: string;
  };
}
```

---

## Troubleshooting

### Error: "Native module FaceTecLivenessModule tried to override FaceTecLivenessModule"

React Native autolinking handles registration. **Do NOT** manually register `FaceTecLivenessPackage` in `MainApplication`.

### Error: "Could not find :facetec-sdk-X.X.X"

1. Verify the `.aar` is in `android/libs/`
2. Verify `flatDir` is configured in `android/app/build.gradle`

### Error: `init_error` with message `REQUEST_ABORTED`

The `deviceKeyIdentifier` is empty or invalid. Check your environment variable `FACETEC_DEVICE_KEY`.

### Initialization hangs / times out

The API endpoint may be unreachable from the device. The OkHttp client has 120s timeouts with 2 retries (~6 min total). Verify network connectivity to the configured `FACETEC_API_ENDPOINT`.

### Custom font not applying

- **iOS**: Verify the font is in `Info.plist` under `UIAppFonts`. Use the PostScript name.
- **Android**: Verify the `.ttf` is in `assets/fonts/`. Use the file name without extension.

### Dependency conflicts with npm install

Always use `yarn install` to respect `yarn.lock`.

---

## Project Structure

```text
# Project root
your-react-native-project/
├── android/
│   └── libs/
│       └── facetec-sdk-X.X.XX.aar          ← SDK binary (not in git)
├── ios/
│   └── FaceTecSDKForDevelopment.xcframework/ ← SDK binary (not in git)
│
# Native module
├── native_modules/
│   └── react-native-facetec/
│       ├── src/
│       │   ├── index.ts                     ← Public exports
│       │   └── FaceTecModule.ts             ← TS types and FaceTec API
│       ├── ios/
│       │   └── FaceTecLiveness/
│       │       ├── FaceTecModule.swift       ← Init + startLivenessCheck
│       │       ├── FaceTecModule.m           ← ObjC bridge
│       │       ├── Config.swift
│       │       ├── SessionRequestProcessor.swift
│       │       └── SampleAppNetworkingRequest.swift
│       ├── android/
│       │   └── src/main/java/com/facetec/
│       │       ├── FaceTecLivenessModule.java  ← Init + startLivenessCheck
│       │       ├── FaceTecLivenessPackage.java
│       │       ├── Config.java
│       │       ├── SessionRequestProcessor.java
│       │       ├── SampleAppNetworkingRequest.java
│       │       ├── SampleAppNetworkingLibExample.java
│       │       └── FaceTecServerResponse.java
│       ├── package.json
│       ├── react-native-facetec.podspec
│       └── README.md
│
# App-level integration
├── app/
│   └── atoms/Facetec/
│       ├── F3DLivenessButton.tsx             ← Button atom (permission + init + session)
│       ├── constants.ts                      ← Default customization + initConfig from env
│       ├── styles.tsx
│       └── index.tsx
```
