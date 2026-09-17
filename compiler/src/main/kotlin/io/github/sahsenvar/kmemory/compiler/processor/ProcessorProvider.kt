package io.github.sahsenvar.kmemory.compiler.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.sahsenvar.kmemory.compiler.di.KoinInitializer

/**
 * Koin kapsayicisini kurup tam bagli bir [Processor] ureten KSP giris noktasi.
 *
 * `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` bu sinifin
 * tam nitelikli adini tasir; paket degisirse o dosya da degismek zorundadir.
 */
internal class ProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val koin = KoinInitializer.initialize(
            environmentLogger = environment.logger,
            codeGenerator = environment.codeGenerator,
            options = environment.options,
        )

        return koin.get<SymbolProcessor>()
    }
}
