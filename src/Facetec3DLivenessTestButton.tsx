import React, { useMemo, useState, useCallback } from 'react';
import {
  ColorValue,
  NativeSyntheticEvent,
  Platform,
  processColor,
  requireNativeComponent,
  StyleProp,
  StyleSheet,
  ViewStyle,
} from 'react-native';

/**
 * Resultado del liveness del servidor
 */
export interface LivenessResult {
  /** Grupo de edad estimado */
  ageV2GroupEnumInt?: number;
  /** Indica si el liveness fue probado */
  livenessProven?: boolean;
}

/**
 * Información del servidor FaceTec
 */
export interface ServerInfo {
  /** Versión del SDK del servidor */
  coreServerSDKVersion?: string;
  /** Modo del servidor (Development Only, Production, etc.) */
  mode?: string;
  /** Aviso de seguridad */
  notice?: string;
}

/**
 * Datos adicionales de la sesión
 */
export interface AdditionalSessionData {
  /** ID de la aplicación */
  appID?: string;
  /** Modelo del dispositivo */
  deviceModel?: string;
  /** Versión del SDK del dispositivo */
  deviceSDKVersion?: string;
  /** ID de instalación */
  installationID?: string;
  /** Plataforma (ios, android) */
  platform?: string;
}

/**
 * Información de la llamada HTTP
 */
export interface HttpCallInfo {
  /** Fecha de la llamada */
  date?: string;
  /** Epoch en segundos */
  epochSecond?: number;
  /** Path de la llamada */
  path?: string;
  /** Método HTTP */
  requestMethod?: string;
  /** ID de transacción */
  tid?: string;
}

/**
 * Tipos de error para el callback onError
 */
export type ErrorType =
  | 'permission_denied'
  | 'init_error'
  | 'session_cancelled'
  | 'network_error'
  | 'internal_error';

/**
 * Evento de error emitido cuando ocurre un error sin respuesta del servidor
 */
export interface ErrorEvent {
  /** Tipo de error */
  errorType: ErrorType;
  /** Mensaje descriptivo del error */
  message: string;
}

/**
 * Respuesta del proceso de liveness - Contiene los datos directos del servidor FaceTec
 * Los campos con valores 1/0 son convertidos a booleanos
 */
export interface LivenessResponse {
  /** Indica éxito general del servidor */
  success?: boolean;
  /** Indica si hubo error en el servidor */
  didError?: boolean;
  /** Blob de respuesta encriptado */
  responseBlob?: string;
  /** Resultado del liveness */
  result?: LivenessResult;
  /** Información del servidor */
  serverInfo?: ServerInfo;
  /** Datos adicionales de la sesión */
  additionalSessionData?: AdditionalSessionData;
  /** Información de la llamada HTTP */
  httpCallInfo?: HttpCallInfo;
}

/**
 * Estados posibles del botón
 */
export type ButtonState = 'initializing' | 'ready' | 'error';

/**
 * Props del componente Facetec3DLivenessTestButton
 */
export interface Facetec3DLivenessTestButtonProps {
  /**
   * Callback que se ejecuta cuando el proceso de liveness termina exitosamente
   * con una respuesta del servidor
   * @param response - Objeto con los datos del servidor FaceTec (campos 1/0 convertidos a boolean)
   */
  onResponse: (response: LivenessResponse) => void;

  /**
   * Callback que se ejecuta cuando ocurre un error sin respuesta del servidor
   * Esto incluye: permiso denegado, error de inicialización, sesión cancelada, error de red
   * @param error - Objeto con el tipo de error y mensaje descriptivo
   */
  onError?: (error: ErrorEvent) => void;

  /**
   * Estilos base del botón (se aplican siempre)
   */
  style?: StyleProp<ViewStyle>;

  /**
   * Estilos adicionales para el estado de error
   * Se combinan con style cuando el botón está en estado de error
   */
  errorStyle?: StyleProp<ViewStyle>;

  /**
   * Estilos adicionales para el estado de inicialización
   * Se combinan con style mientras el SDK se inicializa
   */
  initializingStyle?: StyleProp<ViewStyle>;

  /**
   * Texto mostrado mientras el SDK se inicializa
   * @default "Initializing"
   */
  initializingText?: string;

  /**
   * Texto mostrado cuando el botón está listo para iniciar
   * @default "Start liveness check"
   */
  readyText?: string;

  /**
   * Texto mostrado cuando hay un error de inicialización
   * @default "Initialization error"
   */
  errorText?: string;

  /**
   * Texto mostrado cuando el permiso de cámara es denegado
   * @default "Camera permission denied"
   */
  permissionDeniedText?: string;

  /**
   * Callback opcional que se ejecuta cuando el estado del botón cambia
   * @param state - El nuevo estado del botón
   */
  onStateChange?: (state: ButtonState) => void;
}

/**
 * Respuesta raw del nativo (antes de conversión)
 */
interface NativeLivenessResponse {
  success?: boolean;
  didError?: boolean;
  responseBlob?: string;
  result?: {
    livenessProven?: boolean;
    ageV2GroupEnumInt?: number;
  };
  serverInfo?: ServerInfo;
  additionalSessionData?: AdditionalSessionData;
  httpCallInfo?: HttpCallInfo;
}

/**
 * Evento de error nativo
 */
interface NativeErrorEvent {
  errorType: string;
  message: string;
}

/**
 * Evento de cambio de estado nativo
 */
interface NativeStateChangeEvent {
  state: ButtonState;
}

/**
 * Props internas para el componente nativo
 */
interface NativeFaceTecButtonProps {
  onResponse: (event: NativeSyntheticEvent<NativeLivenessResponse>) => void;
  onError?: (event: NativeSyntheticEvent<NativeErrorEvent>) => void;
  onStateChange?: (event: NativeSyntheticEvent<NativeStateChangeEvent>) => void;
  style?: StyleProp<ViewStyle>;
  initializingText?: string;
  readyText?: string;
  errorText?: string;
  permissionDeniedText?: string;
  // Android-specific style props (Android doesn't inherit RN styles)
  androidBackgroundColor?: number;
  androidBorderRadius?: number;
  androidBorderColor?: number;
  androidBorderWidth?: number;
}

// Componente nativo
const NativeFaceTecButton = requireNativeComponent<NativeFaceTecButtonProps>(
  'FaceTecLivenessButton',
);

/**
 * Facetec3DLivenessTestButton
 *
 * Botón que integra FaceTec 3D Liveness SDK para verificación biométrica.
 *
 * El botón muestra diferentes estados:
 * - "Initializing" - Mientras se inicializa el SDK (aplica initializingStyle)
 * - "Start liveness check" - Listo para usar (aplica solo style)
 * - "Initialization error" - Si falla la inicialización (aplica errorStyle)
 *
 * @example
 * ```tsx
 * import { Facetec3DLivenessTestButton } from 'react-native-facetec';
 *
 * const MyComponent = () => {
 *   return (
 *     <Facetec3DLivenessTestButton
 *       onResponse={(response) => {
 *         if (response.success && response.result?.livenessProven) {
 *           console.log('Liveness verificado!');
 *         }
 *       }}
 *       onError={(error) => console.log('Error:', error.errorType)}
 *       style={{ width: 250, height: 50, backgroundColor: '#007AFF' }}
 *       errorStyle={{ backgroundColor: '#FF3B30', borderRadius: 25 }}
 *       initializingStyle={{ backgroundColor: '#808080' }}
 *     />
 *   );
 * };
 * ```
 */
export const Facetec3DLivenessTestButton: React.FC<
  Facetec3DLivenessTestButtonProps
> = ({
  onResponse,
  onError,
  style,
  errorStyle,
  initializingStyle,
  initializingText = 'Initializing',
  readyText = 'Start liveness check',
  errorText = 'Initialization error',
  permissionDeniedText = 'Camera permission denied',
  onStateChange: onStateChangeProp,
}) => {
  // Track button state internally
  const [buttonState, setButtonState] = useState<ButtonState>('initializing');

  // Compute the combined style based on current state
  const computedStyle = useMemo(() => {
    const baseStyles = [styles.button, style];

    switch (buttonState) {
      case 'error':
        return [...baseStyles, styles.errorDefault, errorStyle];
      case 'initializing':
        return [...baseStyles, styles.initializingDefault, initializingStyle];
      case 'ready':
      default:
        return baseStyles;
    }
  }, [buttonState, style, errorStyle, initializingStyle]);

  // Extract style props for Android (Android doesn't inherit RN styles on native views)
  const androidStyleProps = useMemo(() => {
    if (Platform.OS !== 'android') return {};

    // Cast to ViewStyle since StyleSheet.flatten returns a ViewStyle when flattening valid styles
    const flatStyle = StyleSheet.flatten(computedStyle) as ViewStyle | undefined;

    if (!flatStyle) return {};

    return {
      androidBackgroundColor: flatStyle.backgroundColor
        ? (processColor(flatStyle.backgroundColor as ColorValue) as number)
        : undefined,
      androidBorderRadius:
        typeof flatStyle.borderRadius === 'number'
          ? flatStyle.borderRadius
          : undefined,
      androidBorderColor: flatStyle.borderColor
        ? (processColor(flatStyle.borderColor as ColorValue) as number)
        : undefined,
      androidBorderWidth:
        typeof flatStyle.borderWidth === 'number'
          ? flatStyle.borderWidth
          : undefined,
    };
  }, [computedStyle]);

  /**
   * Handle state change from native
   */
  const handleStateChange = useCallback(
    (event: NativeSyntheticEvent<NativeStateChangeEvent>) => {
      const newState = event.nativeEvent.state;
      setButtonState(newState);
      onStateChangeProp?.(newState);
    },
    [onStateChangeProp],
  );

  /**
   * Maneja el evento nativo y lo transforma al callback de JS
   * Los campos ya vienen convertidos a boolean desde el lado nativo
   */
  const handleNativeResponse = useCallback(
    (event: NativeSyntheticEvent<NativeLivenessResponse>) => {
      const nativeEvent = event.nativeEvent;

      const response: LivenessResponse = {
        success: nativeEvent.success,
        didError: nativeEvent.didError,
        responseBlob: nativeEvent.responseBlob,
        result: nativeEvent.result,
        serverInfo: nativeEvent.serverInfo,
        additionalSessionData: nativeEvent.additionalSessionData,
        httpCallInfo: nativeEvent.httpCallInfo,
      };

      onResponse(response);
    },
    [onResponse],
  );

  /**
   * Maneja los errores nativos (sin respuesta del servidor)
   */
  const handleNativeError = useCallback(
    (event: NativeSyntheticEvent<NativeErrorEvent>) => {
      if (!onError) return;

      const nativeEvent = event.nativeEvent;
      const errorEvent: ErrorEvent = {
        errorType: nativeEvent.errorType as ErrorType,
        message: nativeEvent.message,
      };

      onError(errorEvent);
    },
    [onError],
  );

  return (
    <NativeFaceTecButton
      style={computedStyle}
      onResponse={handleNativeResponse}
      onError={onError ? handleNativeError : undefined}
      onStateChange={handleStateChange}
      initializingText={initializingText}
      readyText={readyText}
      errorText={errorText}
      permissionDeniedText={permissionDeniedText}
      {...androidStyleProps}
    />
  );
};

const styles = StyleSheet.create({
  button: {
    height: 50,
    minWidth: 200,
    ...Platform.select({
      ios: {
        borderRadius: 8,
      },
      android: {
        borderRadius: 8,
        elevation: 2,
      },
    }),
  },
  // Default styles for error state (can be overridden by errorStyle prop)
  errorDefault: {
    backgroundColor: '#FF3B30',
  },
  // Default styles for initializing state (can be overridden by initializingStyle prop)
  initializingDefault: {
    backgroundColor: '#808080',
  },
});

export default Facetec3DLivenessTestButton;
