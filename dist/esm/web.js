import { WebPlugin } from '@capacitor/core';
export class CallServiceWeb extends WebPlugin {
    async startCallService(_options) {
        console.warn('CallService foreground service is not supported on web platform.');
        return { started: false, grantedType: 'none' };
    }
    async updateCallService(_options) {
        console.warn('CallService foreground service is not supported on web platform.');
    }
    async stopCallService() {
        console.warn('CallService foreground service is not supported on web platform.');
    }
    async getAudioOutputs() {
        return {
            available: [{ type: 'speaker', name: 'Speaker' }],
            active: 'speaker',
            hasBluetoothPermission: true
        };
    }
    async setAudioOutput(_options) {
        // No-op on web
    }
    async startAudioRouting(_options) {
        return {
            available: [{ type: 'speaker', name: 'Speaker' }],
            active: 'speaker',
            hasBluetoothPermission: true
        };
    }
    async stopAudioRouting() {
        // No-op on web
    }
    async checkBluetoothPermission() {
        return { granted: true };
    }
    async requestBluetoothPermission() {
        return { granted: true };
    }
    async openAppSettings() {
        // No-op on web
    }
}
//# sourceMappingURL=web.js.map