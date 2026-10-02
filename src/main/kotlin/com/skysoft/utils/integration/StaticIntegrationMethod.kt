package com.skysoft.utils.integration

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Exact, cached optional-mod linkage without hiding API or implementation failures. */
internal object StaticIntegrationMethod {
    fun resolve(owner: Class<*>, name: String, vararg parameters: Class<*>): Method {
        val method = try {
            owner.getMethod(name, *parameters)
        } catch (exception: NoSuchMethodException) {
            throw LinkageError("Missing optional integration ${owner.name}.$name", exception)
        }
        if (!Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
            throw LinkageError("Optional integration ${owner.name}.$name must be static and return void")
        }
        return method
    }

    fun invoke(method: Method, vararg arguments: Any) {
        try {
            method.invoke(null, *arguments)
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        } catch (exception: IllegalAccessException) {
            throw LinkageError("Cannot access optional integration ${method.declaringClass.name}.${method.name}", exception)
        }
    }
}
