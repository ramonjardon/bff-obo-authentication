# ETAPA 1: Construcción (Builder)
# Utilizamos Liberica NIK para Java 25 sobre un entorno de construcción completo
FROM ghcr.io/bell-soft/liberica-native-image-kit-container:jdk-25-nik-slim AS builder

USER root

# Instalación de dependencias necesarias para la compilación nativa de GraalVM
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# 1. Aprovechamos la caché de Docker para las dependencias de Maven
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# 2. Copiamos el código fuente y compilamos el binario nativo
# Añadimos --gc=z para que el binario use ZGC (baja latencia para los 3M de eventos)
COPY src ./src
RUN ./mvnw -Pnative native:compile -DskipTests -B \
    -Dspring-boot.native-image.builder-arguments="--gc=z"

# ---

# ETAPA 2: Ejecución (Runtime)
# Usamos Chainguard GraalVM-Native: Cero vulnerabilidades detectadas (Wolfi OS)
# Incluye glibc y libz, necesarias para el binario generado por NIK
FROM cgr.dev/chainguard/graalvm-native:latest

# Seteamos el directorio de trabajo
WORKDIR /app

# Copiamos el binario generado en la etapa anterior
# Chainguard usa por defecto el usuario nonroot (UID 65532)
COPY --from=builder --chown=65532:65532 /build/target/ms-bff-server .

# Aseguramos permisos de ejecución mínimos
RUN chmod 500 ./ms-bff-server

# Cambiamos al usuario sin privilegios
USER 65532

# El BFF escucha en el puerto 8080
EXPOSE 8080

# Al ser un binario nativo, no hay JVM. Los hilos virtuales y el GC
# ya están embebidos. El binario acepta argumentos de Spring Boot directamente.
# El healthcheck debe gestionarse externamente (K8s o Docker Compose) mediante HTTP.
ENTRYPOINT ["/app/ms-bff-server"]