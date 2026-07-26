# Arquitectura

## Cliente Android

`ProtectionRepository` conserva reglas y estado en DataStore. `ScheduleEvaluator` calcula la unión de todos los horarios activos y trata correctamente los intervalos nocturnos.

La protección de apps tiene dos sensores:

1. `AppBlockAccessibilityService` reacciona a ventanas y contenido, bloquea paquetes elegidos y lee únicamente la barra de direcciones de navegadores compatibles.
2. `UsageMonitorService`, si la persona lo activa, consulta el evento de app en primer plano como respaldo.

Ambos abren `BlockedActivity`; nunca simulan toques, escriben texto ni recopilan el contenido general de la pantalla.

`AdultFilterVpnService` crea una interfaz VPN con una sola ruta, la IP de DNS virtual. No enruta tráfico web. Convierte paquetes DNS UDP a DNS-over-HTTPS, usa Cloudflare Family cuando una regla +18 está activa y responde NXDOMAIN a dominios personalizados.

## Flujo del reto

1. El cliente pide `challenge-start`; el servidor selecciona un reto aleatorio y guarda nonce, expiración e intentos.
2. CameraX guarda una foto temporal, la reduce a 1600 px/aproximadamente 1.5 MB y la envía como base64.
3. `challenge-evaluate` comprueba usuario, sesión, cuota y tamaño antes de llamar a OpenAI.
4. OpenAI devuelve JSON estricto. El servidor normaliza y compara tanto la transcripción como el cálculo independiente con `expected_answer`.
5. En éxito, el cliente concede una pausa local. La imagen nunca se inserta en Supabase ni se escribe en logs.
6. Ante fallo técnico se usa OCR local. Premium y prueba lo obtienen inmediatamente; una cuenta gratuita agotada espera 15 minutos. Tres errores generan un reto nuevo y cinco minutos de espera.

## Datos y confianza

- `public.profiles`, `public.entitlements` y `public.filter_config` tienen RLS y grants mínimos explícitos.
- El banco, sesiones, consumo, compras, eventos y límites están en `private`; las Edge Functions usan una conexión de servidor.
- `service_role`, OpenAI y Google Play nunca forman parte del APK.
- La cuenta empieza anónima. Google se vincula con `/auth/v1/user/identities/authorize`; no se crea un segundo usuario.
- Billing se considera Premium solo después de consultar Android Publisher API. RTDN vuelve a verificar, no confía en el payload de Pub/Sub.

## Decisiones de seguridad

- No existe `QUERY_ALL_PACKAGES`.
- Backup Android deshabilitado para reglas y tokens.
- HTTPS obligatorio y tráfico cleartext deshabilitado.
- Las fotos se eliminan del caché después de cada evaluación.
- La protección excluye UtilLock y no intercepta apps del sistema que no estén en el selector del launcher.
- `store:false` evita almacenar la respuesta en OpenAI; la política de retención del proveedor aún debe reflejarse en privacidad.

