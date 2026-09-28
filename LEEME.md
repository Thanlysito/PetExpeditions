# PetExpeditions (notas en español)

Plugin de Paper 1.21.1: registras a tu lobo, gato o loro domesticado y lo mandas de expedición.
Vuelve con botín, gana XP y sube de nivel. Cada expedición tiene una **sinergia** (un tipo de mascota que rinde más).

## Compilar

En PowerShell, dentro de esta carpeta:

```
.\gradlew build
```

El plugin queda en `build\libs\PetExpeditions-1.0.0.jar`.

## Probar

1. Copia el jar a `Servidor\plugins\`.
2. En `Servidor\plugins\PetExpeditions\config.yml` ya está `language: es` y `test-mode: true`
   (en modo prueba cada "minuto" dura 1 segundo).
3. Enciende el servidor, entra, domestica un lobo con huesos.
4. **Shift + clic derecho** al lobo con la mano vacía, dos veces → se une al equipo.
5. `/expeditions` → elige "Nearby Forest" → elige al lobo. A los 10 segundos vuelve.
6. Reclama con el botón verde "Reclamar todo" o en "Mis mascotas".

7. **Mascotas propias (1.1.0):** `/expeditions give thanlyXD baby_dragon` te da un huevo de Dragón bebé.
   Clic derecho con el huevo → nace y entra al equipo. En "Mis mascotas", Shift + clic lo vuelve huevo.
   También salen como botín raro (por ejemplo 4 % en el Bosque para el Slime Buddy).

Antes de publicar: poner `test-mode: false`.

## Archivos (src/main/java/dev/thanly/expeditions)

- `PetExpeditions.java` — clase principal.
- `Settings.java` — opciones de config.yml.
- `Messages.java` — textos en en/es con MiniMessage.
- `Pet.java` — una mascota (nivel, XP, expedición en curso).
- `PetType.java` — guardar el aspecto del animal y volver a crearlo.
- `PetStore.java` — guarda las mascotas en `players/<uuid>.yml`.
- `Expedition.java` — una expedición de config.yml y sus premios.
- `ExpeditionManager.java` — enviar, calcular éxito, dar botín, subir de nivel, avisar cuando vuelven.
- `MenuHolder.java` / `MenuManager.java` — los 3 menús (tablero, elegir mascota, mis mascotas).
- `PetListener.java` — registrar mascotas, clics en menús, entrar/salir.
- `ExpeditionCommand.java` — el comando /expeditions (y /expeditions give).
- `CustomPets.java` — mascotas propias, rarezas y huevos.
