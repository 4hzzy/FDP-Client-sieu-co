/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.utils

import net.ccbluex.liquidbounce.features.value.Value
import java.lang.reflect.Modifier

object ClassUtils {

    private val cachedClasses = mutableMapOf<String, Boolean>()

    /**
     * Allows you to check for existing classes with the [className]
     */
    fun hasClass(className: String): Boolean {
        return if (cachedClasses.containsKey(className)) {
            cachedClasses[className]!!
        } else try {
            Class.forName(className)
            cachedClasses[className] = true

            true
        } catch (e: ClassNotFoundException) {
            cachedClasses[className] = false

            false
        }
    }

    fun getObjectInstance(clazz: Class<*>): Any {
        clazz.declaredFields.forEach {
            if (it.name.equals("INSTANCE")) {
                return it.get(null)
            }
        }
        throw IllegalAccessException("This class not a kotlin object")
    }

    fun getValues(clazz: Class<*>, instance: Any) = clazz.declaredFields.map { valueField ->
        valueField.isAccessible = true
        valueField[instance]
    }.filterIsInstance<Value<*>>()

    /**
     * scan classes with specified superclass like what Reflections do but with log4j [ResolverUtil]
     * @author liulihaocai
     */
    fun <T : Any> resolvePackage(packagePath: String, klass: Class<T>): List<Class<out T>> {
        val list = mutableListOf<Class<out T>>()

        try {
            val classPath = com.google.common.reflect.ClassPath.from(klass.classLoader)
            for (classInfo in classPath.getTopLevelClassesRecursive(packagePath)) {
                try {
                    val resolved = classInfo.load()

                    resolved.declaredMethods.find {
                        Modifier.isNative(it.modifiers)
                    }?.let {
                        val klass1 = it.declaringClass.typeName + "." + it.name
                        throw UnsatisfiedLinkError(klass1 + "\n\tat ${klass1}(Native Method)") // we don't want native methods
                    }
                    
                    // check if class is assignable from target class
                    if (klass.isAssignableFrom(resolved) && !resolved.isInterface && !Modifier.isAbstract(resolved.modifiers)) {
                        // add to list
                        list.add(resolved as Class<out T>)
                    }
                } catch (e: Throwable) {
                    // Ignore class loading errors
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }
}