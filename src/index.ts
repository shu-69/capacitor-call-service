import { registerPlugin } from '@capacitor/core';
import type { CallServicePlugin } from './definitions';

const CallService = registerPlugin<CallServicePlugin>('CallService', {
  web: () => import('./web').then(m => new m.CallServiceWeb()),
});

export * from './definitions';
export { CallService };
