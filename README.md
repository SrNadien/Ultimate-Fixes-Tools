# NadienFixesTools — Mod NeoForge 1.21.1

**Fire Fix · Lag Analyzer · Debug Tool · Server Diagnostics**

Mod server-side only. Compatible con Arclight y servidores híbridos Bukkit/NeoForge.

---

## 📦 Compilar el mod

### Requisitos
- **JDK 21** (no JRE)
- **Conexión a internet** (primera compilación descarga NeoForge MDK ~200 MB)
- Git (opcional)

### Pasos en Windows
```bat
cd nadienfixestools
gradlew.bat build
```

### Pasos en Linux / macOS
```bash
cd nadienfixestools
chmod +x gradlew
./gradlew build
```

### Salida
```
build/libs/nadienfixestools-1.0.0.jar          ← mod para instalar
build/libs/nadienfixestools-1.0.0-sources.jar  ← código fuente
```

Copia `nadienfixestools-1.0.0.jar` a la carpeta `mods/` de tu servidor.

---

## ⚙️ Configuración

El archivo se genera automáticamente en `config/nadienfixestools.properties` al iniciar el servidor:

```properties
# Fire Fix
enableFireFix=true
maxFireRestoreAttempts=3
ignorePlayerCausedEvents=true
debugFire=true

# Spam Control
enableSpamControl=true
spamCooldownMs=5000
spamMaxSameMessage=3

# Lag Monitor
enableLagMonitor=true
lagThresholdMspt=50.0
lagLogIntervalTicks=200
```

---

## 📁 Archivos de log

Se crean en la carpeta `NadienFixesTools/` en la raíz del servidor:

| Archivo | Contenido |
|---|---|
| `logro.log` | Eventos de fuego: IGNITE_CANCELLED, FIRE_REMOVED, FIRE_RESTORED |
| `rutas.log` | Rutas de clase del causante (MOD / PLUGIN / SISTEMA) |
| `quetolagea.log` | Fuentes de lag con coords, dimensión y MSPT |

---

## 🕹️ Comandos

| Comando | Permiso | Descripción |
|---|---|---|
| `/nftps` | OP 2 | TPS real, MSPT promedio, estado |
| `/nfresources` | OP 2 | CPU, RAM, entidades, chunks, MSPT |
| `/nflag` | OP 2 | Escaneo manual de lag → guarda en log |
| `/nfuptime` | Todos | Uptime del servidor en horas/minutos |

---

## 🔥 Cómo funciona el Fire Fix

1. Se detecta cuando se coloca fuego (`BlockEvent.EntityPlaceEvent`)
2. Si en el siguiente tick el bloque volvió a ser `air`, se identifica el causante via stacktrace filtrado
3. Se registra en `logro.log` y `rutas.log` con el mod/plugin responsable
4. Se restaura el fuego (hasta `maxFireRestoreAttempts` veces)
5. Si se agotan los intentos, se loguea como bloqueo permanente

### Compatibilidad Arclight
El filtro de stacktraces detecta prefijos de Bukkit (`org.bukkit.`, `io.papermc.`, etc.) y los etiqueta como `PLUGIN/BUKKIT` en los logs, diferenciándolos de mods NeoForge.

---

## 🏗️ Estructura del proyecto

```
nadienfixestools/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
└── src/main/
    ├── java/nadiendev/nadienfixestools/
    │   ├── NadienFixesTools.java          ← clase principal
    │   ├── config/ModConfig.java          ← configuración
    │   ├── logging/LogManager.java        ← sistema de logs async
    │   ├── util/StackTraceAnalyzer.java   ← identificador de causas
    │   ├── events/
    │   │   ├── FireFixHandler.java        ← fire fix + restauración
    │   │   └── LagMonitorHandler.java     ← monitor de MSPT/TPS
    │   └── commands/CommandHandler.java   ← /nftps /nfresources /nflag /nfuptime
    └── resources/META-INF/
        └── neoforge.mods.toml
```

---

## 📝 Notas

- El mod es **server-side only**: no requiere instalarse en el cliente
- Logs asíncronos: la escritura a disco no bloquea el thread del servidor
- Control de spam integrado: mensajes repetidos se suprimen tras `spamMaxSameMessage` ocurrencias
- `ignorePlayerCausedEvents=true` evita trackear fuego que pone el propio jugador (evita falsos positivos)
