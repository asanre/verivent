# Ktor server's IntelliJ debugger detector reads java.lang.management, which Android lacks.
# The call is wrapped in try/catch(Throwable) and falls back to false, so it is safe to ignore.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
