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
- **iOS**: `FaceTecSDKForDevelopment.xcframework` (inside the iOS SDK zip)

### Where to place the files

The SDK binaries live in the **project root's platform directories**, not inside the native module:

```text
your-react-native-project/
├── android/
│   └── libs/
│       └── facetec-sdk-X.X.XX.aar          ← Place the .aar here
├── ios/
│   └── FaceTecSDKForDevelopment.xcframework/ ← Place the .xcframework here
│       ├── Info.plist
│       ├── ios-arm64/
│       └── ios-arm64_x86_64-simulator/
├── native_modules/
│   └── react-native-facetec/                ← Module code (no binaries)
└── ...
```

> **Note:** If the `android/libs/` directory doesn't exist, create it.

> **iOS (Xcode):** After placing the `.xcframework` in `ios/`, add it to the **Frameworks** group in the Xcode project navigator for organization. The podspec already configures the framework search paths automatically.

### Updating the SDK version

When updating the FaceTec SDK version:

1. Replace the `.aar` file in `android/libs/` with the new version
2. Update the AAR filename in `native_modules/react-native-facetec/android/build.gradle`:
   ```gradle
   implementation(name: 'facetec-sdk-X.X.XX', ext: 'aar')
   ```
3. Replace the `ios/FaceTecSDKForDevelopment.xcframework/` directory with the new version
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

### 3. Add xcframework to Xcode Frameworks group

In Xcode, drag the `FaceTecSDKForDevelopment.xcframework` folder from `ios/` into the **Frameworks** group in the project navigator. This is for project organization only — the podspec handles the actual linking.

### 4. Add camera permission to Info.plist

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
    // FaceTec SDK AAR – located in android/libs/
    flatDir {
        dirs '../libs'
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

The module has two parts: **initialization** (once at app startup) and the **button component** (in your screen).

### 1. Initialize the SDK

Call `FaceTec.initialize()` once, typically in a top-level `useEffect`:

```typescript
import { FaceTec } from "react-native-facetec";

useEffect(() => {
  FaceTec.initialize({
    deviceKeyIdentifier: "your-device-key",
    apiEndpoint: "https://your-api.com/facetec/process-request",
    headers: { Authorization: "Bearer token" }, // optional
  });
}, []);
```

The button component will stay in "initializing" state until the SDK is ready, so initialization and mounting order doesn't matter.

### 2. Mount the button

```tsx
import { Facetec3DLivenessTestButton } from "react-native-facetec";

<Facetec3DLivenessTestButton
  onResponse={(response) => {
    if (response.success && response.result?.livenessProven) {
      console.log("Liveness verified!");
    }
  }}
  onError={(error) => {
    console.log("Error:", error.errorType, error.message);
  }}
  style={{ width: 250, height: 50, backgroundColor: "#007AFF" }}
  errorStyle={{ backgroundColor: "#FF3B30" }}
  initializingStyle={{ backgroundColor: "#808080" }}
/>;
```

### 3. Full example with customization

```tsx
import React, { useEffect } from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { FaceTec, Facetec3DLivenessTestButton } from "react-native-facetec";

const LivenessScreen = () => {
  const { t } = useTranslation();

  useEffect(() => {
    FaceTec.initialize({
      deviceKeyIdentifier: "your-device-key",
      apiEndpoint: "https://your-api.com/facetec/process-request",
    });
  }, []);

  return (
    <View style={{ flex: 1, justifyContent: "center", alignItems: "center" }}>
      <Facetec3DLivenessTestButton
        onResponse={(response) => {
          if (response.success && response.result?.livenessProven) {
            // Navigate to next screen
          }
        }}
        onError={(error) => console.log(error.errorType)}
        style={{ width: 280, height: 50, backgroundColor: "#0066CC" }}
        readyText={t("facetec.startButton")}
        initializingText={t("facetec.initializing")}
        errorText={t("facetec.error")}
        permissionDeniedText={t("facetec.permissionDenied")}
        customization={{
          // Frame & Overlay
          frameColor: "#ffffff",
          borderColor: "#ffffff",
          outerBackgroundColor: "#000000",

          // Oval
          ovalColor: "#519dbd",
          dualSpinnerColor: "#519dbd",

          // Ready Screen Header
          readyScreenHeaderText: t("facetec.readyHeader"),
          readyScreenHeaderStyles: {
            color: "#1a2332",
            fontFamily: "Montserrat-Bold",
          },

          // Ready Screen Subtext (empty string hides it)
          readyScreenSubtext: "",

          // Action Button ("I'M READY")
          actionButtonText: t("facetec.imReady"),
          actionButtonStyles: {
            backgroundColor: "#0066CC",
            textColor: "#ffffff",
            highlightBackgroundColor: "#004499",
            disabledBackgroundColor: "#99ccff",
            cornerRadius: 30,
            fontFamily: "Montserrat-SemiBold",
          },

          // Retry Screen
          retryScreenHeaderText: t("facetec.retryHeader"),
          retryScreenHeaderStyles: {
            color: "#1a2332",
            fontFamily: "Montserrat-Bold",
          },
          retryScreenSubtext: t("facetec.retrySubtext"),
          retryScreenSubtextStyles: {
            color: "#666666",
            fontFamily: "Montserrat-Regular",
          },

          // Feedback Bar
          feedbackBarStyles: {
            backgroundColor: "#000000b0",
            textColor: "#ffffff",
            cornerRadius: 16,
            fontFamily: "Montserrat-Medium",
          },

          // Feedback Texts (all optional)
          feedbackTexts: {
            moveCloser: t("facetec.feedback.moveCloser"),
            holdSteady: t("facetec.feedback.holdSteady"),
            centerFace: t("facetec.feedback.centerFace"),
            presessionFrameYourFace: t("facetec.feedback.frameYourFace"),
          },

          // Result Screen
          resultMessageStyles: {
            color: "#1a2332",
            fontFamily: "Montserrat-Regular",
          },
        }}
      />
    </View>
  );
};
```

---

## Initialization API

### `FaceTec.initialize(config)`

Initializes the FaceTec SDK. Safe to call multiple times — returns immediately if already initialized.

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

### `FaceTec.isInitialized()`

Returns `Promise<boolean>` — useful for retry logic.

### `FaceTec.getSDKVersion()`

Returns `Promise<string>` with the SDK version.

---

## Button Props

| Prop                   | Type                                   | Required | Default                      | Description                                           |
| ---------------------- | -------------------------------------- | -------- | ---------------------------- | ----------------------------------------------------- |
| `onResponse`           | `(response: LivenessResponse) => void` | Yes      | —                            | Callback when liveness completes with server response |
| `onError`              | `(error: ErrorEvent) => void`          | No       | —                            | Callback for errors without server response           |
| `style`                | `ViewStyle`                            | No       | —                            | Base button styles                                    |
| `errorStyle`           | `ViewStyle`                            | No       | —                            | Additional styles for error state                     |
| `initializingStyle`    | `ViewStyle`                            | No       | —                            | Additional styles for initializing state              |
| `initializingText`     | `string`                               | No       | `"Initializing"`             | Text while SDK initializes                            |
| `readyText`            | `string`                               | No       | `"Start liveness check"`     | Text when ready                                       |
| `errorText`            | `string`                               | No       | `"Initialization error"`     | Text on init error                                    |
| `permissionDeniedText` | `string`                               | No       | `"Camera permission denied"` | Text when camera denied                               |
| `onStateChange`        | `(state: ButtonState) => void`         | No       | —                            | Callback when button state changes                    |
| `customization`        | `FaceTecCustomization`                 | No       | —                            | SDK UI customization (see below)                      |

---

## Customization

The `customization` prop controls all visual aspects of the FaceTec SDK UI, grouped by element. All fields are optional — omitted fields fall back to native Config defaults.

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

### Ready Screen Header

| Property                  | Type                | Description                                 |
| ------------------------- | ------------------- | ------------------------------------------- |
| `readyScreenHeaderText`   | `string`            | Header text (supports `\n` for line breaks) |
| `readyScreenHeaderStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }`                   |

### Ready Screen Subtext

| Property                   | Type                | Description                                  |
| -------------------------- | ------------------- | -------------------------------------------- |
| `readyScreenSubtext`       | `string`            | Subtext below the oval. Set to `''` to hide. |
| `readyScreenSubtextStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }`                    |

### Action Button

| Property             | Type                  | Description                          |
| -------------------- | --------------------- | ------------------------------------ |
| `actionButtonText`   | `string`              | Button label (e.g., `"ESTOY LISTO"`) |
| `actionButtonStyles` | `FaceTecButtonStyles` | See below                            |

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

### Retry Screen Header

| Property                  | Type                | Description                 |
| ------------------------- | ------------------- | --------------------------- |
| `retryScreenHeaderText`   | `string`            | Header text on retry screen |
| `retryScreenHeaderStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }`   |

### Retry Screen Subtext

| Property                   | Type                | Description               |
| -------------------------- | ------------------- | ------------------------- |
| `retryScreenSubtext`       | `string`            | Subtext on retry screen   |
| `retryScreenSubtextStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }` |

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

| Property        | Type                   | Description                                       |
| --------------- | ---------------------- | ------------------------------------------------- |
| `feedbackTexts` | `FaceTecFeedbackTexts` | Override feedback/presession texts (all optional) |

```typescript
interface FaceTecFeedbackTexts {
  moveCloser?: string; // "Move Closer"
  moveAway?: string; // "Move Away"
  centerFace?: string; // "Center Your Face"
  faceNotFound?: string; // "Frame Your Face"
  holdSteady?: string; // "Hold Steady"
  faceNotUpright?: string; // "Hold Your Head Straight"
  faceNotLookingStraight?: string; // "Look Straight Ahead"
  useEvenLighting?: string; // "Light Face More Evenly"
  moveToEyeLevel?: string; // "Move Camera To Eye Level"
  presessionFrameYourFace?: string; // "Frame Your Face In The Oval"
  presessionLookStraight?: string; // "Look Straight Ahead"
  presessionNeutralExpression?: string; // "Neutral Expression, No Smiling"
  presessionRemoveDarkGlasses?: string; // "Remove Dark Glasses"
  presessionConditionsTooBright?: string; // "Conditions Too Bright"
  presessionBrightenEnvironment?: string; // "Brighten Your Environment"
}
```

### Result Screen

| Property              | Type                | Description               |
| --------------------- | ------------------- | ------------------------- |
| `resultMessageStyles` | `FaceTecTextStyles` | `{ color?, fontFamily? }` |

### Font notes

- **iOS**: Use the PostScript name of the font (e.g., `Montserrat-SemiBold`). The font must be registered in `Info.plist` under `UIAppFonts`.
- **Android**: Use the font file name without extension (e.g., `Montserrat-SemiBold`). The font must be in `assets/fonts/`. The module uses React Native's `ReactFontManager` to resolve bundled fonts.
- **Color format**: Hex strings in `#RRGGBB` or `#RRGGBBAA` format.

---

## Error Event

```typescript
interface ErrorEvent {
  errorType: ErrorType;
  message: string;
}

type ErrorType =
  | "permission_denied"
  | "init_error"
  | "device_not_supported"
  | "session_cancelled"
  | "network_error"
  | "internal_error";
```

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

## Button States

| State          | Text                                  | Enabled | Styles applied                |
| -------------- | ------------------------------------- | ------- | ----------------------------- |
| `initializing` | `initializingText`                    | No      | `style` + `initializingStyle` |
| `ready`        | `readyText`                           | Yes     | `style`                       |
| `error`        | `errorText` or `permissionDeniedText` | No      | `style` + `errorStyle`        |

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

1. Placed the `.aar` file in `android/libs/` (project root's android directory)
2. Added the `flatDir` repository configuration in `android/app/build.gradle` (see step 3 of Android Setup)

### Error: `init_error` with message `REQUEST_ABORTED`

The `DeviceKeyIdentifier` is empty or invalid. Make sure you configured it in `FaceTec.initialize()` or in the native Config files.

### Custom font not applying

- **iOS**: Verify the font is listed in `Info.plist` under `UIAppFonts` and use the PostScript name.
- **Android**: Verify the `.ttf` file is in `android/app/src/main/assets/fonts/` and use the file name without extension.

### Dependency conflicts with npm install

Always use `yarn install` instead of `npm install` to respect the existing `yarn.lock` file and avoid version conflicts.

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
# Native module (no binaries inside)
├── native_modules/
│   └── react-native-facetec/
│       ├── src/
│       │   ├── index.ts
│       │   ├── FaceTecModule.ts
│       │   └── Facetec3DLivenessTestButton.tsx
│       ├── ios/
│       │   └── FaceTecLiveness/
│       │       ├── FaceTecModule.swift
│       │       ├── FaceTecModule.m
│       │       ├── FaceTecLivenessViewManager.swift
│       │       ├── FaceTecLivenessViewManager.m
│       │       ├── Config.swift
│       │       ├── SessionRequestProcessor.swift
│       │       └── FaceTecServerResponse.swift
│       ├── android/
│       │   └── src/main/java/com/facetec/
│       │       ├── FaceTecLivenessViewManager.java
│       │       ├── FaceTecLivenessPackage.java
│       │       ├── FaceTecLivenessModule.java
│       │       ├── RNFaceTecLivenessButton.java
│       │       ├── Config.java
│       │       ├── SessionRequestProcessor.java
│       │       └── FaceTecServerResponse.java
│       ├── package.json
│       ├── react-native-facetec.podspec
│       └── README.md
```
