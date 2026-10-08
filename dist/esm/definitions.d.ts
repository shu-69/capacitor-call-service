import type { PluginListenerHandle } from '@capacitor/core';
export interface StartCallServiceOptions {
    title?: string;
    body?: string;
    partnerName?: string;
    partnerPhoto?: string;
    callType?: 'video' | 'voice';
    durationSeconds?: number;
    isMuted?: boolean;
    isSpeakerOn?: boolean;
}
export interface UpdateCallServiceOptions {
    title?: string;
    body?: string;
    partnerName?: string;
    partnerPhoto?: string;
    durationSeconds?: number;
    isMuted?: boolean;
    isSpeakerOn?: boolean;
}
export interface StartCallServiceResult {
    started: boolean;
    grantedType?: 'mic-only' | 'mic+camera' | 'none';
    error?: string;
}
export type AudioOutputType = 'earpiece' | 'speaker' | 'wired_headset' | 'bluetooth';
export interface AudioOutputDevice {
    type: AudioOutputType;
    name: string;
}
export interface AudioOutputsResult {
    available: AudioOutputDevice[];
    active: AudioOutputType | null;
    hasBluetoothPermission: boolean;
}
export interface SetAudioOutputOptions {
    type: AudioOutputType;
}
export interface StartAudioRoutingOptions {
    callType?: 'voice' | 'video';
}
export interface CallServicePlugin {
    startCallService(options?: StartCallServiceOptions): Promise<StartCallServiceResult>;
    updateCallService(options?: UpdateCallServiceOptions): Promise<void>;
    stopCallService(): Promise<void>;
    getAudioOutputs(): Promise<AudioOutputsResult>;
    setAudioOutput(options: SetAudioOutputOptions): Promise<void>;
    startAudioRouting(options?: StartAudioRoutingOptions): Promise<AudioOutputsResult>;
    stopAudioRouting(): Promise<void>;
    requestBluetoothPermission(): Promise<{
        granted: boolean;
    }>;
    addListener(eventName: 'hangup_pressed', listenerFunc: () => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'mute_pressed', listenerFunc: () => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'speaker_pressed', listenerFunc: () => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'audioOutputsChanged', listenerFunc: (result: AudioOutputsResult) => void): Promise<PluginListenerHandle>;
    removeAllListeners(): Promise<void>;
}
