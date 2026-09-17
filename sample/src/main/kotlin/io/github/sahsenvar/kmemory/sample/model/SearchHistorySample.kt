package io.github.sahsenvar.kmemory.sample.model

import kotlinx.serialization.Serializable

/**
 * BASKA BIR PAKETTE duran serilestirilebilir tip.
 *
 * Zad'i kiran somut sekli temsil eder: `List<SearchHistory>`. Uretilen dosya hem
 * `List` tip argumanini hem de bu tipin import'unu dogru tasimazsa derlenmez.
 */
@Serializable
data class SearchHistorySample(val query: String)
