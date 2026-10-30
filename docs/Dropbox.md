# Dropbox für Calibre To Go

Diese Einrichtung erfolgt einmal durch den App-Entwickler. Spätere Nutzer melden
sich mit ihrem eigenen Dropbox-Konto an; sie erstellen keine Entwickler-App.

## 1. Dropbox-App registrieren

Öffne die [Dropbox App Console](https://www.dropbox.com/developers/apps/create).
Erstelle eine App mit **Scoped access** und **Full Dropbox**. So lässt sich eine
vorhandene Calibre-Bibliothek auch ausserhalb eines speziellen App-Ordners auswählen.

Aktiviere unter **Permissions** diese Scopes und speichere die Auswahl:

- `account_info.read`
- `files.metadata.read`
- `files.content.read`

Die App fordert genau diese lesenden Scopes an.

## 2. Öffentlichen App-Key hinterlegen

Kopiere unter **Settings** den **App key**. Ergänze die vorhandene, von Git
ausgeschlossene Datei `local.properties` um eine zusätzliche Zeile:

```properties
DROPBOX_APP_KEY=DEIN_OEFFENTLICHER_APP_KEY
```

Die vorhandene SDK-Konfiguration bleibt bestehen. Alternativ sind
`-PDROPBOX_APP_KEY=...` beim Gradle-Aufruf oder die Umgebungsvariable
`DROPBOX_APP_KEY` möglich.

Synchronisiere das Projekt in Android Studio und baue es neu:

```cmd
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Das Redirect-Schema im Manifest entsteht automatisch als `db-<APP_KEY>`.
MainActivity und Dropbox AuthActivity verwenden den vom SDK vorgesehenen
Single-Task-Modus.

Der Android-Login verwendet den Authorization-Code-Flow mit PKCE. Dafür wird
kein App-Secret eingebaut und kein manuell erzeugter Zugriffstoken benötigt.
Der öffentliche App-Key identifiziert die App; die Kontofreigabe bestätigt der
jeweilige Nutzer im Dropbox-Anmeldefenster.

## 3. Auf dem Gerät prüfen

1. **Mit Dropbox verbinden** auswählen und die Freigabe bestätigen.
2. Den Ordner mit `metadata.db` auswählen und **Diese Bibliothek verwenden** drücken.
3. Buchliste, Cover, Suche, Sortierung und das Öffnen einer Buchdatei prüfen.
4. Zur Bibliotheksauswahl zurückkehren und zwischen Dropbox und OneDrive wechseln.
5. Nach einem App-Neustart Dropbox auswählen; eine gespeicherte Sitzung wird
   ohne erneute interaktive Anmeldung wiederhergestellt.
6. **Abmelden** prüfen. Das entfernt die lokal gespeicherte Anmeldung.

Der SDK-Login unterstützt die installierte Dropbox-App und den Browser als
Rückfall. Beide Wege benötigen einen Test auf einem echten Android-Gerät.

## Speicherung und Dateizugriff

Kurzlebige Access-Tokens erneuert das SDK anhand des Refresh-Tokens. Gespeicherte
Credentials sind mit AES-GCM und einem nicht exportierbaren Schlüssel aus dem
Android Keystore verschlüsselt. Die Credential-Datei ist von Cloud-Backups und
Gerätemigrationen ausgeschlossen.

Die App listet Ordner und lädt Metadaten, Cover und Bücher herunter. Änderungen
an der Dropbox-Bibliothek sind in der Speicher-Schnittstelle nicht vorgesehen.
Eine Bibliothek direkt im Dropbox-Stammverzeichnis wird ebenfalls erkannt.
Bei Team-Konten wird die vom Konto gemeldete Root-Namespace verwendet.

## Referenzen

- [Dropbox OAuth Guide](https://docs.dropboxapi.com/dropbox-api/docs/oauth)
- [Offizielles Java-/Android-SDK](https://github.com/dropbox/dropbox-sdk-java)
- [SDK-Konfiguration für Android](https://github.com/dropbox/dropbox-sdk-java#required-configuration-for-authentication-on-android)
