# Widget Musica (Spotify), uso personale

App Android personale con un widget per Spotify uguale al player multimediale del sistema (quello della tendina e della schermata di blocco):

- **copertina** come sfondo, scurita per leggere il testo;
- **dispositivo** su cui suona la musica (Questo smartphone, cuffie Bluetooth, Spotify Connect), con il pulsante per cambiare uscita;
- **titolo • artista**, che scorre se è lungo;
- **barra di avanzamento** con tempo trascorso e durata, nel colore della copertina;
- **shuffle e +** (aggiungi ai Brani che ti piacciono) con le icone e le funzioni di Spotify stesso, poi precedente, play/pausa, successivo.

Non è un'app di Spotify e non va distribuita.

## Come funziona

Il widget usa solo la **sessione multimediale** di Spotify sul telefono, la stessa che usa il player del sistema. Per leggerla serve l'**Accesso alle notifiche**. Delle notifiche l'app legge solo l'icona di Spotify, mostrata accanto al titolo come nel player del sistema; il contenuto delle notifiche non viene letto né salvato.

Quindi:

- nessun account Spotify da collegare e nessun Client ID;
- l'app non ha il permesso di accedere a internet;
- shuffle e + sono i pulsanti che Spotify offre al sistema: il widget mostra le loro icone (anche lo stato, es. ✓ se il brano ti piace già) e li preme come fa il sistema.

## Requisiti

- **Android Studio** installato, aperto almeno una volta (installa l'SDK Android).
- **Java 17**: Gradle 8.14 non funziona con Java 25, che è quello incluso nelle versioni recenti di Android Studio. Se manca: `winget install Microsoft.OpenJDK.17`. Lo script lo trova da solo.
- Telefono con Android 8.0 o successivo e l'app Spotify.

## Compilare su Windows

1. Doppio clic su `build-personale.bat`.
2. A fine compilazione si apre la cartella con `app-release.apk`. Copialo sul telefono e installalo.

### Se avevi la versione precedente (con login e playlist)

- Installa il nuovo APK sopra quello vecchio: le impostazioni restano. Al primo avvio l'app cancella da sola il login a Spotify salvato.
- Il widget già sulla schermata Home si aggiorna da solo. La misura consigliata ora è **4×2**: tienilo premuto e ridimensionalo.
- La riga `spotify.clientId=` in `local.properties` non serve più: puoi lasciarla o cancellarla.
- Se vuoi, elimina l'app che avevi creato su [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard): non viene più usata.

## Primo avvio

1. Apri **Widget Musica**.
2. **Accesso alle notifiche** → *Concedi l'accesso* → attiva *Widget Musica*.
3. **Widget** → *Aggiungi alla schermata Home*, oppure tieni premuto sulla schermata Home → *Widget* → *Widget Musica*.

## Il widget

| Tocchi | Cosa succede |
| --- | --- |
| copertina, titolo | apre Spotify |
| dispositivo o icona ▷) in alto a destra | cambia uscita audio (vedi limiti) |
| shuffle, + | le stesse funzioni dei pulsanti di Spotify nel player del sistema |
| ⏮ ▶/⏸ ⏭ | comandi di riproduzione |

Con un widget basso (meno di 2 righe) spariscono prima il dispositivo, poi i tempi.

## Limiti noti

- **La barra non si trascina** e non ha il pallino: i widget di Android non supportano i cursori. Si aggiorna ogni secondo, solo con lo schermo acceso e la musica in riproduzione.
- **Cambio uscita**: sui Samsung il widget apre il pannello multimediale di One UI (app di sistema *Media and devices*), come il player della tendina. Samsung non lo documenta: se un aggiornamento di One UI lo sposta o lo chiude alle altre app, il widget usa il pannello di uscita di Android e, in mancanza anche di quello, apre Spotify (*Dispositivi disponibili*). Nell'app, sezione **Widget**, c'è scritto quale pannello apre il pulsante.
- **Shuffle e +** dipendono da Spotify: se Spotify non risponde ai pulsanti premuti da un'altra app, non funzionano dal widget (play, pausa e brani funzionano comunque).
- **Spotify chiuso**: resta l'ultimo brano, in pausa. Play apre Spotify e avvia la musica appena è pronto.
- Il **titolo scorre** se il launcher lo permette; altrimenti viene tagliato.
- Angoli arrotondati da Android 12.

## Sviluppo

- Kotlin; widget con RemoteViews (layout XML), impostazioni con Jetpack Compose.
- AGP 8.13, Gradle 8.14, compileSdk 36, minSdk 26.
- Test unitari: `gradlew testReleaseUnitTest`.
