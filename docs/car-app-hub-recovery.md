# Car App Hub 3.1.1 kaynak kurtarma

Özgün `3.1.1-preview-source.zip` arşivi mevcut çalışma alanında veya GitHub
deposundaki erişilebilir Git geçmişinde bulunamadı. Kullanıcının paylaştığı
debug APK, daha önce teslim edilen sürümün SHA-256 özetiyle eşleşti.

Bu APK'dan derlenmiş uygulama sınıfları, manifest, yerelleştirmeler, araç ekranı
görünümleri, logo ve yazı tipi çıkarılabiliyor. Java görünümü okumayı kolaylaştırır.
JADX bazı yöntemleri tam çeviremediği için ilgili Smali dosyaları da kurtarma
paketinde saklanır. Bunlar düzenlenebilir referanslardır. Özgün Kotlin dosyaları,
yorumlar, testler, Gradle yapılandırması ve imzalama anahtarı APK'dan geri gelmez.

## Yerel kurtarma ve derleme kontrolü

Python 3, Java ve [Apktool 2.12.1](https://github.com/iBotPeaches/Apktool/releases/tag/v2.12.1)
ile kullanıcının debug arşivinden düzenlenebilir bir APK çalışma klasörü üretin:

```sh
python3 tools/recover-car-app-hub.py \
  --apktool-jar /path/to/apktool_2.12.1.jar --verify-build \
  /path/to/Car-App-Hub-3.1.1-preview-debug.zip ./car-app-hub-recovered
```

Betik APK özetini doğrular, kaynakları yeni klasöre çıkarır ve bildirilen
lisansları korur. İsteğe bağlı aşama imzasız bir APK oluşturur. Dokuz DEX
bölümünün, manifestin, kaynak tablosunun ve özgün Xposed metadata dosyalarının
pakette bulunduğunu kontrol eder. İmzasız paket, çalışan 3.1.1 uygulamasının
üzerine güncelleme olarak kurulamaz. Araç ve DHU çalışması bu yapısal kontrolden
ayrı doğrulanmalıdır.

## Yeniden tasarım için giriş noktaları

`CarMirrorActivity` araç menüsünün yerleşimini, seçili paket sırasını ve
içerik görünümünü yönetiyor. Java görünümünde tam çevrilemeyen `showApps`
yöntemi okunurken karşılık gelen Smali kullanılmalıdır. `ProjectionApps`
seçimleri tutuyor. `ProjectionCaptureService` oturumu yönetiyor. Ürün
arayüzünün etkileşimli taslağı `design/car-ui-preview/` altında bulunuyor.

Kurtarma paketi, 3.0.0 checkout'unu güncelleyen bir Gradle projesi değildir.
APK tabanlı değişiklik için mevcut paket kaynak numaraları, menü ile içerik
arasındaki geçişler ve mevcut oturum davranışı korunmalıdır. Nihai entegrasyon
için özgün Gradle yapısı yeniden oluşturulmalı veya APK tabanlı test sürümünün
farklı imzayla kurulması ayrıca ele alınmalıdır. Üçüncü taraf SDK ikilisinin
lisans durumu kurtarma arşivindeki `SDK-NOTICE.md` dosyasında açıklanmıştır.
