# Changelog

Bu dosya projedeki kayda değer tüm değişiklikleri içerir.

Biçim [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) temellidir ve proje
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) kullanır.

---

## [0.1.0]

KspPreferences 2.0.0'dan fork. Yeni koordinatlar (`io.github.sahsenvar:kmemory-annotations`,
`io.github.sahsenvar:kmemory-compiler`) altında sürüm sayacı 0.1.0'dan başlar; aşağıdaki 2.0.0 ve
öncesi upstream'in (`io.github.semenciuccosmin:preferences-*`) geçmişidir.

**Breaking: tüm anotasyon yüzeyi değişti.**

- Tek katmanlı anotasyon ailesi: `@Read` / `@Write` / `@Erase` / `@EraseAll`
- `@Read` iki şekli destekler: `Flow<T?>` ve `suspend fun (): T?`
- Tip imzadan çıkarılır; `@StringPreference` ailesi kaldırıldı
- `defaultValue` kaldırıldı; okumalar daima nullable
- Bildirilen tip üretilen imzaya birebir taşınır: tip argümanları, nullability ve import'lar
  dahil (`Flow<List<SearchHistory>?>`, `value: String?`)
- `List` / `Set` / `Map` / `Collection` / `Array` / `Pair` / `Triple` kapsayıcıları desteklenir;
  JSON metnine düşerler
- Nullable `@Write` parametresine `null` yazmak anahtarı siler
- Üretilen anahtar sabitlerinin adları çarpışmaz; uzun ortak önekli iki anahtar (örn.
  `notification_settings_enabled` / `notification_settings_muted`) artık aynı sabite inmiyor
- `Result<T>` ve `Flow<Unit>` sarmalamaları desteklenmiyor
- Üretilen sınıf `Context` yerine `DataStore<Preferences>` alır
- `PreferencesFactory` / `@ConstructedBy` / reflection kaldırıldı
- `PreferenceListener`: yazma/silme/hata kancaları; hatalar raporlanır ve yeniden fırlatılır
- Şifreleme (`@Encrypted`) 0.2.0'a ertelendi

Ek notlar:

- `suspend` kuralı accessor'a değil dönüş şekline bağlıdır: `Flow` dönen suspend olamaz, dönmeyen
  suspend olmalı
- Dışa açılan tek yüzey üretilen `fun KMemory.<arayüzAdı>()` uzantısıdır; `*Impl` sınıfı `internal`
- `ReturnAdapter` + `kmemory.adapters` KSP seçeneği yalnızca **ileriye dönük seam** olarak duruyor;
  0.1.0'da işleyiciye bağlı değil: seçenek okunur ve uyarı verir, hiçbir dönüş tipini gevşetmez
- Gövdesi olan (soyut olmayan) arayüz fonksiyonları anotasyon zorunluluğundan ve üretimden muaf
- Üretilemeyen soyut üye (miras dahil, fonksiyon ya da özellik) artık sessizce bozuk kod üretmek
  yerine net bir derleme hatası verir; üyesi olmayan marker supertype'lar etkilenmez
- `annotations` KMP (android, jvm, iosArm64, iosSimulatorArm64); `KMemory` çalışma zamanı sınıfı
  JVM + Android kaynak kümesinde
- Sürüm tabanı Zad ile hizalandı: Kotlin 2.3.21, KSP 2.3.9, datastore 1.2.0,
  kotlinx-serialization 1.9.0
- Doğrulama hataları `compiler` modülündeki `ValidationTest` ile, üretilen kodun davranışı `sample`
  modülündeki `SamplePreferencesTest` ile kapsanır
- Upstream artıkları kaldırıldı: `composeApp`, `iosApp`, `sampleAndroid`, `PreferencesFactory`,
  `PreferencesConstructor`, `DataStoreProvider`
- Apache-2.0 korunur; upstream atfı `NOTICE` dosyasındadır

---

## Upstream geçmişi (KspPreferences)

## [2.0.0] — 2026-05-06

### Added
- **Kotlin Multiplatform (KMP) support** — the library now targets Android, iOS (arm64 + simulatorArm64), and JVM Desktop.
- `@ConstructedBy(constructor)` — annotation that links a `@Preferences` interface to a `PreferencesConstructor` object, enabling reflection-free instantiation on all platforms (modelled after Room 3's `@ConstructedBy`).
- `PreferencesConstructor<T>` — interface implemented by a KSP-generated `actual object` that provides a type-safe `initialize(context)` factory method.
- `PreferencesFactory.create(constructor, context)` — constructor-based factory for KMP projects; works on Android, iOS, and Desktop without reflection.
- `PreferencesFactory.create<T>(context)` — reflection-based factory for non-KMP Android/JVM projects; resolves the generated `*Impl` class via `Class.forName`.
- `GenerateConstructorObjectUseCase` — new KSP processor use-case that generates the `actual object` (or plain `object` for non-KMP) implementing `PreferencesConstructor`.
- `sampleAndroid` module — a traditional single-platform Android sample app demonstrating the reflection-based API without KMP.

### Changed
- `PreferencesFactory` is now an empty `object`; both `create` overloads are top-level extension functions for correct overload resolution between the constructor-based and reflection-based variants.
- `@ConstructedBy` is optional — KMP projects use it for type-safe, reflection-free instantiation; non-KMP projects can omit it and use `PreferencesFactory.create<T>(context)` instead.
- The KSP processor detects `expect` vs plain declarations via `Modifier.EXPECT` and conditionally generates `actual object` (KMP) or `object` (non-KMP).
- JVM Desktop DataStore path changed from a relative `datastore/` directory to an absolute path under `~/.local/share/datastore/` for stability across different working directories.

### Removed
- The previous platform-specific `PreferencesFactory` implementations using `expect object PreferencesFactory` with `actual` members have been replaced by the new extension-function-based approach.

---

## [1.1.0]

### Added
- `@ObjectPreference(key, clazz)` — serializable object preference support via `kotlinx.serialization`; returns a nullable type (`T?`).
- `PreferencesFactory.create<T>(context)` — reflection-based factory helper for instantiating generated `*Impl` classes without a DI framework.

---

## [1.0.0] — 2026-04-03

### Added

#### Annotations (`preferences-annotations`)
- `@Preferences(name)` — marks an interface as a KSP-managed DataStore container.
- `@Get` — generates a suspending one-shot read function.
- `@GetFlow` — generates a `Flow`-returning reactive read function.
- `@Set` — generates a suspending write function.
- `@Clear` — generates a suspending function that removes all DataStore entries.
- `@BooleanPreference(key, defaultValue)` — Boolean preference support.
- `@DoublePreference(key, defaultValue)` — Double preference support.
- `@FloatPreference(key, defaultValue)` — Float preference support.
- `@IntPreference(key, defaultValue)` — Int preference support.
- `@LongPreference(key, defaultValue)` — Long preference support.
- `@StringPreference(key, defaultValue)` — String preference support.

#### Compiler (`preferences-compiler`)
- KSP symbol processor that validates annotated interfaces at compile time and generates a fully type-safe `*Impl` DataStore implementation.
- Validation covers annotation composition rules, return types, parameter counts, and `suspend` modifier requirements; all errors are reported in a single build pass without short-circuiting.
- Generated implementation uses a file-scoped `preferencesDataStore` delegate to ensure a single DataStore instance per file, preventing the _"multiple DataStores active for the same file"_ runtime error.
- Koin-based internal DI wiring for all processor use-cases and code generators.
- Detekt static analysis integration with auto-correct and a custom rule configuration.

#### Sample
- Sample application demonstrating all supported preference types with `@Get`, `@GetFlow`, `@Set`, and `@Clear` operations.
- Instrumented test suite (`SamplePreferencesTest`) covering every value type and operation, using Turbine for Flow assertions.
- Koin singleton binding for `SamplePreferences` in tests to avoid multiple DataStore instances on the same file.
