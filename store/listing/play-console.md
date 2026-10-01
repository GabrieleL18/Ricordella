# Remindella: risposte per Play Console

Pacchetto: com.remindella.app · Contatto: lannilab.support@gmail.com · App gratuita

## Sicurezza dei dati
- Raccogli o condividi dati utente? **No**: nessun dato lascia il telefono verso lo sviluppatore (niente permesso INTERNET).
  - Il riconoscimento vocale è del telefono/Google, non dell'app; ML Kit gira sul telefono. Se Console chiede, microfono e foto sono "elaborati solo sul dispositivo".
- Richiesta di cancellazione: i dati stanno sul telefono (Impostazioni › Elimina tutti i dati, oppure disinstalla).
- Informativa privacy: URL della pagina `privacy-policy.html` (vedi in fondo come pubblicarla).

## Altre dichiarazioni
- Annunci: **No**. Pubblico: **adulti (18+)**, non rivolta ai bambini. Categoria: Produttività. Account di accesso: nessuno.
- Classificazione contenuti: nessun contenuto sensibile, tutto "No".

## Permessi sensibili (testo da incollare)
- **USE_EXACT_ALARM**: la funzione principale dell'app è un promemoria/sveglia che deve suonare all'ora esatta scelta dall'utente.
- **USE_FULL_SCREEN_INTENT**: mostra la schermata della sveglia sopra il blocco schermo quando suona un promemoria impostato come sveglia.
- **FOREGROUND_SERVICE_SYSTEM_EXEMPTED** (servizio `AlarmRingService`): tiene attivi suono e vibrazione della sveglia finché l'utente non la ferma. Può richiedere un video che mostri la sveglia che suona a schermo bloccato. ATTENZIONE: è il permesso più contestato; se Google lo rifiuta, bisogna valutare un'alternativa.
- **READ_CALENDAR**: importa, solo su richiesta, gli eventi del calendario del telefono.
- **RECORD_AUDIO**: «Scrivi al volo» a voce, dopo il consenso; l'audio va al riconoscimento vocale del telefono e non è salvato.
- **POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / VIBRATE**: notifiche dei promemoria e ripristino dopo il riavvio.

## Pubblicare la privacy policy
Serve un URL pubblico. Il modo più semplice e gratuito è GitHub Pages (repository pubblico con `privacy-policy.html`, poi Settings › Pages), oppure Google Sites incollando il testo. Non l'ho pubblicata io.
