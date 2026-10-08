plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.metro.tube"
    compileSdk = 34
    defaultConfig {
        applicationId = "it.metro.tube"; minSdk = 29; targetSdk = 34
        // Sempre crescente: gli aggiornamenti si installano sopra la versione precedente
        versionCode = (System.currentTimeMillis() / 60000).toInt()
        versionName = System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }?.removePrefix("v") ?: "1.0"
    }
    // Firma sempre con la stessa chiave: niente "conflitto" con l'app già installata
    signingConfigs {
        create("fixed") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "${rootDir}/keystore/metrotube.keystore")
            storePassword = System.getenv("STORE_PASSWORD") ?: "metrotube"
            keyAlias = System.getenv("KEY_ALIAS") ?: "metrotube"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "metrotube"
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("fixed") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
