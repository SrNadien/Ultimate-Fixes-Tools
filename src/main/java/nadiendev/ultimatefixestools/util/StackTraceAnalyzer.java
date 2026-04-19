package nadiendev.ultimatefixestools.util;

import java.util.*;

/**
 * Analiza stacktraces para identificar qué mod o plugin causó un evento.
 * Filtra clases de Minecraft vanilla y NeoForge para mostrar solo fuentes externas.
 */
public class StackTraceAnalyzer {

    // Prefijos a IGNORAR (vanilla / neoforge internos)
    private static final List<String> IGNORED_PREFIXES = List.of(
            "cpw.mods.",
            "org.spongepowered.asm.",
            "java.",
            "javax.",
            "sun.",
            "com.sun.",
            "jdk.",
            "org.apache.",
            "com.google.",
            "io.netty.",
            "it.unimi.",
            "com.ibm.",
            "org.slf4j."
    );

    // Prefijos conocidos de plugins Bukkit/Arclight
    private static final List<String> BUKKIT_PREFIXES = List.of(
            "org.bukkit.",
            "org.spigotmc.",
            "net.md_5.",
            "io.papermc.",
            "com.destroystokyo.",
            "xyz.jpenilla.",
            "io.github.",
            "me.",
            "com.comphenix."
    );

    public enum SourceType { MOD, PLUGIN, SYSTEM, UNKNOWN }

    public record AnalysisResult(SourceType type, String className, String fullTrace) {}

    /**
     * Analiza el stacktrace actual y devuelve el primer elemento externo relevante.
     */
    public static AnalysisResult analyze() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        List<String> external = new ArrayList<>();
        SourceType detected = SourceType.UNKNOWN;
        String primaryClass = "unknown";

        for (StackTraceElement el : stack) {
            String cls = el.getClassName();

            // Ignorar clases internas del analizador
            if (cls.startsWith("nadiendev.ultimatefixestools.util.StackTraceAnalyzer")) continue;
            if (cls.startsWith("nadiendev.ultimatefixestools.")) continue;

            // Ignorar internos de Minecraft/NeoForge
            if (isIgnored(cls)) continue;

            external.add(cls + "." + el.getMethodName() + ":" + el.getLineNumber());

            if (detected == SourceType.UNKNOWN) {
                primaryClass = cls;
                detected = classify(cls);
            }
        }

        String trace = external.isEmpty() ? "sin_traza_externa" : String.join(" -> ", external.subList(0, Math.min(5, external.size())));
        return new AnalysisResult(detected, primaryClass, trace);
    }

    /**
     * Analiza un array de StackTraceElement ya capturado.
     */
    public static AnalysisResult analyze(StackTraceElement[] stack) {
        List<String> external = new ArrayList<>();
        SourceType detected = SourceType.UNKNOWN;
        String primaryClass = "unknown";

        for (StackTraceElement el : stack) {
            String cls = el.getClassName();
            if (cls.startsWith("nadiendev.ultimatefixestools.")) continue;
            if (isIgnored(cls)) continue;

            external.add(cls + "." + el.getMethodName() + ":" + el.getLineNumber());

            if (detected == SourceType.UNKNOWN) {
                primaryClass = cls;
                detected = classify(cls);
            }
        }

        String trace = external.isEmpty() ? "sin_traza_externa"
                : String.join(" -> ", external.subList(0, Math.min(5, external.size())));
        return new AnalysisResult(detected, primaryClass, trace);
    }

    private static boolean isIgnored(String cls) {
        for (String prefix : IGNORED_PREFIXES) {
            if (cls.startsWith(prefix)) return true;
        }
        return false;
    }

    private static SourceType classify(String cls) {
        for (String prefix : BUKKIT_PREFIXES) {
            if (cls.startsWith(prefix)) return SourceType.PLUGIN;
        }
        String[] parts = cls.split("\\.");
        if (parts.length >= 3) return SourceType.MOD;
        return SourceType.UNKNOWN;
    }

    /** Convierte el SourceType a string legible */
    public static String typeName(SourceType t) {
        return switch (t) {
            case MOD    -> "MOD";
            case PLUGIN -> "PLUGIN/BUKKIT";
            case SYSTEM -> "SISTEMA";
            default     -> "DESCONOCIDO";
        };
    }
}
