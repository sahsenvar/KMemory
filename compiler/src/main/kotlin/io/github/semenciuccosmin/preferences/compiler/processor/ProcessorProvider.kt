package io.github.semenciuccosmin.preferences.compiler.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.semenciuccosmin.preferences.compiler.di.KoinInitializer
import org.koin.mp.KoinPlatform.getKoin

/**
 * KSP [SymbolProcessorProvider] that bootstraps the Koin DI container and creates a
 * fully-wired [Processor] instance.
 */
internal class ProcessorProvider : SymbolProcessorProvider {

    /**
     * Initialises Koin, resolves the required use-cases from the DI graph, and returns a
     * configured [Processor].
     *
     * @param environment The KSP environment providing the logger and code generator.
     * @return A [Processor] ready to process [@Preferences]
     * [io.github.semenciuccosmin.preferences.annotations.Preferences]-annotated interfaces.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        KoinInitializer.initialize(
            environmentLogger = environment.logger,
            codeGenerator = environment.codeGenerator,
            options = environment.options,
        )

        return getKoin().get<SymbolProcessor>()
    }
}
