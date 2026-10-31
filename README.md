# Widget Musica (Spotify), uso personale

App Android personale con un widget per Spotify uguale al player multimediale del sistema (quello della tendina e della schermata di blocco):

- **copertina** come sfondo, scurita per leggere il testo;
- **dispositivo** su cui suona la musica (Questo smartphone, cuffie Bluetooth, Spotify Connect), con il pulsante per cambiare uscita;
- **titolo • artista**, che scorre se è lungo;
- **barra di avanzamento** con tempo trascorso e durata, nel colore della copertina;
- **shuffle e +** (aggiungi ai Brani che ti piacciono) con le icone e le funzioni di Spotify stesso, poi precedente, play/pausa, successivo.

Non è un'app di Spotify e non va distribuita.

## Come funziona

Il widget usa solo la **sessione multimediale** di Spotify sul telefono, la stessa che usa il player del sistema. Per leggerla serve l'**Accesso alle notifiche**; le notifiche però non vengono lette né salvate.

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

L'app ha l'aspetto delle Impostazioni di One UI 9.

1. Apri **Widget Musica**.
2. Tocca **Accesso alle notifiche** e attiva *Widget Musica*.
3. Tocca **Aggiungi alla schermata Home**: One UI chiede dove metterlo e, una volta aggiunto, compare *Widget aggiunto alla schermata Home*. In alternativa tieni premuto sulla schermata Home → *Widget* → *Widget Musica*.

Nell'app trovi anche:

- **Pulsante di uscita**: quale pannello apre il pulsante del widget; toccandolo lo provi;
- **Brani che ti piacciono**: apre in Spotify la playlist dove il **+** del widget salva i brani.

## Il widget

| Tocchi | Cosa succede |
| --- | --- |
| copertina, titolo | apre Spotify |
| dispositivo o icona ▷) in alto a destra | cambia uscita audio (vedi limiti) |
| shuffle, + | le stesse funzioni dei pulsanti di Spotify nel player del sistema. Il + salva il brano in *Brani che ti piacciono* (La tua libreria): diventa ✓ e compare la conferma |
| ⏮ ▶/⏸ ⏭ | comandi di riproduzione |

Il widget si ridimensiona liberamente: pulsanti, testo e margini crescono e si riducono con lui.

- **Basso**: sparisce prima la riga del dispositivo, poi i tempi.
- **Stretto** (circa 2 colonne): restano ⏮ ▶ ⏭ e il pulsante di uscita, come il player piccolo della tendina.

Lo sfondo è la copertina sfumata come nel player di One UI: più chiara al centro in alto, più scura verso i lati e il basso.

In alternativa, nell'app (**Sfondo del widget**) puoi spegnere **Copertina come sfondo** e usare lo sfondo dei widget Samsung, con le stesse opzioni:

- **Opacità**: 0, 50 o 100%;
- **Colori**: Scuro o Chiaro.

I widget Samsung sfocano lo sfondo che hanno dietro, ne rinforzano i colori e lo scuriscono. Il widget di un'app non può sfocare ciò che sta dietro, perché è riservato ai widget di sistema: può solo stendere sopra un velo di colore. Il velo scuro al 50% è misurato sulla Home, accanto a un widget Samsung al 50% sullo stesso sfondo: lascia passare la stessa luce e prende i colori dello sfondo rinforzati, così il colore medio coincide; manca solo la sfocatura. Opacità 0 e 100 e il velo chiaro sono misurati sull'anteprima delle impostazioni dei widget Samsung.

Il colore dello sfondo lo comunica il telefono (da Android 12). Se non lo comunica, per esempio con uno sfondo animato, il velo è grigio neutro. Sotto **Sfondo del widget**, nell'app, è scritto quale dei due casi vale. Per somigliare agli altri widget della Home usa la loro stessa opacità.

Il testo usa il font di sistema Samsung (One UI Sans), come il player della tendina. I widget prendono il font di Android a meno che non si indichi una famiglia di sistema per nome: l'app la cerca negli elenchi dei font del telefono. In **Widget**, nell'app, è scritto quale ha trovato; se non la trova, il testo resta nel font di Android.

Il colore della barra segue la stessa regola di One UI: la tinta della copertina, satura, oppure blu notte per le copertine in bianco e nero.

## Limiti noti

- **La barra non si trascina** e non ha l'onda animata: i widget di Android non supportano i cursori né le animazioni. Il pallino c'è e avanza con la barra, che si aggiorna ogni secondo, solo con lo schermo acceso e la musica in riproduzione.
- **Cambio uscita**: su One UI 9 il widget apre il pannello di uscita di Android, non quello Samsung. Il selettore Samsung della tendina non si può aprire dalle altre app. Il pannello multimediale di *Media and devices* lo era sulle versioni precedenti di One UI: se è presente, il widget usa quello. Se manca anche il pannello di Android, apre Spotify (*Dispositivi disponibili*). Nell'app, sezione **Widget**, c'è scritto quale pannello apre il pulsante.
- **Shuffle e +** dipendono da Spotify: se Spotify non risponde ai pulsanti premuti da un'altra app, non funzionano dal widget (play, pausa e brani funzionano comunque).
- **Spotify chiuso**: resta l'ultimo brano, in pausa. Play apre Spotify e avvia la musica appena è pronto.
- Il **titolo scorre** se il launcher lo permette; altrimenti viene tagliato.
- Angoli arrotondati da Android 12.

## Sviluppo

- Kotlin; widget con RemoteViews (layout XML), impostazioni con Jetpack Compose.
- AGP 8.13, Gradle 8.14, compileSdk 36, minSdk 26.
- Test unitari: `gradlew testReleaseUnitTest`.
