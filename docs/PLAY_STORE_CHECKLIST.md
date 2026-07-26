# Checklist de publicación

## Identidad y ficha

- [ ] Confirmar nombre comercial, package definitivo y titular legal antes del primer upload; el package no debe cambiar después.
- [ ] Crear icono 512×512, feature graphic 1024×500 y capturas reales en teléfono/tablet.
- [ ] Preparar textos ES/EN sin afirmar que el bloqueo es imposible de evadir.
- [ ] Alojar las políticas de privacidad ES/EN con datos legales reales.

## Políticas sensibles

- [ ] Completar la declaración de Accessibility API explicando que la función central es bloquear apps/sitios elegidos.
- [ ] Adjuntar video donde se vea el disclosure previo, consentimiento, activación, regla y pantalla bloqueada.
- [ ] Declarar `VpnService`; explicar que solo enruta DNS, mostrar notificación persistente y el conflicto con otra VPN.
- [ ] Revisar la declaración de foreground services para los subtipos DNS y Usage Access.
- [ ] Confirmar cuestionario de contenido 16+ y que los ejercicios no recopilan datos biométricos.

## Backend y privacidad

- [ ] Sustituir placeholders legales de privacidad.
- [ ] Configurar limpieza de `private.challenge_sessions`, `private.ai_usage` y usuarios anónimos abandonados.
- [ ] Activar CAPTCHA/Turnstile para altas anónimas si el abuso real lo requiere.
- [ ] Probar eliminación de cuenta y cascadas en un proyecto staging.
- [ ] Ejecutar Security Advisor y Performance Advisor de Supabase; resolver hallazgos antes de producción.
- [ ] Rotar cualquier secreto que haya sido compartido fuera del gestor de secretos.

## Billing

- [ ] Crear `utillock_premium_monthly`, oferta mensual y precio objetivo US$2.99 con precios regionales de Play.
- [ ] Conectar Google Play Developer API, cuenta de servicio con privilegio mínimo y RTDN autenticado.
- [ ] Verificar compra, renovación, gracia, cancelación, expiración, reembolso, restore y cuenta eliminada.
- [ ] Mostrar precio y período que devuelve Play; no escribir un precio fijo en UI.

## Calidad

- [ ] Probar Android 10, 12, 14, 15 y 16; Samsung, Pixel y al menos un fabricante agresivo con batería.
- [ ] Probar navegadores soportados y cambios de sus barras URL.
- [ ] Probar horario normal/nocturno, cambio de zona horaria, reinicio, revocación de permisos y dos VPN.
- [ ] Probar cámara girada, foto ilegible, límite 1.5 MB, tres fallos, offline y cuota agotada.
- [ ] Ejecutar `testDebugUnitTest`, `lintDebug`, `bundleRelease` y pruebas internas de Play.
- [ ] Si la cuenta de Play es personal nueva, completar 12 testers durante 14 días antes de solicitar producción.

