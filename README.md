# react-native-facetec-liveness

React Native component for FaceTec 3D Liveness verification.

## Installation

```bash
npm install react-native-facetec-liveness
# or
yarn add react-native-facetec-liveness
```

### iOS Setup

1. Copy `FaceTecSDK.xcframework` to your iOS project or to `ios/` folder of this module

2. Add to your `Podfile`:
```ruby
pod 'react-native-facetec-liveness', :path => '../node_modules/react-native-facetec-liveness'
```

3. Add camera permission to `Info.plist`:
```xml
<key>NSCameraUsageDescription</key>
<string>Necesitamos acceso a la camara para validacion facial</string>
```

4. Run `pod install`

### Android Setup

1. The FaceTec SDK AAR is included in `android/libs/`

2. Add the package to your `MainApplication.java`:
```java
import com.facetecliveness.FaceTecLivenessPackage;

@Override
protected List<ReactPackage> getPackages() {
    return Arrays.asList(
        new MainReactPackage(),
        new FaceTecLivenessPackage() // Add this line
    );
}
```

3. Handle activity result in your `MainActivity.java`:
```java
import com.facetecliveness.FaceTecLivenessModule;

@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    // FaceTec handles this automatically through the module
}
```

4. Ensure camera permission is in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

## Usage

```tsx
import React from 'react';
import { View, Alert } from 'react-native';
import { Facetec3DLivenessTestButton } from 'react-native-facetec-liveness';

const App = () => {
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

export default App;
```

## Props

| Prop | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `onResponse` | `(response: LivenessResponse) => void` | Yes | - | Callback when liveness check completes |
| `style` | `ViewStyle` | No | - | Custom styles for the button |
| `initializingText` | `string` | No | `"Iniciando"` | Text shown while SDK initializes |
| `readyText` | `string` | No | `"Iniciar prueba de vida"` | Text shown when ready |
| `errorText` | `string` | No | `"Error de inicializacion"` | Text shown on init error |

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

## Button States

| State | Text | Color | Enabled |
|-------|------|-------|---------|
| Initializing | "Iniciando" | Gray | No |
| Ready | "Iniciar prueba de vida" | Blue | Yes |
| Error | "Error de inicializacion" | Red | No |

## Configuration

To change the FaceTec API configuration, modify:

- **iOS**: `ios/FaceTecLiveness/Config.swift`
- **Android**: `android/src/main/java/com/facetecliveness/Config.java`

Update `DeviceKeyIdentifier` and `YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT` with your production values.

## License

MIT
