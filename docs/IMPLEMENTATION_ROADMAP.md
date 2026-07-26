# UtilLock: hoja de ruta de implementación y publicación

Este documento resume lo que debe completar el agente externo antes de publicar UtilLock en Google Play.

## Estado actual

- El cliente Android MVP existe en Kotlin/Jetpack Compose.
- La versión de prueba actual es `0.1.1-debug`.
- El bloqueo requiere que la persona active manualmente Accesibilidad; Android puede exigir primero **Permitir configuración restringida** cuando la APK se instala fuera de Google Play.
- La migración SQL, las políticas RLS y las Edge Functions están en `supabase/`.
- El proyecto Android todavía no tiene configurados `SUPABASE_URL` ni `SUPABASE_PUBLISHABLE_KEY` reales.
- La AAB de release no está firmada.
- Ya se hizo una prueba manual en un teléfono, pero falta la matriz completa de pruebas físicas y de publicación.

## 1. Conectar el proyecto real de Supabase

1. Crear o seleccionar el proyecto de producción en el plan Supabase Pro y conservar un proyecto separado para staging.
2. Configurar en `local.properties` únicamente en la máquina de compilación:
   - `SUPABASE_URL`
   - `SUPABASE_PUBLISHABLE_KEY`
3. En Supabase Auth activar:
   - Anonymous Sign-Ins.
   - Google OAuth.
   - Manual Linking de identidades.
   - Redirect URL `utillock://auth-callback`.
4. Aplicar `supabase/migrations/20260722225725_initial_schema.sql`.
5. Confirmar que las tablas públicas tienen RLS y grants explícitos para el Data API. La migración ya incluye grants explícitos; verificar el resultado en el proyecto real porque Supabase está cambiando la exposición automática de tablas nuevas.
6. Ejecutar Security Advisor y Performance Advisor y corregir todos los hallazgos antes de producción.
7. Configurar una estrategia de limpieza para:
   - `private.challenge_sessions` expiradas.
   - `private.ai_usage` antigua.
   - Usuarios anónimos abandonados, según la política de retención definida.
8. Desplegar todas las funciones:

   - `challenge-start`
   - `challenge-evaluate`
   - `billing-verify`
   - `billing-rtdn`
   - `filter-config`
   - `account-delete`

9. Configurar los secretos de Edge Functions. Nunca incluirlos en el APK, GitHub ni este documento:

   - `SUPABASE_DB_URL` usando el transaction pooler.
   - `SUPABASE_SERVICE_ROLE_KEY` si la función lo requiere; solo servidor.
   - `OPENAI_API_KEY`.
   - `OPENAI_EVALUATION_MODEL`.
   - `ANDROID_PACKAGE_NAME=app.utillock.android`.
   - `PLAY_SUBSCRIPTION_PRODUCT_ID=utillock_premium_monthly`.
   - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
   - `GOOGLE_PUBSUB_AUDIENCE`.
   - `GOOGLE_PUBSUB_SERVICE_ACCOUNT_EMAIL`.

10. Verificar con llamadas reales autenticadas que funcionan `challenge-start`, `challenge-evaluate`, `filter-config` y `account-delete`.
11. Probar eliminación de cuenta y comprobar las cascadas sin dejar datos privados huérfanos.
12. Activar CAPTCHA/Turnstile para altas anónimas si el abuso observado lo justifica.

## 2. Activar y probar OpenAI

1. Guardar `OPENAI_API_KEY` como secreto de la función `challenge-evaluate`.
2. Confirmar el modelo permitido mediante `OPENAI_EVALUATION_MODEL`.
3. Ejecutar una evaluación real con una foto legible y otra ilegible.
4. Probar respuesta correcta, incorrecta, tres fallos, expiración, offline y cuota agotada.
5. Confirmar que las fotos no se guardan en Supabase ni en OpenAI (`store:false`) y que se aplican límites de uso.

## 3. Configurar Google Play Billing y RTDN

1. Crear en Play Console el producto de suscripción `utillock_premium_monthly`.
2. Crear la oferta mensual, precio objetivo y precios regionales.
3. Configurar una cuenta de servicio de Google Cloud con privilegio mínimo para Android Publisher API.
4. Conectar Pub/Sub Real-time Developer Notifications (RTDN) hacia la función `billing-rtdn` con push autenticado.
5. Completar `GOOGLE_PUBSUB_AUDIENCE` y `GOOGLE_PUBSUB_SERVICE_ACCOUNT_EMAIL`.
6. Verificar en staging y en una pista de prueba:
   - Compra nueva.
   - Acknowledgement.
   - Restore.
   - Renovación.
   - Periodo de gracia.
   - Cancelación.
   - Expiración.
   - Reembolso/revocación.
   - Eliminación de cuenta.
7. Mostrar en la UI el precio y periodo que devuelve Play, no un precio fijo escrito a mano.

## 4. Legal, privacidad y declaraciones de Play

1. Sustituir en las políticas ES/EN:
   - `[NOMBRE LEGAL]` / `[LEGAL NAME]`.
   - `[EMAIL DE PRIVACIDAD]` / `[PRIVACY EMAIL]`.
   - `[PAÍS]` / `[COUNTRY]`.
2. Alojar ambas políticas en una URL pública estable y poner esa URL en Play Console.
3. Completar el formulario Data Safety con la configuración real de producción.
4. Completar la declaración de Accessibility API explicando que la función principal es bloquear aplicaciones y sitios elegidos por la persona.
5. Preparar un video de Play Console que muestre disclosure, consentimiento, activación, regla y pantalla bloqueada.
6. Declarar `VpnService` y explicar que solo enruta DNS para filtrado local.
7. Declarar los foreground services de DNS y Usage Access.
8. Completar clasificación de contenido, público objetivo y edad mínima 16+.
9. Verificar que el texto legal no prometa que el bloqueo es imposible de evadir.

## 5. Preparar la compilación de producción

1. Confirmar el nombre comercial y el `applicationId` definitivo (`app.utillock.android`) antes del primer upload; no cambiarlo después.
2. Crear un keystore de producción fuera del repositorio.
3. Configurar `signingConfig` local sin guardar contraseñas ni keystores en GitHub.
4. Ejecutar y verificar:

   ```powershell
   .\gradlew.bat testDebugUnitTest lintDebug bundleRelease
   ```

5. Verificar la firma de la AAB y conservar el upload key en un gestor seguro.
6. Subir primero a una pista interna y luego a una pista cerrada.
7. Mantener el versionCode creciente en cada subida.

## 6. Pruebas físicas necesarias

- Android 10, 12, 14, 15 y 16.
- Pixel, Samsung y al menos un fabricante con optimización agresiva de batería.
- Activación y revocación de Accesibilidad.
- Activación de **Permitir configuración restringida** en APKs instaladas fuera de Play.
- Acceso de uso como respaldo.
- Bloqueo de Instagram, Facebook y otras apps seleccionadas.
- Bloqueo rápido, horarios diurnos y horarios nocturnos.
- Reinicio del teléfono y recuperación del bloqueo.
- Chrome, Brave, Edge, Firefox y Samsung Internet.
- Dominios personalizados y filtro +18.
- Conflicto con otra VPN.
- Cámara girada, foto ilegible, límite de 1.5 MB y OCR local.
- Reto remoto, reto local, offline y cuota agotada.
- Cuenta anónima, vinculación de Google, compra/restauración y eliminación de cuenta.
- Comportamiento con notificaciones desactivadas y batería restringida.

## 7. Ficha de Play Store

- Icono de 512×512.
- Feature graphic de 1024×500.
- Capturas reales de teléfono y tablet.
- Descripción ES/EN revisada.
- URL de privacidad y contacto legal.
- Cuestionario de contenido y Data Safety.
- Declaraciones de Accessibility API, VPN y foreground services.
- Video de demostración para permisos sensibles.
- License testers y pruebas internas.
- Si la cuenta personal de Play es nueva, verificar el requisito vigente de testers y duración de la prueba cerrada antes de solicitar producción.

## Criterio de salida

La aplicación está lista para producción únicamente cuando:

1. Una instalación limpia puede iniciar sesión anónimamente y usar el reto real.
2. Una regla bloquea una app y un dominio en dispositivos físicos compatibles.
3. OpenAI, Supabase y Billing tienen pruebas reales exitosas.
4. La eliminación de cuenta funciona y no deja datos privados indebidos.
5. La AAB está firmada con el upload key definitivo.
6. Las declaraciones de Play, políticas legales y Data Safety coinciden con el comportamiento real.
7. La pista interna/cerrada no tiene errores críticos ni regresiones.

