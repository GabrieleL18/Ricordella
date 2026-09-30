# RICORDELLA — Specifica tecnica completa per la realizzazione dell'app Android

## 1. Obiettivo del progetto

Realizzare un'app Android nativa chiamata **Ricordella**.

Ricordella deve essere un grande sistema personale di promemoria e organizzazione, pensato per ricordare all'utente tutto ciò che può avere una data, una scadenza, una ricorrenza, una persona o una "cosa" associata.

L'idea non è creare una semplice lista To-Do, ma una **memoria digitale personale locale**.

L'utente deve poter gestire, per esempio:

- appuntamenti;
- attività da fare;
- scadenze;
- compleanni;
- garanzie;
- manutenzioni;
- assicurazioni;
- revisioni;
- pagamenti;
- rinnovi;
- controlli periodici;
- attività domestiche;
- attività legate a persone;
- attività legate a oggetti, elettrodomestici, veicoli e dispositivi.

Esempio:

> "Ricordami di controllare la lavatrice ogni 6 mesi."

oppure:

> "La garanzia della lavatrice scade il 12 marzo 2028."

oppure:

> "Ricordami di fare il tagliando della macchina tra 15.000 km."

L'app deve essere **local-first** e, nella versione iniziale, completamente utilizzabile senza account, server o connessione Internet.

---

# 2. Principi fondamentali

Questi principi devono essere rispettati durante tutto lo sviluppo.

## 2.1 Local-first

Tutti i dati funzionali dell'app devono essere salvati sul dispositivo.

Non utilizzare:

- backend proprietari;
- database remoto;
- Firebase;
- autenticazione obbligatoria;
- sincronizzazione cloud;
- analytics obbligatori;
- servizi web per il normale funzionamento dell'app.

L'app non deve dipendere dalla connessione Internet.

La UI, il database, le ricerche, il calendario, le attività ricorrenti e le notifiche devono funzionare offline.

Preferire inoltre un'app che non richieda il permesso `INTERNET` nel manifest.

Per la persistenza dei dati strutturati utilizzare Room/SQLite. Room è il livello di persistenza Android consigliato rispetto all'accesso diretto a SQLite. 

## 2.2 Semplicità

L'utente deve poter creare un semplice promemoria in pochi secondi.

Flusso minimo:

`+` → `Promemoria` → titolo → data → salva.

Tutte le altre informazioni devono essere opzionali.

## 2.3 Collegamento tra elementi

Il valore principale dell'app è il collegamento tra:

`Persone ↔ Cose ↔ Promemoria ↔ Eventi ↔ Scadenze`

Esempio:

`Gabriele ↔ Fiat Panda ↔ Tagliando ↔ 23/10/2026`

Toccando un elemento collegato, l'utente deve poter aprire rapidamente il relativo dettaglio.

## 2.4 Nessuna duplicazione inutile

Il progetto deve avere componenti ben separati e responsabilità chiare.

Non creare più classi che fanno la stessa cosa.

Utilizzare nomi descrittivi e coerenti.

Esempi:

- `ReminderRepository`
- `ReminderScheduler`
- `ReminderAlarmReceiver`
- `ReminderViewModel`

Evitare nomi generici come:

- `Manager`
- `Helper`
- `Utils`
- `Common`
- `DataManager`

quando è possibile descrivere meglio la responsabilità.

---

# 3. Stack tecnologico Android

Utilizzare:

- Kotlin;
- Android nativo;
- Jetpack Compose per tutta la UI;
- Material 3;
- Room per il database locale;
- DataStore per le preferenze dell'app;
- ViewModel;
- Kotlin Coroutines;
- Kotlin Flow/StateFlow;
- Navigation Compose o l'attuale soluzione di navigazione Jetpack stabile raccomandata per una nuova app Compose;
- `java.time` per date e orari;
- AlarmManager per promemoria temporali che richiedono una schedulazione Android;
- NotificationCompat / Android notification APIs per le notifiche;
- Storage Access Framework per esportazione/importazione dei backup;
- Android Photo Picker o equivalente sistema Android quando servono immagini/allegati.

Jetpack Compose è il toolkit UI raccomandato per una nuova app Android moderna. 

Per le schermate utilizzare un flusso dati unidirezionale: ViewModel espone lo stato alla UI e la UI invia eventi al ViewModel. 

Per una navigazione coerente utilizzare una soluzione Jetpack Compose e mantenere una singola sorgente di verità per lo stato della navigazione. 

---

# 4. Compatibilità Android

Creare una normale app Android per smartphone.

Configurare:

- `compileSdk`: ultima versione stabile disponibile nell'ambiente di sviluppo;
- `targetSdk`: ultima versione stabile disponibile;
- `minSdk`: Android 9 / API 28, salvo incompatibilità concreta con le librerie scelte.

Non fissare versioni obsolete delle librerie nel codice se è possibile utilizzare un BOM o la versione stabile compatibile più recente.

Per Compose utilizzare il Compose BOM in modo da mantenere coerenti le versioni delle librerie Compose.

L'app deve essere adattabile anche a:

- orientamento verticale;
- orientamento orizzontale;
- tablet;
- schermi più larghi.

Su schermi compatti utilizzare una navigation bar inferiore; su schermi più grandi adattare la navigazione a una rail o layout equivalente. Android fornisce componenti per questo tipo di navigazione adattiva. 

---

# 5. Struttura funzionale dell'app

La versione iniziale deve avere queste sezioni principali:

1. Home
2. Calendario
3. Promemoria
4. Cose
5. Persone
6. Impostazioni

In alternativa, se la navigation bar risulta troppo affollata, `Impostazioni` può essere raggiungibile dall'icona profilo/menu in alto.

Il pulsante `+` deve essere sempre facilmente raggiungibile e consentire la creazione rapida di:

- Promemoria
- Evento
- Cosa
- Persona

---

# 6. Modello concettuale

## 6.1 Persona

Rappresenta una persona alla quale possono essere associate attività e cose.

Campi indicativi:

- `id`
- `name`
- `surname`
- `displayName`
- `photoUri`
- `notes`
- `isArchived`
- `createdAt`
- `updatedAt`

Il nome visualizzato deve essere calcolabile senza duplicare inutilmente dati.

## 6.2 Cosa

"Cosa" è il termine generico dell'app per rappresentare un elemento personale.

Può essere:

- auto;
- moto;
- lavatrice;
- frigorifero;
- televisore;
- telefono;
- computer;
- elettrodomestico;
- attrezzatura;
- documento;
- altro.

Campi base:

- `id`
- `name`
- `type`
- `category`
- `brand`
- `model`
- `serialNumber`
- `purchaseDate`
- `purchasePrice`
- `notes`
- `photoUri`
- `createdAt`
- `updatedAt`
- `isArchived`

Non rendere obbligatori marca, modello, prezzo o numero di serie.

## 6.3 Promemoria

Il promemoria è l'entità centrale del sistema.

Campi principali:

- `id`
- `title`
- `description`
- `type`
- `dueDate`
- `dueTime`
- `isAllDay`
- `status`
- `priority`
- `category`
- `recurrenceRuleId` opzionale
- `createdAt`
- `updatedAt`
- `completedAt`
- `isArchived`

Tipi iniziali:

- `TASK`
- `EVENT`
- `DEADLINE`
- `BIRTHDAY`
- `WARRANTY`
- `MAINTENANCE`
- `PAYMENT`
- `RENEWAL`
- `OTHER`

Non creare una tabella separata per ogni tipo di promemoria se non è necessario.

La differenza tra i tipi deve essere gestita principalmente tramite configurazione e dati specifici collegati.

## 6.4 Associazione Promemoria-Persona

Un promemoria può essere associato a zero, una o più persone.

Creare una relazione molti-a-molti:

`ReminderPersonCrossRef`

Campi:

- `reminderId`
- `personId`

Chiave primaria composta.

## 6.5 Associazione Promemoria-Cosa

Un promemoria può essere associato a zero, una o più cose.

Creare:

`ReminderItemCrossRef`

Campi:

- `reminderId`
- `itemId`

## 6.6 Associazione Persona-Cosa

Una cosa può appartenere o essere associata a più persone.

Creare:

`PersonItemCrossRef`

Possibili ruoli:

- `OWNER`
- `USER`
- `OTHER`

Non rendere obbligatoria questa relazione.

---

# 7. Ricorrenze

Le ricorrenze devono essere un componente autonomo.

Supportare almeno:

- nessuna;
- ogni giorno;
- ogni settimana;
- ogni 2 settimane;
- ogni mese;
- ogni 3 mesi;
- ogni 6 mesi;
- ogni anno;
- intervallo personalizzato.

Esempi:

`Ogni 6 mesi`

`Ogni 15 giorni`

`Ogni anno il 18 novembre`

Campi concettuali della ricorrenza:

- `frequency`
- `interval`
- `startDate`
- `endDate` opzionale
- `daysOfWeek` opzionale
- `dayOfMonth` opzionale
- `monthOfYear` opzionale

Evitare di generare preventivamente migliaia di eventi nel database.

La ricorrenza deve essere memorizzata come regola e il sistema deve calcolare l'occorrenza successiva quando necessario.

Per il calcolo utilizzare API `java.time`.

---

# 8. Date e orari

Separare concettualmente:

- data senza orario;
- data + ora;
- ricorrenza.

Un compleanno, per esempio, può essere una data senza ora.

Un appuntamento può avere data + ora.

Non usare stringhe arbitrarie per la logica delle date.

Utilizzare tipi coerenti e converter Room quando necessario.

Considerare correttamente:

- cambio di fuso orario;
- ora legale;
- cambio di data;
- riavvio del dispositivo.

---

# 9. Manutenzione

Creare una sezione specifica per la manutenzione degli oggetti.

Per una macchina:

```text
Fiat Panda

Manutenzione

12/06/2026
Tagliando
84.000 km
€320

02/02/2026
Pneumatici
80.500 km
€380
```

Entità:

`MaintenanceRecord`

Campi:

- `id`
- `itemId`
- `type`
- `date`
- `odometer` opzionale
- `cost` opzionale
- `description`
- `notes`
- `createdAt`

Una registrazione di manutenzione è uno storico, non necessariamente un promemoria.

Il promemoria futuro deve poter essere associato alla manutenzione.

---

# 10. Chilometraggio

Per oggetti di tipo veicolo permettere promemoria basati sui chilometri.

Esempio:

```text
Tagliando
Ultimo: 84.000 km
Intervallo: 15.000 km
Prossimo: 99.000 km
```

Il valore attuale del chilometraggio deve appartenere alla scheda del veicolo.

L'utente deve poter aggiornare il chilometraggio.

Il sistema deve poter calcolare se una manutenzione è:

- lontana;
- vicina;
- dovuta;
- superata.

Non richiedere GPS o Internet per questa funzionalità.

---

# 11. Garanzia

Ogni "Cosa" acquistabile può avere una garanzia.

Campi:

- `startDate`
- `endDate`
- `durationMonths`
- `seller`
- `purchasePrice`
- `documentUri` opzionale

La garanzia può generare automaticamente una voce di tipo `WARRANTY`.

Esempio:

```text
Lavatrice Samsung

Acquistata:
12/03/2026

Garanzia:
12/03/2028

Stato:
🟢 Attiva
```

Quando la scadenza si avvicina:

`Garanzia in scadenza tra 30 giorni`

Quando supera la data:

`Garanzia scaduta`

La garanzia deve essere visibile:

- nella scheda della cosa;
- nella Home;
- nel calendario;
- nei promemoria.

---

# 12. Tipi di "Cose"

Durante la creazione di una cosa mostrare categorie preimpostate.

### Veicoli

- Auto
- Moto
- Scooter
- Altro

### Casa

- Lavatrice
- Lavastoviglie
- Frigorifero
- Forno
- Condizionatore
- Caldaia
- TV
- Altro

### Elettronica

- Smartphone
- Tablet
- Computer
- Console
- Fotocamera
- Altro

### Documenti

- Documento personale
- Contratto
- Garanzia
- Altro

### Generico

- Attrezzatura
- Oggetto
- Altro

Le categorie devono essere modificabili/estendibili in futuro.

---

# 13. Creazione guidata delle Cose

Quando l'utente sceglie una categoria, proporre campi e promemoria pertinenti.

Esempio per Auto:

```text
Aggiungi auto

Marca
Modello
Targa
Anno
Chilometri attuali

Cosa vuoi ricordare?

☑ Assicurazione
☑ Revisione
☑ Tagliando
☑ Cambio gomme
☐ Altro
```

Esempio per Lavatrice:

```text
Aggiungi lavatrice

Marca
Modello
Data acquisto
Prezzo

Cosa vuoi ricordare?

☑ Garanzia
☑ Pulizia filtro
☑ Pulizia guarnizione
☐ Manutenzione
```

Le proposte devono essere facoltative.

---

# 14. Home

La Home deve rispondere alla domanda:

**"Cosa devo ricordarmi?"**

Struttura:

```text
Buongiorno 👋

2 cose richiedono attenzione

[ Garanzia lavatrice ]
Scade tra 5 giorni
Lavatrice Samsung

[ Tagliando auto ]
Scade tra 12 giorni
Fiat Panda

OGGI

09:00
Pagare bolletta

18:00
Controllare gomme

PROSSIMAMENTE

Garanzia telefono — tra 8 giorni
Compleanno mamma — tra 12 giorni
Tagliando — tra 23 giorni
```

La Home deve ordinare gli elementi per rilevanza temporale.

Ordine suggerito:

1. scaduti;
2. urgenti;
3. oggi;
4. prossimi giorni;
5. prossime settimane;
6. più avanti.

Non mostrare automaticamente centinaia di elementi.

Prevedere un accesso a "Vedi tutto".

---

# 15. Stato temporale

Ogni promemoria deve avere uno stato temporale derivato dalla data.

Stati:

- `OVERDUE`
- `TODAY`
- `UPCOMING`
- `COMPLETED`
- `CANCELLED`

Lo stato temporale non dovrebbe essere memorizzato se può essere calcolato dalla data e dallo stato di completamento.

Evitare duplicazione dei dati.

---

# 16. Calendario

Il calendario deve supportare almeno:

- mese;
- giorno;
- agenda.

Vista mese:

```text
SETTEMBRE 2026

L M M G V S D
  1 2 3 4 5 6
7 8 9 10 11 12 13
14 15 16 17 18 19 20
21 22 23 24 25 26 27
28 29 30
```

Mostrare indicatori piccoli sotto i giorni con elementi.

Esempio:

- punto per attività;
- icona per evento importante;
- indicatore per scadenza.

Non riempire la cella con testo.

Toccando un giorno mostrare gli elementi di quella data.

---

# 17. Agenda

La vista agenda deve mostrare gli elementi ordinati cronologicamente.

```text
29 SETTEMBRE

09:00
💰 Pagare bolletta

12:30
📦 Ritirare pacco

18:00
🚗 Controllare gomme
```

Elementi senza orario:

```text
TUTTO IL GIORNO

• Rinnovare assicurazione
• Controllare filtro
```

L'elemento deve poter essere aperto toccandolo.

---

# 18. Promemoria

La schermata Promemoria deve avere:

- ricerca;
- filtri;
- ordinamento;
- elenco.

Filtri principali:

- Tutti
- Oggi
- In arrivo
- Scaduti
- Completati

Filtri secondari:

- Persona
- Cosa
- Categoria
- Tipo
- Priorità

L'elenco deve mostrare:

```text
🔧 Tagliando auto
23 ottobre
Fiat Panda
Gabriele
```

---

# 19. Creazione rapida

Il pulsante `+` deve aprire un bottom sheet o schermata compatta:

```text
Cosa vuoi aggiungere?

🔔 Promemoria
📅 Evento
📦 Cosa
👤 Persona
```

La UI deve essere molto semplice.

---

# 20. Creazione di un Promemoria

Campi iniziali:

```text
Titolo *
Data *
Ora
```

Opzioni avanzate:

- descrizione;
- ricorrenza;
- priorità;
- categoria;
- persona;
- cosa;
- allegati;
- note;
- impostazioni notifica.

Il salvataggio deve essere possibile anche senza compilare le opzioni avanzate.

---

# 21. Priorità

Supportare:

- normale;
- importante;
- urgente.

Non usare solo il colore per rappresentare la priorità.

Usare anche:

- icona;
- testo;
- eventuale indicatore.

---

# 22. Persone

La schermata Persone deve mostrare una griglia/lista semplice.

Esempio:

```text
PERSONE

Gabriele
Partner
Mamma
Papà
```

Ogni persona può avere:

- nome;
- cognome;
- foto;
- note;
- promemoria;
- eventi;
- cose associate.

La persona non deve diventare una rubrica telefonica.

Lo scopo è organizzativo.

---

# 23. Dettaglio Persona

Esempio:

```text
MAMMA

OGGI
• Telefonare

PROSSIMI EVENTI
🎂 Compleanno
18 novembre

PROMEMORIA
📄 Documento
12 maggio 2027

COSE
🚗 Auto
📱 Smartphone
```

Mostrare solamente le sezioni che hanno contenuti.

---

# 24. Cose

La schermata Cose deve mostrare le cose organizzate per categoria.

Esempio:

```text
Cose

🚗 Veicoli
📱 Elettronica
🧺 Elettrodomestici
🏠 Casa
📄 Documenti
```

Prevedere ricerca globale e filtri.

---

# 25. Dettaglio Cosa

La scheda dettaglio deve riunire tutte le informazioni.

Esempio:

```text
FIAT PANDA

Informazioni
Targa: XXXXXXX
Anno: 2022
Km: 84.230

SCADENZE

Assicurazione
17/02/2027

Revisione
05/05/2028

MANUTENZIONE

12/06/2026
Tagliando
84.000 km
€320

PROMEMORIA

Controllare gomme
Controllare olio

DOCUMENTI

Assicurazione
Libretto
```

Le sezioni vuote non devono essere visualizzate.

---

# 26. Ricerca globale

Creare una ricerca unica per tutta l'app.

Deve trovare:

- persone;
- cose;
- promemoria;
- eventi;
- manutenzioni;
- note.

Esempio:

```text
Ricerca:
lavatrice
```

Risultati:

```text
🧺 Lavatrice Samsung
🔔 Garanzia lavatrice
🔧 Pulizia filtro
```

La ricerca deve essere locale e non dipendere da Internet.

---

# 27. Notifiche

Le notifiche sono una funzionalità fondamentale.

Per Android 13+ gestire la richiesta runtime di `POST_NOTIFICATIONS`. 

Creare almeno un notification channel dedicato ai promemoria.

Esempio:

```text
Ricordella

La garanzia della lavatrice scade tra 7 giorni.
```

Toccando la notifica aprire il dettaglio del promemoria.

Possibili azioni:

- Completa
- Rimanda
- Apri

---

# 28. Scheduling delle notifiche

Creare un componente separato:

`ReminderScheduler`

Responsabilità:

- programmare;
- annullare;
- riprogrammare;
- ricostruire gli allarmi.

Per promemoria con tempo preciso utilizzare `AlarmManager` quando la precisione dell'orario è una caratteristica richiesta dall'utente.

Per attività non strettamente temporali utilizzare il meccanismo meno preciso e più efficiente disponibile.

Android specifica che gli exact alarm richiedono particolare gestione dei permessi a partire da Android 12; utilizzare questa capacità solo quando necessaria al funzionamento realmente time-sensitive dell'app. 

Non usare un timer in memoria dell'app per garantire i promemoria.

I promemoria devono continuare a funzionare quando l'app è chiusa.

---

# 29. Riavvio del dispositivo

Dopo il riavvio Android, gli allarmi devono essere ricostruiti.

Implementare un receiver dedicato, ad esempio:

`BootCompletedReceiver`

che:

1. verifica che il database esista;
2. legge i promemoria attivi;
3. calcola la prossima occorrenza;
4. ricrea gli allarmi necessari.

Gestire inoltre, quando opportuno:

- cambio di fuso orario;
- modifica dell'orario di sistema;
- modifica del calendario;
- revoca dei permessi richiesti.

Non memorizzare un numero indefinito di alarm inutili.

Programmare solo le prossime occorrenze necessarie.

---

# 30. Ricorrenza e notifica

Per un promemoria ricorrente:

```text
Pulizia filtro

Ogni 3 mesi
```

Quando la notifica viene gestita:

1. determinare se l'occorrenza corrente viene completata o rimandata;
2. calcolare la successiva;
3. aggiornare lo stato;
4. pianificare la prossima notifica.

Non duplicare indefinitamente il promemoria.

---

# 31. Snooze / Rimanda

Ogni promemoria notificato può avere:

- completa;
- rimanda di 10 minuti;
- rimanda di 1 ora;
- rimanda a domani;
- scegli data e ora.

Le opzioni devono poter essere configurate in futuro.

Il comportamento del pulsante "Rimanda" deve essere centralizzato nel dominio e non implementato direttamente nella UI.

---

# 32. Allegati e documenti

Permettere di associare file locali a una Cosa o a un Promemoria.

Esempi:

- scontrino;
- fattura;
- manuale;
- garanzia;
- libretto;
- fotografia.

Salvare preferibilmente riferimenti a file/URI e non duplicare inutilmente i file.

Per scegliere documenti utilizzare il file picker Android.

Per immagini utilizzare le API Android appropriate, preferibilmente senza chiedere permessi di storage non necessari.

---

# 33. Foto

Una Cosa può avere una foto principale.

Esempio:

```text
📷
Lavatrice Samsung
```

La foto è opzionale.

Non rendere necessaria la fotocamera per usare l'app.

---

# 34. Backup locale

Poiché l'app non deve utilizzare cloud sync, implementare un sistema di backup manuale locale.

Funzioni:

```text
Esporta backup
Importa backup
```

Formato consigliato:

`ricordella-backup-YYYY-MM-DD.zip`

Contenuto:

```text
backup/
    database.json
    settings.json
    attachments/
```

Oppure un database versionato + manifest di backup.

Il backup deve includere:

- persone;
- cose;
- promemoria;
- ricorrenze;
- associazioni;
- manutenzioni;
- allegati;
- impostazioni applicative necessarie.

Prima dell'importazione:

1. validare il file;
2. controllare versione formato;
3. controllare integrità;
4. mostrare all'utente cosa verrà importato;
5. chiedere conferma;
6. eseguire l'importazione in modo transazionale;
7. riprogrammare le notifiche.

---

# 35. Privacy e backup Android

L'app non deve implementare alcun proprio sistema cloud.

Se l'obiettivo è mantenere il modello strettamente locale, configurare anche il comportamento di backup Android in modo esplicito e non affidarsi al default.

Considerare `android:allowBackup` e le `dataExtractionRules` per evitare che il normale sistema di backup copi automaticamente i dati dell'app.

Android consente di controllare la partecipazione al backup tramite questi meccanismi, ma il comportamento dei trasferimenti dispositivo-dispositivo può avere differenze tra produttori; la specifica non deve promettere più di quanto Android permetta realmente. 

In ogni caso il backup manuale creato da Ricordella deve essere sotto il controllo dell'utente.

---

# 36. Impostazioni

Schermata:

```text
Impostazioni

Aspetto
    Tema
    Chiaro / Scuro / Sistema

Calendario
    Primo giorno della settimana
    Formato data

Notifiche
    Abilitate
    Suono
    Vibrazione
    Anticipo predefinito

Dati
    Esporta backup
    Importa backup
    Elimina tutti i dati

Informazioni
    Versione
    Privacy
```

Utilizzare DataStore per le preferenze semplici dell'app. 

---

# 37. Architettura software

Utilizzare un'architettura semplice ma scalabile.

Struttura consigliata:

```text
app/
├── core/
│   ├── common/
│   ├── date/
│   ├── notifications/
│   ├── navigation/
│   └── ui/
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   ├── dao/
│   │   ├── entity/
│   │   └── converter/
│   ├── repository/
│   └── backup/
│
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│
└── feature/
    ├── home/
    ├── calendar/
    ├── reminders/
    ├── items/
    ├── people/
    └── settings/
```

Non creare cartelle o livelli solamente per seguire una moda architetturale.

Ogni livello deve avere una responsabilità reale.

---

# 38. Data layer

Room:

```text
AppDatabase
    ↓
DAO
    ↓
Repository
    ↓
UseCase
    ↓
ViewModel
    ↓
Compose UI
```

I DAO devono occuparsi dell'accesso al database.

I repository devono rappresentare l'accesso ai dati.

I use case devono contenere operazioni di dominio riutilizzabili.

I ViewModel devono gestire lo stato della schermata e gli eventi utente.

La UI non deve accedere direttamente a Room.

---

# 39. ViewModel

Esempi:

```text
HomeViewModel
CalendarViewModel
ReminderListViewModel
ReminderDetailViewModel
ItemListViewModel
ItemDetailViewModel
PersonListViewModel
PersonDetailViewModel
SettingsViewModel
```

Ogni ViewModel deve esporre uno stato UI chiaro.

Esempio concettuale:

```text
UiState
    isLoading
    data
    error
```

La UI osserva lo stato e invia eventi:

```text
onReminderClicked()
onReminderCompleted()
onReminderDeleted()
onAddReminderClicked()
```

Non inserire logica di database nei Composable.

---

# 40. Repository

Esempi:

```text
ReminderRepository
PersonRepository
ItemRepository
MaintenanceRepository
SettingsRepository
BackupRepository
```

Non creare un enorme `AppRepository`.

Ogni repository deve avere una responsabilità comprensibile.

---

# 41. Use case

Creare use case solo quando la logica merita di essere isolata o riutilizzata.

Esempi:

```text
CreateReminderUseCase
CompleteReminderUseCase
DeleteReminderUseCase
GetUpcomingRemindersUseCase
CalculateNextOccurrenceUseCase
ScheduleReminderUseCase
RescheduleAllRemindersUseCase
CreateItemUseCase
AddMaintenanceRecordUseCase
ExportBackupUseCase
ImportBackupUseCase
```

Evitare una classe UseCase inutilmente lunga.

---

# 42. Gestione calendario e data

Creare un componente centralizzato per:

- calcolo giorni;
- prossime occorrenze;
- ricorrenze;
- confronto date;
- filtri temporali;
- mese corrente;
- settimana corrente.

Non duplicare la stessa logica dentro Home, Calendario e Promemoria.

---

# 43. Database Room

Database iniziale:

```text
Person
Item
Reminder
RecurrenceRule
MaintenanceRecord
Attachment
Category
```

Tabelle di relazione:

```text
ReminderPersonCrossRef
ReminderItemCrossRef
PersonItemCrossRef
```

Utilizzare indici sulle colonne frequentemente cercate, ad esempio:

- `dueDate`
- `status`
- `type`
- `category`
- `personId`
- `itemId`

Gestire le migrazioni Room sin dall'inizio.

Non utilizzare `fallbackToDestructiveMigration()` nella versione definitiva.

I dati dell'utente non devono sparire a causa di un aggiornamento dell'app.

---

# 44. Identificatori

Utilizzare identificatori stabili e non dipendenti dall'ordine delle righe.

UUID/String UUID sono una scelta appropriata se si prevede una futura importazione/esportazione o eventuale sincronizzazione.

Non usare il nome dell'oggetto come ID.

---

# 45. Cancellazione

Distinguere:

- eliminazione;
- archiviazione;
- completamento.

Per esempio un promemoria completato deve restare nello storico se utile.

Una Cosa non dovrebbe essere cancellata automaticamente quando viene eliminato un promemoria associato.

Gestire correttamente le relazioni e le foreign key.

---

# 46. Importazione backup

L'importazione non deve creare duplicati se il backup contiene elementi già presenti e viene applicato su uno stato compatibile.

Definire una strategia:

- sostituisci tutti i dati;
- oppure importa in aggiunta.

Nella prima versione è preferibile offrire chiaramente:

`Ripristina backup sostituendo i dati attuali`

con avviso esplicito.

In futuro si potrà aggiungere un vero merge.

---

# 47. UI Design

Stile:

- Material 3;
- moderno;
- pulito;
- amichevole;
- leggermente giocoso;
- non infantile.

Utilizzare:

- card;
- rounded corners;
- typography chiara;
- icone Material;
- spaziatura consistente;
- bottom sheet;
- FAB;
- chip per filtri;
- badge/stati.

Non usare troppi colori.

Lo stato deve essere comprensibile anche senza affidarsi esclusivamente al colore.

---

# 48. Tema

Supportare:

- tema chiaro;
- tema scuro;
- tema di sistema.

Definire un design system centralizzato:

```text
RicordellaTheme
RicordellaColors
RicordellaTypography
RicordellaDimensions
```

Non inserire colori hardcoded nei singoli Composable.

---

# 49. Componenti UI riutilizzabili

Creare componenti comuni quando realmente riutilizzati.

Esempi:

```text
ReminderCard
ItemCard
PersonCard
DateSelector
PriorityIndicator
EmptyState
SectionHeader
SearchBar
FilterChipRow
```

Non creare un componente per ogni piccola riga dell'interfaccia.

La regola deve essere:

**riutilizzare quando migliora chiarezza e consistenza, non astrarre per forza.**

---

# 50. Stati vuoti

Ogni sezione vuota deve avere un messaggio utile.

Esempio:

```text
📅

Nessun promemoria

Non c'è nulla da ricordare in questa giornata.

[ + Aggiungi ]
```

---

# 51. Errori

Non mostrare stack trace all'utente.

Mostrare messaggi comprensibili:

```text
Non è stato possibile salvare il promemoria.
Riprova.
```

Per errori recuperabili utilizzare snackbar/dialog appropriati.

---

# 52. Accessibilità

L'app deve supportare:

- font grandi;
- content description per icone funzionali;
- contrasto sufficiente;
- target touch adeguati;
- navigazione prevedibile;
- contenuti non comprensibili esclusivamente tramite colore.

Le date e le priorità devono avere anche rappresentazione testuale.

---

# 53. Prestazioni

L'app deve rimanere fluida anche con:

- migliaia di promemoria;
- centinaia di cose;
- centinaia di persone;
- grandi quantità di storico.

Non caricare tutto il database in memoria.

Usare query filtrate/paginate quando necessario.

Non effettuare operazioni pesanti sul main thread.

---

# 54. Ricerca e filtri

La ricerca deve essere eseguita dal database quando la quantità dei dati lo rende necessario.

Evitare di caricare tutte le righe per poi filtrarle manualmente nella UI.

La query deve poter cercare almeno in:

- titolo promemoria;
- descrizione;
- nome cosa;
- marca;
- modello;
- nome persona;
- note.

---

# 55. Prima versione MVP

La prima versione funzionante deve contenere:

### Core

- database Room;
- repository;
- ViewModel;
- Compose;
- navigazione;
- tema.

### Persone

- crea;
- modifica;
- elimina/archivia;
- dettaglio;
- associazioni.

### Cose

- crea;
- modifica;
- elimina/archivia;
- dettaglio;
- categorie;
- garanzia.

### Promemoria

- crea;
- modifica;
- elimina;
- completa;
- data;
- ora;
- ricorrenza;
- priorità;
- persona;
- cosa.

### Calendario

- mese;
- agenda;
- dettaglio giorno.

### Notifiche

- permission;
- channel;
- scheduling;
- apertura dettaglio;
- completa;
- snooze;
- rescheduling.

### Backup

- esporta;
- importa;
- validazione;
- ripristino.

---

# 56. Funzionalità da lasciare dopo l'MVP

Non implementare subito:

- sincronizzazione cloud;
- account;
- condivisione familiare online;
- geolocalizzazione;
- AI;
- OCR automatico;
- integrazione email;
- WhatsApp;
- calendario Google;
- servizi web;
- pubblicità.

L'architettura deve consentire di aggiungerle in futuro, ma non devono complicare inutilmente la prima versione.

---

# 57. Evoluzioni future

Possibili funzioni future:

## AI locale

Riconoscere automaticamente:

- tipo di oggetto;
- informazioni da scontrini;
- date di garanzia;
- dati di fatture;
- scadenze dai documenti.

## OCR

Fotografare una ricevuta:

```text
Lavatrice Samsung
€499
12/03/2026
```

e proporre automaticamente la scheda.

## Importazione documenti

Importare PDF e cercare testo localmente.

## Posizione

Possibilità futura:

> "Ricordami di comprare il filtro quando sono vicino al negozio."

## Condivisione

Condivisione selettiva di dati tra dispositivi, mantenendo comunque il funzionamento offline come base.

## Sincronizzazione

Eventuale sincronizzazione futura completamente opzionale.

---

# 58. Regole per l'AI di coding

Durante la realizzazione:

1. Non creare codice duplicato.
2. Non creare classi enormi.
3. Ogni classe deve avere una responsabilità chiara.
4. I nomi di metodi e variabili devono descrivere la funzione.
5. Non inserire logica di business nei Composable.
6. Non accedere direttamente al database dalla UI.
7. Centralizzare la logica delle date.
8. Centralizzare la schedulazione delle notifiche.
9. Non utilizzare singleton casuali per aggirare l'architettura.
10. Non usare variabili globali modificabili per lo stato dell'app.
11. Non utilizzare stringhe per rappresentare enum o stati se è possibile usare enum/sealed class.
12. Non duplicare modelli senza un reale motivo.
13. Non utilizzare servizi esterni per funzionalità che possono funzionare localmente.
14. Non aggiungere dipendenze inutili.
15. Preferire le API Android/Jetpack ufficiali.
16. Gestire gli errori in modo esplicito.
17. Scrivere test per la logica importante.
18. Non usare `fallbackToDestructiveMigration()` nella build definitiva.
19. Non usare timer in memoria per i promemoria.
20. Non salvare dati sensibili in logcat.

---

# 59. Test obbligatori

Creare test almeno per:

## Ricorrenze

- ogni giorno;
- ogni settimana;
- ogni mese;
- ogni 3 mesi;
- ogni 6 mesi;
- ogni anno;
- intervalli personalizzati;
- fine ricorrenza.

## Date

- cambio mese;
- fine anno;
- anno bisestile;
- cambio ora legale;
- date passate;
- date future.

## Promemoria

- creazione;
- modifica;
- completamento;
- cancellazione;
- snooze;
- ricorrenza.

## Relazioni

- promemoria/persona;
- promemoria/cosa;
- persona/cosa.

## Backup

- export;
- import;
- dati mancanti;
- backup corrotto;
- incompatibilità versione.

## Notifiche

- scheduling;
- cancellazione;
- modifica;
- reboot;
- cambio orario;
- permesso notifiche negato.

---

# 60. Criteri di accettazione

L'app è considerata correttamente realizzata quando:

### Dati

- tutti i dati dell'app possono essere creati e modificati offline;
- chiudendo e riaprendo l'app i dati restano presenti;
- non esiste alcun requisito di account.

### Promemoria

- un promemoria può avere data e ora;
- può avere una ricorrenza;
- può essere completato;
- può essere associato a persone e cose;
- può essere modificato senza perdere lo storico quando previsto.

### Notifiche

- una notifica viene ricevuta anche con l'app chiusa;
- dopo il riavvio gli allarmi vengono ricostruiti;
- modificando un promemoria viene aggiornata la relativa schedulazione;
- cancellando un promemoria la notifica viene annullata.

### Cose

- è possibile creare una cosa;
- è possibile aggiungere una garanzia;
- è possibile creare manutenzioni;
- è possibile associare una o più persone;
- tutte le scadenze collegate sono visibili.

### Calendario

- mostra correttamente gli elementi;
- permette di selezionare una data;
- apre i dettagli dell'elemento.

### Backup

- è possibile esportare tutti i dati;
- è possibile importare un backup;
- dopo il ripristino vengono riprogrammate le notifiche.

---

# 61. Strategia di sviluppo

Sviluppare in fasi, verificando la build dopo ogni fase.

## Fase 1 — Progetto base

- crea progetto Android;
- Kotlin;
- Compose;
- Material 3;
- tema;
- struttura package;
- navigazione iniziale.

Risultato: app compilabile con schermate vuote.

## Fase 2 — Database

- Room;
- entità;
- DAO;
- relazioni;
- repository.

Risultato: dati persistenti offline.

## Fase 3 — Persone e Cose

- CRUD;
- liste;
- dettaglio;
- relazioni.

## Fase 4 — Promemoria

- CRUD;
- data;
- ora;
- completamento;
- associazioni.

## Fase 5 — Calendario

- mese;
- giorno;
- agenda.

## Fase 6 — Ricorrenze

- regole;
- calcolo prossima occorrenza;
- test.

## Fase 7 — Notifiche

- permission;
- channel;
- AlarmManager;
- receiver;
- reboot;
- snooze;
- scheduling.

## Fase 8 — Garanzie e manutenzioni

- warranty;
- maintenance record;
- chilometraggio.

## Fase 9 — Backup

- export;
- import;
- validazione;
- restore.

## Fase 10 — Rifinitura

- accessibilità;
- dark mode;
- performance;
- test;
- gestione errori;
- responsive/adaptive layouts.

---

# 62. Regola importante sullo sviluppo con AI

Non generare tutta l'app in un unico passaggio.

L'AI di coding deve procedere per incrementi.

Per ogni fase:

1. analizzare il codice esistente;
2. modificare solo ciò che serve;
3. compilare il progetto;
4. correggere gli errori;
5. eseguire i test;
6. verificare la navigazione;
7. passare alla fase successiva.

Prima di aggiungere una nuova libreria controllare se la funzionalità può essere implementata con una libreria Android/Jetpack già presente.

Non riscrivere componenti funzionanti senza una motivazione tecnica.

---

# 63. Primo obiettivo concreto

La prima build deve produrre una versione funzionante con:

```text
HOME
CALENDARIO
PROMEMORIA
COSE
PERSONE

                +

        PROMEMORIA
        EVENTO
        COSA
        PERSONA
```

L'utente deve riuscire a:

1. creare una persona;
2. creare una macchina;
3. associare la macchina alla persona;
4. creare un promemoria "Tagliando";
5. associare il promemoria alla macchina e alla persona;
6. impostare la data;
7. vedere il promemoria nella Home;
8. vederlo nel calendario;
9. ricevere la notifica;
10. completarlo;
11. chiudere l'app;
12. riaprirla e ritrovare tutti i dati.

Solo dopo aver completato questo flusso end-to-end aggiungere le funzioni secondarie.

---

# 64. Identità del prodotto

Nome:

**Ricordella**

Payoff possibile:

**"Pensaci tu, al resto pensa lei."**

Concetto:

> Ricordella si ricorda delle cose al posto tuo.

La UI deve comunicare affidabilità e semplicità, con un carattere leggermente amichevole.

Non deve sembrare un gestionale aziendale e non deve sembrare una semplice lista della spesa.

Deve sembrare una **memoria personale organizzata**.

---

# 65. Vincolo finale

La priorità assoluta è:

**semplicità per l'utente + solidità dei dati + funzionamento offline + notifiche affidabili + architettura pulita.**

Non aggiungere funzionalità solamente perché sono tecnicamente possibili.

Ogni nuova funzione deve migliorare il concetto centrale:

**"Metto una cosa in Ricordella e so che non me la dimenticherò."**
