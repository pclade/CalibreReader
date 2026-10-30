# Google Drive einrichten

Calibre To Go greift mit Google Identity Services und der Drive API v3 lesend auf
bestehende Calibre-Bibliotheken zu. Die Google-Anmeldung benötigt keine Client-ID,
keinen API-Key und kein Client-Secret in der APK oder in local.properties:
Google Play-Dienste erkennen den registrierten Android-Client anhand von
Paketname und Signaturzertifikat.

## Einmalige Einrichtung durch den Entwickler

1. In der [Google Cloud Console](https://console.cloud.google.com/) ein Projekt
   erstellen oder auswählen.
2. Für genau dieses Projekt die **Google Drive API** aktivieren.
3. Unter **Google Auth Platform** die App-Bezeichnung **Calibre To Go** und die
   Kontaktangaben einrichten. Für persönliche Google-Konten die Zielgruppe
   **External** wählen und zunächst den Veröffentlichungsstatus **Testing** verwenden.
4. Den eigenen Google-Account unter **Audience / Test users** hinzufügen.
5. Unter **Data Access** den verwendeten Scope eintragen:
   `https://www.googleapis.com/auth/drive.readonly`.
6. Unter **Clients** einen OAuth-Client vom Typ **Android** anlegen.
   Paketname: `ch.sakru.calibrereader`.
   Dazu den SHA-1-Fingerabdruck genau des Zertifikats eintragen, mit dem die
   installierte Test-App signiert ist.

Den Debug-Fingerabdruck auf dem eigenen Entwicklungsrechner ermitteln:

```powershell
Set-Location 'C:\dev\561-android-studio-projects\calibre-reader'
.\gradlew.bat :app:signingReport
```

Den Wert **SHA1** aus der Variante **debug** übernehmen. Ein anderer Rechner kann
einen anderen Debug-Schlüssel besitzen. Der SHA-1-Wert aus GitHub Actions
ersetzt den lokalen Fingerabdruck nicht.

Für die spätere Play-Store-Version einen weiteren Android-Client mit dem
**App-Signing-Zertifikat von Google Play** registrieren. Dessen SHA-1 steht in
der Play Console; der Upload-Schlüssel kann ein anderes Zertifikat sein.

Für diese rein lokale Integration ist kein Web-OAuth-Client, kein eigener
Token-Server und kein google-services.json erforderlich. Endnutzer legen keine
Entwicklerprojekte an.

## Zugriff und Konten

- Google Play-Dienste führen Kontoauswahl und Berechtigungsdialog aus.
- Nach der Zustimmung ermittelt die App Konto-Adresse und stabile Drive-Konto-ID
  über `about.get`. Gespeicherte Bibliotheken und Caches bleiben nach Anbieter,
  Konto und Bibliothek getrennt.
- Kurzlebige Zugriffstokens werden bei Bedarf erneut über AuthorizationClient
  angefordert. Bei HTTP 401 wird der ungültige Cache-Token entfernt und die
  Anfrage einmal mit einem neuen Token wiederholt.
- Die App speichert selbst keine Google-Zugriffs- oder Refresh-Tokens.
  Nur die Konto-Zuordnung wird mit AES-GCM und Android Keystore verschlüsselt
  lokal abgelegt und von Backup und Geräteübertragung ausgeschlossen.
- **Abmelden** widerruft die Google-Berechtigung. Zur nächsten Verbindung ist
  wieder eine Zustimmung erforderlich. Auch unter
  [Google-Konto / Drittanbieter-Verbindungen](https://myaccount.google.com/connections)
  kann der Zugriff entfernt werden.
- **Cloud-Anbieter wechseln** beendet die aktive Ansicht; die Konto-Zuordnung
  bleibt für eine spätere Verbindung erhalten.

## Bibliotheksordner

Die Navigation beginnt in **Meine Ablage**. Wähle den Ordner, der metadata.db
und die zugehörigen Autoren- und Buchordner enthält. Eine Bibliothek direkt im
Stammverzeichnis wird ebenfalls unterstützt.

Mehrseitige Ordnerlisten werden vollständig gelesen. Ordner- und Datei-
Verknüpfungen werden über ihre Ziel-ID aufgelöst; erforderliche Resource Keys
werden mitgeführt und mit gespeicherten Ordner-IDs erhalten. Ein erreichbarer
geteilter Ordner lässt sich über eine Verknüpfung in Meine Ablage öffnen.
Eine eigene Ansicht zum Auflisten aller geteilten Ablagen ist derzeit nicht enthalten.

Google Drive erlaubt mehrere Einträge mit demselben Namen im selben Ordner.
Bei einem mehrdeutigen Calibre-Pfad zeigt die App einen Fehler, damit keine
zufällige Datei als Cover oder Buch verwendet wird. Solche Dubletten müssen in
Drive bereinigt werden. Google-Docs-Dateien sind keine binären Calibre-Dateien.

Die App verwendet ausschliesslich lesende API-Anfragen. Änderungen der
Desktop-Bibliothek müssen bereits vollständig nach Drive synchronisiert sein,
bevor die Bibliothek in der App neu geladen wird.

## Veröffentlichung

`drive.readonly` erlaubt lesenden Zugriff auf alle für das Konto erreichbaren
Drive-Dateien, nicht nur auf einen einzelnen ausgewählten Ordner. Die App
benötigt dies für ihre vorhandene Ordnernavigation und den Zugriff auf bereits
vorhandene metadata.db, Cover und Buchdateien. `drive.file` ist auf einzelne der
App zugänglich gemachte Dateien begrenzt und erfordert einen anderen Auswahl-
und Freigabeablauf.

Google stuft `drive.readonly` als **Restricted Scope** ein. Vor der öffentlichen
Nutzung ist die OAuth-Verifizierung vorzubereiten: Branding, Datenschutzerklärung,
Begründung des Datenzugriffs und Demonstration des Ablaufs. Für serverseitige
Speicherung oder Übertragung eingeschränkter Drive-Daten nennt Google zusätzliche
Anforderungen an eine Sicherheitsbewertung. Diese Integration verwendet keinen
eigenen Server.

Quellen:
- [Google: Android-Autorisierung](https://developer.android.com/identity/authorization)
- [Google: Drive-Berechtigungen](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Google: Resource Keys](https://developers.google.com/workspace/drive/api/guides/resource-keys)

## Gerätetest

Nach der Cloud-Einrichtung auf einem Gerät oder Emulator mit Google Play-Diensten:

1. Google Drive wählen, richtiges Testkonto auswählen und die Leseberechtigung erlauben.
2. Einen Calibre-Ordner auswählen; Bücher, Cover und mindestens eine Buchdatei öffnen.
3. App neu starten und dieselbe Bibliothek erneut öffnen.
4. Den Anmeldedialog abbrechen und das Gerät während des Dialogs drehen; anschliessend erneut verbinden.
5. Mit einem zweiten Konto prüfen, dass die gespeicherten Bibliotheken des ersten
   Kontos nicht erscheinen.
6. Abmelden, erneut verbinden und den erneuten Zustimmungsdialog prüfen.
7. Zwischen Google Drive, Dropbox und OneDrive wechseln.
8. Wenn möglich einen Bibliotheksordner im Stammverzeichnis sowie eine
   zugängliche Ordner-Verknüpfung prüfen.

Ein erfolgreicher JVM-Test ersetzt diese echten Konto- und Gerätetests nicht.

## Fehler einordnen

- **App-Version noch nicht korrekt eingerichtet:** Paketname, Android-OAuth-Client,
  SHA-1 der tatsächlich installierten APK und verwendetes Cloud-Projekt prüfen.
- **API noch nicht aktiviert:** Drive API im Projekt des OAuth-Clients aktivieren.
- **Zugriff blockiert / App nicht verifiziert:** Testnutzer, Zielgruppe,
  Veröffentlichungsstatus und angeforderte Berechtigung prüfen.
- **Erneut verbinden:** Berechtigung wurde entfernt oder muss neu bestätigt werden.
- **Name existiert mehrfach:** Dubletten im angegebenen Drive-Ordner bereinigen.
