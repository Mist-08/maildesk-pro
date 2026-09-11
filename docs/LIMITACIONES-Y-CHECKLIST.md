# Limitaciones conocidas y checklist previo a entrega comercial

## Limitaciones conocidas (primera entrega)

1. **Sin bandeja de entrada (IMAP).** La aplicación es un gestor de envío. La lectura de correo
   recibido queda como ampliación: añadir `jakarta.mail` IMAP con Angus, sincronización periódica y
   almacenamiento local de cabeceras; no se muestra ninguna bandeja ficticia.
2. **OAuth2 para SMTP no implementado.** Proveedores que solo aceptan XOAUTH2 (Microsoft 365 con
   autenticación básica deshabilitada, Google sin contraseña de aplicación) requieren un relay SMTP
   autorizado o una ampliación futura. No se sustituye por autenticación incompatible.
3. **«Aceptado por el servidor» ≠ entregado.** No hay seguimiento de rebotes (DSN/bounces) ni
   confirmaciones de lectura; un rebote posterior llega al buzón `Reply-To`/`MAIL_FROM` y no se refleja
   en la aplicación.
4. **Sesiones en memoria.** Las sesiones HTTP viven en el proceso; al reiniciar, todos vuelven a
   iniciar sesión. Para varias instancias o alta disponibilidad se recomienda Spring Session JDBC.
5. **Sin cola distribuida.** El procesador de salida está pensado para una instancia; varias instancias
   funcionarían gracias a la reclamación atómica, pero no está probado bajo esa configuración.
6. **Editor HTML básico** (`contenteditable` + `execCommand`, obsoleto pero soportado). El servidor
   sanea siempre el HTML; no hay editor de imágenes incrustadas (solo `<img src="https://…">`).
7. **Adjuntos**: máximo 10 por mensaje, 10 MB cada uno, 25 MB en total; tipos permitidos por extensión
   y firma binaria. No se analiza el contenido de ZIP/Office en busca de macros o malware.
8. **Límites de frecuencia por IP** dependen de que el proxy inverso envíe `X-Forwarded-For`
   (`APP_FORWARD_HEADERS=native`); sin proxy se usa la IP directa.
9. **Perfil `local` con H2** es solo para demostraciones; PostgreSQL es la base soportada.
10. **Verificación en dos pasos por correo**: protege frente a contraseñas filtradas, pero no es
    resistente al phishing ni a un buzón comprometido. No se ofrecen códigos maestros ni accesos
    alternativos.
11. **Idioma**: interfaz en español (México); no hay internacionalización.
12. **Pruebas ejecutadas con H2 en modo PostgreSQL y GreenMail.** Las migraciones usan SQL estándar
    compatible con ambos motores, pero la ejecución contra PostgreSQL real debe verificarse en el
    entorno del cliente (ver checklist). En el entorno de desarrollo de esta entrega no había
    PostgreSQL ni Docker operativos.
13. **Análisis de vulnerabilidades**: el perfil Maven `security-audit` (OWASP Dependency-Check) está
    configurado pero no se ejecutó en esta entrega porque requiere descargar la base NVD (varios
    minutos y, en la práctica, una clave de API de NVD).

## Checklist previo a entrega comercial

### Infraestructura y secretos
- [ ] PostgreSQL 14+ provisionado, con usuario dedicado y contraseña fuerte (`DB_*`).
- [ ] `APP_OTP_HMAC_SECRET` (≥ 32 caracteres aleatorios) y `APP_SETUP_SECRET` generados y guardados en un gestor de secretos; `.env` fuera del repositorio.
- [ ] `APP_BASE_URL` con HTTPS real; proxy inverso con TLS y HSTS; `APP_COOKIE_SECURE=true`, `APP_FORWARD_HEADERS=native`.
- [ ] Puerto 8080 no expuesto a Internet; solo el proxy.
- [ ] Zona horaria del servidor correcta (fechas del panel y de auditoría).

### Correo
- [ ] SMTP real configurado con el método autorizado por el proveedor (contraseña de aplicación o relay); `APP_MAIL_REAL=true`.
- [ ] SPF, DKIM y DMARC del dominio del remitente `MAIL_FROM` publicados.
- [ ] Envío de prueba a una cuenta externa (Gmail/Outlook) verificado manualmente, incluida la llegada a bandeja y no a spam.
- [ ] Cuota diaria (`APP_MAIL_QUOTA_PER_USER_PER_DAY`) y máximo de destinatarios acordados con el cliente y dentro de los límites del proveedor.

### Cuentas y acceso
- [ ] Alta inicial completada con el correo del administrador real; `APP_SETUP_SECRET` retirado o rotado después.
- [ ] Usuarios invitados; roles revisados; al menos un segundo administrador o un plan de recuperación.
- [ ] Política de contraseñas y tiempo de inactividad (`APP_SESSION_TIMEOUT`) acordados.

### Verificación funcional en el entorno del cliente
- [ ] `mvnw test` en verde en la máquina de despliegue.
- [ ] Migraciones Flyway aplicadas sobre PostgreSQL sin errores (`flyway_schema_history`).
- [ ] Flujo completo probado: acceso con código, redacción con adjunto y plantilla, envío, historial, fallo controlado (SMTP apagado) y reintento.
- [ ] Respaldo (`scripts\backup-db.ps1`) y restauración (`scripts\restore-db.ps1`) ensayados.
- [ ] Revisión de `docs/GUIA-OPERACION.md` con el cliente (operación diaria y errores comunes).

### Seguridad
- [ ] `mvnw -Psecurity-audit -DskipTests verify` ejecutado y CVEs ≥ 7 resueltos o justificados.
- [ ] Búsqueda de secretos en el código y en el historial del repositorio antes de entregar.
- [ ] Logs revisados: sin contraseñas, códigos, tokens ni cuerpos de correo.
- [ ] Revisión externa (pentest o revisión de pares) programada; no declarar «seguridad absoluta».

### Comercial
- [ ] Alcance, limitaciones y ampliaciones (IMAP, OAuth2 SMTP, rebotes, Spring Session) documentados en la propuesta.
- [ ] Licencias de terceros (`docs/DEPENDENCIAS-Y-LICENCIAS.md`) incluidas en la entrega.
- [ ] Soporte y actualizaciones (Spring Boot, JDK) acordados por contrato.
