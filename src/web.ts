import { WebPlugin } from '@capacitor/core';
import type {
  CallServicePlugin,
  StartCallServiceOptions,
  UpdateCallServiceOptions,
} from './definitions';

export class CallServiceWeb extends WebPlugin implements CallServicePlugin {
  async startCallService(_options?: StartCallServiceOptions): Promise<void> {
    console.warn('CallService foreground service is not supported on web platform.');
  }

  async updateCallService(_options?: UpdateCallServiceOptions): Promise<void> {
    console.warn('CallService foreground service is not supported on web platform.');
  }

  async stopCallService(): Promise<void> {
    console.warn('CallService foreground service is not supported on web platform.');
  }
}
