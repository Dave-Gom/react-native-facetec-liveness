# react-native-facetec

React Native native module for [FaceTec](https://facetec.com) 3D Liveness verification.

Provides `FaceTec.initialize()` and `FaceTec.startLivenessCheck()` as Promise-based APIs with full TypeScript support and extensive UI customization.

## Requirements

- React Native 0.60+
- iOS 13.0+
- Android minSdkVersion 21+
- A [FaceTec account](https://dev.facetec.com/account) with valid Device Key Identifier
- FaceTec SDK binaries (not included — see [FaceTec SDK Setup](#facetec-sdk-binaries))

## Installation

```bash
npm install react-native-facetec
# or
yarn add react-native-facetec
```

### iOS

```bash
cd ios && pod install
```

Add camera permission to your `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Camera access is required for face verification</string>
```

### Android

Add the FaceTec AAR flatDir repository to `android/app/build.gradle`:

```gradle
repositories {
    flatDir {
        dirs '../libs'
    }
}
```

Ensure your `AndroidManifest.xml` includes:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

## FaceTec SDK Binaries

The FaceTec SDK binaries are **not included** in this package due to licensing. Download them from your [FaceTec account](https://dev.facetec.com/downloads) and place them in your project:

```text
your-project/
├── android/
│   └── libs/
│       └── facetec-sdk-X.X.XX.aar
├── ios/
│   ├── FaceTecSDKForDevelopment.xcframework/   # Development + simulator
│   └── FaceTecSDK.xcframework/                 # Production (device only)
```

> **iOS (Xcode):** After placing the `.xcframework` directories in `ios/`, add them to the **Frameworks** group in Xcode. Use a Podfile `post_install` hook to select the correct framework per build configuration.

> **Android:** A single AAR works for all environments. Dev vs production mode is determined by the `deviceKeyIdentifier` passed during initialization.

## Usage

Camera permissions are **not** handled by this module — request them from JavaScript before calling any methods.

```typescript
import { FaceTec } from 'react-native-facetec';
import { PermissionsAndroid, Platform } from 'react-native';

async function requestCameraPermission(): Promise<boolean> {
  if (Platform.OS === 'android') {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CAMERA
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }
  // iOS: handled via Info.plist prompt on first camera access,
  // or use a library like react-native-permissions
  return true;
}

async function handleLivenessCheck() {
  // 1. Request camera permission
  const hasPermission = await requestCameraPermission();
  if (!hasPermission) return;

  // 2. Initialize the SDK (reuses instance if config is unchanged)
  await FaceTec.initialize({
    deviceKeyIdentifier: 'your-device-key',
    apiEndpoint: 'https://your-api.com/facetec/process-request',
    headers: { Authorization: 'Bearer token' }, // optional
  });

  // 3. Start liveness check
  const response = await FaceTec.startLivenessCheck({
    frameColor: '#ffffff',
    actionButtonText: 'I AM READY',
  });

  if (response.result?.livenessProven) {
    // Verification successful
  }
}
```

## API Reference

### `FaceTec.initialize(config)`

Initializes the FaceTec SDK. Safe to call multiple times — returns immediately if already initialized with the same config. If called while a previous initialization is in progress, the previous one is cancelled and its promise is rejected with `init_cancelled`.

```typescript
interface FaceTecInitConfig {
  deviceKeyIdentifier: string;
  apiEndpoint?: string;
  headers?: Record<string, string>;
}
```

Returns `Promise<boolean>` — resolves `true` on success.

### `FaceTec.isInitialized()`

Returns `Promise<boolean>`.

### `FaceTec.getInitializationStatus()`

Returns `Promise<InitializationStatus>`:

```typescript
interface InitializationStatus {
  status: 'idle' | 'initializing' | 'initialized' | 'error';
  error?: string;
}
```

### `FaceTec.getSDKVersion()`

Returns `Promise<string>`.

### `FaceTec.startLivenessCheck(customization?)`

Starts a 3D liveness session with optional UI customization.

- **Resolves** with `LivenessResponse` on server response
- **Rejects** with `{ code: ErrorType, message: string }` on failure

## Customization

Pass a `FaceTecCustomization` object to `startLivenessCheck()` to control the SDK UI. All fields are optional — omitted fields use native defaults.

### Frame & Overlay

| Property | Type | Description |
| --- | --- | --- |
| `outerBackgroundColor` | `string` | Background behind the frame |
| `frameColor` | `string` | Frame background color |
| `borderColor` | `string` | Frame border color |

### Oval

| Property | Type | Description |
| --- | --- | --- |
| `ovalColor` | `string` | Oval stroke color |
| `dualSpinnerColor` | `string` | Oval spinner/progress color |

### Ready Screen

| Property | Type | Description |
| --- | --- | --- |
| `readyScreenHeaderText` | `string` | Header text (supports `\n`) |
| `readyScreenHeaderStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }` |
| `readyScreenSubtext` | `string` | Subtext below the oval |
| `readyScreenSubtextStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }` |

### Action Button

| Property | Type | Description |
| --- | --- | --- |
| `actionButtonText` | `string` | Button label |
| `actionButtonStyles` | `FaceTecButtonStyles` | See below |

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

| Property | Type | Description |
| --- | --- | --- |
| `retryScreenHeaderText` | `string` | Header on retry screen |
| `retryScreenHeaderStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }` |
| `retryScreenSubtext` | `string` | Subtext on retry screen |
| `retryScreenSubtextStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }` |

### Feedback Bar

```typescript
interface FaceTecFeedbackBarStyles {
  fontFamily?: string;
  textColor?: string;
  backgroundColor?: string;
  cornerRadius?: number;
}
```

### Feedback Texts

Override texts shown during the liveness scan:

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

### Result Screen Texts

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

### Fonts

- **iOS**: Use the PostScript name (e.g., `Montserrat-SemiBold`). Must be listed in `Info.plist` under `UIAppFonts`.
- **Android**: Use the font file name without extension (e.g., `Montserrat-SemiBold`). Must be in `assets/fonts/`.
- **Colors**: Hex strings — `#RRGGBB` or `#RRGGBBAA`.

## Error Handling

Errors are returned as promise rejections with `{ code: ErrorType, message: string }`.

```typescript
import { FaceTec, FaceTecErrorType } from 'react-native-facetec';

try {
  const response = await FaceTec.startLivenessCheck();
} catch (error) {
  switch (error.code) {
    case FaceTecErrorType.SESSION_CANCELLED:
      // User dismissed the session
      break;
    case FaceTecErrorType.NETWORK_ERROR:
      // Network issue — retry or show message
      break;
    case FaceTecErrorType.INIT_ERROR:
      // SDK failed to initialize
      break;
    default:
      // Handle other errors
      break;
  }
}
```

### Error Types

| Code | Description |
| --- | --- |
| `init_error` | SDK initialization failed |
| `init_cancelled` | Initialization cancelled by a newer `initialize()` call |
| `permission_denied` | Operation denied due to missing permissions |
| `camera_permission` | Camera permission not granted |
| `device_not_supported` | Device is not supported by FaceTec |
| `session_cancelled` | User cancelled the liveness session |
| `network_error` | Network error or session timeout |
| `internal_error` | Unexpected internal error |

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

## Troubleshooting

### "Native module FaceTecLivenessModule tried to override FaceTecLivenessModule"

React Native autolinking handles registration. Do **not** manually register `FaceTecLivenessPackage` in `MainApplication`.

### "Could not find :facetec-sdk-X.X.X"

1. Verify the `.aar` is in `android/libs/`
2. Verify `flatDir` is configured in `android/app/build.gradle`

### `init_error` with message `REQUEST_ABORTED`

The `deviceKeyIdentifier` is empty or invalid. Verify your device key from the [FaceTec dashboard](https://dev.facetec.com/account).

### Initialization hangs or times out

The API endpoint may be unreachable. The HTTP client uses 120s timeouts with 2 retries (~6 min total). Verify network connectivity to your configured endpoint.

### Custom font not applying

- **iOS**: Verify the font is listed in `Info.plist` under `UIAppFonts`. Use the PostScript name.
- **Android**: Verify the `.ttf`/`.otf` file is in `assets/fonts/`. Use the file name without extension.

## License

MIT
