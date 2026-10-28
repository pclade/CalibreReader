# Calibre To Go

Android-App zum lesenden Zugriff auf Calibre-Bibliotheken in OneDrive.
Der Projekt- und Paketname lautet weiterhin `CalibreReader` beziehungsweise
`ch.sakru.calibrereader`.

## Aktueller Funktionsumfang

- Anmeldung mit einem Microsoft-365-Geschäfts- oder Schulkonto.
- Ordnernavigation und Erkennung einer Calibre-Bibliothek über `metadata.db`.
- Lokal gespeicherte Bibliotheksauswahl und Listen- oder Rasteransicht.
- Anzeige von Büchern und Covern sowie Download und Öffnen von Buchdateien.
- Suche nach Titel oder Autor ohne Beachtung der Gross-/Kleinschreibung.
- Sortierung nach Titel oder Autor, jeweils auf- oder absteigend.

Die App schreibt nichts in OneDrive. Die gemeinsame Schnittstelle `CloudStorage`
wird für Metadaten, Cover und Buchdateien verwendet. Google Drive ist im
Anbietermodell vorgesehen; Google Drive und Dropbox sind noch nicht implementiert.
Die Suchanfrage und die Sortierauswahl gelten bisher nur für die laufende Sitzung.

## Projekt bauen

Voraussetzungen:

- JDK 17.
- Android SDK Platform 36 und Build Tools 36.0.0.
- Android Studio oder ein über `ANDROID_HOME` beziehungsweise `local.properties`
  konfiguriertes Android SDK.

Der vollständige Gradle-Wrapper ist eingecheckt. Eine separate Gradle-Installation
ist nicht erforderlich. Beim ersten Aufruf werden Gradle und die Abhängigkeiten
heruntergeladen; die Gradle-Distribution wird anhand ihrer SHA-256-Prüfsumme geprüft.

Windows:

```cmd
gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux und macOS:

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

Die Debug-APK liegt anschliessend unter
`app/build/outputs/apk/debug/app-debug.apk`.

Verwendete Build-Versionen:

| Komponente | Version |
| --- | --- |
| Gradle | 9.5.0 |
| Android Gradle Plugin | 9.3.3 |
| Kotlin und Compose-Compiler-Plugin | 2.3.20 |
| Compose BOM | 2026.06.00 |
| Activity Compose | 1.13.0 |
| MSAL Android | 8.4.1 |

Die Version von Material3 wird gemeinsam mit den übrigen Compose-Bibliotheken
über die Compose BOM verwaltet.

## Microsoft-Anmeldung konfigurieren

Die verwendete Microsoft-Entra-App-Registration benötigt die Android-Plattform
mit dem Paketnamen `ch.sakru.calibrereader` und dem Signatur-Hash des Zertifikats,
mit dem die installierte App signiert wurde. Für den aktuellen Kontotyp ist eine
Registrierung für Konten aus beliebigen Organisationsverzeichnissen vorgesehen.

Die folgenden Dateien müssen zur App-Registration und zum verwendeten
Signaturzertifikat passen:

- `app/src/main/res/raw/auth_config.json`: Client-ID und MSAL-Redirect-URI.
- `app/src/main/AndroidManifest.xml`: Redirect-URI für `BrowserTabActivity`.

Die von Entra angezeigte MSAL-Konfiguration ist die Grundlage. Der Signatur-Hash
im Redirect-URI der JSON-Konfiguration ist URL-kodiert; der `android:path` im
Manifest enthält den nicht URL-kodierten Hash mit führendem `/`.

Für Microsoft Graph werden die delegierten Berechtigungen `User.Read` und
`Files.Read` verwendet. Der Zugriff auf `/me/drive` erfolgt für das jeweils
angemeldete Konto.

Ein erfolgreicher Build bestätigt noch keine Anmeldung: Auf einem anderen
Entwicklungsrechner kann ein anderes Debug-Zertifikat verwendet werden. Dessen
Hash muss ebenfalls in der Entra-Registrierung hinterlegt sein.

## Nächste Erweiterungen

- Dropbox und Google Drive mit eigener Anmeldung und Anbieterauswahl.
- Getrennte Sitzungs- und Cache-Zuordnung für mehrere Cloud-Anbieter und Bibliotheken.
- Vorbereitung der Release-Signierung und der Veröffentlichung im Google Play Store.
