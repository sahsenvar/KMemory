package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType
import io.github.sahsenvar.kmemory.compiler.model.ReturnShape

/**
 * Uretilen dosyanin paket bildirimi ve import blogunu uretir.
 *
 * Import kumesi anotasyonlardan DEGIL, uretilecek govdelerden turetilir: hangi erisim
 * varsa yalnizca onun ihtiyaci eklenir. Upstream'in `Context`, `preferencesDataStore` ve
 * `PreferencesFactory` importlari tamamen kalkti — uretilen sinif artik platform gormez
 * (spec §6).
 *
 * Anahtar fabrikalari da ayni sekilde kullanilana gore secilir; `stringPreferencesKey`
 * [PreferenceType.OBJECT] icin de ayni fabrika oldugundan `distinct()` tekrar uretmez.
 *
 * Akis importlari ERISIME degil DONUS SEKLINE bakar (spec §4.0): `Flow<Unit>` donen bir
 * `@Write` de `Flow`, `flow` ve `catch` ister. Secim `reads`'e bagli kalsaydi Flow donen
 * yazma "unresolved reference: flow" ile patlardi.
 */
internal class GenerateImportsUseCase {

    /**
     * @param packageName Uretilen dosyanin paketi; arayuzun paketiyle aynidir.
     * @param functions Arayuzun tum fonksiyonlari; hangi akis/duzenleme importlarinin
     *   gerektigi buradan okunur.
     * @param models Anahtar gruplari; anahtar fabrikalari ve nesne tipi importlari buradan gelir.
     * @return `package` satiriyla baslayip satir sonuyla biten Kotlin kaynagi.
     */
    operator fun invoke(
        packageName: String,
        functions: List<FunctionModel>,
        models: List<PreferenceModel>,
    ): String {
        val reads = functions.filter { it.accessor == Accessor.READ }
        val mutations = functions.filter { it.accessor != Accessor.READ }
        // WRITE/ERASE/ERASE_ALL govdelerinin tamami `dataStore.edit` cagirir; salt-okunur bir
        // arayuzde bu import hic uretilmez.
        val mutates = mutations.isNotEmpty()
        val needsJson = models.any { it.type == PreferenceType.OBJECT }

        // `Flow` ve `catch` iki tarafta da kullanilir; `map`/`first` yalnizca okumada,
        // `flow` builder'i yalnizca Flow donen yazma/silmede.
        val anyFlowReturn = functions.any { it.returnShape == ReturnShape.FLOW }
        val needsFlowBuilder = mutations.any { it.returnShape == ReturnShape.FLOW }

        val imports = buildList {
            add(DATA_STORE)
            add(PREFERENCES)
            if (mutates) add(EDIT)
            models.map { it.type.keyFactory }.distinct().sorted().forEach { add("$PREFERENCES_CORE.$it") }
            add(KMEMORY)
            // Kosulsuz: uretilen `report()` hangi erisim olursa olsun dinleyiciye daima bir
            // PreferenceFailure verir, yani salt-ilkel bir arayuzde de kullanilir.
            add(PREFERENCE_FAILURE)
            // `from` bir COMPANION UZANTISIDIR: tip commonMain'de, kurucusu yalnizca JVM'in
            // tasiyabilecegi iki satir oldugu icin fabrika jvmAndAndroidMain'de durur. Cagri
            // sekli `PreferenceFailure.from(...)` olarak kalir ama uzanti import EDILMEDEN
            // cozulmez; bu satir dusunce uretilen kod "unresolved reference: from" ile patlar.
            add(PREFERENCE_FAILURE_FROM)
            add(PREFERENCE_LISTENER)
            // KOSULSUZ: uretilen report() her erisim sekli icin ayni govdeyi tasir ve ilk
            // satiri iptali eliyor. (errorMapper duz bir fonksiyon tipi oldugu icin import
            // istemiyor.)
            add(CANCELLATION)
            if (anyFlowReturn) {
                add(FLOW)
                add(FLOW_CATCH)
            }
            // Sira alfabetiktir (catch < first < flow < map); uretilen dosya el yazimi gibi
            // okunsun diye.
            if (reads.any { it.returnShape == ReturnShape.SUSPEND }) add(FLOW_FIRST)
            if (needsFlowBuilder) add(FLOW_BUILDER)
            if (reads.any { it.returnShape == ReturnShape.FLOW }) add(FLOW_MAP)
            if (needsJson) add(JSON)
            // Imzalarda gorunen tiplerin import'lari; tip argumanlari dahil her seviye
            // buradan gelir, cunku `Flow<Map<String, Profile>?>` imzasi `Profile`'i de
            // gorunur kilmak zorunda. Ayni pakette duran tip import EDILMEZ: Kotlin bunu
            // hata saymaz ama uretilen dosyayi kendi paketine import eden bir satir
            // okunabilirligi bozar.
            functions.mapNotNull { it.declaredType }
                .flatMap { it.imports }
                .filter { it.substringBeforeLast('.', "") != packageName }
                .distinct()
                .sorted()
                .forEach { add(it) }
        }.distinct()

        return buildString {
            appendLine("package $packageName")
            appendLine()
            imports.forEach { appendLine("import $it") }
        }
    }

    private companion object {
        const val PREFERENCES_CORE = "androidx.datastore.preferences.core"
        const val DATA_STORE = "androidx.datastore.core.DataStore"
        const val PREFERENCES = "$PREFERENCES_CORE.Preferences"
        const val EDIT = "$PREFERENCES_CORE.edit"
        const val KMEMORY = "io.github.sahsenvar.kmemory.KMemory"
        const val PREFERENCE_LISTENER = "io.github.sahsenvar.kmemory.listener.PreferenceListener"
        const val PREFERENCE_FAILURE = "io.github.sahsenvar.kmemory.listener.PreferenceFailure"
        const val PREFERENCE_FAILURE_FROM = "io.github.sahsenvar.kmemory.listener.from"
        const val CANCELLATION = "kotlin.coroutines.cancellation.CancellationException"
        const val FLOW = "kotlinx.coroutines.flow.Flow"
        const val FLOW_BUILDER = "kotlinx.coroutines.flow.flow"
        const val FLOW_CATCH = "kotlinx.coroutines.flow.catch"
        const val FLOW_FIRST = "kotlinx.coroutines.flow.first"
        const val FLOW_MAP = "kotlinx.coroutines.flow.map"
        const val JSON = "kotlinx.serialization.json.Json"
    }
}
