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
| copertina, titolo | apre Spotify (e dice all'app dove si trova il widget, per la sfocatura) |
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

I widget Samsung sfocano lo sfondo che hanno dietro, ne rinforzano i colori e lo scuriscono. Il widget di un'app non può vedere ciò che ha dietro, ma l'app può leggere lo sfondo del telefono e disegnare lei la stessa sfocatura. Per farlo:

1. In **Sfondo del widget** spegni *Copertina come sfondo* e attiva **Sfocatura**.
2. Android apre la pagina **Accesso a tutti i file**: attivalo per *Widget Musica*. Da Android 13 è l'unico modo per un'app di leggere lo sfondo del telefono. L'app non ha accesso a internet, quindi lo sfondo non esce dal telefono.
3. **Tocca il widget in due punti diversi**, per esempio play e brano successivo, oppure un pulsante e un punto vuoto della card. Android non dice all'app dove si trova il widget sulla schermata Home: lo dice il tocco. Con una griglia fitta One UI Home mostra i widget più piccoli della loro misura (all'84% sul Galaxy S24 Ultra di prova): il tocco dà l'angolo del pulsante com'è sullo schermo, ma la sua misura prima del rimpicciolimento, quindi da un tocco solo lo sfondo risulterebbe ingrandito e fuori posto. Due pulsanti distano sullo schermo quanto nel layout, per la scala: dai due tocchi l'app la misura, una volta sola. Se sposti il widget, basta toccarlo di nuovo una volta. Sotto **Sfondo del widget** è scritta la scala misurata.
4. L'immagine dello sfondo riempie lo schermo, centrata, come la mostra la Home: sul telefono di prova coincide con uno screenshot della Home al 99,98%, ed è quello che sfoca il widget Samsung nello stesso punto (differenza di circa 5/255 con il codice dell'app).

Su One UI anche una foto può essere disegnata da un'app di sfondi (Android la segnala come sfondo animato): l'app prova comunque a leggerne l'immagine. Se il telefono non la dà, compare **Immagine dello sfondo**: scegli la stessa foto che usi come sfondo e il widget sfoca quella. Se cambi lo sfondo, sceglila di nuovo.

Sfocatura, colori rinforzati e velo al 50%, scuro e chiaro, sono misurati sulla Home accanto a un widget Samsung al 50% sullo stesso sfondo: la differenza è di circa 5/255. Scuro e chiaro hanno la stessa sfocatura e la stessa trasparenza, cambia solo il colore del velo. Opacità 0 e 100 sono stimate dall'anteprima delle impostazioni dei widget Samsung.

Senza sfocatura (spenta, prima del primo tocco, con uno sfondo animato o se il telefono non dà l'immagine dello sfondo) resta un velo di colore. Il velo al 50%, scuro o chiaro, lascia passare la stessa luce del widget Samsung e prende i colori dello sfondo rinforzati, così il colore medio coincide; manca la sfocatura. Il colore dello sfondo viene dall'immagine dello sfondo se l'app la può leggere, altrimenti dal telefono (da Android 12); se nessuno dei due lo dà, il velo è grigio neutro. Sotto **Sfondo del widget**, nell'app, è scritto quale caso vale. Per somigliare agli altri widget della Home usa la loro stessa opacità.

Il testo usa il font di sistema Samsung (One UI Sans), come il player della tendina. I widget prendono il font di Android a meno che non si indichi una famiglia di sistema per nome: l'app la cerca negli elenchi dei font del telefono. In **Widget**, nell'app, è scritto quale ha trovato; se non la trova, il testo resta nel font di Android e lì sono elencati i file dei font che l'app vede, per capire come si chiama sul telefono.

Il colore della barra segue la stessa regola di One UI: la tinta della copertina, satura, oppure blu notte per le copertine in bianco e nero.

## Limiti noti

- **La barra non si trascina** e non ha l'onda animata: i widget di Android non supportano i cursori né le animazioni. Il pallino c'è e avanza un pixel alla volta (di solito circa 4 volte al secondo, al massimo 10), solo con lo schermo acceso e la musica in riproduzione: i widget non possono animare la barra, quindi l'app la ridisegna.
- **Cambio uscita**: su One UI 9 il widget apre il pannello di uscita di Android, non quello Samsung. Il selettore Samsung della tendina non si può aprire dalle altre app. Il pannello multimediale di *Media and devices* lo era sulle versioni precedenti di One UI: se è presente, il widget usa quello. Se manca anche il pannello di Android, apre Spotify (*Dispositivi disponibili*). Nell'app, sezione **Widget**, c'è scritto quale pannello apre il pulsante.
- **Shuffle e +** dipendono da Spotify: se Spotify non risponde ai pulsanti premuti da un'altra app, non funzionano dal widget (play, pausa e brani funzionano comunque).
- **Spotify chiuso**: resta l'ultimo brano, in pausa. Play apre Spotify e avvia la musica appena è pronto.
- Il **titolo scorre** se il launcher lo permette; altrimenti viene tagliato.
- Angoli arrotondati e sfocatura da Android 12.
- **Sfocatura durante lo scorrimento**: i widget Samsung sono sfocati dal launcher in tempo reale, quindi mentre scorri tra le schermate mostrano sempre lo sfondo che hanno dietro. Il widget di un'app è un'immagine fissa: mentre scorri si sposta con il widget, e torna allineata quando la schermata si ferma. Se ti dà fastidio, spegni _Sfocatura_.
- **Sfondi disegnati da un'app** (One UI li segnala come animati, anche quando sono una foto): One UI mostra la foto un po' sfocata sulla Home, circa 4 dp (misurati su uno screenshot confrontato con la foto), e il widget aggiunge quella sfocatura.
- **Sfocatura**: lo sfondo si sposta con il widget solo dopo un tocco; Samsung Home non deve far scorrere lo sfondo tra le pagine (è l'impostazione predefinita). La scala misurata vale anche per il widget del Politecnico, che la legge dallo stesso file (`.scala-widget-home` nella memoria del telefono).

## Sviluppo

- Kotlin; widget con RemoteViews (layout XML), impostazioni con Jetpack Compose.
- AGP 8.13, Gradle 8.14, compileSdk 36, minSdk 26.
- Test unitari: `gradlew testReleaseUnitTest`.
