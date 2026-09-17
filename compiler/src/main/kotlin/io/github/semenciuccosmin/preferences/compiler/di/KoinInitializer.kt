package io.github.semenciuccosmin.preferences.compiler.di

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.sahsenvar.kmemory.compiler.logger.Logger
import io.github.sahsenvar.kmemory.compiler.usecase.CollectFunctionsUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateReadFunctionUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GroupByKeyUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.ValidateInterfaceUseCase
import io.github.semenciuccosmin.preferences.compiler.processor.Processor
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateClearFunctionUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateCompanionObjectUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateConstructorObjectUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateFunctionUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateImplementationUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateImportsUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GenerateSetFunctionUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GetPreferencesNameUseCase
import io.github.semenciuccosmin.preferences.compiler.usecase.GetValueTypeAnnotationData
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Bootstraps the Koin dependency-injection container for the KSP processor.
 *
 * Because the KSP processor runs inside the Kotlin compiler daemon (not inside an Android
 * process), there is no framework-managed DI lifecycle. [KoinInitializer] creates and
 * configures the Koin container manually at the start of each processing round, wiring
 * together all use-cases, validators, and code-generators that the processor depends on.
 *
 * All dependencies are registered as **factories** so that a fresh instance is resolved
 * every time one is requested. This keeps each processing invocation stateless and avoids
 * stale state between incremental KSP rounds.
 */
object KoinInitializer {

    /** spec §9.1 — virgulle ayrilmis `ReturnAdapter` FQN listesi. */
    private const val ADAPTERS_OPTION = "kmemory.adapters"

    /**
     * Starts a Koin container and registers all processor dependencies.
     *
     * Should be called once at the beginning of a KSP processing round, typically from
     * `SymbolProcessor.process()`. The [environmentLogger] and [codeGenerator] are
     * KSP-provided objects that cannot be constructed by Koin itself, so they are captured
     * from the call site and supplied to the relevant factories via closures.
     *
     * @param environmentLogger The [KSPLogger] provided by the KSP runtime, used to report
     *                          validation errors and warnings back to the build system.
     * @param codeGenerator     The [CodeGenerator] provided by the KSP runtime, used to
     *                          create and write the generated `*Impl` source files.
     * @param options           KSP secenekleri; yalnizca [ADAPTERS_OPTION] okunur (spec §9.1).
     */
    fun initialize(
        environmentLogger: KSPLogger,
        codeGenerator: CodeGenerator,
        options: Map<String, String> = emptyMap(),
    ) {
        val registeredAdapterTypes = parseAdapterTypes(options)

        startKoin {
            modules(
                module {
                    factory<Logger> { Logger(environmentLogger) }
                    factory<SymbolProcessor> {
                        Processor(
                            validateInterfaceUseCase = get(),
                            generateImplementationUseCase = get(),
                        )
                    }

                    factoryOf(::GetValueTypeAnnotationData)

                    factoryOf(::CollectFunctionsUseCase)
                    factoryOf(::GroupByKeyUseCase)
                    factory {
                        ValidateInterfaceUseCase(
                            logger = get(),
                            registeredAdapterTypes = registeredAdapterTypes,
                        )
                    }

                    factoryOf(::GenerateReadFunctionUseCase)
                    factoryOf(::GenerateSetFunctionUseCase)
                    factoryOf(::GenerateClearFunctionUseCase)
                    factoryOf(::GenerateFunctionUseCase)
                    factoryOf(::GetPreferencesNameUseCase)
                    factoryOf(::GenerateCompanionObjectUseCase)
                    factoryOf(::GenerateImportsUseCase)
                    factory {
                        GenerateConstructorObjectUseCase(
                            codeGenerator = codeGenerator
                        )
                    }

                    factory {
                        GenerateImplementationUseCase(
                            logger = get(),
                            codeGenerator = codeGenerator,
                            generateImportsUseCase = get(),
                            generateFunctionUseCase = get(),
                            generateCompanionObjectUseCase = get(),
                            generateConstructorObjectUseCase = get(),
                        )
                    }
                }
            )
        }
    }

    private fun parseAdapterTypes(options: Map<String, String>): Set<String> =
        options[ADAPTERS_OPTION]
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
