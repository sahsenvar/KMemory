package io.github.sahsenvar.kmemory.compiler.logger

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

/**
 * [KSPLogger] uzerine ince bir sarmalayici.
 *
 * Upstream'deki 12 sabit metinli `logXxxError` fonksiyonunun yerini alir: mesaji cagiran
 * uretir, boylece her dogrulama kurali kendi baglamini (arayuz adi, fonksiyon adi, bulunan
 * tip) mesaja koyabilir. [node] verildiginde IDE ve derleme ciktisi hatayi dogru kaynak
 * konumuna baglar.
 */
internal class Logger(private val logger: KSPLogger) {

    /** KSP derlemesini dusurur ve [message]'i [node]'un kaynak konumuyla raporlar. */
    fun error(message: String, node: KSNode? = null) {
        logger.error(message, node)
    }

    /**
     * Derlemeyi DUSURMEZ; yalnizca raporlar.
     *
     * Desteklenmeyen bir KSP secenegi icin kullanilir: secenegi sessizce yok saymak, kullanicinin
     * "tanittim, artik calisiyor" sanmasina yol acar.
     */
    fun warn(message: String, node: KSNode? = null) {
        logger.warn(message, node)
    }
}
