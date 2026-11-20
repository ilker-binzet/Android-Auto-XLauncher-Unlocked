# Car App Hub — özgün araç arayüzü revizyonu

Güncelleme: Gönderilen 3.1.1 debug APK'sı temel alınarak yerel uygulama menüsü
ve içerik kontrolleri oluşturuldu. [Kurtarma derlemesi](../recovery-ui/README.md)
özgün APK'yı girdi olarak kullanıyor. Aşağıdaki notlar ilk tasarım teslimini
belgeliyor.

Bu çalışma, kullanıcının yeniden tasarım isteğiyle başlatıldı. Önceki bekleme
talimatından sonra artık tasarım hazırlanması açıkça istendi. Fotoğraflardaki
diğer uygulamanın arayüzü kopyalanmıyor. İlk teslim, incelenebilir bir
[etkileşimli önizleme](../design/car-ui-preview/index.html) ve uygulamaya aktarım
sınırlarıdır; yeni Android Auto sürümü veya APK değildir.

## Tasarım kararları

- Büyük simge ve ad içeren, kutu çerçevelerine boğulmayan uygulama menüsü.
- Altı uygulama/sayfa; kısa ekranlarda üç, dar önizlemede dört. Gerçek uygulama
  listesi telefonda düzenlenecek; görüntüye sığdırmak için hedefler küçültülmeyecek.
- Tek ve ölçülü kehribar vurgu, mevcut grafit/kırık beyaz tema paleti,
  Space Grotesk tipografi. Teknik durum cümleleri ve süs öğeleri yok.
- İçerik açıkken dar kontrol şeridi; kısa ekranlarda alt şerit. Menüye dönme,
  Geri, Görüntü ve Bitir aynı sırayı koruyor. Görünüm seçenekleri gerektiğinde açılıyor.
- Sığdır ve Doldur seçenekleri en-boy oranını koruyor. Paylaşımı bitirme onayı,
  iptal, boş son açılanlar, eksik ikon ve uzun ad durumları tasarlandı.
- Android Auto'nun kendi gezinme/medya çubuğu yeniden çizilmiyor veya kapatılmıyor.

## Doğrulanan kaynak durumu ve engel

`ilker-binzet/Android-Auto-XLauncher-Unlocked` deposunun yerel ve uzaktaki
`development` dalı `b60b7d5`; `app/build.gradle.kts` sürümü **3.0.0**. GitHub
API, tüm Git ref'leri ve mevcut pull request kontrol edildi. Diğer dal da
libxposed geçişine ait; 3.1.1 kaynakları bu ref'lerde yok. Önceki çalışan
Car App Hub kaynak arşivi ve paylaşım kodu mevcut çalışma alanında bulunmuyor.

Bu yüzden uygulama işlevleri, paket kimliği, sürüm ve imza değiştirilmedi.
Önizlemedeki örnek uygulama listesi gerçek telefon taraması gibi sunulmadı.
Çalışan sürümün yerine eksik bir APK hazırlanmadı.

Entegrasyon için daha önce teslim edilen
**`Car-App-Hub-3.1.1-preview-source.zip`** veya bu kaynakları gerçekten içeren
dal/commit gerekiyor. Kaynak geldiğinde tasarım mevcut araç paylaşımının üzerine
uygulanacak. Kullanıcının bildirdiği tam telefon yansıtma ve FPS sorunları ayrı
açık başlıklardır; bu tasarım çalışması bunları çözmüş gibi raporlanmıyor.

## Tasarım incelemesi

God of Design'ın yön, UI/UX, erişilebilirlik, anti-slop ve inceleme rehberleri
kullanıldı. Rehberler bu görev için ayrı klasöre alınarak okundu; uygulamaya
eklenti/çalışma zamanı bağımlılığı eklenmedi.

| Boyut | Değerlendirme |
| --- | --- |
| Kavram | Araç içi uygulama seçimine odaklı; pazarlama panelleri ve dekoratif sayaçlar yok. |
| Hiyerarşi | Uygulama seçimi birincil, görünüm ve oturum kontrolleri ayrı. |
| Yerleşim | Beş boyutta taşma/hedef kontrolleri; dar yükseklikte sayfa yoğunluğu azalıyor. |
| Tipografi | Yerel font dosyası, kısa adlar, uzun adlar için iki satır. |
| Renk | Önceki semantik palet; temel altı metin/zemin çiftinde hesaplanan kontrast en az 5,65:1. |
| Simgeler | Kimliği belirgin örnek uygulama ikonları; gerçek paket ikonları entegrasyonda bağlanacak. |
| Tutarlılık | Aynı kontrol sırası, tek tema kaynağı, odak ve seçim göstergeleri. |
| Kullanılabilirlik | Açma/dönme, sayfalama, iptal ve yeniden başlama tarayıcıda çalışıyor. |
| Erişilebilirlik | Etiketler, klavye ve görünür odak var; gerçek araç/TalkBack denemesi yapılmadı. |
| İşçilik | Yerel varlıklar, gölgesiz yüzeyler; tarayıcı ekran görüntüleriyle gözden geçirildi. |

Bu inceleme bir Android Auto uygunluk veya gerçek cihaz doğrulaması değildir.
