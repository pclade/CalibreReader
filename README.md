# Calibre To Go

Android-App zum lesenden Zugriff auf Calibre-Bibliotheken in OneDrive, Dropbox und Google Drive.
Der Projekt- und Paketname lautet weiterhin `CalibreReader` beziehungsweise
`ch.sakru.calibrereader`.

## Aktueller Funktionsumfang

- Anmeldung mit einem Microsoft-365-Geschäfts- oder Schulkonto.
- Dropbox-Anmeldung mit PKCE und verschlüsselt gespeicherter Sitzung.
- Google-Drive-Autorisierung über Google Play-Dienste mit erneuerbaren Zugriffstokens.
- Wechsel zwischen Cloud-Speichern und nach Anbieter und Konto getrennte Bibliotheken.
- Ordnernavigation und Erkennung einer Calibre-Bibliothek über `metadata.db`.
- Lokal gespeicherte Bibliotheksauswahl und Listen- oder Rasteransicht.
- Anzeige von Büchern und Covern sowie Download und Öffnen von Buchdateien.
- Suche nach Titel oder Autor ohne Beachtung der Gross-/Kleinschreibung.
- Sortierung nach Titel oder Autor, jeweils auf- oder absteigend.

Die App schreibt nichts in die Cloud-Bibliotheken. Die gemeinsame Schnittstelle `CloudStorage`
wird für Metadaten, Cover und Buchdateien verwendet. Google Drive unterstützt
Ordnernavigation, gespeicherte Bibliotheken und erreichbare Drive-Verknüpfungen.
Die Suchanfrage und die Sortierauswahl gelten bisher nur für die laufende Sitzung.

## Dropbox einrichten

Der Entwickler hinterlegt einmalig den öffentlichen Dropbox-App-Key als
`DROPBOX_APP_KEY` in `local.properties`, als Gradle-Property oder als gleichnamige
Umgebungsvariable. Die Schritte stehen in [docs/Dropbox.md](docs/Dropbox.md).
Ohne Key bleibt Dropbox in der Speicherauswahl deaktiviert. OneDrive und die
JVM-Tests benötigen diesen Key nicht.

## Google Drive einrichten

Im Google-Cloud-Projekt die Drive API aktivieren und einen Android-OAuth-Client
für `ch.sakru.calibrereader` sowie den SHA-1 der installierten App anlegen.
Für den ersten Test das eigene Konto als Testnutzer eintragen. Es wird kein
Google-Schlüssel in local.properties hinterlegt.
Einrichtung, Berechtigungen und Gerätetests stehen in
[docs/google-drive.md](docs/google-drive.md). Die verwendete Leseberechtigung
ist ein Restricted Scope und benötigt vor der öffentlichen Nutzung die
entsprechende Google-OAuth-Prüfung.

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
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux und macOS:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
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
| Dropbox Java-/Android-SDK | 7.1.1 |
| Google Play services Auth | 22.0.0 |
| Jackson Core (LTS) | 2.21.8 |

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

## Validierung

Die JVM-Tests verwenden lokale HTTP-Antworten und keine Cloud-Konten. Sie prüfen
Paginierung, Dateipfade, Token-Erneuerung, Verschlüsselung, Kontowechsel und den
Abbruch laufender Anfragen. Cover werden nach Anbieter, Konto, Bibliothek und
Buch-ID getrennt und mit einem Speicherlimit gehalten. Heruntergeladene Buchdateien
liegen ebenfalls in getrennten Verzeichnissen pro Anbieter, Konto und Bibliothek.

Für Dropbox und Google Drive sind nach ihrer jeweiligen Entwickler-Konfiguration
zusätzliche Tests der echten Anmeldung, Cover und Buchdateien auf einem Android-Gerät erforderlich.

## Nächste Erweiterungen

- Vorbereitung der Release-Signierung und der Veröffentlichung im Google Play Store.
