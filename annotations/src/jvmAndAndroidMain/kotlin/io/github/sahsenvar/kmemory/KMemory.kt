package io.github.sahsenvar.kmemory

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.sahsenvar.kmemory.adapter.ReturnAdapter
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import kotlinx.serialization.json.Json

/**
 * Uretilen kaynaklari besleyen merkezi ornek — Ktorfit'teki `ktorfit { }` nesnesinin karsiligi.
 *
 * Kutuphane `Context` gormez: bagimlilik bir [storeFactory] lambda'sidir. Android'de
 * `filesDir`'e, JVM testinde gecici bir dizine baglanir.
 *
 * ## Neden JVM/Android?
 * Bu tip `commonMain`'de DEGIL, JVM ve Android hedeflerinin paylastigi kaynak kumesindedir.
 * [stores] onbellegi paylasilan degistirilebilir durumdur ve es zamanlilik korumasi ister;
 * 0.1.0'da `kotlinx-atomicfu` bagimliligi eklemek yerine calisma-zamani yuzeyi JVM/Android'e
 * sabitlendi ve `synchronized` kullanildi. Anotasyonlar, [PreferenceListener] ve
 * [ReturnAdapter] `commonMain`'de kalir, yani iOS dahil tum hedeflerde gorulur.
 * iOS icin calisma-zamani gerektiginde atomicfu karari orada verilecek.
 *
 * @property storeFactory dosya adindan bir [DataStore] ureten fabrika
 * @property json `@Serializable` tiplerin kodlanmasinda kullanilan yapilandirma
 * @property listener tum kaynaklarin paylastigi yazma/silme/hata dinleyicisi
 * @property adapters tuketicinin kaydettigi donus adaptorleri (tasarim §4.1)
 */
public class KMemory internal constructor(
    public val storeFactory: (name: String) -> DataStore<Preferences>,
    public val json: Json,
    public val listener: PreferenceListener,
    public val adapters: List<ReturnAdapter> = emptyList(),
) {

    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    private val lock = Any()

    /**
     * Dosya adi basina TEK store.
     *
     * DataStore ayni dosya icin ikinci bir ornek kuruldugunda calisma aninda patlar;
     * bu onbellek konfor degil zorunluluktur.
     *
     * Uretilen uzanti fonksiyonlari bu uyeyi cagirir, bu yuzden `public` olmak zorundadir
     * (`internal` modul sinirini gecemez). Elle cagirmak icin degildir.
     */
    public fun store(name: String): DataStore<Preferences> = synchronized(lock) {
        stores.getOrPut(name) { storeFactory(name) }
    }
}

/** [KMemory] kurucusu. */
public class KMemoryBuilder {

    /**
     * ZORUNLU. Dosya adindan bir [DataStore] uretir.
     *
     * Verilmezse [build] kurulumu reddeder; bkz. [kmemory].
     */
    public lateinit var storeFactory: (name: String) -> DataStore<Preferences>

    public var json: Json = Json

    public var listener: PreferenceListener = PreferenceListener.None

    public val adapters: MutableList<ReturnAdapter> = mutableListOf()

    /**
     * Eksik [storeFactory] KURULUM aninda yakalanir.
     *
     * Kontrol olmadan da hata cikardi — `lateinit` erisimi patlardi — ama tipi
     * `UninitializedPropertyAccessException`, mesaji ise "lateinit property storeFactory has
     * not been initialized" olurdu: tuketiciye kutuphanenin ic detayini gosteren, nasil
     * duzeltilecegini soylemeyen bir metin. [check] ayni ani yakalar, cozumu de yazar.
     */
    internal fun build(): KMemory {
        check(this::storeFactory.isInitialized) {
            "KMemory kurulamadi: storeFactory zorunludur. " +
                "kmemory { storeFactory = { name -> ... } } seklinde verin."
        }

        return KMemory(
            storeFactory = storeFactory,
            json = json,
            listener = listener,
            adapters = adapters.toList(),
        )
    }
}

/**
 * ```
 * val memory = kmemory {
 *     storeFactory = { name -> PreferenceDataStoreFactory.createWithPath { basePath.resolve(name) } }
 *     listener = crashlyticsPreferenceListener
 * }
 * ```
 */
public fun kmemory(block: KMemoryBuilder.() -> Unit): KMemory = KMemoryBuilder().apply(block).build()
