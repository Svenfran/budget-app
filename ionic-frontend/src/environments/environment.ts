// Gilt für Dev und Prod. Die Backend-URL kommt aus src/config/config.ts,
// das im Production-Build per `fileReplacements` (angular.json) durch config.prod.ts ersetzt wird.

import { appConfig } from "src/config/config";

export const environment = {
  production: false,
  apiBaseUrl: `${appConfig.baseUrl}`,
  websocketUrl: `${appConfig.baseUrl}/ws`
};

/*
 * For easier debugging in development mode, you can import the following file
 * to ignore zone related error stack frames such as `zone.run`, `zoneDelegate.invokeTask`.
 *
 * This import should be commented out in production mode because it will have a negative impact
 * on performance if an error is thrown.
 */
// import 'zone.js/plugins/zone-error';  // Included with Angular CLI.
