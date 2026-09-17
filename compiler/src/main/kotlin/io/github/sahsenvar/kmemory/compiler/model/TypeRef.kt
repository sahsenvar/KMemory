package io.github.sahsenvar.kmemory.compiler.model

/**
 * Arayuzde BILDIRILEN bir tipin, uretilen koda oldugu gibi tasinabilen hali.
 *
 * Neden var: uretilen `override` imzasinin arayuzdekiyle birebir ayni olmasi zorunludur.
 * Tipi `declaration.simpleName` ile almak tip argumanlarini ve nullability'yi dusuruyordu —
 * `List<SearchHistory>` `List`'e, `String?` `String`'e iniyor ve uretilen sinif
 * "overrides nothing" ile derlenmiyordu. Bu model, tipin imzada gorunen metnini,
 * karsilastirilabilir nitelenmis halini ve gereken import'lari BIR ARADA tasir ki
 * cagiran taraflar ayni tipi iki farkli sekilde yeniden turetmesin.
 *
 * @property source Imzaya yazilacak metin; tip argumanlari VE nullability dahil
 *   (`List<String>?`, `String?`, `Map<String, List<Int>>`).
 * @property nonNull [source]'un nullability'siz hali; `json.decodeFromString<...>` gibi
 *   null kabul etmeyen konumlarda kullanilir.
 * @property qualified Tum adlarin tam nitelikli, nullability'nin atilmis oldugu hali.
 *   Ayni anahtara bagli iki fonksiyonun tip catismasi bununla olculur: `String?` yazan bir
 *   `@Write` ile `String?` okuyan bir `@Read` catismaz, cunku saklanan tip aynidir.
 * @property rootFqName Tip argumanlari atilmis kok tipin tam nitelikli adi
 *   (`kotlin.collections.List`); ilkel tip tespiti bunun uzerinden yapilir.
 * @property isNullable [source] null kabul ediyor mu; `@Write` govdesinin "null gelirse
 *   anahtari sil" dalini ureten karar budur.
 * @property hasArguments Tip argumani tasiyor mu; tasiyan hicbir tip DataStore ilkeli olamaz.
 * @property imports [source]'un derlenebilmesi icin gereken FQN'ler. Varsayilan olarak
 *   import edilen paketler (`kotlin`, `kotlin.collections`, …) disaridadir; onlari yazmak
 *   uretilen dosyaya gereksiz satir ekler.
 */
internal data class TypeRef(
    val source: String,
    val nonNull: String,
    val qualified: String,
    val rootFqName: String,
    val isNullable: Boolean,
    val hasArguments: Boolean,
    val imports: Set<String>,
)
