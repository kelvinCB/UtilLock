# UtilLock

UtilLock es un MVP nativo para Android que bloquea aplicaciones y sitios elegidos mediante horarios o un bloqueo rápido. Para pausar la protección, la persona debe resolver un ejercicio corto de lógica, escribir la respuesta en papel y fotografiarla.

La app está inspirada funcionalmente en AppBlock, pero usa identidad, interfaz y arquitectura propias. El cliente funciona en español e inglés según el idioma del dispositivo.

## Qué está implementado

- Selección de apps visibles en el launcher, sin `QUERY_ALL_PACKAGES`.
- Bloqueo por Accesibilidad, con respaldo opcional mediante Usage Access.
- Detección de dominios en Chrome, Brave, Edge, Firefox y Samsung Internet.
- VPN local de solo DNS para dominios personalizados y contenido adulto mediante Cloudflare Family DoH.
- Bloqueo rápido de 30, 60 o 120 minutos y horarios semanales, incluidos rangos que cruzan medianoche.
- Pantalla de bloqueo y pausas de 5, 15 o 30 minutos.
- Reto con CameraX, evaluación remota estructurada y alternativa OCR local con ML Kit.
- Cuatro pausas de IA gratuitas; Premium con hasta 30 evaluaciones diarias.
- Suscripción de Google Play (`utillock_premium_monthly`), verificación en servidor y RTDN.
- Cuenta anónima de Supabase, vinculación con Google antes de comprar/restaurar y eliminación de cuenta.
- PostgreSQL con RLS, grants explícitos, tablas privadas y Edge Functions sin guardar fotos.

## Estructura

- `app/`: cliente Kotlin, Jetpack Compose, CameraX, ML Kit y Play Billing.
- `supabase/migrations/`: esquema, RLS, grants, retos y configuración inicial.
- `supabase/functions/`: retos, OpenAI, Billing, RTDN, configuración y eliminación de cuenta.
- `docs/`: arquitectura, privacidad, Data Safety, pruebas y publicación.

## Compilar Android

Requisitos normales: JDK 17, Android SDK Platform 36 y Build Tools 35.0.0. Este workspace ya tiene una toolchain de verificación en `.tooling/`, ignorada por git.

1. Copia `local.properties.example` a `local.properties`.
2. Configura `sdk.dir`, `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY`.
3. Ejecuta:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

El APK de prueba se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Para exportar un archivo con nombre listo para compartir, ejecuta `./gradlew.bat :app:exportDebugApk`. El resultado queda en `app/build/outputs/apk/named/UtilLock-v0.1.1-debug.apk`. Cuando se configure el keystore de producción, `:app:exportReleaseApk` generará `UtilLock-v0.1.1-release.apk`.

En el primer arranque, abre la pestaña `Protección` y activa manualmente `Accesibilidad`. Para el filtro DNS +18, Android también pedirá confirmar la VPN local. El bloqueo de aplicaciones no puede funcionar sin Accesibilidad o el respaldo de `Acceso de uso`.

Para una AAB de producción, crea un keystore fuera del repositorio, configura una `signingConfig` local y ejecuta `bundleRelease`. Nunca confirmes keystores, contraseñas, `service_role` ni claves de OpenAI.

## Configurar Supabase Pro

1. Crea un proyecto dedicado y copia su URL y publishable key al `local.properties` local.
2. En Auth habilita Anonymous Sign-Ins, Google y Manual Linking.
3. Añade `utillock://auth-callback` a las Redirect URLs.
4. Vincula y aplica el esquema:

```powershell
npx supabase login
npx supabase link --project-ref TU_PROJECT_REF
npx supabase db push
```

5. Configura `OPENAI_API_KEY` desde `supabase/functions/.env.example` como Supabase Edge Function Secret. Las variables `SUPABASE_URL`, `SUPABASE_DB_URL` y las claves de servidor ya son provistas por el entorno alojado; nunca se copian al APK.
6. Despliega:

```powershell
npx supabase functions deploy challenge-start
npx supabase functions deploy challenge-evaluate
npx supabase functions deploy billing-verify
npx supabase functions deploy billing-rtdn
npx supabase functions deploy filter-config
npx supabase functions deploy account-delete
```

Las funciones validan por sí mismas el JWT del usuario; por eso `verify_jwt=false` en el gateway no equivale a acceso público. Este enfoque también funciona con las claves JWT asimétricas actuales de Supabase.

## OpenAI

`challenge-evaluate` autentica al usuario, valida formato/tamaño real de la foto, la coloca de forma temporal en el bucket privado `challenge-photos` y llama a Responses API con entrada de imagen, Structured Outputs y `store:false`. El modelo configurable `OPENAI_EVALUATION_MODEL` usa `gpt-5.6-terra` por defecto. El servidor exige legibilidad, escritura manuscrita, una respuesta suficiente y coincidencia entre transcripción, cálculo independiente y respuesta determinista. El objeto privado se elimina siempre al terminar la evaluación.

## Google Play

- Crea la suscripción `utillock_premium_monthly` y al menos una oferta base mensual.
- Da a la cuenta de servicio acceso de solo lectura a pedidos/suscripciones en Play Console.
- Configura Pub/Sub RTDN con push autenticado hacia `billing-rtdn`.
- Completa `GOOGLE_PUBSUB_AUDIENCE` y `GOOGLE_PUBSUB_SERVICE_ACCOUNT_EMAIL`.
- Ejecuta las pruebas con license testers y una pista de prueba cerrada antes de producción.

Consulta [PLAY_STORE_CHECKLIST.md](docs/PLAY_STORE_CHECKLIST.md) antes de enviar la app.

## Límites deliberados del MVP

- Android no permite una garantía absoluta frente a desinstalación, modo seguro, cambios OEM o revocación de permisos. UtilLock comunica el estado real y no promete invulnerabilidad.
- La inspección de URL depende de la estructura de Accesibilidad del navegador; el DNS cubre el resto a nivel de dominio, no rutas concretas.
- La VPN local no puede convivir con otra VPN activa.
- El filtro adulto depende de Cloudflare Family. Los dominios personalizados se responden localmente con NXDOMAIN.
- No se incluyen ubicación/Wi-Fi, límites por cantidad de aperturas, bloqueo de notificaciones, administración parental ni iOS.
