# Dependencias y licencias · MailDesk Pro

Versiones fijadas en `pom.xml` (padre `spring-boot-starter-parent 3.5.16`, que gestiona las versiones
de Spring, Hibernate, Flyway, H2, PostgreSQL JDBC, Jackson, Tomcat y Angus Mail). Verificadas en
Maven Central el 10/09/2026.

| Dependencia | Versión | Licencia | Uso |
|---|---|---|---|
| Spring Boot (web, thymeleaf, security, data-jpa, validation, mail, actuator, test) | 3.5.16 | Apache-2.0 | Base del monolito |
| Spring Framework / Spring Security / Spring Data JPA | gestionadas por Boot 3.5.16 | Apache-2.0 | MVC, seguridad, persistencia |
| Hibernate ORM | gestionada (6.6.x) | LGPL-2.1 | JPA |
| Thymeleaf + thymeleaf-extras-springsecurity6 | gestionada (3.1.x) / 3.1.5.RELEASE | Apache-2.0 | Vistas |
| Apache Tomcat (embebido) | gestionada (10.1.x) | Apache-2.0 | Servidor HTTP |
| Flyway (core + database-postgresql) | gestionada (11.x) | Apache-2.0 | Migraciones |
| PostgreSQL JDBC | gestionada (42.7.x) | BSD-2-Clause | Base de datos |
| H2 Database | gestionada (2.3.x) | MPL-2.0 / EPL-1.0 | Perfil local y pruebas |
| Jakarta Mail API + Eclipse Angus Mail | gestionadas (2.1.x) | EPL-2.0 / GPL-2.0 con Classpath Exception / EDL-1.0 | Transporte SMTP |
| Bouncy Castle `bcprov-jdk18on` | 1.85.2 | MIT (licencia Bouncy Castle) | Argon2id para contraseñas |
| OWASP Java HTML Sanitizer | 20260313.1 | Apache-2.0 | Saneamiento de HTML |
| Jackson | gestionada | Apache-2.0 | JSON (autoguardado) |
| HikariCP | gestionada | Apache-2.0 | Pool de conexiones |
| Micrometer / Actuator | gestionada | Apache-2.0 | `/actuator/health` |
| SLF4J + Logback | gestionadas | MIT / EPL-1.0 + LGPL-2.1 | Registro |
| **Solo pruebas:** GreenMail (`greenmail-junit5`) | 2.1.13 | Apache-2.0 | SMTP aislado |
| **Solo pruebas:** JUnit 5, AssertJ, Hamcrest, Mockito, spring-security-test | gestionadas | EPL-2.0 / Apache-2.0 / BSD-3 / MIT | Pruebas |
| **Plugin (perfil `security-audit`):** OWASP dependency-check-maven | 13.0.0 | Apache-2.0 | Análisis de vulnerabilidades |
| **Plugin:** exec-maven-plugin | 3.6.3 | Apache-2.0 | Herramienta SMTP de desarrollo |

Imágenes Docker: `maven:3.9.9-eclipse-temurin-21`, `eclipse-temurin:21-jre` (Temurin, GPL-2.0 con
Classpath Exception), `postgres:16-alpine` (PostgreSQL License), `axllent/mailpit` (MIT).

Todas las licencias permiten uso comercial y redistribución en una solución instalada para un cliente.
Hibernate (LGPL) y Logback (LGPL/EPL) se usan sin modificar como bibliotecas enlazadas, lo que es
compatible con software propietario. Revise con asesoría legal antes de redistribuir el código fuente.

Para regenerar el inventario completo con licencias detectadas automáticamente:

```powershell
.\mvnw.cmd -q org.codehaus.mojo:license-maven-plugin:2.5.0:add-third-party
```

(el informe se genera en `target/generated-sources/license/THIRD-PARTY.txt`).
