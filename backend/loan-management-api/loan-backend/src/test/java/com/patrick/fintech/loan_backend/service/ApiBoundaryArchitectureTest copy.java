package com.patrick.fintech.loan_backend.service;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails when a controller method exposes a JPA entity directly in its return
 * type.
 */
class ApiBoundaryArchitectureTest {
    @Test
    void controllersMustNotExposeJpaEntities() throws Exception {
        List<Class<?>> controllers = findClasses("com.patrick.fintech.loan_backend.controller");
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (containsEntity(method.getGenericReturnType(), new HashSet<>())) {
                    violations.add(controller.getSimpleName() + "#" + method.getName() + " -> "
                            + method.getGenericReturnType());
                }
            }
        }
        assertTrue(violations.isEmpty(), "JPA entities exposed by API controllers:\n" + String.join("\n", violations));
    }

    private boolean containsEntity(Type type, Set<Type> seen) {
        if (type == null || !seen.add(type))
            return false;
        if (type instanceof Class<?> c)
            return c.isAnnotationPresent(Entity.class)
                    || Arrays.stream(c.getDeclaredClasses()).anyMatch(x -> x.isAnnotationPresent(Entity.class));
        if (type instanceof ParameterizedType p) {
            if (containsEntity(p.getRawType(), seen))
                return true;
            return Arrays.stream(p.getActualTypeArguments()).anyMatch(t -> containsEntity(t, seen));
        }
        if (type instanceof WildcardType w)
            return Arrays.stream(w.getUpperBounds()).anyMatch(t -> containsEntity(t, seen));
        if (type instanceof GenericArrayType a)
            return containsEntity(a.getGenericComponentType(), seen);
        return false;
    }

    private List<Class<?>> findClasses(String pkg) throws Exception {
        String path = pkg.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> urls = cl.getResources(path);
        List<Class<?>> result = new ArrayList<>();
        while (urls.hasMoreElements()) {
            File dir = new File(urls.nextElement().toURI());
            File[] files = dir.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
            if (files != null)
                for (File f : files)
                    result.add(Class.forName(pkg + '.' + f.getName().replace(".class", "")));
        }
        return result;
    }
}
