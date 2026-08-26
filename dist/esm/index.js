import { registerPlugin } from '@capacitor/core';
const CallService = registerPlugin('CallService', {
    web: () => import('./web').then(m => new m.CallServiceWeb()),
});
export * from './definitions';
export { CallService };
//# sourceMappingURL=index.js.map