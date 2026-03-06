import React, { useMemo } from 'react';
import {
  ColorValue,
  NativeSyntheticEvent,
  Platform,
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
 * Informacion del servidor FaceTec
 */
export interface ServerInfo {
  /** Version del SDK del servidor */
  coreServerSDKVersion?: string;
  /** Modo del servidor (Development Only, Production, etc.) */
  mode?: string;
  /** Aviso de seguridad */
  notice?: string;
}

/**
 * Datos adicionales de la sesion
 */
export interface AdditionalSessionData {
  /** ID de la aplicacion */
  appID?: string;
  /** Modelo del dispositivo */
  deviceModel?: string;
  /** Version del SDK del dispositivo */
  deviceSDKVersion?: string;
  /** ID de instalacion */
  installationID?: string;
  /** Plataforma (ios, android) */
  platform?: string;
}

/**
 * Informacion de la llamada HTTP
 */
export interface HttpCallInfo {
  /** Fecha de la llamada */
  date?: string;
  /** Epoch en segundos */
  epochSecond?: number;
  /** Path de la llamada */
  path?: string;
  /** Metodo HTTP */
  requestMethod?: string;
  /** ID de transaccion */
  tid?: string;
}

/**
 * Respuesta del proceso de liveness - Contiene los datos directos del servidor FaceTec
 * Los campos con valores 1/0 son convertidos a booleanos
 * Si hay error de red o no hay respuesta del servidor, los campos seran undefined
 */
export interface LivenessResponse {
  /** Indica exito general del servidor */
  success?: boolean;
  /** Indica si hubo error en el servidor */
  didError?: boolean;
  /** Blob de respuesta encriptado */
  responseBlob?: string;
  /** Resultado del liveness */
  result?: LivenessResult;
  /** Informacion del servidor */
  serverInfo?: ServerInfo;
  /** Datos adicionales de la sesion */
  additionalSessionData?: AdditionalSessionData;
  /** Informacion de la llamada HTTP */
  httpCallInfo?: HttpCallInfo;
}

/**
 * Props del componente Facetec3DLivenessTestButton
 */
export interface Facetec3DLivenessTestButtonProps {
  /**
   * Callback que se ejecuta cuando el proceso de liveness termina
   * @param response - Objeto con los datos del servidor FaceTec (campos 1/0 convertidos a boolean)
   */
  onResponse: (response: LivenessResponse) => void;

  /**
   * Estilos personalizados para el boton
   */
  style?: StyleProp<ViewStyle>;

  /**
   * Texto mostrado mientras el SDK se inicializa
   * @default "Iniciando"
   */
  initializingText?: string;

  /**
   * Texto mostrado cuando el boton esta listo para iniciar
   * @default "Iniciar prueba de vida"
   */
  readyText?: string;

  /**
   * Texto mostrado cuando hay un error de inicializacion
   * @default "Error de inicializacion"
   */
  errorText?: string;

  /**
   * Texto mostrado cuando el permiso de camara es denegado
   * @default "Permiso de camara denegado"
   */
  permissionDeniedText?: string;

  /**
   * Estilos personalizados para el boton cuando hay un error
   * Si no se proporciona, se usara el color rojo por defecto
   * Solo se aplica backgroundColor
   */
  errorStyle?: StyleProp<ViewStyle>;

  /**
   * Estilos personalizados para el boton mientras se inicializa
   * Si no se proporciona, se usara el estilo de la prop style
   * Solo se aplica backgroundColor
   */
  initializingStyle?: StyleProp<ViewStyle>;
}

/**
 * Respuesta raw del nativo (antes de conversion)
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
 * Props internas para el componente nativo
 */
interface NativeFaceTecButtonProps {
  onResponse: (event: NativeSyntheticEvent<NativeLivenessResponse>) => void;
  style?: StyleProp<ViewStyle>;
  initializingText?: string;
  readyText?: string;
  errorText?: string;
  permissionDeniedText?: string;
  errorBackgroundColor?: ColorValue;
  initializingBackgroundColor?: ColorValue;
}

// Componente nativo
const NativeFaceTecButton = requireNativeComponent<NativeFaceTecButtonProps>(
  'FaceTecLivenessButton',
);

/**
 * Facetec3DLivenessTestButton
 *
 * Boton que integra FaceTec 3D Liveness SDK para verificacion biometrica.
 *
 * El boton muestra diferentes estados:
 * - "Iniciando" (gris, deshabilitado) - Mientras se inicializa el SDK
 * - "Iniciar prueba de vida" (azul, habilitado) - Listo para usar
 * - "Error de inicializacion" (rojo, deshabilitado) - Si falla la inicializacion
 *
 * @example
 * ```tsx
 * import { Facetec3DLivenessTestButton } from 'react-native-facetec';
 *
 * const MyComponent = () => {
 *   const handleResponse = (response) => {
 *     if (response.success && !response.didError && response.result?.livenessProven) {
 *       console.log('Liveness verificado exitosamente!');
 *     } else if (response.didError) {
 *       console.log('Error en el servidor');
 *     } else if (response.success === undefined) {
 *       console.log('Sin respuesta del servidor (error de red o cancelado)');
 *     } else {
 *       console.log('Liveness no verificado');
 *     }
 *   };
 *
 *   return (
 *     <Facetec3DLivenessTestButton
 *       onResponse={handleResponse}
 *       style={{ width: 250, height: 50 }}
 *     />
 *   );
 * };
 * ```
 */
export const Facetec3DLivenessTestButton: React.FC<
  Facetec3DLivenessTestButtonProps
> = ({
  onResponse,
  style,
  initializingText = 'Iniciando',
  readyText = 'Iniciar prueba de vida',
  errorText = 'Error de inicializacion',
  permissionDeniedText = 'Permiso de camara denegado',
  errorStyle,
  initializingStyle,
}) => {
  // Extract backgroundColor from errorStyle if provided
  const errorBackgroundColor = useMemo(() => {
    if (!errorStyle) return undefined;
    const flatStyle = StyleSheet.flatten(errorStyle);
    return flatStyle?.backgroundColor as ColorValue | undefined;
  }, [errorStyle]);

  // Extract backgroundColor from initializingStyle if provided, fallback to style
  const initializingBackgroundColor = useMemo(() => {
    if (initializingStyle) {
      const flatStyle = StyleSheet.flatten(initializingStyle);
      return flatStyle?.backgroundColor as ColorValue | undefined;
    }
    // Fallback to style's backgroundColor
    if (style) {
      const flatStyle = StyleSheet.flatten(style);
      return flatStyle?.backgroundColor as ColorValue | undefined;
    }
    return undefined;
  }, [initializingStyle, style]);

  /**
   * Maneja el evento nativo y lo transforma al callback de JS
   * Los campos ya vienen convertidos a boolean desde el lado nativo
   */
  const handleNativeResponse = (
    event: NativeSyntheticEvent<NativeLivenessResponse>,
  ) => {
    const nativeEvent = event.nativeEvent;

    // Pasar directamente los datos del servidor FaceTec
    // Los campos 1/0 ya estan convertidos a boolean en el lado nativo
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
  };

  return (
    <NativeFaceTecButton
      style={[styles.button, style]}
      onResponse={handleNativeResponse}
      initializingText={initializingText}
      readyText={readyText}
      errorText={errorText}
      permissionDeniedText={permissionDeniedText}
      errorBackgroundColor={errorBackgroundColor}
      initializingBackgroundColor={initializingBackgroundColor}
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
});

export default Facetec3DLivenessTestButton;
