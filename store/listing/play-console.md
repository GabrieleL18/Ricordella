# Remindella: risposte per Play Console

Pacchetto: com.remindella.app · Contatto: lannilab.support@gmail.com · App gratuita

## Sicurezza dei dati
- Raccogli o condividi dati utente? **No**: nessun dato lascia il telefono verso lo sviluppatore (niente permesso INTERNET).
  - Il riconoscimento vocale è del telefono/Google, non dell'app; ML Kit gira sul telefono. Se Console chiede, microfono e foto sono "elaborati solo sul dispositivo".
- Richiesta di cancellazione: i dati stanno sul telefono (Impostazioni › Elimina tutti i dati, oppure disinstalla).
- Informativa privacy: URL della pagina `privacy-policy.html` (vedi in fondo come pubblicarla).

- Dati sulla salute: la sezione Ciclo tiene le date solo sul telefono, non le invia a nessuno; è facoltativa e protetta a richiesta. Se Console chiede «Informazioni sulla salute», sono **non raccolte** (restano sul dispositivo).
- La condivisione via Drive e il backup scrivono un file nel posto scelto dall'utente con il selettore di Android: non è un invio dell'app a un server.
- Verificato sul manifest finale (release): nessun permesso INTERNET né ACCESS_NETWORK_STATE. Li aggiungeva la telemetria di ML Kit; ora sono rimossi in `AndroidManifest.xml` con `tools:node="remove"`. Dopo ogni aggiornamento delle librerie ricontrolla con `.\gradlew processReleaseMainManifest` e guardando `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.

## Altre dichiarazioni
- Annunci: **No**. Pubblico: **adulti (18+)**, non rivolta ai bambini. Categoria: Produttività. Account di accesso: nessuno.
- Classificazione contenuti: nessun contenuto sensibile, tutto "No".

## Permessi sensibili (testo da incollare)
- **USE_EXACT_ALARM**: la funzione principale dell'app è un promemoria/sveglia che deve suonare all'ora esatta scelta dall'utente.
- **USE_FULL_SCREEN_INTENT**: mostra la schermata della sveglia sopra il blocco schermo quando suona un promemoria impostato come sveglia.
- **FOREGROUND_SERVICE_SYSTEM_EXEMPTED** (servizio `AlarmRingService`): tiene attivi suono e vibrazione della sveglia finché l'utente non la ferma. Può richiedere un video che mostri la sveglia che suona a schermo bloccato. ATTENZIONE: è il permesso più contestato; se Google lo rifiuta, bisogna valutare un'alternativa.
- **READ_CALENDAR**: importa, solo su richiesta, gli eventi del calendario del telefono.
- **RECORD_AUDIO**: «Scrivi al volo» a voce (l'audio va al riconoscimento vocale del telefono e non è salvato) e messaggi vocali nelle note (salvati solo sul telefono, dentro la nota). Sempre dopo il consenso e su azione dell'utente.
- **USE_BIOMETRIC**: (aggiunto dalla libreria androidx.biometric) sblocco opzionale della sezione Ciclo con impronta o PIN.
- **POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / VIBRATE**: notifiche dei promemoria e ripristino dopo il riavvio.

## Pubblicare la privacy policy
Serve un URL pubblico. Il modo più semplice e gratuito è GitHub Pages (repository pubblico con `privacy-policy.html`, poi Settings › Pages), oppure Google Sites incollando il testo. Non l'ho pubblicata io.
