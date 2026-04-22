import { NativeModules } from 'react-native';

const { FaceTecLivenessModule } = NativeModules;

// --- Style Interfaces ---

/**
 * Styles for a text element (header, subtext, message).
 */
export interface FaceTecTextStyles {
  /** Font family name (e.g., "Montserrat-Bold") */
  fontFamily?: string;
  /** Text color as hex string (e.g., "#1a2332") */
  color?: string;
}

/**
 * Styles for the action button ("I'M READY", retry button, etc.).
 */
export interface FaceTecButtonStyles {
  /** Button label font family */
  fontFamily?: string;
  /** Button label text color */
  textColor?: string;
  /** Button background color */
  backgroundColor?: string;
  /** Button background color when pressed */
  highlightBackgroundColor?: string;
  /** Button background color when disabled */
  disabledBackgroundColor?: string;
  /** Button corner radius */
  cornerRadius?: number;
}

/**
 * Styles for the feedback bar (shown during scan).
 */
export interface FaceTecFeedbackBarStyles {
  /** Feedback bar text font family */
  fontFamily?: string;
  /** Feedback bar text color */
  textColor?: string;
  /** Feedback bar background color */
  backgroundColor?: string;
  /** Feedback bar corner radius */
  cornerRadius?: number;
}

/**
 * Customizable feedback/presession texts shown during the liveness scan.
 * All fields are optional — only override the ones you need.
 */
export interface FaceTecFeedbackTexts {
  /** "Move Closer" */
  moveCloser?: string;
  /** "Move Away" */
  moveAway?: string;
  /** "Center Your Face" */
  centerFace?: string;
  /** "Frame Your Face" */
  faceNotFound?: string;
  /** "Hold Steady" */
  holdSteady?: string;
  /** "Hold Your Head Straight" */
  faceNotUpright?: string;
  /** "Look Straight Ahead" */
  faceNotLookingStraight?: string;
  /** "Light Face More Evenly" */
  useEvenLighting?: string;
  /** "Move Camera To Eye Level" */
  moveToEyeLevel?: string;
  /** "Frame Your Face In The Oval" */
  presessionFrameYourFace?: string;
  /** "Look Straight Ahead" */
  presessionLookStraight?: string;
  /** "Neutral Expression, No Smiling" */
  presessionNeutralExpression?: string;
  /** "Remove Dark Glasses" */
  presessionRemoveDarkGlasses?: string;
  /** "Conditions Too Bright" */
  presessionConditionsTooBright?: string;
  /** "Brighten Your Environment" */
  presessionBrightenEnvironment?: string;
}

/**
 * Customizable texts for the upload/processing result screen.
 * All fields are optional — only override the ones you need.
 */
export interface FaceTecResultScreenTexts {
  /** Message shown while uploading the 3D face scan */
  uploadMessage?: string;
  /** Message when upload is still in progress / slow connection */
  uploadMessageStillUploading?: string;
  /** Success message after 3D liveness check (prior to ID scan) */
  successMessage?: string;
  /** Success message for 3D enrollment */
  successEnrollmentMessage?: string;
  /** Success message for 3D reverification */
  successReverificationMessage?: string;
  /** Success message for 3D liveness + official ID photo */
  successLivenessAndIdMessage?: string;
}

/**
 * FaceTec SDK UI customization grouped by element.
 * All color values are hex strings (e.g., "#ffffff", "#000000b0").
 * Any omitted field falls back to the native Config file default.
 */
export interface FaceTecCustomization {
  // -- Frame & Overlay --

  /** Background color behind the FaceTec frame */
  outerBackgroundColor?: string;
  /** FaceTec frame background color */
  frameColor?: string;
  /** FaceTec frame border color */
  borderColor?: string;

  // -- Oval --

  /** Oval stroke color around the face */
  ovalColor?: string;
  /** Oval spinner/progress color */
  dualSpinnerColor?: string;

  // -- Ready Screen Header --

  /** Header text on the ready screen (e.g., "Get Ready For\nYour Video Selfie") */
  readyScreenHeaderText?: string;
  /** Styles for the ready screen header text */
  readyScreenHeaderStyles?: FaceTecTextStyles;

  // -- Ready Screen Subtext --

  /** Subtext below the oval on the ready screen. Set to '' to hide. */
  readyScreenSubtext?: string;
  /** Styles for the ready screen subtext */
  readyScreenSubtextStyles?: FaceTecTextStyles;

  // -- Action Button --

  /** Action button label text (e.g., "I'M READY") */
  actionButtonText?: string;
  /** Styles for the action button */
  actionButtonStyles?: FaceTecButtonStyles;

  // -- Retry Screen Header --

  /** Header text on the retry screen */
  retryScreenHeaderText?: string;
  /** Styles for the retry screen header text */
  retryScreenHeaderStyles?: FaceTecTextStyles;

  // -- Retry Screen Subtext --

  /** Subtext on the retry screen */
  retryScreenSubtext?: string;
  /** Styles for the retry screen subtext */
  retryScreenSubtextStyles?: FaceTecTextStyles;

  // -- Feedback Bar --

  /** Styles for the feedback bar shown during scan */
  feedbackBarStyles?: FaceTecFeedbackBarStyles;

  // -- Feedback Texts --

  /** Override feedback/presession texts shown during the liveness scan */
  feedbackTexts?: FaceTecFeedbackTexts;

  // -- Result Screen --

  /** Styles for the result screen message */
  resultMessageStyles?: FaceTecTextStyles;

  // -- Result Screen Texts --

  /** Override upload/processing texts shown on the result screen */
  resultScreenTexts?: FaceTecResultScreenTexts;
}

/**
 * Configuration for FaceTec SDK initialization
 */
export interface FaceTecInitConfig {
  /** FaceTec Device Key Identifier (required) */
  deviceKeyIdentifier: string;
  /** FaceTec API endpoint URL (optional, falls back to native Config value) */
  apiEndpoint?: string;
  /** Custom headers to include in FaceTec API requests */
  headers?: Record<string, string>;
}

// --- Initialization Status ---

/**
 * Status of the FaceTec SDK initialization process.
 */
export type InitializationStatusValue = 'idle' | 'initializing' | 'initialized' | 'error';

/**
 * Result from getInitializationStatus().
 * When status is 'error', the error field contains the failure reason.
 */
export interface InitializationStatus {
  status: InitializationStatusValue;
  error?: string;
}

// --- Response Types ---

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

// --- Error Types ---

/**
 * Tipos de error para el callback onError
 */
export type ErrorType =
  | 'permission_denied'
  | 'init_error'
  | 'init_cancelled'
  | 'device_not_supported'
  | 'session_cancelled'
  | 'network_error'
  | 'internal_error'
  | 'camera_permission';

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
 * Error types returned by FaceTec SDK operations.
 * Use these to branch on specific error conditions in your error handlers.
 */
export enum FaceTecErrorType {
  INIT_ERROR = 'init_error',
  INIT_CANCELLED = 'init_cancelled',
  PERMISSION_DENIED = 'permission_denied',
  CAMERA_PERMISSION = 'camera_permission',
  DEVICE_NOT_SUPPORTED = 'device_not_supported',
  SESSION_CANCELLED = 'session_cancelled',
  NETWORK_ERROR = 'network_error',
  INTERNAL_ERROR = 'internal_error',
}

// --- Module API ---

/**
 * FaceTec SDK module - handles initialization, configuration, and liveness sessions.
 *
 * Call `FaceTec.initialize()` once at app startup.
 * Then use `FaceTec.startLivenessCheck()` to launch a liveness session,
 * or mount `Facetec3DLivenessTestButton` which calls it internally.
 *
 * @example
 * ```typescript
 * import { FaceTec } from 'react-native-facetec';
 *
 * // Initialize once
 * await FaceTec.initialize({
 *   deviceKeyIdentifier: 'your-device-key',
 *   apiEndpoint: 'https://your-api.com/facetec',
 *   headers: { 'Authorization': 'Bearer token' },
 * });
 *
 * // Start liveness check
 * const response = await FaceTec.startLivenessCheck({
 *   frameColor: '#ffffff',
 *   readyScreenHeaderText: 'Place your face in the frame',
 * });
 * ```
 */
export const FaceTec = {
  /**
   * Initialize the FaceTec SDK with the given configuration.
   * Safe to call multiple times — subsequent calls return true immediately
   * if already initialized, or reject if initialization is in progress.
   *
   * @param config - SDK configuration
   * @returns Promise that resolves to true on success
   * @throws Error if deviceKeyIdentifier is not provided or initialization fails
   */
  async initialize(config: FaceTecInitConfig): Promise<boolean> {
    if (!config.deviceKeyIdentifier) {
      throw new Error('FaceTec.initialize: deviceKeyIdentifier is required');
    }
    return FaceTecLivenessModule.initialize({
      deviceKeyIdentifier: config.deviceKeyIdentifier,
      apiEndpoint: config.apiEndpoint ?? '',
      headers: config.headers ?? {},
    });
  },

  /**
   * Check if the FaceTec SDK has been successfully initialized.
   * Useful for retry logic or conditional UI rendering.
   *
   * @returns Promise that resolves to true if initialized, false otherwise
   */
  async isInitialized(): Promise<boolean> {
    return FaceTecLivenessModule.isInitialized();
  },

  /**
   * Get the current initialization status of the FaceTec SDK.
   * Unlike `isInitialized()` which only returns a boolean, this method
   * returns the full status including error information when initialization fails.
   *
   * @returns Promise with status ('idle' | 'initializing' | 'initialized' | 'error') and optional error message
   */
  async getInitializationStatus(): Promise<InitializationStatus> {
    return FaceTecLivenessModule.getInitializationStatus();
  },

  /**
   * Get the FaceTec SDK version string.
   *
   * @returns Promise that resolves to the version string
   */
  async getSDKVersion(): Promise<string> {
    return FaceTecLivenessModule.getSDKVersion();
  },

  /**
   * Start a 3D liveness check session.
   * Checks camera permission, applies customization, and launches the FaceTec UI.
   *
   * @param customization - Optional UI customization for the session
   * @returns Promise that resolves with the server response on success
   * @throws Rejects with error code and message on failure (permission denied, cancelled, etc.)
   */
  async startLivenessCheck(
    customization?: FaceTecCustomization,
  ): Promise<LivenessResponse> {
    return FaceTecLivenessModule.startLivenessCheck(customization ?? {});
  },
};
