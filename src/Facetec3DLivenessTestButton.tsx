import React from 'react';
import {
  requireNativeComponent,
  ViewStyle,
  StyleSheet,
  NativeSyntheticEvent,
  Platform,
  StyleProp,
} from 'react-native';

/**
 * Posibles estados del resultado de liveness
 */
export type LivenessStatus =
  | 'success'
  | 'error'
  | 'initError'
  | 'cancelled'
  | 'SESSION_COMPLETED'
  | 'USER_CANCELLED_FACE_SCAN'
  | 'REQUEST_ABORTED';

/**
 * Respuesta del proceso de liveness
 */
export interface LivenessResponse {
  /** Indica si la verificacion fue exitosa */
  success: boolean;
  /** Estado del resultado */
  status: LivenessStatus;
  /** Mensaje descriptivo del resultado */
  message: string;
}

/**
 * Props del componente Facetec3DLivenessTestButton
 */
export interface Facetec3DLivenessTestButtonProps {
  /**
   * Callback que se ejecuta cuando el proceso de liveness termina
   * @param response - Objeto con el resultado de la verificacion
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
}

/**
 * Props internas para el componente nativo
 */
interface NativeFaceTecButtonProps {
  onResponse: (event: NativeSyntheticEvent<LivenessResponse>) => void;
  style?: StyleProp<ViewStyle>;
  initializingText?: string;
  readyText?: string;
  errorText?: string;
}

// Componente nativo
const NativeFaceTecButton =
  requireNativeComponent<NativeFaceTecButtonProps>('FaceTecLivenessButton');

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
 * import { Facetec3DLivenessTestButton } from 'react-native-facetec-liveness';
 *
 * const MyComponent = () => {
 *   const handleResponse = (response) => {
 *     if (response.success) {
 *       console.log('Verificacion exitosa!');
 *     } else {
 *       console.log('Error:', response.message);
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
}) => {
  /**
   * Maneja el evento nativo y lo transforma al callback de JS
   */
  const handleNativeResponse = (
    event: NativeSyntheticEvent<LivenessResponse>
  ) => {
    const { success, status, message } = event.nativeEvent;

    // Normalizar la respuesta
    const normalizedResponse: LivenessResponse = {
      success: success || status === 'SESSION_COMPLETED',
      status,
      message: message || status,
    };

    onResponse(normalizedResponse);
  };

  return (
    <NativeFaceTecButton
      style={[styles.button, style]}
      onResponse={handleNativeResponse}
      initializingText={initializingText}
      readyText={readyText}
      errorText={errorText}
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
