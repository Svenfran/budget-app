-- DIV-46. Tokens ohne DeviceId entfernen (der JwtAuthenticationFilter lehnt sie seit DIV-18 ohnehin ab), dann
-- token.device_id als Pflichtspalte festlegen. Betroffene Nutzer melden sich einmal neu an.
-- Wird auch von TokenDeviceIdNotNullIntegrationSpec als Native Query ausgeführt. Deshalb Anweisungen nur durch
-- Semikolon am Zeilenende trennen und in Kommentaren weder Semikolon noch Doppelpunkt verwenden.

DELETE FROM token WHERE device_id IS NULL;

ALTER TABLE token ALTER COLUMN device_id SET NOT NULL;
