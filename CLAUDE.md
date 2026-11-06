# Regole del progetto

- **Aspetto nativo One UI 9**: l'app e il widget sono per un Galaxy S24 Ultra con One UI 9 e devono sembrare parte del sistema.
  - Il widget riprende il player multimediale della tendina.
  - Le schermate dell'app riprendono le pagine delle Impostazioni Samsung.
    - Colori, misure e componenti sono in `app/src/main/java/it/personale/spotifywidget/ui/OneUi.kt`, misurati su screenshot del telefono.
    - Prima di aggiungere un componente nuovo, chiedi uno screenshot dell'equivalente Samsung invece di usare lo stile Material standard.
- **Uso personale**: l'app non va distribuita, non usa il marchio Spotify nel nome e non contiene segreti.
- **Lingua**: testi dell'interfaccia e guida in italiano; codice e commenti in inglese.
