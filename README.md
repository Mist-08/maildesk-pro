# MailDesk Pro

Gestor profesional de envío de correos para pequeñas empresas: redacción con adjuntos y plantillas,
contactos con etiquetas, cola de salida persistente con historial real, usuarios con roles y
**verificación en dos pasos por correo**. Monolito modular en **Java 21 / Spring Boot 3.5**, Thymeleaf,
PostgreSQL + Flyway y Jakarta Mail (Eclipse Angus).

> Un negocio por instalación, múltiples usuarios. La bandeja de entrada (IMAP) queda fuera de esta
> versión; ver `docs/LIMITACIONES-Y-CHECKLIST.md`.

## Índice

1. [Qué necesitas instalar](#1-qué-necesitas-instalar)
2. [Obtener el proyecto](#2-obtener-el-proyecto)
3. [Configuración inicial](#3-configuración-inicial)
4. [Ejecutar desde NetBeans](#4-ejecutar-desde-netbeans)
5. [Ejecutar desde consola o Docker](#5-ejecutar-desde-consola-o-docker)
6. [Dónde ver los correos en local](#6-dónde-ver-los-correos-en-local)
7. [Probar con correo real](#7-probar-con-correo-real)
8. [Manual de uso de la aplicación](#8-manual-de-uso-de-la-aplicación)
9. [Pruebas automáticas](#9-pruebas-automáticas)
10. [Respaldo y restauración](#10-respaldo-y-restauración)
11. [Solución de problemas](#11-solución-de-problemas)
12. [Configuración y perfiles](#12-configuración-y-perfiles)
13. [Estructura del código](#13-estructura-del-código)
14. [Seguridad: alcance honesto](#14-seguridad-alcance-honesto)

---

## 1. Qué necesitas instalar

| Herramienta | Versión | Para qué | Descarga |
|---|---|---|---|
| **JDK 21** (LTS) | 21.x | Compilar y ejecutar | [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21) u [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21) |
| **Apache NetBeans** | 22 o superior (probado con 26) | IDE recomendado; incluye Maven | [netbeans.apache.org](https://netbeans.apache.org/download/) |
| **Git** | cualquiera reciente | Clonar el repositorio | [git-scm.com](https://git-scm.com/downloads) |
| **Docker Desktop** (opcional) | 24+ | PostgreSQL y Mailpit sin instalarlos | [docker.com](https://www.docker.com/products/docker-desktop/) |
| **PostgreSQL** (opcional) | 14+ | Base de datos para `dev`/`prod` | [postgresql.org](https://www.postgresql.org/download/) |

No hace falta instalar Maven: el proyecto incluye el *Maven Wrapper* (`mvnw.cmd` en Windows, `mvnw` en
Linux/macOS) y NetBeans trae su propio Maven.

**Comprobar el JDK** (en PowerShell o CMD):

```powershell
java -version
```

Debe mostrar `21.x`. Si tienes varios JDK instalados no pasa nada: los scripts detectan el 21 y NetBeans
permite elegirlo por proyecto (ver sección 4). También arranca con JDK 25, pero el objetivo oficial es 21.

Para empezar sin PostgreSQL ni Docker usa el perfil `local` (predeterminado): guarda los datos en una base
H2 en la carpeta `data/` del proyecto. Es suficiente para probar todo.

## 2. Obtener el proyecto

**Opción A · Git en consola**

```powershell
git clone https://github.com/Mist-08/maildesk-pro.git
cd maildesk-pro
```

**Opción B · desde NetBeans**: menú **Team → Git → Clone…**, pega la URL
`https://github.com/Mist-08/maildesk-pro.git`, elige la carpeta destino y al terminar acepta **Open Project**.

**Opción C · ZIP**: botón verde **Code → Download ZIP** en GitHub y descomprime.

## 3. Configuración inicial

La aplicación se configura con variables de entorno leídas de un archivo `.env` en la raíz del proyecto
(se carga automáticamente al arrancar, también desde NetBeans). Ese archivo **no** se sube al repositorio.

**Windows (recomendado)** · en la raíz del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```

El script:
1. Verifica el JDK.
2. Crea `.env` a partir de `.env.example`.
3. Genera valores aleatorios para `APP_SETUP_SECRET` (secreto del alta inicial) y `APP_OTP_HMAC_SECRET`
   (clave de los códigos de verificación).
4. Compila el proyecto (la primera vez descarga dependencias; puede tardar unos minutos).

**Linux/macOS o manual**: copia `.env.example` a `.env`, escribe un `APP_SETUP_SECRET` de al menos 16
caracteres y un `APP_OTP_HMAC_SECRET` de al menos 32 caracteres aleatorios, y ejecuta `./mvnw -DskipTests package`.

Después abre `.env` con cualquier editor (en NetBeans aparece en **Files**) y ajusta:

| Variable | Qué poner |
|---|---|
| `APP_INITIAL_ADMIN_EMAIL` | Correo de la persona que será administradora. Recibirá el código del alta inicial. |
| `APP_SETUP_SECRET` | Ya generado. Lo escribirás una sola vez en la pantalla de alta inicial. |
| `MAIL_*` | Déjalos como están para usar el SMTP local de pruebas; para correo real ver sección 7. |

## 4. Ejecutar desde NetBeans

### 4.1 Abrir el proyecto
1. **File → Open Project…** y selecciona la carpeta `maildesk-pro` (NetBeans la reconoce como proyecto
   Maven por el `pom.xml`). Si la clonaste desde NetBeans ya está abierta.
2. Espera a que termine la indexación y la descarga de dependencias (barra de progreso inferior derecha).

### 4.2 Elegir el JDK 21 para el proyecto
1. **Tools → Java Platforms → Add Platform…** y apunta a la carpeta del JDK 21 si aún no aparece.
2. Clic derecho en el proyecto → **Properties → Build → Compile → Java Platform** → elige el JDK 21 → **OK**.
   No es necesario cambiar el JDK con el que arranca NetBeans.

### 4.3 Arrancar el SMTP local (para recibir los códigos)
Mientras no configures un correo real, los códigos de verificación se envían a un servidor SMTP local de
pruebas. Ábrelo en una terminal (en NetBeans: **Window → IDE Tools → Terminal**, o cualquier PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-dev-smtp.ps1
```

Déjalo abierto. Alternativa con Docker: `docker compose up -d mailpit` (interfaz en <http://localhost:8025>).

### 4.4 Ejecutar
1. Clic derecho en el proyecto → **Run** (o **F6**). NetBeans lanza la clase principal definida en
   `start-class` del `pom.xml`.
2. En la ventana **Output** verás `Started MailDeskApplication` y, si es una instalación nueva,
   `Alta inicial pendiente: visite http://localhost:8080/setup`.
3. Abre <http://localhost:8080> en el navegador.
4. Para detenerla, pulsa el botón rojo **Stop** de la ventana Output (o cierra NetBeans).

Perfil: por defecto arranca en `local`. Para otro perfil, **Properties → Run → VM Options** y añade
`-Dspring.profiles.active=dev` (o cambia `SPRING_PROFILES_ACTIVE` en `.env`).

### 4.5 Depurar
Clic derecho → **Debug** (Ctrl+F5). Pon puntos de interrupción en cualquier clase, por ejemplo en
`ChallengeService.verify` para seguir la verificación del código.

### 4.6 Compilar y probar desde NetBeans
- **Clean and Build** (Shift+F11): compila y genera `target/maildesk-pro-1.0.0.jar`.
- **Test** (Alt+F6): ejecuta la suite completa (equivale a `mvnw test`); los resultados aparecen en la
  ventana **Test Results**.
- Si cambias el `pom.xml` y NetBeans no lo refleja: clic derecho → **Reload Project**.

## 5. Ejecutar desde consola o Docker

**Windows**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-dev-smtp.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

`start.ps1` admite `-Profile local|dev|prod` y `-Jar` (ejecuta el `.jar` empaquetado).

**Linux/macOS**

```bash
./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.mycompany.maildesk.devtools.DevSmtpCatcher
```

```bash
./mvnw spring-boot:run
```

**Docker Compose** · solo infraestructura (PostgreSQL en `localhost:5432` y Mailpit) y la app desde el IDE:

```powershell
docker compose up -d db mailpit
powershell -ExecutionPolicy Bypass -File scripts\start.ps1 -Profile dev
```

Todo en contenedores (perfil `prod`; requiere `.env` completo):

```powershell
docker compose --profile app up -d --build
```

## 6. Dónde ver los correos en local

En los perfiles `local` y `dev` **nada sale a Internet**: todos los correos (códigos de verificación,
invitaciones, restablecimientos y los mensajes que redactes) van al SMTP local de pruebas. Puedes leerlos en:

| Opción | Dónde aparecen |
|---|---|
| `scripts\start-dev-smtp.ps1` | En la consola donde lo ejecutaste y, además, en el archivo `data\correo-local.log` (se crea solo). Cada correo se imprime con *Para*, *Asunto* y el texto; el código de verificación es el número de 8 dígitos. |
| `docker compose up -d mailpit` | Interfaz web en <http://localhost:8025>, con vista HTML, texto y adjuntos. |

Si abres la aplicación y ves el aviso amarillo «Correo real pendiente de configuración», estás en este
modo. Si al pedir un código aparece «No se pudo enviar el correo…», es que el SMTP local no está en marcha.

## 7. Probar con correo real

Para que los códigos de verificación, invitaciones y mensajes lleguen a bandejas reales:

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

3. Reinicia la aplicación (**Stop** y **Run** en NetBeans, o `scripts\start.ps1`). El aviso amarillo
   desaparece y en *Administración → Configuración* verás «Servidor real declarado». Ya no necesitas el SMTP local.
4. Prueba el flujo completo:
   - Si es una instalación nueva, haz el alta inicial: el código de 8 dígitos llega a
     `APP_INITIAL_ADMIN_EMAIL` (revisa también *Spam*). Si ya hiciste el alta con el SMTP local, simplemente
     inicia sesión: el código llegará ahora a tu correo real.
   - Invita a otra dirección tuya desde *Usuarios*: recibirás el enlace de invitación.
   - Redacta un mensaje a una cuenta tuya y envíalo. En *Enviados* aparecerá «Aceptado por el servidor»
     con la respuesta SMTP (`250 …`) y el correo llegará a la bandeja del destinatario. Las respuestas
     llegan al correo del usuario que lo redactó (cabecera `Reply-To`).
   - Prueba *¿Olvidaste tu contraseña?*: el enlace de restablecimiento llega a tu bandeja.
5. Si algo falla, revisa el estado del mensaje en *Fallidos* (motivo exacto) y la sección 11. Los fallos
   típicos son contraseña de aplicación incorrecta (`AuthenticationFailedException`) o un `MAIL_HOST` mal escrito.

Ten en cuenta los límites del proveedor (Gmail personal: ~500 correos/día) y ajusta
`APP_MAIL_QUOTA_PER_USER_PER_DAY` en `.env` si hace falta.

## 8. Manual de uso de la aplicación

### 8.1 Alta inicial del administrador (una sola vez)
1. Abre <http://localhost:8080>. Mientras no exista un administrador, redirige a `/setup`.
2. Escribe el **secreto de instalación** (valor de `APP_SETUP_SECRET` en `.env`), tu nombre y una
   contraseña de la aplicación (mínimo 10 caracteres combinando letras con números o símbolos; es
   independiente de la contraseña de tu buzón).
3. Pulsa **Crear cuenta y enviar código**. Llega un código de 8 dígitos al correo del administrador
   (consola del SMTP local o tu bandeja real).
4. Escríbelo en la pantalla de verificación. Caduca a los 5 minutos, admite 5 intentos y se puede
   reenviar tras 60 segundos. Al validarlo entras directamente al **Panel**.
5. El alta queda cerrada para siempre (`/setup` responde «No encontrado»).

### 8.2 Iniciar y cerrar sesión
- **Iniciar sesión**: correo + contraseña → código de verificación por correo → Panel. Tras 5 contraseñas
  incorrectas la cuenta se bloquea 15 minutos.
- **Cerrar sesión**: botón inferior del menú lateral. La sesión también expira tras 30 minutos sin actividad.
- **¿Olvidaste tu contraseña?**: enlace en la pantalla de acceso; recibirás un enlace de un solo uso
  (30 minutos). Al usarlo se cierran todas tus sesiones.

### 8.3 Panel
Muestra métricas reales: mensajes aceptados por el servidor, fallidos e inciertos, en cola, borradores,
cuota diaria usada, contactos y plantillas, últimos fallos y últimos aceptados. Los administradores ven
además usuarios activos, cola global, estado del correo y auditoría reciente.

### 8.4 Usuarios (solo administradores)
*Administración → Usuarios*:
- **Invitar**: escribe el correo y el rol (`Usuario` o `Administrador`). La persona recibe un enlace
  válido 72 horas, define su propia contraseña y su correo queda verificado.
- **Deshabilitar / Habilitar**: una cuenta deshabilitada pierde sus sesiones al instante.
- **Cambiar rol**: no puedes cambiar tu propio rol ni dejar el sistema sin administradores.
- **Revocar invitaciones** pendientes.
Los administradores gestionan cuentas, pero **no** pueden ver los mensajes, contactos ni plantillas de
otros usuarios.

### 8.5 Contactos y etiquetas
*Contactos*: alta con nombre, correo, empresa, teléfono, notas y etiquetas; búsqueda por nombre, correo
o empresa; filtro por etiqueta. En el panel derecho creas etiquetas con color. El botón **Escribir**
abre un borrador con el contacto como destinatario.

### 8.6 Plantillas
*Plantillas → Nueva plantilla*: nombre, asunto y cuerpo con formato. Puedes usar las variables
`{{nombre}}`, `{{email}}`, `{{empresa}}` (del contacto elegido), `{{negocio}}`, `{{remitente}}` y
`{{fecha}}`. Son sustituciones de texto: nunca se ejecuta código. Al editar una plantilla ves una vista
previa con datos de ejemplo. **Redactar con esta** crea un borrador ya rellenado.

### 8.7 Redactar y enviar
1. **Redactar** en el menú lateral (o desde un contacto o plantilla).
2. Rellena **Para** (varios correos separados por comas; al escribir dos letras se sugieren contactos),
   **CC**, **CCO** y **Asunto**.
3. Elige **HTML con formato** (barra con negrita, cursiva, listas, títulos, enlaces) o **Texto sin formato**.
4. **Adjuntos** (panel derecho): pdf, png, jpg, gif, webp, docx, xlsx, pptx, zip, txt y csv; máximo 10
   archivos, 10 MB cada uno y 25 MB en total. Se valida el contenido real del archivo, no solo la extensión.
5. **Usar plantilla**: elige plantilla y, opcionalmente, un contacto para rellenar las variables.
6. Marca **Incluir mi firma** si quieres añadir tu firma (o la del negocio si no tienes propia).
7. El borrador **se guarda solo** cada pocos segundos (estado arriba a la derecha). **Guardar borrador**
   lo guarda al instante; **Enviar** lo pone en la cola de salida.

### 8.8 Estados de los mensajes
| Vista | Estado | Significado |
|---|---|---|
| Borradores | *Borrador* | Editable. |
| Bandeja de salida | *En cola* / *Enviando* | Pendiente o en proceso. Puedes **Cancelar envío** mientras está en cola. |
| Enviados | *Aceptado por el servidor* | El servidor SMTP aceptó el mensaje (se guarda su respuesta, p. ej. `250 OK`). **No** garantiza que el destinatario lo recibió. |
| Fallidos | *Fallido* | Se muestra el motivo. Los fallos de conexión se reintentan solos hasta 3 veces; los rechazos definitivos no. Puedes **Reintentar**. |
| Fallidos | *Resultado incierto* | La conexión se perdió tras transmitir datos; no se reintenta para evitar duplicados. Confirma con el destinatario y usa **Duplicar como borrador**. |
| Fallidos | *Cancelado* | Cancelado por el usuario. |

Todas las vistas permiten buscar por asunto o destinatario y paginar. **Duplicar como borrador** crea
una copia editable de cualquier mensaje; **Eliminar** borra mensajes que no estén en cola.

### 8.9 Mi cuenta
Clic en tu nombre (abajo en el menú): cambiar nombre visible, firma con formato, contraseña (se cierran
las demás sesiones) y correo de acceso (pide tu contraseña actual y envía un código a la nueva dirección).

### 8.10 Configuración del negocio (solo administradores)
*Administración → Configuración*: nombre comercial, color principal, firma predeterminada, logotipo
(PNG/JPEG/GIF/WebP hasta 1 MB) y estado del correo saliente (servidor, remitente, cola global). Las
credenciales SMTP no se editan desde la interfaz: viven en `.env`.

### 8.11 Auditoría (solo administradores)
*Administración → Auditoría*: accesos correctos y fallidos, códigos rechazados, invitaciones, cambios de
contraseña o correo, mensajes en cola/aceptados/fallidos, cambios de configuración, con fecha, actor e IP.
Nunca se registran contraseñas, códigos, tokens ni cuerpos de correo.

### 8.12 Tema claro/oscuro y móvil
Botón **Tema** arriba a la derecha. En pantallas pequeñas el menú lateral se abre con **Menú**.

## 9. Pruebas automáticas

Suite de 44 pruebas (unitarias e integración) con H2 en memoria y GreenMail como SMTP aislado; **no**
envían correo externo ni tocan tu base de datos local.

- En NetBeans: clic derecho en el proyecto → **Test** (Alt+F6). Resultados en **Test Results**.
- En consola:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-tests.ps1
```

```powershell
.\mvnw.cmd test
```

- Una sola clase: `.\mvnw.cmd test -Dtest=TwoStepLoginIT`

Cubre: acceso en dos pasos y bloqueo de rutas antes del segundo paso; códigos incorrectos, vencidos,
reutilizados, reenviados y verificados en paralelo; límites de intentos y solicitudes; recuperación de
contraseña y revocación de sesiones; roles y aislamiento entre usuarios; CSRF, HTML peligroso, adjuntos
inválidos y cabeceras maliciosas; borradores, cola de salida y fallos SMTP; renderizado de todas las páginas.

Prueba manual sugerida: alta inicial → invitar un usuario → redactar con adjunto y plantilla → enviar →
ver estado en *Enviados* → detener el SMTP local y reintentar para ver el fallo controlado.

Análisis de dependencias vulnerables (descarga la base NVD; tarda):

```powershell
.\mvnw.cmd -Psecurity-audit -DskipTests verify
```

## 10. Respaldo y restauración

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup-db.ps1
```

Crea `backups\<fecha>\` con la base de datos (H2 o volcado PostgreSQL), los adjuntos y logotipo
(`storage.zip`) y la clave HMAC local. Para restaurar, con la aplicación detenida:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore-db.ps1 -BackupDir backups\<fecha>
```

Detalles en `docs/GUIA-OPERACION.md`.

## 11. Solución de problemas

| Síntoma | Causa | Solución |
|---|---|---|
| NetBeans: `Could not find or load main class ${start-class}` | `pom.xml` antiguo sin la propiedad `start-class` | Actualiza el repositorio; clic derecho → **Reload Project**. |
| `Port 8080 was already in use` | Otra instancia sigue corriendo | Pulsa **Stop** en la ventana Output de NetBeans o cierra la consola anterior; o cambia `APP_PORT` en `.env`. |
| «No se pudo enviar el correo de código de verificación» | SMTP local apagado o credenciales reales incorrectas | Arranca `start-dev-smtp.ps1` / Mailpit, o revisa `MAIL_*` (sección 7). |
| No llega el código a mi correo real | Contraseña de aplicación incorrecta, `MAIL_HOST` mal escrito, correo en Spam | Mira *Fallidos* o el registro de la aplicación; revisa Spam. |
| «Secreto de instalación incorrecto» | El valor no coincide con `APP_SETUP_SECRET` | Copia el valor exacto de `.env` (sin espacios) y reinicia si lo cambiaste. |
| `Falta APP_OTP_HMAC_SECRET` al arrancar | Perfil `prod` sin secretos | Define la variable (32+ caracteres). En `local`/`dev` se genera sola en `data/otp-hmac.key`. |
| Base H2 bloqueada (`Database may be already in use`) | Dos instancias con el mismo `data/maildesk-local` | Detén la otra instancia. |
| NetBeans compila con JDK 25 y avisa de métodos restringidos | JDK distinto al objetivo | Es solo un aviso; para evitarlo elige JDK 21 en **Properties → Build → Compile**. |
| `scripts\*.ps1` no se ejecutan | Política de ejecución de PowerShell | Usa `powershell -ExecutionPolicy Bypass -File …` como en los ejemplos. |
| 403 al enviar un formulario | Sesión expirada (token CSRF caducado) | Recarga la página e inicia sesión. |
| «Cuenta bloqueada temporalmente» | 5 contraseñas incorrectas | Espera 15 minutos o restablece la contraseña. |
| «Se alcanzó el máximo de códigos por hora» | 5 códigos solicitados en una hora | Espera; el límite no se reinicia al reenviar. |
| Flyway: `Migration checksum mismatch` | Se editó una migración ya aplicada | No edites migraciones aplicadas; crea `V2__...sql`. |

Más casos (SSL, proveedores, HTTPS) en `docs/GUIA-OPERACION.md`.

## 12. Configuración y perfiles

| Perfil | Base de datos | Correo | Uso |
|---|---|---|---|
| `local` (predeterminado) | H2 en `./data/maildesk-local` | SMTP local `localhost:1025` | Demostraciones y NetBeans sin infraestructura |
| `dev` | PostgreSQL `localhost:5432` | SMTP local | Desarrollo con Compose |
| `test` | H2 en memoria | GreenMail en 127.0.0.1:3025 | Pruebas automáticas |
| `prod` | PostgreSQL (`DB_*`) | SMTP real (`MAIL_*`, TLS, validación de certificados) | Producción; aborta si faltan secretos |

Todas las variables están documentadas en `.env.example`. Las variables de entorno reales tienen
prioridad sobre `.env`. `.env`, `data/` y `target/` están excluidos del repositorio: **nunca subas secretos**.

Scripts (`scripts/`): `setup.ps1`, `start.ps1`, `start-dev-smtp.ps1`, `run-tests.ps1`,
`backup-db.ps1`, `restore-db.ps1`.

Documentos (`docs/`): `GUIA-OPERACION.md` (SMTP por proveedor, alta inicial, respaldos, HTTPS,
errores), `DEPENDENCIAS-Y-LICENCIAS.md`, `LIMITACIONES-Y-CHECKLIST.md`.

## 13. Estructura del código

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
src/test/java                     pruebas (y DevSmtpCatcher, el SMTP local de desarrollo)
```

Tecnologías: Spring Boot 3.5 (Web, Security, Data JPA, Validation, Mail, Actuator), Thymeleaf,
Flyway, PostgreSQL / H2, Jakarta Mail + Eclipse Angus, Argon2id (Bouncy Castle), OWASP Java HTML Sanitizer,
JUnit 5 + GreenMail. Versiones y licencias en `docs/DEPENDENCIAS-Y-LICENCIAS.md`.

## 14. Seguridad: alcance honesto

- Contraseñas con Argon2id; códigos de verificación de 8 dígitos generados con `SecureRandom`, guardados
  solo como HMAC con clave externa, de un solo uso, con vencimiento y límites persistentes.
- Sesión renovada al completar el acceso, cookies `HttpOnly` y `SameSite`, CSRF en todos los formularios,
  cabeceras de seguridad (CSP, X-Frame-Options), HTML saneado, adjuntos validados por firma binaria,
  autorización por propietario en cada recurso.
- La verificación en dos pasos por correo añade una capa frente a contraseñas comprometidas, pero
  **no** es resistente al phishing ni sustituye un canal de correo seguro. No se afirma seguridad
  absoluta ni preparación para producción sin completar `docs/LIMITACIONES-Y-CHECKLIST.md` y una
  revisión externa.
