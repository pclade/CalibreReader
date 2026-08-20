# CalibreReader – Phase 1

Minimaler Android-Prototyp für:

1. Anmeldung mit einem Microsoft-365-Geschäfts-/Schulkonto
2. Delegierter Nur-Lese-Zugriff via Microsoft Graph
3. Anzeige des Root-Inhalts des OneDrive des angemeldeten Benutzers

Die App schreibt **nichts** in OneDrive. Für Microsoft Graph werden nur folgende delegierte Scopes angefordert:

- `User.Read`
- `Files.Read`

## Voraussetzungen

- Android Studio
- JDK 17
- Android SDK 36
- Eine Microsoft-Entra-App-Registration

## Entra-Konfiguration

Die App Registration soll für diesen Prototyp als Multi-Tenant-App konfiguriert sein:

**Accounts in any organizational directory (Any Microsoft Entra ID tenant – Multitenant)**

Unter **Authentication → Add a platform → Android**:

- Package name: `ch.sakru.calibrereader`
- Development Signature Hash eintragen
- Die von Entra angezeigte MSAL-Konfiguration kopieren
- **Allow public client flows = Yes**

Unter **API permissions → Microsoft Graph → Delegated permissions**:

- `User.Read`
- `Files.Read`

## Zwei Platzhalter ersetzen

### 1. `app/src/main/res/raw/auth_config.json`

Ersetze:

- `YOUR_CLIENT_ID`
- `YOUR_URL_ENCODED_SIGNATURE_HASH`

Am sichersten ist es, den kompletten von Entra erzeugten `redirect_uri` zu übernehmen.

### 2. `app/src/main/AndroidManifest.xml`

Ersetze:

- `YOUR_SIGNATURE_HASH`

Hier muss der Signatur-Hash **nicht URL-encodiert** stehen. Der führende `/` ist bereits vorhanden.

## Gradle

Das Projekt verwendet:

- Android Gradle Plugin 8.13.2
- Kotlin 2.3.20
- Jetpack Compose BOM 2026.06.00
- Activity Compose 1.13.0
- MSAL Android 8.4.1

Die Datei `gradle/wrapper/gradle-wrapper.properties` ist enthalten. Die Binärdatei
`gradle-wrapper.jar` ist aus Portabilitätsgründen nicht im ZIP enthalten. Android Studio
kann beim Öffnen eine Gradle-Konfiguration bzw. einen Wrapper anlegen/aktualisieren.

Falls du das Projekt lieber aus einem frisch erzeugten Android-Studio-Projekt startest,
kannst du auch einfach die Ordner `app/src/main/...` sowie die Dependencies übernehmen.

## Erwartetes Ergebnis

Nach erfolgreichem Login:

    OneDrive
    user@firma.ch

    📁 Dokumente
    📁 778 Calibre
    📁 Bilder
    ...

Der Graph-Aufruf lautet:

    GET https://graph.microsoft.com/v1.0/me/drive/root/children

`/me` bezieht sich auf den aktuell angemeldeten Benutzer, nicht auf das OneDrive des App-Entwicklers.

## Nächster Schritt

Phase 2:

- Ordnernavigation
- Calibre-Stammordner auswählen
- `metadata.db` erkennen
- `metadata.db` read-only herunterladen und lokal auswerten
- erste Bücher mit Titel, Autor und Cover darstellen
