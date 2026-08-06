package com.example.minecraftmixinhelper.domain.service

object MixinTemplateRenderer {

    fun render(
        targetClass: String,
        methodName: String,
        descriptor: String,
        injectionType: String = "@Inject",
        atTarget: String = "HEAD",
        cancellable: Boolean = false
    ): String {
        val normalizedInjection = injectionType.trim()
        val isRedirect = normalizedInjection == "@Redirect"
        val isInject = normalizedInjection == "@Inject" || !isRedirect

        return if (isRedirect) {
            renderRedirect(targetClass, methodName, descriptor, atTarget)
        } else {
            renderInject(targetClass, methodName, descriptor, atTarget, cancellable)
        }
    }

    private fun renderInject(
        targetClass: String,
        methodName: String,
        descriptor: String,
        atTarget: String,
        cancellable: Boolean
    ): String {
        val isVoid = descriptor.trim().endsWith(")V")
        val callbackType = if (isVoid) "CallbackInfo" else "CallbackInfoReturnable<Any>"
        val cancellableSuffix = if (cancellable) ", cancellable = true" else ""
        // 正确的 Inject 回调参数：CallbackInfo 而非 @Local
        val callbackParam = "ci: $callbackType"

        // 生成的类名：取目标类简单名 + Mixin
        val simpleName = targetClass.split(".", "/").last().substringAfterLast("$")
        val mixinClassName = simpleName + "Mixin"

        return """
            @Mixin(${targetClass}::class)
            class $mixinClassName {
                @Inject(method = "$methodName", at = @At("$atTarget")$cancellableSuffix)
                private fun on${simpleName.replaceFirstChar { it.uppercase() }}${methodName.replaceFirstChar { it.uppercase() }}($callbackParam) {
                    // TODO: insert mixin logic here
                    // if (cancellable) ci.cancel()
                }
            }
        """.trimIndent()
    }

    private fun renderRedirect(
        targetClass: String,
        methodName: String,
        descriptor: String,
        atTarget: String
    ): String {
        // @Redirect 需要指定 target 为方法描述符
        // 简化：假设重定向的是目标类的方法调用
        val simpleName = targetClass.split(".", "/").last().substringAfterLast("$")
        val mixinClassName = simpleName + "Mixin"

        return """
            @Mixin(${targetClass}::class)
            class $mixinClassName {
                @Redirect(method = "$methodName", at = @At(value = "INVOKE", target = "L${targetClass.replace('.', '/')};$methodName$descriptor"))
                private fun redirect${methodName.replaceFirstChar { it.uppercase() }}(): Any? {
                    // TODO: insert redirect logic here
                    return null
                }
            }
        """.trimIndent()
    }
}
