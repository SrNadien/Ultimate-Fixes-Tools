Ultimate FixesTools

NeoForge 1.21.1  ·  Server-side only  ·  No client required

A server-side utility mod that acts as your server's all-in-one fire repair system, lag analyzer, and diagnostics toolkit. Built for vanilla NeoForge servers and hybrid setups like Arclight.



Fire fix system

→Hooks into BlockFadeEvent and neighbor notifications to catch cancellations instantly.
→Captures a filtered stacktrace exposing the exact mod or plugin responsible.
→Restores fire on the next tick if removed within 1–2 ticks — up to maxFireRestoreAttempts retries.
→Avoids infinite loops: logs a permanent block warning after max attempts and stops.
→Optionally ignores player-caused events to reduce false positives.


Lag analyzer

→Rolling 100-tick history — real MSPT average and TPS, measured tick-by-tick.
→Flags chunks with 50+ entity clusters and logs their coordinates and dimension.
→Configurable MSPT threshold — only logs when things actually matter.
→CPU and RAM stats accessible via command at any time.


Logging system

All data written asynchronously (no tick overhead) to NadienFixesTools/ in your server root.

firelogs.logIGNITE_CANCELLED, FIRE_REMOVED, FIRE_RESTORED with timestamps and cause.
rutas.logFull class path of the detected culprit, labeled MOD / PLUGIN / SYSTEM.
quetolagea.logLag events with coordinates, dimension, MSPT, and entity counts.

Built-in spam filter: repeated messages are suppressed after a configurable threshold.



Commands

/nftpsLive TPS, average MSPT, last tick time, server health status. OP 2
/nfresourcesCPU, RAM (used/total/max), entity count, loaded chunks, MSPT. OP 2
/nflagManual lag scan across all dimensions, saved to quetolagea.log. OP 2
/nfuptimeServer uptime in hours, minutes, and seconds. All players


Configuration

Auto-generated at config/nadienfixestools.properties on first launch.

enableFireFixmaxFireRestoreAttemptsignorePlayerCausedEventsdebugFireenableSpamControlspamCooldownMsspamMaxSameMessageenableLagMonitorlagThresholdMsptlagLogIntervalTicks


Requirements

→Minecraft 1.21.1 with NeoForge 21.1.215
→Server-side only — clients do not need to install this mod.
→Compatible with Arclight and other NeoForge/Bukkit hybrid servers.
→No dependencies beyond NeoForge itself.
