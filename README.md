# PURELOCK – Mi protección contra la pornografía (Android)

Fuerza el DNS **CleanBrowsing Adult Filter** con una VPN local (solo DNS) y hace difícil apagarlo durante un impulso.
**Es fricción, no un muro:** lee la sección de limitaciones.

## A. Conseguir el APK desde GitHub (botón de descarga)
Funciona desde el navegador del celular. Necesitas una cuenta gratuita en github.com.

**Opción 1 – Solo desde el celular (un único archivo)**
1. En github.com toca **+ > New repository**. Nombre: `purelock`. Puede ser **Private**. Toca **Create repository**.
2. Dentro del repositorio toca **Add file > Create new file**.
3. En el campo del nombre escribe exactamente `.github/workflows/build.yml` (las barras crean las carpetas).
4. Abre el archivo `PureLock-build.yml` de la entrega, copia TODO su contenido y pégalo en el editor.
5. Toca **Commit changes > Commit changes**.
6. Abre la pestaña **Actions**. Verás "Construir PureLock APK" ejecutándose (5–10 min). Si pide habilitar Actions, acepta. Si no arranca: elígelo y toca **Run workflow**.
7. Cuando salga el check verde ✔, vuelve a la pestaña **Code**. A la derecha (o abajo, en el celular) toca **Releases > PureLock build 1** y luego **PureLock.apk**: ese es el enlace de descarga.

**Opción 2 – Repositorio completo (con computadora):** descomprime `PureLock.zip`, sube TODO su contenido a un repositorio nuevo (rama `main`). El workflow `.github/workflows/build.yml` compila solo y publica en Releases. También puedes abrir la carpeta en **Android Studio** > Build > Build APK(s).

El APK también aparece como "Artifact" dentro de cada ejecución en Actions, pero el enlace de Releases es más cómodo.

## B. Instalar en el celular
1. Descarga `PureLock.apk` y ábrelo. Android pedirá permitir "instalar apps desconocidas" para tu navegador o gestor de archivos.
2. Si Play Protect avisa de una app "no verificada", toca **Instalar de todos modos** (el APK está firmado con clave de depuración y usa Accesibilidad).
3. Antes de abrir la app: Ajustes > Red > **DNS privado = Automático**. Quita el DNS manual de CleanBrowsing.
4. Abre PureLock y entrega el teléfono a tu persona de confianza (sección C).
5. Concede los 3 permisos de la pantalla de configuración:
   - **VPN** (filtro DNS).
   - **Accesibilidad** (guardián). En Android 13+, si aparece en gris: Ajustes > Apps > PureLock > ⋮ > *Permitir ajustes restringidos*, y vuelve a intentarlo.
   - **Administrador del dispositivo** (anti-desinstalación).
6. Ajustes > Red > VPN > PureLock > ⚙: activa **VPN siempre activa** y **Bloquear conexiones sin VPN**. (Hazlo ahora; después el guardián bloqueará esa pantalla.)
7. Desactiva "Usar DNS seguro" en Chrome y desinstala navegadores con DoH propio (Firefox, Brave) o no los uses.
8. Si el panel muestra "PROTECCIÓN ACTIVADA" y la revisión de evasiones no muestra ⚠, quedó lista.

## C. Persona de confianza
En el primer arranque, ella escribe su nombre, su teléfono y un PIN de 6+ dígitos que tú no debes ver. El PIN se guarda solo como hash (PBKDF2); nadie puede leerlo, ni siquiera tú o yo.
Para cambiar algo: **Solicitar desbloqueo** (se le abre un SMS para avisarla) → espera de 24 h → ella escribe el PIN → ventana de 10 minutos → se vuelve a bloquear sola. 5 PIN fallidos = bloqueo de 1 hora. Si ella pierde el PIN, solo queda el reinicio de fábrica.
Botones del panel: **Pedir ayuda** (SMS pre-escrito) y **Llamar**.
Consejo: que ella también controle tu cuenta de Google (contraseña/recuperación). Es lo que más cierra las salidas.

## D. Qué impide desactivarlo
- VPN local de DNS + "siempre activa" + "bloquear sin VPN": apagarla corta el internet en lugar de dejarte sin filtro.
- Guardián (Accesibilidad): te saca de Ajustes si abres pantallas con "PureLock", DNS privado con host, VPN siempre activa, restablecimiento de fábrica o depuración USB, y cuenta cada intento.
- Administrador del dispositivo: Android no permite desinstalar la app.
- Vigilancia cada 15 s con reactivación y avisos; arranca sola tras reiniciar.
- PIN de otra persona + espera de 24 h + ventana de 10 min.

## E. Limitaciones (sin promesas falsas)
- Se salta con: reinicio de fábrica desde recovery, modo seguro, borrar la app desde tu cuenta de Google en otro dispositivo, ADB por USB, o un teléfono secundario.
- Una VPN de terceros reemplaza a PureLock; Android revoca el permiso y la app solo puede avisarte.
- Apps con DNS propio (DoH en Firefox/Brave, VPNs, algunos navegadores) evaden cualquier filtro DNS y PureLock no puede detectarlo. Solo se filtra IPv4/UDP.
- Un filtro DNS no cubre todo (redes sociales, buscadores, contenido en apps). Si CleanBrowsing cae, no se resuelve nada.
- El guardián usa coincidencia de texto: según la marca puede bloquear de más (por ejemplo la lista de apps si aparece PureLock) o de menos. Se ajusta en `GuardService.kt` > `forbidden()`.
- Un "dueño del dispositivo" real (la protección más fuerte) requiere configurar el teléfono con ADB desde una computadora; esta app no lo hace.
- El código compila contra la API de Android 33 en mi verificación, pero no lo probé en un dispositivo físico.

## iPhone
iOS no permite una app así. Usa Tiempo en pantalla con un código que ponga otra persona, Restricciones de contenido > Contenido web > Limitar sitios para adultos, y bloquea cambios de cuenta y borrado de apps.
