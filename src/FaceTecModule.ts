import { NativeModules } from 'react-native';

const { FaceTecLivenessModule } = NativeModules;

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
