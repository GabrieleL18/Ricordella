# Ricordella

> *Pensaci tu, al resto pensa lei.*

Ricordella è un'app Android nativa per ricordare tutto ciò che ha una data, una scadenza, una ricorrenza,
una persona o una "cosa" associata: appuntamenti, compleanni, garanzie, manutenzioni, assicurazioni,
revisioni, pagamenti, rinnovi.

È **local-first**: funziona completamente offline, senza account, senza server e **senza il permesso `INTERNET`**.
La specifica completa è in [`Ricordella_specifica_tecnica_android.md`](Ricordella_specifica_tecnica_android.md).

## Funzionalità (MVP)

| Area | Cosa fa |
|---|---|
| **Home** | "Cosa devo ricordarmi?": elementi che richiedono attenzione (scaduti, urgenti, scadenze vicine, km in arrivo), oggi, prossimamente |
| **Calendario** | Vista mese con indicatori (attività, eventi, scadenze), vista giorno, agenda a 90 giorni; le ricorrenze sono proiettate senza salvarle |
| **Promemoria** | Ricerca e filtri eseguiti da SQLite (Tutti/Oggi/In arrivo/Scaduti/Completati + persona, cosa, categoria, tipo, priorità), ordinamento, completamento rapido |
| **Creazione rapida** | `+` → Promemoria / Evento / Cosa / Persona. Solo titolo e data sono obbligatori |
| **Ricorrenze** | Ogni giorno, settimana, 2 settimane, mese, 3 mesi, 6 mesi, anno, personalizzata (intervallo, giorni della settimana, data di fine) |
| **Cose** | Categorie predefinite (veicoli, casa, elettronica, documenti, generico), creazione guidata con promemoria suggeriti, garanzia con stato (attiva / in scadenza / scaduta), storico manutenzioni, chilometraggio, foto, documenti allegati |
| **Persone** | Scheda con oggi, prossimi eventi, promemoria e cose associate (con ruolo) |
| **Notifiche** | Canale dedicato, azioni *Completa*, *Tra 10 min*, *Domani*; tocco → dettaglio; ricostruzione dopo riavvio, aggiornamento app, cambio ora/fuso |
| **Rimanda** | 10 minuti, 1 ora, domani mattina, data e ora a scelta (logica centralizzata nel dominio) |
| **Ricerca globale** | Persone, cose, promemoria/eventi, manutenzioni e note |
| **Backup** | Esportazione/importazione `.zip` via Storage Access Framework, con manifest versionato, firma SHA-256, controllo di integrità, anteprima e ripristino transazionale |
| **Impostazioni** | Tema chiaro/scuro/sistema, primo giorno della settimana, formato data, notifiche, anticipo predefinito, orario dei promemoria "tutto il giorno", privacy |

## Stack

Kotlin · Jetpack Compose + Material 3 · Navigation Compose (route type-safe) · layout adattivo
(`NavigationSuiteScaffold`: barra in basso su telefono, rail su tablet/orizzontale) · Room · DataStore ·
ViewModel + StateFlow · Coroutines · `java.time` · AlarmManager · kotlinx.serialization (solo backup e route).

`minSdk 28` · `targetSdk/compileSdk 37`.

## Architettura

```
app/src/main/java/com/ricordella/app/
├── core/
│   ├── date/            formattazione di date e orari per la UI
│   ├── navigation/      route, AppNavigator, shell adattiva e "+"
│   ├── notifications/   ReminderNotifier, scheduler AlarmManager, receiver
│   ├── ui/              tema (RicordellaTheme), componenti riutilizzabili
│   └── AppContainer.kt  dependency injection manuale
├── data/
│   ├── local/           database Room, DAO, converter
│   ├── repository/      implementazioni Room/DataStore dei repository
│   └── backup/          formato e repository di backup
├── domain/
│   ├── model/           entità (usate anche da Room e dal backup), enum, regole
│   ├── date/            ricorrenze, stato temporale, pianificazione notifiche
│   ├── repository/      interfacce dei repository
│   └── usecase/         operazioni di dominio (salva, completa, rimanda, ...)
└── feature/             home, calendar, reminders, items, people, search, settings
```

Flusso dati unidirezionale: `Room → DAO → Repository → UseCase → ViewModel (StateFlow) → Compose`.
La UI non accede mai al database.

Scelte principali:

- **Identificatori UUID** stabili, necessari per backup e ripristino.
- **Stato temporale derivato** (scaduto/oggi/in arrivo) e mai salvato.
- **Ricorrenze come regole**: si salva solo la regola; al completamento il promemoria avanza all'occorrenza
  successiva e il completamento finisce nello storico (`reminder_completion`), senza duplicare righe.
- **Un solo allarme alla volta**: `ReminderScheduler` programma soltanto la prossima notifica; quando scatta
  mostra i promemoria dovuti e programma la successiva. Così non si supera mai il limite di allarmi di Android.
  Gli *exact alarm* vengono usati solo per promemoria con orario e solo se l'utente li consente.
- **Nessun backup automatico Android** (`allowBackup=false` + `dataExtractionRules`): il backup è manuale
  e resta sotto il controllo dell'utente.
- **Migrazioni Room** esportate in `app/schemas`, senza `fallbackToDestructiveMigration`.
- **Allegati e foto** salvati come riferimenti (URI con permesso persistente), senza duplicare i file.

## Compilare ed eseguire

Requisiti: Android Studio recente (o JDK 17+ e Android SDK 37).

```bash
./gradlew assembleDebug
```

L'APK viene generato in `app/build/outputs/apk/debug/`. Per installarlo su un dispositivo collegato:

```bash
./gradlew installDebug
```

## Test

```bash
./gradlew testDebugUnitTest          # test JVM: ricorrenze, date e ora legale, notifiche, use case, backup
./gradlew connectedDebugAndroidTest  # test Room su dispositivo/emulatore: relazioni, cancellazioni, query, ripristino
```

## Prossimi passi

Fuori dall'MVP, come da specifica: sincronizzazione opzionale, condivisione, OCR/AI locale,
promemoria basati sulla posizione. L'architettura li consente senza complicare la versione attuale.
