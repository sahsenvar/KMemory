<h1 align="center">KMemory</h1>

<p align="center">
  <strong>Bir Kotlin arayüzünden üretilen, tip güvenli DataStore erişimi.</strong>
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white"/>
  <img alt="KSP" src="https://img.shields.io/badge/KSP-2.3.9-orange"/>
  <img alt="Status" src="https://img.shields.io/badge/0.1.0-mavenLocal-lightgrey"/>
</p>

---

## Amaç

`androidx.datastore-preferences` üzerine yazılan kod her seferinde aynıdır: `Preferences.Key`
tanımla, `dataStore.data.map { }` yaz, `dataStore.edit { }` yaz, hatayı nereye raporlayacağına
karar ver, anahtarı bir yerde sabit tut. KMemory bu katmanı bir **arayüzden** üretir.

```kotlin
@Preferences(name = "auth.preferences_pb")
interface AuthMemorySource {

    @Read(KEY_TOKEN)  fun readToken(): Flow<String?>
    @Read(KEY_TOKEN)  suspend fun readTokenOnce(): String?
    @Write(KEY_TOKEN) suspend fun writeToken(value: String)
    @Erase(KEY_TOKEN) suspend fun eraseToken()

    @EraseAll suspend fun eraseAll()

    companion object {
        private const val KEY_TOKEN = "1e01afdb-736a-4854-9370-99c9f1f051af"
    }
}
```

İşlemci bunun karşılığında `AuthMemorySourceImpl` sınıfını ve `fun KMemory.authMemorySource()`
fabrika uzantısını üretir. Üretilen kodun hiçbir yerinde `android.content.Context` geçmez —
bağımlılık `DataStore<Preferences>`'tir; dolayısıyla üretilen kaynak Robolectric'siz, düz bir JVM
testinde gerçek bir geçici dosya üzerinde koşar.

Bu depo [SemenciucCosmin/KspPreferences](https://github.com/SemenciucCosmin/KspPreferences) 2.0.0'ın
fork'udur; anotasyon yüzeyinin tamamı yeniden tasarlanmıştır (bkz. [CHANGELOG](CHANGELOG.md)).

---

## Kurulum

0.1.0 henüz Maven Central'da **değildir**; `publishToMavenLocal` ile yerel depoya yayınlanır.

```kotlin
// settings.gradle.kts — tüketici tarafı
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()        // androidx.datastore YALNIZCA google() deposundadır
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts — tüketici modül
plugins {
    kotlin("plugin.serialization")            // yalnızca @Serializable tip saklayacaksan
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("io.github.sahsenvar:kmemory-annotations:0.1.0")
    ksp("io.github.sahsenvar:kmemory-compiler:0.1.0")
}
```

`kmemory-annotations`; `datastore-preferences`, `kotlinx-coroutines-core` ve
`kotlinx-serialization-json` bağımlılıklarını `api` olarak taşır — üretilen kodun imzalarında
göründükleri için ayrıca eklemen gerekmez.

**Sürüm tabanı:** Kotlin `2.3.21`, KSP `2.3.9`, datastore `1.2.0`, kotlinx-serialization `1.9.0`.
Bunlar tek tüketicinin (Zad) sürümleridir; kütüphane bilerek daha yeni bir datastore/serialization
ile yayınlanmaz, aksi halde Gradle'ın conflict-resolution'ı tüketiciyi sessizce yukarı çekerdi.

---

## Anotasyonlar

| Anotasyon | Hedef | İmza | Üretilen |
|---|---|---|---|
| `@Preferences(name)` | arayüz | — | `name` doğrudan DataStore **dosya adıdır**, `STORE_NAME` sabitine literal gömülür |
| `@Read(key)` | fonksiyon | `fun x(): Flow<T?>` | `dataStore.data.map { … }` |
| `@Read(key)` | fonksiyon | `suspend fun x(): T?` | `dataStore.data.first().let { … }` |
| `@Write(key)` | fonksiyon | `suspend fun x(value: T)` | `dataStore.edit { prefs[KEY] = value }` |
| `@Write(key)` | fonksiyon | `fun x(value: T): Flow<Unit>` | `flow { dataStore.edit { … }; emit(Unit) }` |
| `@Erase(key)` | fonksiyon | `suspend fun x()` | `dataStore.edit { prefs.remove(KEY) }` |
| `@Erase(key)` | fonksiyon | `fun x(): Flow<Unit>` | `flow { dataStore.edit { prefs.remove(KEY) }; emit(Unit) }` |
| `@EraseAll` | fonksiyon | `suspend fun x()` / `fun x(): Flow<Unit>` | `dataStore.edit { prefs.clear() }` — arayüz başına en fazla bir tane |

Tip **imzadan çıkarılır**; upstream'in `@StringPreference` / `@IntPreference` / … ailesi yoktur.
`@Erase` imzasında tip taşımaz: işlemci fonksiyonları anahtara göre gruplar ve tipi aynı gruptaki
`@Read`/`@Write`'tan alır.

Desteklenen `T`: `String`, `Int`, `Long`, `Float`, `Double`, `Boolean` ve `@Serializable` işaretli
herhangi bir tip (JSON metni olarak `String` alanında saklanır). Ayrıca kotlinx-serialization'ın
kutudan desteklediği kapsayıcılar — `List`, `Set`, `Map`, `Collection`, `Array`, `Pair`, `Triple` —
eleman tipleri de desteklendiği sürece kullanılabilir (`List<SearchHistory>` gibi); bunlar da JSON
metnine düşer. `Result<T>` gibi tanınmayan sarmalayıcılar derlemede reddedilir.

Bildirilen tip üretilen imzaya **birebir** taşınır: tip argümanları, nullability ve gereken
import'lar dahil. `@Write` parametresi nullable ise `null` yazmak anahtarı **siler** — "null'ı yok
say" davranışı çağıranı sessizce yanıltırdı, çünkü `readX()` eski değeri döndürmeye devam ederdi.

### `@Read`'in iki şekli

`Flow<T?>` canlı okumadır: anahtar her değiştiğinde yeniden yayınlar.
`suspend fun (): T?` tek seferlik okumadır: akışın ilk değerini alır.

İkisi aynı anahtar için aynı arayüzde birlikte bildirilebilir; değeri okuyan ifade ikisinde de
birebir aynı üretilir.

### `@Write` / `@Erase` / `@EraseAll`'un iki şekli

`suspend fun x(…)` işi **çağrı anında** yapar.
`fun x(…): Flow<Unit>` işi **collect anında** yapar ve ardından tek bir `Unit` yayar.

```kotlin
override fun writeAuthToken(value: String): Flow<Unit> = flow {
    dataStore.edit { prefs -> prefs[KEY_AUTH_TOKEN] = value }
    listener.onWrite(STORE_NAME, RAW_AUTH_TOKEN)
    emit(Unit)
}.catch { error -> report(RAW_AUTH_TOKEN, error); throw error }
```

İkisi de yerleşiktir ve aynı arayüzde, hatta aynı anahtar için birlikte bildirilebilir; hangisinin
kullanılacağı **tüketicinin** kararıdır. Zad tarafında seçim `Flow`'dur: böylece `MemorySource`
üyeleri `RemoteSource` üyeleriyle aynı şekli taşır ve repository idiomu değişmez.

```kotlin
// Repository — RemoteSource ile birebir aynı idiom
override fun writeAuthToken(value: String): Flow<Unit> =
    flow { emitAll(memorySource.writeAuthToken(value)) }.flowOn(ioContext)
```

> **Soğukluk bilinçlidir:** `collect` edilmeyen bir yazma **hiç olmaz** — ne diske yazılır ne de
> `PreferenceListener` tetiklenir. Hata da aynı şekilde yalnızca collect anında doğar. Bu davranış
> `sample` modülündeki `FlowSamplePreferencesTest` ile sabitlenmiştir; "kolaylık olsun" diye sıcak
> hale getirilmesi bir davranış kırılmasıdır.

### suspend kuralı dönüş şekline bağlıdır, accessor'a değil

> **`Flow` dönen fonksiyon `suspend` OLAMAZ. `Flow` dönmeyen fonksiyon `suspend` OLMALI.**

Flow soğuktur, iş `collect` anında yapılır — `suspend fun (): Flow<T>` anlamsızdır. Kural dört
anotasyon için de aynıdır; "`@Write` daima suspend'dir" gibi accessor bazlı bir istisna yoktur.

---

## Üretilen sabit adları accessor adından türer

Anahtarın kendisi bir UUID olabilir; üretilen sabitin **adı** ondan türemez. Ad, o anahtarı
paylaşan grubun **ilk bildirilen** fonksiyonunun adından üretilir:

```kotlin
@Read(KEY_PROFILE)  fun readProfile(): Flow<Profile?>      // KEY_PROFILE / RAW_PROFILE
@Write(KEY_PROFILE) fun writeProfile(value: Profile): Flow<Unit>
@Erase(KEY_PROFILE) fun eraseProfile(): Flow<Unit>
```

Kural:

1. Grubun ilk fonksiyonunun adı alınır.
2. Baştaki erişim öneki kelime sınırında, büyük/küçük harf duyarsız soyulur:
   `read` · `write` · `erase` · `get` · `set` · `put` · `delete` · `clear`. Soymadan sonra kalan
   baştaki ayırıcılar da kırpılır (`read_pin` → `PIN`, `_PIN` değil).
3. Kalan `UPPER_SNAKE_CASE`'e çevrilir — `readPinCode` → `PIN_CODE`,
   `writeSearchHistory` → `SEARCH_HISTORY`.
4. Kalan boşsa (`fun read()`) ya da ASCII tanımlayıcı eki değilse, anahtarın ilk 20
   alfanümeriği + grup indeksi kullanılır (`RAW_K_0`).
5. Aynı **tanımlayıcıyı** üreten **çarpışan tüm gruplara** grup indeksi eklenir
   (`KEY_PROFILE_0` / `KEY_PROFILE_1`); çarpışmayanlar indekssiz kalır.

### İki ad uzayı: `KEY_` ve `RAW_`

Her grup companion'a iki tanımlayıcı yazar — anahtarın metnini tutan `const` sabit (`RAW_<ek>`) ve
ondan yapılan `Preferences.Key` (`KEY_<ek>`). **Hiçbir önek diğerinin öneki olamaz:** `P2 == P1 + R`
olsaydı `ek_1 == R + ek_2` olan her ek çiftinde iki farklı grup aynı tanımlayıcıyı üretir ve
companion "Conflicting declarations" ile düşerdi. Metin sabiti eskiden `KEY_NAME_` öneki taşıyordu
ve `readNameSurname` + `readSurname` ikilisi tam bu şekilde derlemeyi kırıyordu; `KEY_` / `RAW_`
çifti ilk karakterinden ayrıştığı için uzaylar arası çarpışma artık **yapısal olarak imkânsız**.
Tekillik ayrıca ekler üzerinden değil, companion'a yazılacak **son adlar** üzerinden doğrulanır —
ileride üçüncü bir ad uzayı eklenirse koruma orada da devrededir.

Değişen yalnızca **tanımlayıcı adı**dır: anahtarın kendisi `RAW_X = "<uuid>"` literalinde
olduğu gibi kalır ve `const` sabitler derleme zamanında inline edildiği için APK'da görünmez —
bu bir güvenlik değişikliği değil, üretilen kodun ve stacktrace'in okunabilirliğidir.

---

## Varsayılan değer kavramı yoktur

Hiçbir anotasyonda `defaultValue` bulunmaz ve **her okuma nullable'dır**. Anahtar hiç yazılmamışsa
`null` gelir.

Varsayılan bir **iş kuralıdır** ve repository katmanına aittir. Kütüphaneye konduğunda iki ayrı
durum tek bir değere çöker: "kullanıcı bu tercihi hiç değiştirmedi" ile "kullanıcı bilerek
varsayılanla aynı değeri seçti" ayırt edilemez hale gelir. Migration'da da yanıltır: eski sürümün
varsayılanı ile yeni sürümün varsayılanı farklıysa, diskte hiçbir şey değişmeden davranış değişir
ve bunun izi hiçbir yere düşmez.

```kotlin
// Repository — varsayılanın ait olduğu yer
suspend fun theme(): Theme = memorySource.readTheme()?.let(Theme::valueOf) ?: Theme.System
```

---

## `Result<T>` neden desteklenmiyor

Yerleşik dönüş şekilleri yalnızca şunlardır: `@Read` → `Flow<T?>` veya `T?`;
`@Write`/`@Erase`/`@EraseAll` → `Flow<Unit>` veya `Unit`. Başka bir dönüş tipi **derleme hatasıdır**
(`Flow<Int>` dönen bir `@Write` dahil: yazmanın yayacak bir değeri yoktur).

- **`Result<T>` yerleşik değil**, çünkü hata yutmanın varsayılan yolu olurdu. KMemory'nin hata
  sözleşmesi tektir: önce `PreferenceListener.onError` ile raporla, sonra **aynı hatayı yeniden
  fırlat**. `Result` döndürmek çağıranın hatayı görmezden gelmesini sessiz ve kolay kılar; "anahtar
  yok" ile "disk okunamadı" tek bir görünümde birleşirdi.
- **Yazma için `Flow<Unit>` yerleşiktir** (0.1.0'da bu karar değişti). Gerekçe soğukluğun bir
  tuzak olmadığı değil, tuzağın **görünür** olduğudur: tüketici arayüzün tamamını tek şekilde —
  ya hep `suspend` ya hep `Flow` — yazdığında `collect` etmeyi unutmak, tek bir çağrı yerini değil
  bütün bir katmanı etkileyen ve derhal fark edilen bir hatadır. Buna karşılık iki şekli
  karıştırmak, `MemorySource` ile `RemoteSource`'un imzalarını ayırıp her repository'de bir adaptör
  katmanı doğuruyordu. `suspend` şekli kaldırılmadı; ikisi de yerleşiktir.

İleriye dönük kapı açık: `ReturnAdapter` arayüzü ve `kmemory { adapters += … }` alanı **seam
olarak duruyor** — ama 0.1.0'da işleyiciye bağlı değil.

> **0.1.0 sınırı:** `kmemory.adapters` **hiçbir dönüş tipini gevşetmez.** Seçenek okunur, verildiğinde
> "henüz desteklenmiyor" uyarısı verilir, ve yerleşik olmayan her şekil yine derleme hatası olur.
> Bypass bilerek kaldırıldı: doğrulamayı gevşetip üretimi hiç değiştirmiyordu, yani tanıtılan tip
> doğrulamadan geçiyor ama üretilen `override` bildirilen tiple uyuşmadığı için **tüketici modülde**
> patlıyordu. Ucu uca adaptör desteği 0.2.0'a bırakıldı; o gelene kadar derleme güvenliğinden
> hiçbir şey kaybedilmiyor.

---

## `KMemory` örneği ve DI'da bağlama

Kütüphane `Context` görmez; bağımlılık bir `storeFactory` lambda'sıdır.

```kotlin
val memory = kmemory {
    storeFactory = { name ->
        PreferenceDataStoreFactory.createWithPath {
            context.filesDir.resolve(name).absolutePath.toPath()
        }
    }
    json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    listener = crashlyticsPreferenceListener
}

val auth: AuthMemorySource = memory.authMemorySource()   // üretilen uzantı
```

`storeFactory` **zorunludur**: verilmeden `kmemory { }` çağrılırsa kurulum
`IllegalStateException` ile reddedilir (ilk kullanımda değil, kurulum anında).

`KMemory.store(name)` dosya adı başına **tek** `DataStore` örneği tutar. Bu bir konfor değil
zorunluluktur: DataStore aynı dosya için ikinci bir örnek kurulduğunda çalışma anında patlar.
Üretilen sınıf bu yüzden `internal`'dır — dışa açılan tek yüzey `fun KMemory.<arayüzAdı>()`
uzantısıdır ve store tekilliğini `KMemory` üzerinden geçmeye zorlar.

Uzantının görünürlüğü **arayüzün kendi görünürlüğünü izler**: `internal interface
AuthMemorySource` için `internal fun KMemory.authMemorySource()` üretilir. Arayüzü modül içinde
tutmak isteyen tüketici bu yüzden `EXPOSED_FUNCTION_RETURN_TYPE` hatasına çarpmaz.

Koin ile:

```kotlin
@Single
fun preferenceListener(reporter: CrashlyticsReporter): PreferenceListener =
    object : PreferenceListener {
        override fun onError(store: String, key: String?, error: Throwable) {
            reporter.record(error, mapOf("store" to store, "key" to (key ?: "*")))
        }
    }

@Single
fun provideKMemory(context: Context, listener: PreferenceListener): KMemory = kmemory { … }

@Single fun authMemorySource(kmemory: KMemory): AuthMemorySource = kmemory.authMemorySource()
@Single fun userMemorySource(kmemory: KMemory): UserMemorySource = kmemory.userMemorySource()
```

Debug flavor'da aynı bean'i, tercih trafiğini debug drawer'a basan bir implementasyonla değiştirmek
yeterlidir.

### `PreferenceListener`

```kotlin
interface PreferenceListener {
    fun onWrite(store: String, key: String) {}
    fun onErase(store: String, key: String?) {}   // key == null → eraseAll
    fun onError(store: String, key: String?, error: Throwable) {}

    object None : PreferenceListener
}
```

**Dinleyici değerleri asla görmez** — yalnızca store adı, anahtar ve hatanın tipi. Aksi halde bir
hata ayıklama günlükçüsü PIN'i veya oturum token'ını logcat'e düşürürdü.

Hatalar yutulmaz: okuma akışında `.catch { throw report(…) }`, yazma/silmede
`try/catch` → raporla → aynı hatayı fırlat. Üretilen sınıfta parametrenin varsayılanı
`PreferenceListener.None`'dır, yani vermek zorunda değilsin.

#### `PreferenceFailure`

`onError`'a giden `error` **daima** bir `PreferenceFailure`'dır; üretilen kod dinleyiciye hiçbir
dalda yabancı bir `Throwable` geçirmez.

```kotlin
class PreferenceFailure(
    val store: String,         // hangi DataStore dosyası
    val key: String?,          // hangi anahtar (null → store ölçeği)
    val originalType: String,  // orijinal hatanın sınıf adı, örn. "java.io.IOException"
) : RuntimeException("<store>/<key> basarisiz: <originalType>") {
    companion object {
        fun from(store: String, key: String?, error: Throwable): PreferenceFailure
    }
}
```

**Neden bir sarmalayıcı gerekiyor.** Hataya bağlı serbest metin saklanan değeri taşıyabilir.
En bilinen kaynak serileştirmedir:

```text
Unexpected JSON token at offset 41: ... JSON input: {"pin":"1234","token":"ey..."}
java.lang.IllegalArgumentException: geçersiz pin: 1234
```

…ama tek kaynak o değildir: `androidx.datastore.preferences.core.Preferences.toString()` store'un
**tüm anahtar=değer çiftlerini** basar, yani mesajında bir anlık görüntü taşıyan herhangi bir
DataStore istisnası aynı sızıntıyı üretir:

```text
java.lang.IllegalStateException: tercih doğrulaması başarısız: { 3f1c…0003 = SONDA-SIR-1001 }
```

- **Ne taşınır:** `store`, `key`, `originalType` ve orijinalin **kopyalanmış yığın izi**. Bir sınıf
  adı ve bir kare listesi tanım gereği saklanan veriyi taşıyamaz, yani tanı korunur.
- **Ne taşınmaz:** orijinalin **mesajı**, **`cause`**'u ve **`suppressed`** zinciri. Üçü de serbest
  metindir ve `stackTraceToString()` hepsini basar — biri tutulsaydı sızıntı aynı yoldan geri
  gelirdi.
- **Çağırana fırlatılan hata değişmez**: sarmalayıcı yalnızca dinleyiciye gider, `readX()` /
  `writeX()` çağıranı orijinali **tipi ve mesajıyla** almaya devam eder. Çağıran zaten değere
  erişebilen koddur; orada gizlilik kaybı yoktur.
- **Kapı yok, tahmin yok.** "Hangi hata tehlikeli" sorusunu tahmin eden her filtre bir devir sonra
  yanlış çıktı: önce tip kapısı (`error is SerializationException`) — Kotlin'in en yaygın doğrulama
  deyimi olan `init { require(…) }` `IllegalArgumentException` fırlatır ve kotlinx bunu sarmalamaz,
  yani kapıdan geçerdi; sonra konum kapısı (`encode`/`decode` çağrısının etrafı) — DataStore'un
  kendi hatalarını kaçırırdı. Artık tek bir merkezî `report` noktası var ve istisnasız her dal
  oradan geçiyor.
- `PreferenceFailure`, yığın izi kopyalamak için `Throwable.stackTrace`'e ihtiyaç duyduğundan
  `KMemory` ile aynı **JVM + Android** kaynak kümesindedir; `PreferenceListener` `commonMain`'de
  kalır ve bu yüzden `onError`'ın parametre tipi `Throwable` olarak durur.

---

## `commit()` / `apply()` ayrımı yoktur

DataStore'da SharedPreferences'ın `commit()` / `apply()` ikilemi **yoktur**. `dataStore.edit { }`
daima atomiktir, askıya alır ve yazma kalıcı olana kadar dönmez. "apply" karşılığı yoktur — ve bu
bir eksiklik değildir: SharedPreferences'taki sessiz veri kaybı hatalarının çoğunun kaynağı tam
olarak `apply()`'dır. Böyle bir seçenek eklemek o tuzağı geri getirmek olurdu.

Çağıranın yazmayı beklemek istememesi ayrı bir konudur ve **çağıranın kararıdır**
(`scope.launch { … }`), kütüphanenin seçeneği değil.

Gerçek bir strateji kararı varsa o da **bozulma (corruption) politikasıdır**:
`ReplaceFileCorruptionHandler`. Bu, `DataStore`'un kurulduğu yere — yani `storeFactory`'ye, yani DI
modülüne — aittir; anotasyona değil. Kütüphane bu kararı ne verir ne de görür.

---

## Derleme zamanı doğrulamaları

Aşağıdakilerin her biri KSP hatası üretir (`compiler` modülündeki `ValidationTest` hepsini mesaj
metniyle birlikte doğrular):

- her **soyut** fonksiyon `@Read`/`@Write`/`@Erase`/`@EraseAll`'dan birini taşımalı — gövdesi olan
  arayüz fonksiyonu (`suspend fun isUserLoggedIn(): Boolean = …`) kendi uygulamasını zaten taşır,
  anotasyon zorunluluğu taşımaz ve üretilmez
- üretilemeyen soyut üye: supertype'tan miras alınıp arayüzde yeniden bildirilmeyen soyut bir üye
  (fonksiyon ya da özellik) net bir hata verir — hangi üye, hangi supertype'tan geldiği ve ne
  yapılması gerektiği yazar. **Üyesi olmayan marker supertype'lar sorun değildir.**
- arayüzde birden fazla `@EraseAll` bildirilemez
- `@Write` tam 1 parametre almalı; `@Read`/`@Erase`/`@EraseAll` parametre almamalı
- yerleşik olmayan dönüş şekli — `Result<Int>`, `@Write`'ta `Flow<Int>` vb.
- aynı anahtar için farklı tipler bildirilmiş
- yalnızca `@Erase` ile geçen anahtar: tip çıkarılamıyor, o anahtar için bir `@Read` veya `@Write`
  gerekli
- `Flow` dönen fonksiyon suspend olamaz (Flow soğuktur)
- `Flow` dönmeyen fonksiyon suspend olmalı
- `@Read` değeri nullable olmalı

---

## KSP seçenekleri

| Seçenek | Değer | Varsayılan | Anlam |
|---|---|---|---|
| `kmemory.adapters` | virgülle ayrılmış FQN listesi | boş | `ReturnAdapter`'ların karşıladığı dönüş tipleri. **0.1.0'da uygulanmaz**: okunur, uyarı verilir, hiçbir tipi gevşetmez (yukarıdaki sınıra bak). |

```kotlin
ksp {
    arg("kmemory.adapters", "kotlin.Result")
}
```

---

## Modüller ve platformlar

| Modül | Artifact | Platform |
|---|---|---|
| `annotations` | `io.github.sahsenvar:kmemory-annotations` | KMP: android, jvm, iosArm64, iosSimulatorArm64 |
| `compiler` | `io.github.sahsenvar:kmemory-compiler` | JVM (KSP işlemcisi) |
| `sample` | yayınlanmaz | JVM — üretilen kodun davranış testleri |

Anotasyonlar, `PreferenceListener` ve `ReturnAdapter` `commonMain`'dedir, yani iOS dahil tüm
hedeflerde görünür. Çalışma zamanı sınıfı `KMemory` ise JVM + Android kaynak kümesindedir: store
önbelleği paylaşılan değiştirilebilir durumdur ve eşzamanlılık koruması ister; 0.1.0'da
`kotlinx-atomicfu` bağımlılığı eklemek yerine `synchronized` kullanıldı. iOS için çalışma zamanı
gerektiğinde atomicfu kararı orada verilecek.

---

## Bilinen sınırlar (0.1.0)

- **Şifreleme yok.** `@Encrypted` / `PreferenceCipher` 0.1.0 kapsamı dışındadır.
- **Adaptör seam'i uçtan uca değil** (yukarıda).
- **Maven Central'a yayınlanmadı.** 0.1.0 yalnızca `publishToMavenLocal` ile tüketilir.

---

## Yerel yayın

```bash
./gradlew publishToMavenLocal
```

Artifact'lar `~/.m2/repository/io/github/sahsenvar/` altına düşer.

---

## Lisans

Apache License 2.0 — bkz. [LICENSE](LICENSE); upstream atfı için [NOTICE](NOTICE).
