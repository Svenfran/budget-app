// Vorlage für die (gitignorierten) Backend-URLs:
// - nach src/config/config.ts kopieren (dev, Tests): LAN-IP des Rechners mit dem Backend, Port 9090
// - nach src/config/config.prod.ts kopieren (Production-Build): Railway-URL des Backends
export const appConfig = {
  baseUrl: "http://localhost:9090"
};
