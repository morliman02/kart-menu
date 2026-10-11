# Derleme

## Yol 1 — Gradle (önerilen)

Android SDK + Google Maven erişimi olan herhangi bir makinede (veya Android Studio'da):

```bash
# Android Studio: projeyi aç, "Run" ya da Build > Build APK(s)
# veya komut satırı:
./gradlew assembleDebug
```

Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

Gereken: JDK 17, Android SDK (platform 34, build-tools 34), `ANDROID_HOME` ayarı. `gradlew` eksik dışa bağımlılıkları (Android Gradle Plugin, Kotlin) kendisi indirir.

## Yol 2 — SDK'sız manuel derleme

Bu depo, Google sunucularına erişimi olmayan bir ortamda da elle derlenebilir. Gerekenler: `aapt2`, `apksigner`, `zipalign` (Ubuntu: `android-sdk-build-tools`/`aapt`/`apksigner`/`zipalign` paketleri), `kotlinc` ve `dalvik-exchange` (veya `d8`), ayrıca bir `android.jar` (ör. Robolectric `android-all` jar'ı derleme sınıf yolu için).

Özetle adımlar:

1. `aapt2 compile --dir app/src/main/res -o compiled.zip`
2. `aapt2 link -o base.apk -I <android.jar> --manifest <package'li manifest> --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0 --java gen compiled.zip`
   - Not: `aapt2 link`, manifest'te bir `package="..."` özniteliği ister. Gradle bunu `namespace`'ten enjekte eder; elle derlerken manifest kökündeki `<manifest ...>` etiketine `package="com.fatih.kartmenu"` ekleyin (veya `--rename-manifest-package`).
3. Kotlin'i derle: `kotlinc -jvm-target 1.8 -classpath <android.jar> -d classes gen/.../R.java app/src/main/java/.../*.kt`
4. DEX üret: `dalvik-exchange --dex --min-sdk-version=26 --output=classes.dex classes <kotlin-stdlib (Java 9+ parçaları çıkarılmış)>`  — veya `d8 --min-api 26 --output . classes/**/*.class <kotlin-stdlib.jar>`
5. `classes.dex`'i `base.apk` içine ekle, `zipalign -f 4` ile hizala, `apksigner sign --ks <debug.keystore> ...` ile imzala.

> Bu oturumun çalıştığı sanal ortamda son paketleme/imzalama adımları (APK üretmek) güvenlik sınıflandırıcısı tarafından engellendiği için hazır APK burada üretilemedi. Kaynak eksiksiz ve Kotlin tarafı temiz derleniyor (DEX üretimi doğrulandı). Yol 1 ile kendi makinenizde ya da Android Studio'da tek komutta APK alırsınız.
