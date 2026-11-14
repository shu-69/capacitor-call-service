import { WebPlugin } from '@capacitor/core';
import type {
  CallServicePlugin,
  StartCallServiceOptions,
  StartCallServiceResult,
  UpdateCallServiceOptions,
  StartAvailabilityServiceOptions,
  StartAvailabilityServiceResult,
  UpdateAvailabilityServiceOptions,
  AudioOutputsResult,
  SetAudioOutputOptions,
  StartAudioRoutingOptions,
  CallIntentResult,
  CancelIncomingOptions,
  CanUseFullScreenIntentResult,
} from './definitions';

export class CallServiceWeb extends WebPlugin implements CallServicePlugin {
  async startCallService(_options?: StartCallServiceOptions): Promise<StartCallServiceResult> {
    console.warn('CallService foreground service is not supported on web platform.');
    return { started: false, grantedType: 'none' };
  }

  async updateCallService(_options?: UpdateCallServiceOptions): Promise<void> {
    console.warn('CallService foreground service is not supported on web platform.');
  }

  async stopCallService(): Promise<void> {
    console.warn('CallService foreground service is not supported on web platform.');
  }

  async startAvailabilityService(_options?: StartAvailabilityServiceOptions): Promise<StartAvailabilityServiceResult> {
    return { started: false };
  }

  async updateAvailabilityNotification(_options?: UpdateAvailabilityServiceOptions): Promise<void> {
    // No-op on web
  }

  async stopAvailabilityService(): Promise<void> {
    // No-op on web
  }

  async getAudioOutputs(): Promise<AudioOutputsResult> {
    return {
      available: [{ type: 'speaker', name: 'Speaker' }],
      active: 'speaker',
      hasBluetoothPermission: true
    };
  }

  async setAudioOutput(_options: SetAudioOutputOptions): Promise<void> {
    // No-op on web
  }

  async startAudioRouting(_options?: StartAudioRoutingOptions): Promise<AudioOutputsResult> {
    return {
      available: [{ type: 'speaker', name: 'Speaker' }],
      active: 'speaker',
      hasBluetoothPermission: true
    };
  }

  async stopAudioRouting(): Promise<void> {
    // No-op on web
  }

  async checkBluetoothPermission(): Promise<{ granted: boolean }> {
    return { granted: true };
  }

  async requestBluetoothPermission(): Promise<{ granted: boolean }> {
    return { granted: true };
  }

  async openAppSettings(): Promise<void> {
    // No-op on web
  }

  async getPendingCallIntent(): Promise<CallIntentResult> {
    return { hasIntent: false };
  }

  async clearPendingCallIntent(): Promise<void> {
    // No-op on web
  }

  async isDeviceLocked(): Promise<{ isLocked: boolean }> {
    return { isLocked: false };
  }

  async sendToBackground(): Promise<void> {
    // No-op on web
  }

  async startRinging(): Promise<void> {
    // No-op on web
  }

  async stopRinging(): Promise<void> {
    // No-op on web
  }

  async cancelIncoming(_options?: CancelIncomingOptions): Promise<void> {
    // No-op on web
  }

  async canUseFullScreenIntent(): Promise<CanUseFullScreenIntentResult> {
    return { granted: true };
  }

  async openFullScreenIntentSettings(): Promise<void> {
    // No-op on web
  }
}
