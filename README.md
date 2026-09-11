# MailDesk Pro

Gestor profesional de envío de correos para pequeñas empresas: redacción con adjuntos y plantillas,
contactos con etiquetas, cola de salida persistente con historial real, usuarios con roles y
**verificación en dos pasos por correo**. Monolito modular en **Java 21 / Spring Boot 3.5**, Thymeleaf,
PostgreSQL + Flyway y Jakarta Mail (Eclipse Angus).

> Un negocio por instalación, múltiples usuarios. La bandeja de entrada (IMAP) queda fuera de esta
> versión; ver `docs/LIMITACIONES-Y-CHECKLIST.md`.

## Contenido

1. [Requisitos](#requisitos)
2. [Instalación rápida (Windows)](#instalación-rápida-windows)
3. [Instalación con Docker](#instalación-con-docker)
4. [NetBeans](#abrir-compilar-y-ejecutar-en-netbeans)
5. [Cómo usarlo](#cómo-usarlo)
6. [Cómo probarlo](#cómo-probarlo)
7. [Configuración](#configuración)
8. [Funcionalidades](#funcionalidades)
9. [Estructura](#estructura)
10. [Seguridad](#seguridad-alcance-honesto)

## Requisitos

| Componente | Versión | Notas |
|---|---|---|
| JDK | 21 (LTS) | Compila con `release 21`. Los scripts detectan un JDK 21 instalado sin cambiar la configuración global. |
| Maven | Wrapper incluido (`mvnw.cmd` / `mvnw`, Maven 3.9.9) | No hace falta instalar Maven. |
| Base de datos | PostgreSQL 14+ (perfiles `dev`/`prod`) | El perfil `local` usa H2 en archivo para arrancar sin PostgreSQL. |
| SMTP | Cualquier servidor SMTP autorizado | Mientras no exista, se usa un SMTP **local aislado** y la interfaz muestra «Correo real pendiente de configuración». |
| Docker (opcional) | Docker 24+ / Compose v2 | Para PostgreSQL + Mailpit o para ejecutar todo en contenedores. |

## Instalación rápida (Windows)

```powershell
git clone https://github.com/Mist-08/maildesk-pro.git
cd maildesk-pro
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```

`setup.ps1` crea `.env` a partir de `.env.example`, genera `APP_SETUP_SECRET` y `APP_OTP_HMAC_SECRET`
aleatorios y compila. Antes de arrancar, edita `.env` y escribe en `APP_INITIAL_ADMIN_EMAIL` el
correo de la persona que será administradora.

Después, en dos consolas:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-dev-smtp.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

Abre <http://localhost:8080>. Al no existir administrador, se muestra el **alta inicial**:
introduce el valor de `APP_SETUP_SECRET` (está en `.env`), tu nombre y una contraseña. El código de
verificación de 8 dígitos llega al SMTP local y se muestra en la consola de `start-dev-smtp.ps1`.

En Linux/macOS los equivalentes son `./mvnw spring-boot:run` (con las variables de `.env` cargadas
automáticamente) y `./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.mycompany.maildesk.devtools.DevSmtpCatcher`.

## Instalación con Docker

Solo infraestructura (PostgreSQL + Mailpit) y la aplicación desde el IDE o los scripts:

```powershell
docker compose up -d db mailpit
powershell -ExecutionPolicy Bypass -File scripts\start.ps1 -Profile dev
```

Mailpit captura todo el correo saliente en <http://localhost:8025>.

Todo en contenedores (perfil `prod`; requiere `.env` completo):

```powershell
docker compose --profile app up -d --build
```

## Abrir, compilar y ejecutar en NetBeans

1. **Archivo → Abrir proyecto…** y selecciona la carpeta del repositorio (proyecto Maven).
2. **Propiedades del proyecto → Compilar → Plataforma Java**: elige JDK 21 (si tu NetBeans usa otro JDK,
   no hace falta desinstalar nada; también arranca con JDK 25).
3. Crea `.env` (ejecuta `scripts\setup.ps1` o copia `.env.example`). La aplicación lo carga sola.
4. Arranca el SMTP local (`scripts\start-dev-smtp.ps1` o `docker compose up -d mailpit`).
5. Clic derecho en el proyecto → **Run** (F6). NetBeans usa la propiedad `start-class` del `pom.xml`.
6. **Test** (clic derecho → *Test*) ejecuta la misma suite que `mvnw test`.

## Cómo usarlo

1. **Alta inicial**: `/setup` una sola vez, con el secreto de instalación y verificación del correo del administrador.
2. **Invitar usuarios**: *Administración → Usuarios*. Cada persona recibe un enlace de un solo uso (72 h) y define su contraseña.
3. **Acceder**: correo + contraseña y después el código de 8 dígitos enviado al correo.
4. **Contactos y etiquetas**: *Contactos* (búsqueda, etiquetas de color, «Escribir» directo).
5. **Plantillas**: *Plantillas*, con variables seguras `{{nombre}}`, `{{email}}`, `{{empresa}}`, `{{negocio}}`, `{{remitente}}`, `{{fecha}}`.
6. **Redactar**: Para/CC/CCO, asunto, HTML o texto, firma, adjuntos (pdf, imágenes, Office, zip, txt, csv). Se autoguarda como borrador.
7. **Enviar**: el mensaje entra en la *Bandeja de salida*; el procesador lo entrega al SMTP y lo marca
   como *Aceptado por el servidor*, *Fallido* (con motivo y reintento) o *Resultado incierto*.
8. **Personalizar**: *Administración → Configuración* (nombre comercial, logotipo, color, firma) y
   *Auditoría* para revisar acciones.
9. **Correo real**: configura `MAIL_*` y `APP_MAIL_REAL=true` en `.env` (ver `docs/GUIA-OPERACION.md`).

## Cómo probarlo

Suite automática (44 pruebas; H2 en memoria + GreenMail como SMTP aislado, sin correo externo):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-tests.ps1
```

o directamente:

```powershell
.\mvnw.cmd test
```

Cubre: acceso en dos pasos y bloqueo de rutas antes del segundo paso; códigos incorrectos, vencidos,
reutilizados, reenviados y verificados en paralelo; límites de intentos y solicitudes; recuperación de
contraseña y revocación de sesiones; roles y aislamiento entre usuarios; CSRF, HTML peligroso,
adjuntos inválidos y cabeceras maliciosas; borradores, cola de salida y fallos SMTP.

Prueba manual sugerida: alta inicial → invitar un usuario → redactar con adjunto y plantilla → enviar →
ver estado en *Enviados* → detener el SMTP local y reintentar para ver el fallo controlado.

### Dónde ver los correos en local

En los perfiles `local` y `dev` **nada sale a Internet**: todos los correos (códigos de verificación,
invitaciones, restablecimientos y los mensajes que redactes) van al SMTP local de pruebas. Puedes leerlos en:

| Opción | Dónde aparecen |
|---|---|
| `scripts\start-dev-smtp.ps1` | En la consola donde lo ejecutaste y, además, en el archivo `data\correo-local.log` (se crea solo). Cada correo se imprime con *Para*, *Asunto* y el texto; el código de verificación es el número de 8 dígitos. |
| `docker compose up -d mailpit` | Interfaz web en <http://localhost:8025>, con vista HTML, texto y adjuntos. |

Si abres la aplicación y ves el aviso amarillo «Correo real pendiente de configuración», estás en este
modo. Si al pedir un código aparece «No se pudo enviar el correo…», es que el SMTP local no está en marcha.

### Probar con correo real (que los códigos lleguen a tu bandeja)

1. Consigue credenciales SMTP de un proveedor. Ejemplo con **Gmail**: activa la verificación en dos pasos
   en tu cuenta de Google y crea una *contraseña de aplicación* (Cuenta de Google → Seguridad →
   Contraseñas de aplicaciones; son 16 caracteres). La contraseña normal de Gmail **no** funciona.
   Otros proveedores válidos: Brevo, SendGrid, Mailgun o Amazon SES (dan usuario y clave SMTP).
   Microsoft 365 / Outlook suele exigir OAuth2 o un relay; ver `docs/GUIA-OPERACION.md`.
2. Edita `.env` (nunca lo subas al repositorio):

   ```
   APP_INITIAL_ADMIN_EMAIL=tu-correo@gmail.com   # aquí llegarán los códigos del administrador
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=tu-correo@gmail.com
   MAIL_PASSWORD=xxxx xxxx xxxx xxxx              # contraseña de aplicación de 16 caracteres
   MAIL_AUTH=true
   MAIL_STARTTLS=true
   MAIL_SSL=false
   MAIL_FROM=tu-correo@gmail.com                  # Gmail reescribe el remitente si no coincide con la cuenta
   MAIL_FROM_NAME=MailDesk Pro
   APP_MAIL_REAL=true
   ```

3. Reinicia la aplicación (`scripts\start.ps1` o *Run* en NetBeans). El aviso amarillo desaparece y en
   *Administración → Configuración* verás «Servidor real declarado». Ya no necesitas el SMTP local.
4. Prueba el flujo completo:
   - Si es una instalación nueva, haz el alta inicial: el código de 8 dígitos llega a
     `APP_INITIAL_ADMIN_EMAIL` (revisa también *Spam*). Si ya hiciste el alta con el SMTP local, simplemente
     inicia sesión: el código llegará ahora a tu correo real.
   - Invita a otra dirección tuya desde *Usuarios*: recibirás el enlace de invitación.
   - Redacta un mensaje a una cuenta tuya y envíalo. En *Enviados* aparecerá «Aceptado por el servidor»
     con la respuesta SMTP (`250 …`) y el correo llegará a la bandeja del destinatario. Las respuestas
     llegan al correo del usuario que lo redactó (cabecera `Reply-To`).
   - Prueba *¿Olvidaste tu contraseña?*: el enlace de restablecimiento llega a tu bandeja.
5. Si algo falla, revisa el estado del mensaje en *Fallidos* (motivo exacto) y la tabla de errores de
   `docs/GUIA-OPERACION.md`. Los fallos típicos son contraseña de aplicación incorrecta
   (`AuthenticationFailedException`) o un `MAIL_HOST` mal escrito.

Ten en cuenta los límites del proveedor (Gmail personal: ~500 correos/día) y ajusta
`APP_MAIL_QUOTA_PER_USER_PER_DAY` en `.env` si hace falta.

Análisis de dependencias vulnerables (descarga la base NVD, tarda):

```powershell
.\mvnw.cmd -Psecurity-audit -DskipTests verify
```

## Configuración

| Perfil | Base de datos | Correo | Uso |
|---|---|---|---|
| `local` (predeterminado) | H2 en `./data/maildesk-local` | SMTP local `localhost:1025` | Demostraciones y NetBeans sin infraestructura |
| `dev` | PostgreSQL `localhost:5432` | SMTP local | Desarrollo con Compose |
| `test` | H2 en memoria | GreenMail en 127.0.0.1:3025 | Pruebas automáticas |
| `prod` | PostgreSQL (`DB_*`) | SMTP real (`MAIL_*`, TLS, validación de certificados) | Producción; aborta si faltan secretos |

Todas las variables están documentadas en `.env.example`. La aplicación carga `.env` explícitamente;
las variables de entorno reales tienen prioridad. `.env`, `data/` y `target/` están excluidos del
repositorio: **nunca subas secretos**.

Scripts (`scripts/`): `setup.ps1`, `start.ps1`, `start-dev-smtp.ps1`, `run-tests.ps1`,
`backup-db.ps1`, `restore-db.ps1`.

## Funcionalidades

- Alta inicial de administrador de un solo uso (secreto externo + verificación del correo).
- Usuarios por invitación, roles `ADMIN` y `USER`, deshabilitación, auditoría de acciones.
- Acceso: contraseña (Argon2id) → código de 8 dígitos (5 min, un solo uso, 5 intentos, reenvío tras
  60 s, 5 solicitudes/hora por cuenta, límites por IP). Sesión renovada al completar el acceso; cierre
  por inactividad; revocación de sesiones al cambiar o restablecer la contraseña.
- Recuperación de contraseña con token aleatorio de un solo uso; cambio de correo con reautenticación
  y verificación de la nueva dirección.
- Redacción con Para/CC/CCO, HTML saneado (OWASP) o texto, firma, adjuntos validados por firma binaria,
  autoguardado, plantillas con variables, contactos con etiquetas, búsqueda y paginación.
- Cola de salida persistente con concurrencia limitada, reintentos solo para fallos transitorios, cuota
  diaria, protección contra doble clic y estados honestos (nunca se afirma la entrega al destinatario).
- Panel con métricas reales, personalización de marca, modo claro/oscuro, interfaz responsive en español.

## Estructura

```
src/main/java/com/mycompany/maildesk
├── auth/        acceso en dos pasos, alta inicial, invitaciones, recuperación
├── user/        usuarios y roles
├── messages/    borradores, adjuntos, cola de salida (OutboxProcessor)
├── mail/        transporte SMTP, clasificación de fallos, correos del sistema
├── contacts/    contactos y etiquetas
├── templates/   plantillas y sustitución de variables
├── account/     perfil, firma, contraseña, cambio de correo
├── admin/       usuarios, configuración, auditoría
├── settings/    personalización del negocio
├── audit/       registro de auditoría
├── common/      utilidades (hash, saneamiento, almacenamiento)
└── config/      seguridad, propiedades, carga de .env
src/main/resources/db/migration   migraciones Flyway
src/main/resources/templates      vistas Thymeleaf
scripts/                          PowerShell (setup, start, SMTP local, pruebas, respaldo)
docs/                             guía de operación, dependencias y licencias, limitaciones y checklist
```

## Seguridad: alcance honesto

La verificación en dos pasos por correo añade una capa frente a contraseñas comprometidas, pero
**no** es resistente al phishing ni sustituye un canal de correo seguro. No se afirma seguridad
absoluta ni preparación para producción sin completar el checklist de `docs/LIMITACIONES-Y-CHECKLIST.md`
y una revisión externa.
