package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Variance
import io.github.sahsenvar.kmemory.compiler.model.TypeRef

/**
 * Bir [KSType]'i uretilen koda yazilabilir kaynak metnine cevirir.
 *
 * Tek gorevi var ve o gorev TEK yerde durmak zorunda: uretilen `override` imzasi ile
 * arayuzdeki bildirim arasinda en ufak bir fark (dusen bir tip argumani, dusen bir `?`)
 * "overrides nothing" demektir. `@Read` deger tipi ile `@Write` parametre tipi bu yuzden
 * ayni renderer'dan gecer; iki ayri yerde elle turetilselerdi biri digerinden sapabilirdi.
 *
 * Nitelendirme stratejisi: imzada KISA ad kullanilir ve gereken FQN import olarak toplanir.
 * Alternatif — her adi tam nitelikli yazmak — derlenir ama uretilen dosyayi okunamaz hale
 * getirir ve ayni tipin iki farkli metinle gorunmesine yol acar.
 */
internal class RenderTypeUseCase {

    /**
     * @param type Cozulmus bildirilen tip; `null` ya da hatali tipte `null` doner (o durumda
     *   zaten [ValidateInterfaceUseCase] derlemeyi dusurur).
     * @return Tipin imza metni, nitelenmis hali ve import'lari; kok tipi nitelenemiyorsa
     *   (`T` gibi bir tip parametresi) `null`.
     */
    operator fun invoke(type: KSType?): TypeRef? {
        if (type == null || type.isError) return null
        val rootFqName = type.declaration.qualifiedName?.asString() ?: return null

        val imports = mutableSetOf<String>()
        val notNullable = type.makeNotNullable()

        return TypeRef(
            source = render(type, imports, useQualifiedNames = false),
            nonNull = render(notNullable, imports, useQualifiedNames = false),
            // Nitelenmis hal yalnizca karsilastirma icin uretilir; import toplamaz.
            qualified = render(notNullable, mutableSetOf(), useQualifiedNames = true),
            rootFqName = rootFqName,
            isNullable = type.isMarkedNullable,
            hasArguments = type.arguments.isNotEmpty(),
            imports = imports,
        )
    }

    private fun render(type: KSType, imports: MutableSet<String>, useQualifiedNames: Boolean): String {
        val declaration = type.declaration
        val fqName = declaration.qualifiedName?.asString()
        val base = when {
            // Tip parametresi (`T`) nitelenemez; bildirildigi gibi birakilir.
            fqName == null -> declaration.simpleName.asString()
            useQualifiedNames -> fqName
            else -> {
                if (fqName.substringBeforeLast('.', "") !in DEFAULT_IMPORT_PACKAGES) imports += fqName
                declaration.simpleName.asString()
            }
        }

        val arguments = type.arguments.joinToString(", ") { render(it, imports, useQualifiedNames) }
        val nullability = if (type.isMarkedNullable) "?" else ""

        return if (arguments.isEmpty()) base + nullability else "$base<$arguments>$nullability"
    }

    /** Yildiz izdusumu ve varyans isaretleri imzanin parcasidir; atilirlarsa override kirilir. */
    private fun render(
        argument: KSTypeArgument,
        imports: MutableSet<String>,
        useQualifiedNames: Boolean,
    ): String {
        if (argument.variance == Variance.STAR) return "*"
        val resolved = argument.type?.resolve() ?: return "*"
        val prefix = when (argument.variance) {
            Variance.COVARIANT -> "out "
            Variance.CONTRAVARIANT -> "in "
            else -> ""
        }
        return prefix + render(resolved, imports, useQualifiedNames)
    }

    private companion object {

        /**
         * Kotlin'in her dosyaya kendiliginden ekledigi paketler.
         *
         * Bunlari import etmek gecerlidir ama uretilen dosyaya `import kotlin.collections.List`
         * gibi tamamen gereksiz satirlar koyar.
         */
        val DEFAULT_IMPORT_PACKAGES = setOf(
            "kotlin",
            "kotlin.annotation",
            "kotlin.collections",
            "kotlin.comparisons",
            "kotlin.io",
            "kotlin.jvm",
            "kotlin.ranges",
            "kotlin.sequences",
            "kotlin.text",
            "java.lang",
        )
    }
}
