import { WebPlugin } from '@capacitor/core';
import type { CallServicePlugin, StartCallServiceOptions, StartCallServiceResult, UpdateCallServiceOptions } from './definitions';
export declare class CallServiceWeb extends WebPlugin implements CallServicePlugin {
    startCallService(_options?: StartCallServiceOptions): Promise<StartCallServiceResult>;
    updateCallService(_options?: UpdateCallServiceOptions): Promise<void>;
    stopCallService(): Promise<void>;
}
