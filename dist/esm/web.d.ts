import { WebPlugin } from '@capacitor/core';
import type { CallServicePlugin, StartCallServiceOptions, StartCallServiceResult, UpdateCallServiceOptions, AudioOutputsResult, SetAudioOutputOptions, StartAudioRoutingOptions } from './definitions';
export declare class CallServiceWeb extends WebPlugin implements CallServicePlugin {
    startCallService(_options?: StartCallServiceOptions): Promise<StartCallServiceResult>;
    updateCallService(_options?: UpdateCallServiceOptions): Promise<void>;
    stopCallService(): Promise<void>;
    getAudioOutputs(): Promise<AudioOutputsResult>;
    setAudioOutput(_options: SetAudioOutputOptions): Promise<void>;
    startAudioRouting(_options?: StartAudioRoutingOptions): Promise<AudioOutputsResult>;
    stopAudioRouting(): Promise<void>;
    requestBluetoothPermission(): Promise<{
        granted: boolean;
    }>;
}
