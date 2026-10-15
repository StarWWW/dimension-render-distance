# Dimension Render Distance

Overworld, Nether, End ve diğer tüm boyutlar için ayrı görüş mesafesi ve simülasyon mesafesi. Boyut değiştirdiğinde iki mesafe de kendiliğinden o boyutun değerine geçer.

*English: A client-side Fabric mod for Minecraft 26.1.2 and 26.2 that keeps a separate render distance and simulation distance per dimension and switches both automatically when you change dimension.*

## Özellikler

- **İki ayrı mesafe:** Her boyut için görüş mesafesi (chunk'ların ne kadar uzağa çizildiği) ve simülasyon mesafesi (mobların, ekinlerin ve farmların ne kadar uzağa kadar çalıştığı).
- **Otomatik hatırlama:** Her boyutta mesafeleri normal şekilde (Video Ayarları veya Sodium) değiştir, mod bulunduğun boyut için hatırlar.
- **Ayar ekranı:** Kaydırılabilir bir liste, her boyutta yan yana iki kaydırıcı. En sol konum o boyutta o mesafeye dokunmaz. Bulunduğun boyut ▶ ile işaretlidir. **Tümünü Sıfırla** her şeyi (onay sorduktan sonra) baştaki haline getirir.
- **Özel boyutlar:** Sunucuların ve diğer modların boyutları, girildiğinde listeye kendiliğinden eklenir. Artık kullanmadığın boyutları listedeki **×** ile silebilirsin.
- **Sunucu bildirimi:** Yeni görüş mesafesi sunucuya hemen bildirilir.
- **Sunucu sınırı:** Ayar ekranında sunucunun izin verdiği görüş mesafesi ve sunucunun simülasyon mesafesi gösterilir.
- **Mesaj:** Mesafe değişince hotbar'ın üstünde kısa bir mesaj çıkar (ör. "Nether · görüş mesafesi 8 chunk · simülasyon mesafesi 6 chunk"), istenirse kapatılabilir.
- Mod Menu desteği, ayar ekranı için tuş atanabilir ve 19 dil desteği var.
- Hiç mixin yok, sadece oyunun normal mesafe ayarlarını kullanır. Bu yüzden Sodium ve diğer modlarla çakışmaz.

## Simülasyon mesafesi hakkında

Simülasyon mesafesini tek oyunculu dünyalarda ve LAN'a açtığın dünyada oyunun kendi sunucusu kullanır. Bir sunucuya bağlıyken simülasyon mesafesini o sunucu belirler, senin ayarın orada bir şey değiştirmez. Ayar ekranı bunu ve sunucunun değerini gösterir.

## Gereksinimler

- Minecraft 26.1.2 (26.1.x) veya 26.2, Fabric Loader 0.19+, Java 25
- Fabric API (26.1.2 için 0.155.3+, 26.2 için 0.161.0+)
- İsteğe bağlı: Mod Menu

## Derleme

Java 25 gerekir. Prism Launcher'ın indirdiği Java 25'i de kullanabilirsin:

```
set JAVA_HOME=%APPDATA%\PrismLauncher\java\java-runtime-epsilon
gradlew build
```

Bu Minecraft 26.1.2 jar'ını üretir. 26.2 için:

```
gradlew build -Pmc=26.2
```

Hazır jar'lar `build/libs/` klasörüne çıkar (`-sources` olmayan dosya), adında Minecraft sürümü yazar: `dimrender-<sürüm>+26.1.2.jar` ve `dimrender-<sürüm>+26.2.jar`. Oyununun sürümüne uyanı kur. Her derleme jar'ı ayrıca ana klasördeki `builds/<minecraft sürümü>/` klasörüne kopyalar ve oradaki eski sürümünü siler; ana klasördeki `build-all.bat` bütün modları her sürüm için tek seferde derler. `gradlew build` ayrıca `src/test` altındaki JUnit testlerini de çalıştırır.

### Desteklenen Minecraft sürümleri

Her sürümün bağımlılık sürümleri `versions/<minecraft>.properties` dosyasındadır (Fabric API, Mod Menu). Kodun neredeyse tamamı ortaktır; Minecraft sürümleri arasında farklı olan birkaç çağrı (26.2'de ekranların `Minecraft.gui` üzerinden açılması gibi) `src/mc26.1/java` ve `src/mc26.2/java` altındaki `Compat` sınıfındadır. Yeni bir Minecraft sürümü eklemek için oraya bir `versions/` dosyası ve gerekirse bir `Compat` kopyası eklenir.

## Proje yapısı

```
src/main/java/dimrender/
  DimRenderMod.java            giriş noktası, oyun ayarlarına erişim, tuş ataması
  DimensionSwitcher.java       boyut değişimi ve hatırlama mantığı (oyundan bağımsız, test edilebilir)
  Setting.java                 iki mesafe: görüş ve simülasyon
  DimRenderConfig.java         ayarlar (config/dimrender.properties)
  client/DimRenderScreen.java  ayarlar ekranı
  client/ui/                   ayar ekranı iskeleti; üç modda aynıdır, ekranlar aynı görünür
  compat/ModMenuIntegration.java
src/mc26.1/java, src/mc26.2/java   Minecraft sürümüne göre değişen birkaç çağrı (Compat)
versions/                      her Minecraft sürümü için bağımlılık sürümleri
src/test/java/dimrender/       JUnit testleri (boyut değişimi, ayarlar, dil dosyaları)
src/main/resources/            fabric.mod.json, dil dosyaları, ikon
tools/MakeIcon.java            logoyu üretir: java tools/MakeIcon.java
tools/make_lang.py             dil dosyalarını üretir ve İngilizce'ye göre kontrol eder: python3 tools/make_lang.py
```

Bir yazıyı değiştirmek veya dil eklemek için `tools/make_lang.py` içindeki tabloyu düzenle ve çalıştır.

## Notlar

- Sunucuda gerçek görüş mesafesi, senin ayarın ile sunucunun izin verdiği mesafeden küçük olanıdır.
- Distant Horizons'ın LOD mesafesi ayrı bir ayardır ve bu mod onu değiştirmez.
- Eski sürümün ayar dosyası olduğu gibi okunur, kayıtlı görüş mesafeleri kaybolmaz.

## Lisans

MIT, `LICENSE` dosyasına bak.
