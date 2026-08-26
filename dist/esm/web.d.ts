import { WebPlugin } from '@capacitor/core';
import type { CallServicePlugin, StartCallServiceOptions, UpdateCallServiceOptions } from './definitions';
export declare class CallServiceWeb extends WebPlugin implements CallServicePlugin {
    startCallService(_options?: StartCallServiceOptions): Promise<void>;
    updateCallService(_options?: UpdateCallServiceOptions): Promise<void>;
    stopCallService(): Promise<void>;
}
