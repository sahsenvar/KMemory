package io.github.sahsenvar.kmemory.compiler.model

/**
 * Bir tercihin diskteki temsili.
 *
 * [keyFactory] androidx.datastore'un anahtar fabrikasinin adi, [simpleName] ise imzada
 * gorunen Kotlin tipinin kisa adidir.
 *
 * [OBJECT] `@Serializable` isaretli tipleri temsil eder. Bu tipler diskte JSON metni olarak
 * durdugu icin onlar da `stringPreferencesKey` kullanir; fark yalnizca uretilen govdede
 * `json.encodeToString` / `json.decodeFromString` cagrisinin bulunmasidir.
 */
internal enum class PreferenceType(val keyFactory: String, val simpleName: String) {
    STRING("stringPreferencesKey", "String"),
    INT("intPreferencesKey", "Int"),
    LONG("longPreferencesKey", "Long"),
    FLOAT("floatPreferencesKey", "Float"),
    DOUBLE("doublePreferencesKey", "Double"),
    BOOLEAN("booleanPreferencesKey", "Boolean"),
    OBJECT("stringPreferencesKey", "String");

    companion object {

        /**
         * Kisa tip adindan yerlesik ilkel tipi bulur; yerlesik degilse `null` doner.
         *
         * [OBJECT] bilerek elenir: onun [simpleName] degeri de `"String"` oldugu icin ada gore
         * eslesmesine izin verilseydi her `String` tercihi nesne sanilirdi. [OBJECT] yalnizca
         * "ilkel degil" durumunun karsiligidir ve cagiran tarafta fallback olarak secilir.
         */
        fun fromSimpleName(name: String): PreferenceType? =
            entries.firstOrNull { it != OBJECT && it.simpleName == name }
    }
}
