package io.github.sahsenvar.kmemory.compiler.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.github.sahsenvar.kmemory.annotation.Preferences
import io.github.sahsenvar.kmemory.compiler.usecase.CollectFunctionsUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GenerateImplementationUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GroupByKeyUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.ValidateInterfaceUseCase

/**
 * `@Preferences` arayuzlerini isleyen KSP [SymbolProcessor].
 *
 * Akis: topla → anahtara gore grupla → dogrula → (gecerliyse) uret.
 *
 * Dogrulama BASARISIZ olan arayuz atlanir ama isleme durmaz: bir arayuzun hatasi
 * digerlerinin uretimini engellemesin, ve tek derlemede tum hatalar raporlansin.
 * Uretim yapilmadiginda `logger.error` zaten derlemeyi dusurur.
 */
internal class Processor(
    private val collectFunctionsUseCase: CollectFunctionsUseCase,
    private val groupByKeyUseCase: GroupByKeyUseCase,
    private val validateInterfaceUseCase: ValidateInterfaceUseCase,
    private val generateImplementationUseCase: GenerateImplementationUseCase,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotationName = Preferences::class.qualifiedName ?: return emptyList()

        resolver.getSymbolsWithAnnotation(annotationName)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }
            .forEach(::processInterface)

        return emptyList()
    }

    /**
     * Anotasyon zorunlulugu ve uretim yalnizca SOYUT fonksiyonlara aittir.
     *
     * Govdesi olan bir arayuz fonksiyonu (`suspend fun isUserLoggedIn(): Boolean = ...`) kendi
     * uygulamasini zaten tasir: uretilmesine gerek yoktur ve hicbir accessor anotasyonuna
     * uymaz. Filtre TEK yerdedir ki dogrulamanin saydigi liste ile toplamanin gezdigi liste
     * ayni olsun; ayrisirlarsa sayi karsilastirmasi yanlis arayuzleri reddeder.
     */
    private fun processInterface(declaration: KSClassDeclaration) {
        val declaredFunctions = declaration.getDeclaredFunctions().filter { it.isAbstract }.toList()
        val functions = collectFunctionsUseCase(declaredFunctions)
        val models = groupByKeyUseCase(functions)

        val valid = validateInterfaceUseCase(
            declaration = declaration,
            declaredFunctions = declaredFunctions,
            functions = functions,
            models = models,
        )

        if (valid) {
            generateImplementationUseCase(
                declaration = declaration,
                functions = functions,
                models = models,
            )
        }
    }
}
