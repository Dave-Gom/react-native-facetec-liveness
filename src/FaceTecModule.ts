import { NativeModules } from 'react-native';

const { FaceTecLivenessModule } = NativeModules;

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

/**
 * Error types returned by FaceTec SDK operations.
 * Use these to branch on specific error conditions in your error handlers.
 */
export enum FaceTecErrorType {
  INIT_ERROR = 'init_error',
  PERMISSION_DENIED = 'permission_denied',
  DEVICE_NOT_SUPPORTED = 'device_not_supported',
  SESSION_CANCELLED = 'session_cancelled',
  NETWORK_ERROR = 'network_error',
  INTERNAL_ERROR = 'internal_error',
}

/**
 * FaceTec SDK module - handles initialization and configuration
 *
 * Call `FaceTec.initialize()` once before mounting `Facetec3DLivenessTestButton`.
 * The button will stay in "initializing" state until the SDK is ready,
 * so you can mount the button and call initialize() in any order.
 *
 * @example
 * ```typescript
 * import { FaceTec, FaceTecErrorType } from 'react-native-facetec';
 *
 * await FaceTec.initialize({
 *   deviceKeyIdentifier: 'your-device-key',
 *   apiEndpoint: 'https://your-api.com/facetec',
 *   headers: { 'Authorization': 'Bearer token' },
 * });
 *
 * // Check initialization state (e.g., for retry logic)
 * const ready = await FaceTec.isInitialized();
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
   * Get the FaceTec SDK version string.
   *
   * @returns Promise that resolves to the version string
   */
  async getSDKVersion(): Promise<string> {
    return FaceTecLivenessModule.getSDKVersion();
  },
};
