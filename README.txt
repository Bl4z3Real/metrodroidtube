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
