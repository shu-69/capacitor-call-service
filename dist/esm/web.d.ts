import { WebPlugin } from '@capacitor/core';
import type { CallServicePlugin, StartCallServiceOptions, StartCallServiceResult, UpdateCallServiceOptions, StartAvailabilityServiceOptions, StartAvailabilityServiceResult, UpdateAvailabilityServiceOptions, AudioOutputsResult, SetAudioOutputOptions, StartAudioRoutingOptions, CallIntentResult } from './definitions';
export declare class CallServiceWeb extends WebPlugin implements CallServicePlugin {
    startCallService(_options?: StartCallServiceOptions): Promise<StartCallServiceResult>;
    updateCallService(_options?: UpdateCallServiceOptions): Promise<void>;
    stopCallService(): Promise<void>;
    startAvailabilityService(_options?: StartAvailabilityServiceOptions): Promise<StartAvailabilityServiceResult>;
    updateAvailabilityNotification(_options?: UpdateAvailabilityServiceOptions): Promise<void>;
    stopAvailabilityService(): Promise<void>;
    getAudioOutputs(): Promise<AudioOutputsResult>;
    setAudioOutput(_options: SetAudioOutputOptions): Promise<void>;
    startAudioRouting(_options?: StartAudioRoutingOptions): Promise<AudioOutputsResult>;
    stopAudioRouting(): Promise<void>;
    checkBluetoothPermission(): Promise<{
        granted: boolean;
    }>;
    requestBluetoothPermission(): Promise<{
        granted: boolean;
    }>;
    openAppSettings(): Promise<void>;
    getPendingCallIntent(): Promise<CallIntentResult>;
    clearPendingCallIntent(): Promise<void>;
    isDeviceLocked(): Promise<{
        isLocked: boolean;
    }>;
    sendToBackground(): Promise<void>;
}
