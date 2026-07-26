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
2. CameraX guarda una foto temporal, la reduce a 1600 px/aproximadamente 1.5 MB y la envía como base64 a la única Edge Function autorizada para evaluar.
3. `challenge-evaluate` comprueba usuario, sesión, cuota, Base64, firma real del archivo, MIME y tamaño. Luego crea un objeto efímero en el bucket privado `challenge-photos`, que no tiene políticas de acceso para clientes.
4. La Edge Function es el único componente que llama a OpenAI. OpenAI devuelve JSON estricto con legibilidad, presencia y suficiencia de escritura manuscrita, transcripción, cálculo, coincidencia y confianza.
5. El servidor recalcula la decisión y exige que transcripción y cálculo coincidan con `expected_answer`. En éxito, el cliente concede una pausa local. La foto no se escribe en logs y el objeto privado se elimina en un bloque `finally`.
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
- Las fotos se eliminan del caché y del bucket privado temporal después de cada evaluación.
- La protección excluye UtilLock y no intercepta apps del sistema que no estén en el selector del launcher.
- `store:false` evita almacenar la respuesta en OpenAI; la política de retención del proveedor aún debe reflejarse en privacidad.

