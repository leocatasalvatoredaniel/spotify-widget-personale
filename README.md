# Widget Musica (Spotify), uso personale

App Android personale con un widget per Spotify:

- **Brano in riproduzione**: copertina, titolo e artista, con comandi play/pausa, precedente e successivo. Si aggiorna in tempo reale.
- **Mi piace**: aggiunge o toglie il brano in ascolto dai Brani che ti piacciono.
- **Playlist con un tocco**: fino a 4 playlist scelte da te.
- **Ascoltati di recente**: un tocco riprende il brano dalla playlist o dall'album da cui l'avevi ascoltato.
- I colori del widget vengono dalla copertina, con testo sempre leggibile.

Non è un'app di Spotify e non va distribuita. Usa solo strumenti ufficiali:

- la [Web API di Spotify](https://developer.spotify.com/documentation/web-api);
- le sessioni multimediali di Android, cioè gli stessi comandi della tendina delle notifiche.

## Come funziona

| Cosa | Da dove arriva |
| --- | --- |
| Brano in riproduzione, copertina, play/pausa, avanti, indietro | Sessione multimediale di Spotify sul telefono: in tempo reale, senza internet. Richiede **Accesso alle notifiche**: l'app usa solo la riproduzione, non legge né salva le notifiche. |
| Mi piace, playlist, ascoltati di recente, avvio di una playlist | Web API di Spotify, con il tuo account. |

L'accesso a Spotify usa il flusso ufficiale con PKCE: l'app non contiene password né segreti. Il login avviene nel browser e torna all'app tramite un indirizzo locale del telefono (`http://127.0.0.1`), come Spotify richiede per le app nuove dal 2025 e come raccomanda lo standard per le app native (RFC 8252). I token sono cifrati con una chiave del Keystore di Android e non finiscono nei backup.

## Requisiti

- **Spotify Premium**: Spotify fa funzionare le app personali («modalità sviluppo») solo se il loro proprietario è Premium.
- **Android Studio** installato, aperto almeno una volta (installa l'SDK Android).
- Telefono con Android 8.0 o successivo e l'app Spotify.

## 1. Registrare l'app su Spotify (una volta)

1. Vai su [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) e premi **Create app**.
2. Compila così:
   - **Redirect URI**: `http://127.0.0.1:43821/callback` (esattamente così, con la porta), poi premi *Add* e controlla che compaia nell'elenco. Gli schemi come `nomeapp://` e gli indirizzi senza porta vengono rifiutati;
   - **APIs used**: spunta **Web API**;
   - nome e descrizione come preferisci.
3. In **User Management** aggiungi il tuo nome e l'email del tuo account Spotify.
4. Da **Settings** copia il **Client ID**.

## 2. Compilare su Windows

1. Doppio clic su `build-personale.bat`.
2. La prima volta si apre `local.properties` nel Blocco note. Scrivi il Client ID dopo `spotify.clientId=`, salva, chiudi e avvia di nuovo il file. Esempio, con il tuo ID al posto di `abc123…`:

   ```
   spotify.clientId=abc123…
   ```

3. A fine compilazione si apre la cartella con `app-release.apk`. Copialo sul telefono e installalo.

`local.properties` resta solo sul tuo PC: è escluso dal repository.

## 3. Primo avvio

1. Apri **Widget Musica**.
2. **Accesso alle notifiche** → *Concedi l'accesso* → attiva *Widget Musica*.
3. **Account Spotify** → *Collega Spotify* → accedi nel browser e accetta. Alla fine il browser mostra «Spotify collegato»: premi *Torna a Widget Musica*.
4. **Playlist nel widget** → *Carica le mie playlist* → scegline fino a 4.
5. **Widget** → *Aggiungi alla schermata Home*, oppure tieni premuto sulla schermata Home → *Widget* → *Widget Musica*.

## Il widget

Il contenuto si adatta alla dimensione:

| Dimensione | Contenuto |
| --- | --- |
| 2×2 | copertina e play/pausa |
| 4×2 | copertina, titolo, Mi piace, precedente / play / successivo |
| 4×3 e oltre | in più le playlist preferite e gli ascoltati di recente (scorrevoli) |

| Tocchi | Cosa succede |
| --- | --- |
| copertina o titolo | apre Spotify |
| ◀◀ ▶/❚❚ ▶▶ | comandi di riproduzione (se Spotify è chiuso, lo apre) |
| cuore | aggiunge o toglie il brano dai Brani che ti piacciono |
| una playlist | la avvia sul telefono |
| un brano recente | lo riprende dalla playlist o dall'album da cui l'avevi ascoltato |

Tenendo premuto il widget si aprono le impostazioni.

## Limiti noti

- Se Spotify è chiuso, toccando una playlist l'app apre Spotify e avvia la riproduzione dopo qualche secondo, quando Spotify risulta pronto. Spotify non permette di avviare la riproduzione su un telefono su cui non è aperto.
- Gli «ascoltati di recente» li fornisce Spotify: un brano compare dopo che è stato ascoltato per un po', quindi la lista può essere indietro di un brano.
- Le API di Spotify per gli sviluppatori cambiano spesso: questa versione usa gli endpoint attuali (es. `/me/library` per i Mi piace). Se qualcosa smette di funzionare, l'errore compare in una notifica breve (toast).
- Angoli arrotondati e alcune dimensioni preferite del widget richiedono Android 12 o successivo.

## Sviluppo

- Kotlin, Jetpack Glance (widget) e Jetpack Compose (impostazioni).
- AGP 8.13, Gradle 8.14, compileSdk 36, minSdk 26.
- Test unitari: `gradlew testReleaseUnitTest`.
