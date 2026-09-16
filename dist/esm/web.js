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
}
//# sourceMappingURL=web.js.map