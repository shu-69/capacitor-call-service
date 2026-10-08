import { WebPlugin } from '@capacitor/core';
import type {
  CallServicePlugin,
  StartCallServiceOptions,
  StartCallServiceResult,
  UpdateCallServiceOptions,
  AudioOutputsResult,
  SetAudioOutputOptions,
  StartAudioRoutingOptions,
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

  async requestBluetoothPermission(): Promise<{ granted: boolean }> {
    return { granted: true };
  }
}
