import type { CapacitorConfig } from '@capacitor/cli';

const isDev = process.env["APP_ENV"] === 'dev';

const config: CapacitorConfig = {
  appId: isDev ? 'de.svenfran.divvyapp.dev' : 'de.svenfran.divvyapp',
  appName: isDev ? 'Divvy-Dev' : 'Divvy',
  webDir: 'www',
  server: {
    cleartext: isDev ? true : false,
    androidScheme: isDev ? 'http' : 'https'
  },
  plugins: {
    StatusBar: {
      overlaysWebView: false
    }
  }
};

export default config;
