import type { PluginListenerHandle } from '@capacitor/core';

export interface StartCallServiceOptions {
  title?: string;
  body?: string;
  partnerName?: string;
  callType?: 'video' | 'voice';
  durationSeconds?: number;
  isMuted?: boolean;
  isSpeakerOn?: boolean;
}

export interface UpdateCallServiceOptions {
  title?: string;
  body?: string;
  durationSeconds?: number;
  isMuted?: boolean;
  isSpeakerOn?: boolean;
}

export interface CallServicePlugin {
  startCallService(options?: StartCallServiceOptions): Promise<void>;
  updateCallService(options?: UpdateCallServiceOptions): Promise<void>;
  stopCallService(): Promise<void>;

  addListener(
    eventName: 'hangup_pressed',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'mute_pressed',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'speaker_pressed',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
