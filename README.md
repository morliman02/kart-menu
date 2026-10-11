# Kart Menüsü (Android)

Diğer uygulamaların üstünde çalışan, kayıtlı kartları yüzen bir menüde gösteren Android uygulaması.
Kotlin, `minSdk 26` (Android 8.0+), `targetSdk 34`. Ek bağımlılık yok; yalnızca Android framework + Kotlin stdlib.

## Ne yapar (madde madde)

1. **Android, overlay + accessibility.** Uygulama `SYSTEM_ALERT_WINDOW` (diğer uygulamaların üstünde çizim) iznini ve bir **erişilebilirlik servisini** kullanır. Böylece başka bir uygulama (ör. Amazon) açıkken bile ekranın üstünde çalışabilir.
2. **Kart kaydetme.** Her kart için üç bilgi tutulur: **kart numarası**, **son kullanma tarihi (AA/YY)** ve **kart üzerindeki isim**. Kart numarası Luhn kontrolünden geçer, tarih geçmiş olamaz. CVV bilerek **tutulmaz**.
3. **Şifreli saklama.** Şifreleme anahtarı **Android Keystore** içinde (destekleyen cihazlarda donanımda) üretilir, uygulamadan dışarı çıkarılamaz. Kart listesi **AES‑256‑GCM** ile şifrelenip uygulamaya özel alana yazılır — diskte düz metin kart bilgisi olmaz. `INTERNET` izni yoktur; veri telefondan dışarı çıkamaz, buluta yedeklenmez.
4. **Soldan sağa kaydırma.** Ekranın sol kenarında ince, yarı saydam bir tutamak durur. Herhangi bir ekrandayken (ör. Amazon ödeme sayfası) parmağınızı bu tutamaktan sağa kaydırınca hareket algılanır.
5. **Yüzen menü.** Hareket algılanınca ekranın üstünde yüzen bir menü açılır; kayıtlı kartlar **Kart 1, Kart 2, Kart 3 …** diye listelenir (maskeli: `•••• 1234`).
6. **Tek dokunuşla otomatik doldurma.** Bir karta dokununca uygulama, ekrandaki kart numarası, son kullanma tarihi ve isim kutularını tanır ve o kartın üç bilgisini bu kutulara otomatik yazar (accessibility `ACTION_SET_TEXT`).
7. **Ekle / sil.** Kartlar ana ekrandan istenildiği zaman eklenebilir ve listede uzun basılarak silinebilir.
8. **Alan tanıma (etiketten).** Kutular, ekrandaki etiketlerine bakılarak eşleştirilir: "kart numarası", "son kullanma tarihi", "kartın üzerindeki ad" gibi Türkçe etiketler ve bunların İngilizce karşılıkları (card number, expiry / valid thru, name on card / cardholder …). Etiket; kutunun ipucu metni (hint), içerik açıklaması, görünüm kimliği (view id) ve düzende hemen önündeki metin görünümünden okunur.

## Adresler (Amazon adres ekranı)

Aynı mantık adresler için de çalışır:

- Adresler de **şifreli saklanır** (aynı Keystore anahtarı, AES‑256‑GCM), ana ekrandan **eklenip silinebilir** (silmek için listede uzun basın).
- Yüzen menüde kartların altında **Adres 1, Adres 2 …** diye listelenir.
- Bir adrese dokununca uygulama, ekrandaki adres formunun kutularını etiketlerinden tanıyıp doldurur.

Tutulan alanlar (Amazon.com.tr adres formuna göre seçildi): **ad soyad (alıcı)**, **telefon numarası**, **adres** (mahalle, cadde/sokak, bina, daire), **ilçe**, **şehir/il**, **posta kodu**. Tanınan etiketler Türkçe ve İngilizce: ad soyad / full name / recipient, telefon / phone, adres / address / street / mahalle, ilçe / district, şehir / city, posta kodu / zip / postal. Formda olmayan alanlar atlanır; sadece eşleşen kutular doldurulur.

> Not: CVV kartlarda tutulmadığı gibi, adres alanları da yalnızca sizin girdiğiniz kadardır. Hiçbir veri telefondan dışarı çıkmaz.

## Tek dokunuşla doldurma nasıl çalışır

- Karta dokununca uygulama alttaki ekranın (ör. Amazon ödeme sayfası) metin kutularını gezip her birini etiketine göre sınıflar (madde 8) ve tanıdığı kutulara ilgili bilgiyi yazar (madde 6). Kaç alanın dolduğunu kısa bir bildirimle gösterir.
- Menüdeki küçük **"Numara / SKT / İsim"** düğmeleri tek bir alanı elle yazmak için yedek yoldur; bunlar o an odaktaki alana yazar.
- Hiçbir kutu etiketinden tanınamazsa, uygulama önce odaktaki alana kart numarasını yazmayı dener; o da olmazsa numarayı **panoya kopyalar** ve **60 saniye sonra panodan otomatik siler** (Android 13+ için "hassas içerik" olarak işaretlenir, pano önizlemesinde gizlenir).

## Güvenlik notları

- Uygulama ve kart ekleme ekranı `FLAG_SECURE` ile işaretlidir: ekran görüntüsü alınamaz, son uygulamalar önizlemesinde görünmez.
- Erişilebilirlik servisi ekran **içeriğini okumaz/kaydetmez**; yalnız kart seçtiğinizde o anki metin alanına yazar.
- Sol kenardaki tutamak bölgesinde sistemin "geri" hareketi (Android 10+) devre dışı bırakılarak kaydırma güvenle algılanır.

## Derleme

Google Maven'e erişimi olan bir ortamda tek komut:

```bash
./gradlew assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
```

Ayrıntı ve SDK kurulumu için **BUILD.md**'ye bakın.

## Kurulumdan sonra (telefonda)

1. APK'yı kurun, uygulamayı açın.
2. **Diğer uygulamaların üstünde gösterme** iznini verin.
3. **Erişilebilirlik servisi**ni açın (Ayarlar açılır; listeden "Kart Menüsü kaydırma hareketi"ni etkinleştirin).
4. **Kart ekle** ile kartlarınızı girin.
5. Herhangi bir ekranda sol kenardan sağa kaydırıp menüyü açın.
