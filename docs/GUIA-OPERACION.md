# Guía de operación · MailDesk Pro

## 1. Configuración SMTP (correo saliente)

La cuenta que **envía** es independiente de las cuentas con las que las personas **acceden** a la
aplicación. Todos los mensajes salen desde el remitente autorizado `MAIL_FROM`; el nombre del usuario
aparece en el remitente visible y su correo va en `Reply-To`, de modo que las respuestas le llegan.

Variables (en `.env` o en el entorno):

| Variable | Descripción |
|---|---|
| `MAIL_HOST`, `MAIL_PORT` | Servidor y puerto (587 STARTTLS o 465 SSL en producción). |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Credenciales SMTP autorizadas por el proveedor. |
| `MAIL_AUTH` | `true` cuando el servidor exige autenticación. |
| `MAIL_STARTTLS` / `MAIL_SSL` | Cifrado. En `prod` STARTTLS es obligatorio por defecto y siempre se validan certificados. |
| `MAIL_FROM`, `MAIL_FROM_NAME` | Remitente autorizado por el proveedor (SPF/DKIM). |
| `APP_MAIL_REAL` | `true` solo cuando el SMTP configurado es real. Con `false` la interfaz avisa «Correo real pendiente de configuración». |
| `MAIL_*_TIMEOUT_MS` | Tiempos de espera de conexión, lectura y escritura. |

**Importante sobre proveedores.** No se deduce el proveedor a partir del dominio del correo
institucional ni se asume que acepte la contraseña habitual del buzón:

- *Microsoft 365 / Exchange Online*: la autenticación básica SMTP suele estar deshabilitada y el
  proveedor exige OAuth2. Esta entrega **no** implementa OAuth2 para SMTP; use un relay SMTP
  autorizado por el administrador del tenant (por ejemplo, un conector de relay o un servicio de envío
  transaccional) o solicite que se habilite «SMTP AUTH» con contraseña de aplicación, si su
  organización lo permite.
- *Google Workspace / Gmail*: requiere «contraseña de aplicación» con verificación en dos pasos
  activa o un relay autorizado; la contraseña normal no funciona.
- *Servicios transaccionales (SES, SendGrid, Mailgun, Brevo, etc.)*: proporcionan usuario/clave SMTP
  y exigen verificar el dominio o el remitente.

Cuando el proveedor requiera OAuth2 exclusivamente, **no** lo sustituya por autenticación básica ni
desactive TLS: documente el bloqueo y use un relay compatible.

Comprobación: en **Administración → Configuración** se muestra el servidor, el remitente y el estado.
Envíe un mensaje de prueba a una cuenta propia y verifique el estado *Aceptado por el servidor* (que
solo indica que el SMTP recibió el mensaje, no que el destinatario lo recibió).

### Desarrollo sin credenciales

- `scripts\start-dev-smtp.ps1` arranca un SMTP local aislado (GreenMail) que muestra en consola los
  correos recibidos, incluidos los códigos de verificación.
- `docker compose up -d mailpit` ofrece lo mismo con interfaz web en <http://localhost:8025>.

Nada sale a Internet en estos modos y la aplicación nunca simula envíos exitosos: si el SMTP local
no está en marcha, el envío falla y se muestra el error.

## 2. Alta inicial del administrador

1. Defina `APP_INITIAL_ADMIN_EMAIL` (por ejemplo `admin@tu-empresa.com`) y
   `APP_SETUP_SECRET` (mínimo 16 caracteres; `scripts\setup.ps1` lo genera).
2. Arranque la aplicación y abra `/setup` (la pantalla de acceso redirige automáticamente mientras no
   exista un administrador).
3. Introduzca el secreto, su nombre y una contraseña de la aplicación (mínimo 10 caracteres, letras
   más números o símbolos; es independiente de la contraseña de su buzón).
4. Verifique el código de 8 dígitos enviado a la dirección del administrador inicial.
5. El alta queda cerrada de forma permanente (`/setup` responde 404). Los siguientes usuarios se
   crean por **invitación** desde *Administración → Usuarios*.

Recomendación: tras completar el alta, elimine `APP_SETUP_SECRET` del entorno o cámbielo.

## 3. Respaldo y restauración

- `scripts\backup-db.ps1` crea `backups\<fecha>\` con el volcado de PostgreSQL (`pg_dump -Fc`, local
  o vía `docker compose exec db`), o la copia de la base H2 en perfil `local`, más `storage.zip`
  (adjuntos y logotipo) y la clave HMAC local si existe.
- `scripts\restore-db.ps1 -BackupDir backups\<fecha>` restaura con la aplicación detenida.
- Conserve fuera del servidor: el respaldo, `.env` (o el gestor de secretos equivalente) y
  `APP_OTP_HMAC_SECRET`. Sin la clave HMAC los desafíos pendientes dejan de validar (los usuarios solo
  tendrían que iniciar sesión de nuevo).
- Programe respaldos diarios con el Programador de tareas de Windows o `cron`; pruebe una restauración
  antes de la entrega.

## 4. Despliegue con HTTPS

La aplicación escucha HTTP en `APP_PORT` (8080). En producción colóquela detrás de un proxy inverso
con TLS (Caddy, Nginx, Traefik o IIS con ARR) y configure:

```
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://correo.su-dominio.mx
APP_FORWARD_HEADERS=native      # respeta X-Forwarded-Proto/For del proxy
APP_COOKIE_SECURE=true          # cookie de sesión solo por HTTPS
```

Ejemplo mínimo con Caddy (`Caddyfile`):

```
correo.su-dominio.mx {
    reverse_proxy 127.0.0.1:8080
}
```

Caddy obtiene y renueva certificados automáticamente. Con Nginx, añada `proxy_set_header
X-Forwarded-Proto $scheme;` y `X-Forwarded-For`. Limite el acceso al puerto 8080 a la red local y use
HSTS en el proxy. Las cookies son `HttpOnly` y `SameSite=Lax`; con `APP_COOKIE_SECURE=true` también
`Secure`.

Comprobaciones de arranque en `prod`: si faltan `APP_OTP_HMAC_SECRET`, `MAIL_HOST`/`MAIL_FROM` o
(sin administrador) `APP_SETUP_SECRET`, la aplicación se detiene con un mensaje claro.

## 5. Resolución de errores

| Síntoma | Causa probable | Acción |
|---|---|---|
| «No se pudo enviar el correo de código de verificación» | SMTP local apagado o credenciales SMTP inválidas | Arranque `start-dev-smtp.ps1`/Mailpit o revise `MAIL_*`; vea el registro de la aplicación. |
| Estado *Fallido* con `AuthenticationFailedException` | Usuario/clave SMTP rechazados o proveedor que exige OAuth2 | Use el método autorizado por el proveedor (sección 1). |
| Estado *Fallido* con `SSLHandshakeException` / certificado | Certificado del SMTP no confiable o host incorrecto | Corrija `MAIL_HOST` (debe coincidir con el certificado). No se desactiva la validación. |
| Estado *Resultado incierto* | La conexión se perdió tras transmitir datos | Confirme con el destinatario antes de reenviar; use *Duplicar como borrador*. |
| «Falta APP_OTP_HMAC_SECRET» al arrancar en `prod` | Secreto no definido | Genere 32+ caracteres aleatorios y defínalo en el entorno. |
| «Cuenta bloqueada temporalmente» | 5 contraseñas incorrectas | Espere 15 minutos o restablezca la contraseña. |
| «Se alcanzó el máximo de códigos por hora» | 5 solicitudes de código en una hora | Espere; el límite no se reinicia al reenviar. |
| Sesión cerrada con `?revoked` | Cambio de contraseña, restablecimiento o cuenta deshabilitada | Inicie sesión de nuevo. |
| 403 al enviar un formulario | Token CSRF caducado (sesión expirada) | Recargue la página e inicie sesión. |
| Flyway: «Migration checksum mismatch» | Se editó una migración ya aplicada | Nunca edite migraciones aplicadas; cree `V2__...sql`. |
| NetBeans compila con JDK 25 | Plataforma del proyecto distinta | Seleccione JDK 21 en *Propiedades del proyecto → Compilar*. |

Registros: nivel `INFO` por defecto; nunca se registran contraseñas, códigos, tokens ni cuerpos de
correo. Para diagnósticos temporales use `logging.level.com.mycompany.maildesk=DEBUG`.
