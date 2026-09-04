# Phase 4 — Search Places

## Sonuç
Search Places uygulandı. Backend/API acceptance doğrulandı. Phase 5 başlatılmadı.
Simulator üzerinde manuel acceptance yapılmadı; kullanıcı tarafından yapılacak.

## İncelenen mevcut akış
ExploreScreen şehir/kategori için exploreApi.places çağırıyordu; mekan adı sorgusu
yoktu. ExploreService kişiselleştirme ve starter sonuçlar içeriyordu.
PlaceProviderService destination resolution, coğrafi sınır kontrolü, provider
önbelleği ve canonical Place upsert işlemlerini zaten yapıyordu. OSM provider'ın
geniş keşif sorguları node/way destekliyordu. SavedPlace ve itinerary işlemleri
canonical Place kimliğiyle çalışıyordu.

Yeni arama mevcut kişiselleştirme listesine ek bir query filtresi olarak
uygulanmadı: query-aware provider discovery gerektiğinden ayrı endpoint/service
eklendi. Mevcut destination resolver, Place cache/upsert, doğrulama kontratı ve
PlaceResponse/PlaceDetail kullanıldı.

## Backend/API sözleşmesi
GET /api/explore/places/search?city=Edirne&q=Selimiye

- Authentication gerekli.
- Şehir gerekli; sorgu 2–100 karakter. Kontrol karakterleri reddedilir.
- İsim eşleşmesi, kategori ve küçük bir EN/TR alias kümesi desteklenir:
  coffee/cafe/kahve/kafe, museum/müze, gallery/galeri, culture/kültür,
  food/restaurant/yemek/restoran, walking/yürüyüş, park.
- Cache eşleşmesi canonical, doğrulanmış ve destination coğrafi sınırı içinde olmalı.
- Eşleşme yoksa destination resolution → query-aware OSM discovery → canonical
  Place persistence → doğrulama → mevcut PlaceResponse.
- museum/gallery/park sorguları gerçek OSM type etiketleriyle filtrelenir.
  Yeni arama keşfinin type etiketleri cache eşleşmesi için korunur.
- İlgisiz bir kategori cache hit'i mekan adı discovery'sini engellemez.
- OSM sorgularında regex özel karakterleri literal olarak escape edilir.
- Arama node, way ve relation kapsar. Geniş itinerary/provider sorguları değişmez.
- Sonuçlar deterministic exact-name, prefix, normalized-name, ID sırasındadır.
  Aramaya özgü provider limiti 40, response limiti 20'dir.
- Eşleşme yoksa []; provider HTTP/transport hatası varsa sonuç yerine 503.
  Doğrulanamayan destination 400 döndürür.
- Starter/preview/synthetic fallback, yeni feedback eventi veya arama geçmişi yoktur.
- Schema/migration değişikliği yoktur.

## Mobil davranış
Explore ekranında mevcut seyahatin destination'ı içinde mekan/kategori aranır.
En az iki karakterden sonra Ara veya klavyedeki Search ile açıkça gönderilir.
Yazma sırasında istek gönderilmez. Temizle, mevcut kategori listesine geri döner.
Loading/error/empty durumları ayrıdır. Search mode'da preview fallback kullanılmaz.
İstek sürümü kontrolü eski sonuçların yeni sorguyu ezmesini engeller.
Sonuç mevcut PlaceDetail ekranına aynı canonical kimlikle açılır; mevcut save/add
akışları yeniden oluşturulmadı. EN/TR metinler eklendi.
Bu fazda ayrı destination seçicisi ve yeni SEARCHED/VIEWED eventleri eklenmedi.

## Automated acceptance
PlaceSearchIntegrationTest gerçek controller, JWT filter, destination resolver,
Nominatim/OSM HTTP adapter, cache/persistence ve mapper'ı çalıştırır. HTTP
cevapları deterministic test fixture'larıdır; fixture ID'leri canlı OSM kanıtı
olarak sunulmaz.

11 backend testi:
- Edirne/Selimiye adı ve relation desteği.
- Tallinn/Maiasmokk adı.
- Edirne ve Tallinn coffee kategori sorguları.
- Cache'te şehir yokken Ghent, Brno ve Ljubljana museum discovery.
- Cache reuse ve ilgisiz cache'in discovery'yi engellememesi.
- Boş sonuç/provider hatası, authentication/input validation, literal regex.
- Search işleminin feedback sayısını değiştirmemesi.

5 yeni mobil test:
- Şehir/sorgu gönderimi ve canonical sonuç kimliği.
- Submit öncesi provider çağrılmaması.
- Geç gelen eski response'un reddedilmesi.
- Hata durumunda sıfır sonuç ve error.
- Temizleme sonrası normal Explore akışı.

## Canlı backend/API acceptance
SearchLiveAcceptance test-classpath launcher'ı yeni H2 memory DB, rastgele loopback
port ve gerçek Nominatim/Overpass kullanarak endpoint'e authenticated HTTP GET
istekleri yaptı. Dönen her ID'nin persistence'da bulunduğu ve PlannerPlaceContract
ile doğrulandığı kontrol edildi. Normal developer/production DB kullanılmadı.
Tüm denemeler search-places-live-api.json dosyasında; ilk başarısız denemeler de
korunmuştur.

| Senaryo | Canlı sonuç |
| --- | --- |
| Edirne / Selimiye | Selimiye Camii — relation/3376582; son denemede 4 sonuç |
| Tallinn / Maiasmokk | Maiasmokk — node/274236812 |
| Edirne / coffee | 20 sonuç; Coffee Bee Bakery Lab, HiJazz Coffee ve diğer canonical kafeler |
| Tallinn / coffee | Maiasmokk; mevcut canonical cache eşleşmesi |
| Ghent / museum | 18 sonuç; Stadsmuseum Gent — way/105533772, diğer müzeler |
| Brno / museum | 20 sonuç; Moravské zemské muzeum — node/647885009, diğer müzeler |
| Ljubljana / museum | 20 sonuç; Mestni muzej Ljubljana — node/1502918208, diğer müzeler |

Ghent canlı resolver tarafından Gent olarak canonicalize edilir. Unseen şehir
testleri boş şehir cache'iyle başlar; canlı koşuda da bu şehirlere ait önceki
kayıtlar bulunmuyordu. Sonuç sayıları provider/cache durumuna göre değişebilir.

## Canlı kontrolün ortaya çıkardığı düzeltmeler
1. İlk Selimiye isteği 504, Ghent isteği 429 aldı. Bunlar acceptance başarısı
   sayılmadı. Provider hatasının [] gibi görünmesi arama akışında 503'e düzeltildi.
   Bekleme sonrası aynı provider ile tekrar kontrol edildi.
2. Selimiye tekrarında caminin kendisi eksikti. OSM relation desteği yeni search
   sorgusuna eklendi. Son canlı kontrol Selimiye Camii relation/3376582'yi buldu.
   Mevcut itinerary discovery kapsamı değiştirilmedi.

## Test sonuçları
- Backend compile + tam regression: 26 suite, 174 test, 0 failure/error/skip.
- Mobil: 23 test, 0 failure/skip.
- TypeScript: npx tsc --noEmit başarılı.
- git diff --check başarılı.
- Mevcut event, auth, Explore/provider, itinerary, destination, start-area, weather
  testleri tam regression içinde geçti.

## Değişen dosyalar
Yeni backend:
- backend/src/main/java/com/journy/backend/explore/search/PlaceSearchQuery.java
- backend/src/main/java/com/journy/backend/explore/search/PlaceSearchService.java
- backend/src/main/java/com/journy/backend/explore/search/PlaceSearchController.java

Provider entegrasyonu:
- backend/src/main/java/com/journy/backend/explore/provider/PlaceProvider.java
- backend/src/main/java/com/journy/backend/explore/provider/PlaceProviderService.java
- backend/src/main/java/com/journy/backend/explore/provider/OsmOverpassPlaceProvider.java

Mobil:
- mobile/src/api/journyApi.ts
- mobile/src/screens/ExploreScreen.tsx
- mobile/src/i18n/translations.ts

Test/rapor:
- backend/src/test/java/com/journy/backend/explore/search/PlaceSearchIntegrationTest.java
- backend/src/test/java/com/journy/backend/explore/search/SearchLiveAcceptance.java
- mobile/tests/placeSearch.test.cjs
- docs/personalization-phase-4.md
- docs/search-places-live-api.json

## Kapsam koruması ve sınırlamalar
TasteFeedback ağırlıkları, ProfileMapper, Taste Profile yüzdeleri, mevcut Explore
ranking formülü/limitleri ve mevcut kategori eşleme yolları değiştirilmedi.
Yeni search alias/type çözümlemesi yalnızca aramaya aittir. Weather, itinerary
generation ve start-area iş kuralları değiştirilmedi.

Public provider 429/504 verebilir; canlı sonuç garantisi değildir. API provider
kesintisini 503 olarak gösterir. Cache eşleşmesi varsa discovery yapılmaz; search
tüm şehirdeki tüm mekanların eksiksiz katalog taraması değildir. Pagination,
fuzzy/semantic matching ve query+category birleşik doğal dil sorguları kapsam dışı.
Provider adı eşleşmesi OSM'nin case-insensitive regex davranışıyla sınırlıdır;
cache normalization diacritic-insensitive olsa da provider için tam transliteration
garantisi yoktur.

## Kullanıcının yapacağı simulator acceptance
MANUAL SIMULATOR ACCEPTANCE PERFORMED: NO

1. Backend'i güncel Phase 4 koduyla çalıştır; mobil bundle'ı yenile.
2. Edirne seyahati aktifken Explore → Selimiye yaz → Ara.
   Selimiye Camii sonucunu aç; mekan adını ve detayını kontrol et.
3. Tallinn seyahatinde Maiasmokk ara ve detayını aç.
4. Her iki şehirde coffee ara; kategori sonuçlarını kontrol et.
5. Bir sonucu mevcut Save/Add to trip akışında kullan; gerçek mekan kimliği korunmalı.
6. Sorguyu değiştir/temizle; eski sonuç kalmamalı, normal Explore'a dönmeli.
7. Eşleşmeyen sorgu ve bağlantı kesintisinde empty/error ayrımını kontrol et.
8. Ghent/Brno/Ljubljana seyahatlerinde museum ara.
   Backend/API seviyesinde boş şehir cache'i discovery'si ayrıca automated ve
   canlı testlerle doğrulandı; simulator DB'si artık cache içeriyor olabilir.

## Durma noktası
Phase 4 implementasyonu ve backend/API kontrolleri tamamlandı.
Simulator acceptance kullanıcıya bırakıldı. Phase 5'e geçilmedi.
