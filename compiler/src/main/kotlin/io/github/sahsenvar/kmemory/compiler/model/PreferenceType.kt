package io.github.sahsenvar.kmemory.compiler.model

/**
 * Bir tercihin diskteki temsili.
 *
 * [keyFactory] androidx.datastore'un anahtar fabrikasinin adi, [simpleName] imzada gorunen
 * Kotlin tipinin kisa adi, [qualifiedName] ise ayni tipin tam nitelikli adidir.
 *
 * [OBJECT] `@Serializable` isaretli tipleri ve serilestirilebilir koleksiyonlari temsil eder.
 * Bu tipler diskte JSON metni olarak durdugu icin onlar da `stringPreferencesKey` kullanir;
 * fark yalnizca uretilen govdede `json.encodeToString` / `json.decodeFromString` cagrisinin
 * bulunmasidir.
 */
internal enum class PreferenceType(
    val keyFactory: String,
    val simpleName: String,
    val qualifiedName: String,
) {
    STRING("stringPreferencesKey", "String", "kotlin.String"),
    INT("intPreferencesKey", "Int", "kotlin.Int"),
    LONG("longPreferencesKey", "Long", "kotlin.Long"),
    FLOAT("floatPreferencesKey", "Float", "kotlin.Float"),
    DOUBLE("doublePreferencesKey", "Double", "kotlin.Double"),
    BOOLEAN("booleanPreferencesKey", "Boolean", "kotlin.Boolean"),
    OBJECT("stringPreferencesKey", "String", "kotlin.String");

    companion object {

        /**
         * Tam nitelikli tip adindan yerlesik ilkel tipi bulur; yerlesik degilse `null` doner.
         *
         * Ad karsilastirmasi KISA ad uzerinden DEGIL, tam nitelikli ad uzerinden yapilir:
         * baska bir pakette tanimli bir `String`/`Int` ilkel sanilmamali.
         *
         * [OBJECT] bilerek elenir: onun [qualifiedName] degeri de `"kotlin.String"` oldugu icin
         * ada gore eslesmesine izin verilseydi her `String` tercihi nesne sanilirdi. [OBJECT]
         * yalnizca "ilkel degil" durumunun karsiligidir ve cagiran tarafta fallback olarak secilir.
         */
        fun fromQualifiedName(name: String): PreferenceType? =
            entries.firstOrNull { it != OBJECT && it.qualifiedName == name }
    }
}
