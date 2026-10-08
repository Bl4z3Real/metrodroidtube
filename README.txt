MetroTube - YouTube con interfaccia Metro / Windows Phone (Android 10+)
1. Apri la cartella in Android Studio (Hedgehog o più recente) e attendi la sincronizzazione Gradle.
2. In app/src/main/java/it/metro/tube/MainActivity.kt sostituisci API_KEY con la tua chiave YouTube Data API v3
   (console.cloud.google.com > API e servizi > abilita "YouTube Data API v3" > Credenziali > Chiave API).
3. Run su dispositivo/emulatore. Build > Build APK per l'APK.

Pubblicazione per tutti (GitHub Releases)
1. Crea un repository PUBBLICO su GitHub e carica tutto il contenuto della cartella (incluso .github).
2. Per pubblicare una versione: git tag v1.0 && git push --tags
   Il workflow "Release APK" compila e allega MetroTube-v1.0.apk alla pagina Releases: chiunque può scaricarlo.
3. NON mettere la tua chiave API nel codice o nei secret: l'APK pubblico non la contiene.
   Ogni utente inserisce la propria chiave gratuita in Settings > API key (al primo avvio l'app lo chiede).
4. Il workflow "Build APK" (tab Actions > Run workflow) serve solo per prove: scarica l'artifact MetroTube-debug-apk.

Login Google (iscrizioni, feed, like, commenti)
1. Google Cloud Console: abilita "YouTube Data API v3"; schermata di consenso OAuth; Credenziali > ID client OAuth > tipo "TV e dispositivi con input limitato".
2. Inserisci ID e secret in Settings > OAuth client dell'app, oppure (per la release pubblica) nei secret del repository OAUTH_CLIENT_ID e OAUTH_CLIENT_SECRET.
3. Con la schermata di consenso in modalità "Test" possono accedere solo gli utenti di test (max 100); per tutti serve la verifica di Google.
Nota: YouTube non offre via API la cronologia di visione né "Guarda più tardi": restano salvate sul dispositivo.

Aggiornare l'app senza conflitti
- L'APK è sempre firmato con la stessa chiave (keystore/metrotube.keystore, password "metrotube") e il versionCode cresce a ogni build:
  installando una nuova versione sopra la vecchia i dati restano (cronologia, chiave API, login).
- Solo la PRIMA volta, se hai installato versioni firmate con un'altra chiave, serve disinstallarle una volta.
- Per usare una chiave tutta tua (consigliato se il repository è pubblico): crea la keystore, poi nei secret del repository metti
  KEYSTORE_BASE64 (base64 del file), STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Se non li imposti si usa quella inclusa.
