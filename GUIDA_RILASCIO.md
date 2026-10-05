# Guida: testare e creare l'AAB per il Play Store

Tutti i comandi si lanciano da PowerShell nella cartella del progetto (`E:\Progetti\Ricordella`).

## 1. Testare

### Test automatici (sul PC, non toccano il telefono)
```powershell
.\gradlew.bat test
```
Se fallisce, non rilasciare.

### Provare l'app sul telefono
Non usare `connectedAndroidTest`: **cancella i dati dell'app sul telefono**. Si installa solo con `adb install -r`, che aggiorna senza toccare i dati.

- **Build di debug** (pacchetto separato `com.remindella.app.debug`, dati separati, non tocca l'app dello Store):
  ```powershell
  .\gradlew.bat :app:assembleDebug
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  ```
- **Build di release** (identica a quella dello Store, minificata): è quella da provare prima di caricare, perché R8 può rompere cose che in debug funzionano.
  ```powershell
  .\gradlew.bat :app:assembleRelease
  adb install -r app\build\outputs\apk\release\app-release.apk
  ```
  Se sul telefono c'è l'app installata dallo Store, la firma è diversa (chiave Google) e `-r` fallisce: in quel caso non disinstallare (perderesti i dati), prova la build di debug.

Cosa controllare sulla release: avvio, backup/ripristino, widget (Sveglia, Timer, ecc.), timer e sveglie che suonano, notifiche, lingue.

## 2. Creare l'AAB

### Prerequisiti (nella radice del progetto, fuori da git)
- `keystore.properties`: password e alias della firma. Senza, la build non è firmata e lo script si ferma.
- `upload-keystore.jks`: la chiave di upload. **Tienine una copia fuori dal PC**: se la perdi devi chiedere a Google il reset.

### Generare
```powershell
.\release.ps1                 # versionCode +1, versionName con ultima cifra +1 (0.5.0 -> 0.5.1)
.\release.ps1 -Name 0.6.0     # versionName scelto (versionCode sempre +1)
.\release.ps1 -NoBump         # ricompila senza cambiare versione
```
Lo script aggiorna `app/build.gradle.kts`, compila APK e AAB firmati e copia il mapping in `simboli\<versione>\`.

Risultato da caricare:
```
app\build\outputs\bundle\release\app-release.aab
```
L'APK (`app\build\outputs\apk\release\app-release.apk`) serve solo per le prove sul telefono.

### Regole
- Il `versionCode` deve essere **più alto** di quello già caricato sulla Play Console, altrimenti rifiuta il file.
- Per una nuova versione, aggiungi in cima a `AllNews` in `feature/news/News.kt` un `NewsRelease` con lo stesso `versionCode`: le novità compaiono da sole al primo avvio.
- Aggiorna `note-rilascio.txt` (max 500 caratteri per lingua).
- Conserva `simboli\<versione>\mapping.txt` per ogni versione pubblicata: serve a leggere i crash (l'AAB lo contiene già, ma la copia è il tuo backup; la cartella non è in git).

## 3. Caricare sulla Play Console
1. Play Console › l'app › **Test e rilascio** › scegli la traccia (Test interni per provare, poi Produzione) › **Crea nuova release**.
2. Carica `app-release.aab`.
3. Incolla le note di rilascio da `note-rilascio.txt` (blocchi `<it-IT>`, `<en-US>`, ecc.).
4. **Salva › Verifica release › Avvia il rollout**.

Consiglio: passa sempre dai **Test interni** prima della Produzione, installa dal link Play e ricontrolla le funzioni principali.

## 4. Dopo la pubblicazione
```powershell
git add -A
git commit -m "Versione X.Y.Z (codice)"
git push
```
