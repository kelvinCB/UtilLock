# Política de privacidad de UtilLock

Última actualización: 22 de julio de 2026.

> Antes de publicar, reemplazar `[NOMBRE LEGAL]`, `[EMAIL DE PRIVACIDAD]` y `[PAÍS]` y alojar esta política en una URL pública estable.

`[NOMBRE LEGAL]`, con domicilio en `[PAÍS]`, es responsable de UtilLock. Para consultas o solicitudes de privacidad: `[EMAIL DE PRIVACIDAD]`.

## Datos tratados

- Reglas de bloqueo, horarios, lista de apps y dominios: permanecen en el dispositivo.
- Accesibilidad: se procesa localmente el paquete de la app visible y, en navegadores compatibles, la barra de direcciones. No se guarda ni envía el contenido de la pantalla.
- Acceso de uso: si se activa, se procesa localmente qué aplicación está en primer plano.
- DNS: las consultas se envían directamente a Cloudflare DNS/Family para resolver o filtrar dominios. UtilLock no conserva un historial DNS.
- Cámara: la foto del ejercicio se guarda temporalmente en el caché. Si corresponde evaluación con IA, se envía a la Edge Function de UtilLock y de allí a OpenAI. No se almacena en Supabase y se elimina del dispositivo tras evaluarla.
- Cuenta: Supabase conserva un identificador de usuario. Si vinculas Google, también trata la identidad/correo proporcionados por Google.
- Servicio: se conservan reto asignado, intentos, resultado, cuota diaria y marca de tiempo; no la foto.
- Compra: se conserva el token de compra, producto, estado y expiración verificados con Google Play.

## Finalidades y base

Los datos se tratan para ejecutar reglas solicitadas, verificar pausas, prevenir abuso, gestionar la cuenta, restaurar Premium, atender seguridad y cumplir obligaciones legales. La activación de Accesibilidad, VPN, cámara y vinculación de Google es voluntaria y se explica antes de solicitarla.

## Proveedores

- Supabase: autenticación, PostgreSQL y Edge Functions.
- OpenAI: evaluación visual durante la prueba gratuita o Premium. Las solicitudes usan `store:false`; OpenAI puede conservar datos limitados temporalmente por seguridad conforme a sus políticas vigentes.
- Google: inicio de sesión y Google Play Billing.
- Cloudflare: resolución DNS/filtrado familiar.

No vendemos datos personales ni los usamos para publicidad.

## Conservación

La foto solo existe durante la evaluación. Sesiones de reto y registros de uso se conservan el tiempo necesario para cuotas, seguridad y soporte; se recomienda una tarea de borrado de 90 días antes de producción. Los datos de compra se conservan mientras sean necesarios para restauración y obligaciones contables. Al eliminar la cuenta se borran los datos vinculados en UtilLock; cancelar la suscripción en Google Play es una acción separada.

## Derechos y controles

Puedes revocar permisos desde Android, detener la VPN, desvincular el uso de la app o usar “Eliminar cuenta y datos” dentro de Perfil. También puedes escribir a `[EMAIL DE PRIVACIDAD]` para acceso, corrección, eliminación, oposición o portabilidad cuando corresponda.

UtilLock está dirigida a mayores de 16 años y no busca recopilar datos de menores. Podemos actualizar esta política e indicaremos la nueva fecha.

