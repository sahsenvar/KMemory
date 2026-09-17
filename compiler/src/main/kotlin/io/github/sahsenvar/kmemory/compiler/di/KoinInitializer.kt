package io.github.sahsenvar.kmemory.compiler.di

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.sahsenvar.kmemory.compiler.logger.Logger
import io.github.sahsenvar.kmemory.compiler.processor.Processor
import io.github.sahsenvar.kmemory.compiler.usecase.CollectFunctionsUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateEraseFunctionUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateImplementationUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateImportsUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateReadFunctionUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateWriteFunctionUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GroupByKeyUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.ValidateInterfaceUseCase
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Islemcinin Koin kapsayicisini kurar.
 *
 * KSP islemcisi Kotlin derleyici daemon'unda calisir; cerceve yonetimli bir DI yasam dongusu
 * yoktur, bu yuzden kapsayici elle kurulur. [KSPLogger] ve [CodeGenerator] Koin tarafindan
 * uretilemedigi icin cagri yerinden yakalanip closure ile verilir.
 *
 * Her bagimlilik **factory**'dir: her cozumde yeni ornek doner, boylece artimli KSP turlari
 * arasinda bayat durum tasinmaz.
 *
 * YENI EKLENEN HER use-case buraya kaydedilmelidir; eksik kayit derlemede degil, islemci
 * calisirken `NoDefinitionFoundException` ile patlar.
 */
object KoinInitializer {

    /** spec §9.1 — virgulle ayrilmis `ReturnAdapter` FQN listesi. */
    private const val ADAPTERS_OPTION = "kmemory.adapters"

    /**
     * @param environmentLogger KSP'nin sagladigi gunlukcu; dogrulama hatalari buraya gider.
     * @param codeGenerator KSP'nin sagladigi uretec; `*Impl` dosyalari bununla yazilir.
     * @param options KSP secenekleri; yalnizca [ADAPTERS_OPTION] okunur (spec §9.1).
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
                            collectFunctionsUseCase = get(),
                            groupByKeyUseCase = get(),
                            validateInterfaceUseCase = get(),
                            generateImplementationUseCase = get(),
                        )
                    }

                    factoryOf(::CollectFunctionsUseCase)
                    factoryOf(::GroupByKeyUseCase)
                    factory {
                        ValidateInterfaceUseCase(
                            logger = get(),
                            registeredAdapterTypes = registeredAdapterTypes,
                        )
                    }

                    factoryOf(::GenerateReadFunctionUseCase)
                    factoryOf(::GenerateWriteFunctionUseCase)
                    factoryOf(::GenerateEraseFunctionUseCase)
                    factoryOf(::GenerateImportsUseCase)
                    factory {
                        GenerateImplementationUseCase(
                            logger = get(),
                            codeGenerator = codeGenerator,
                            generateImportsUseCase = get(),
                            generateReadFunctionUseCase = get(),
                            generateWriteFunctionUseCase = get(),
                            generateEraseFunctionUseCase = get(),
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
