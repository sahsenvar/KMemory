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

    private fun processInterface(declaration: KSClassDeclaration) {
        val declaredFunctions = declaration.getDeclaredFunctions().toList()
        val functions = collectFunctionsUseCase(declaration)
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
