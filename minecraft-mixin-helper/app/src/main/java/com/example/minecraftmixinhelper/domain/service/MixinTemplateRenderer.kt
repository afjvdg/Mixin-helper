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
        val callback = if (descriptor.endsWith(")V")) "CallbackInfo" else "CallbackInfoReturnable"
        val ciParam = if (cancellable) "@Local $callback ci" else "@Local $callback ci"

        return """
            @Mixin($targetClass::class)
            class ${targetClass.split(".").last()}Mixin {
                $injectionType(method = "$methodName", at = @At("$atTarget")${if (cancellable) ", cancellable = true" else ""})
                private fun on$methodName${if (injectionType == "@Redirect") "Redirect" else ""}(
                    ${ciParam}
                ) {
                    // TODO: insert mixin logic here
                }
            }
        """.trimIndent()
    }
}