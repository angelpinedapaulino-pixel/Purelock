# PureLock 2.0 — bloqueo del DNS privado con Device Owner (Android 12 / Xiaomi MIUI)

**Qué hace:** fija el DNS privado `adult-filter-dns.cleanbrowsing.org` y le pide a Android que impida cambiarlo,
con los controles oficiales de administración (DevicePolicyManager). Para quitarlo hace falta: pedir desbloqueo → esperar 24 h →
PIN de tu persona de confianza → ventana de 10 min.

## Limitación técnica (léela antes de perder tiempo)
En Android 12 **ninguna app puede bloquear el DNS privado si no es "propietaria del dispositivo" (Device Owner)**.
Accesibilidad y Administrador normal solo "simulan" el bloqueo y son justo lo que Google Play Protect marca; por eso se eliminaron.
Device Owner **se activa una sola vez desde una computadora con ADB**, y exige:
- Que el teléfono **no tenga cuentas** (Mi, Google…) ni **usuarios extra** (Segundo espacio, Apps duales) en ese momento.
- En MIUI, activar **Depuración USB (ajustes de seguridad)** en Opciones de desarrollador. MIUI lo condiciona a iniciar sesión con Mi Account, lo que choca con el requisito anterior; es la parte más probable de dar problemas (no lo pude probar en tu modelo).
- **No exige restablecer de fábrica** en principio: se puede hacer en un teléfono ya configurado si se cumplen esos requisitos. Si Android lo rechaza y no puedes quitar la causa, la única salida sería un restablecimiento.

**No sé si tu MIUI respeta la restricción "no cambiar DNS privado" en su pantalla de Ajustes** (MIUI personaliza Ajustes). Por eso hay un segundo mecanismo: PureLock vigila el ajuste y lo restaura en segundos
(disparador por cambio) o como máximo en ~15 min (tarea periódica). Se prueba en el paso 5.

## Qué cambió respecto a build-14
Comprobé el repositorio: el código de GitHub era idéntico al zip anterior (solo difería el workflow).
- **Eliminado:** servicio de Accesibilidad, VPN local, permisos INTERNET / ACCESS_NETWORK_STATE / POST_NOTIFICATIONS, receptor de arranque anterior.
- **Permisos del APK:** solo `RECEIVE_BOOT_COMPLETED` (reaplicar tras reiniciar). Nada de contactos, SMS, llamadas, archivos, ubicación ni internet. Sin publicidad, analítica ni servidores. `allowBackup=false`.
- **Nuevo (todo API oficial):** DNS privado fijo; restricciones de Android (no cambiar DNS privado, no VPN, no restablecer redes, no instalar APK externos, no crear usuarios, no modo seguro, no restablecer de fábrica, no depuración USB); la app no se desinstala ni se le borran los datos (`setUserControlDisabledPackages`); Chrome con incógnito desactivado, DNS propio apagado y SafeSearch; navegadores alternativos (Firefox, Brave, Xiaomi Browser…) suspendidos. El panel **lee de vuelta** el estado real desde Android.
- **Firma:** build de **release con tu clave** (secretos de GitHub). El workflow se detiene si faltan, y también si el APK queda firmado con clave debug o pide permisos de más.

## 1. Crear tu clave de firma (una sola vez, en una computadora con Java)
```
keytool -genkeypair -v -keystore purelock-release.jks -alias purelock -keyalg RSA -keysize 4096 -validity 10000
```
Convierte el archivo a texto base64: Linux `base64 -w0 purelock-release.jks`; macOS `base64 -i purelock-release.jks`;
Windows PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("purelock-release.jks"))`.

En GitHub: **Settings > Secrets and variables > Actions > New repository secret**, crea 4:
`KEYSTORE_BASE64` (el texto), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `purelock`), `KEY_PASSWORD`.

**Cómo proteger la clave privada**
- Nunca la subas al repositorio (`.gitignore` ya excluye `*.jks`). Los secretos de GitHub no se pueden leer de vuelta.
- Guarda el `.jks` y las contraseñas en un gestor de contraseñas + una copia offline cifrada. Borra el texto base64 del portapapeles y de archivos.
- Activa verificación en dos pasos en GitHub y no aceptes colaboradores que no necesites.
- Si pierdes la clave no podrás actualizar la app instalada (sí podrás quitarla con el PIN y reinstalar). Si se filtra, genera otra y reinstala.
- Quien controle el repositorio y esta clave puede publicar una versión sin PIN. Para que la protección sea real, **deja el repositorio y los secretos a cargo de tu persona de confianza**.

## 2. Subir los cambios y compilar
Añade primero los 4 secretos (si no, el build falla a propósito). Luego, en una computadora:
```
git clone https://github.com/angelpinedapaulino-pixel/Purelock
cd Purelock
git am < /ruta/a/PureLock-fix.patch
git push origin main
```
Se lanza **Actions > Construir y publicar PureLock**. Al terminar (check verde) aparece el Release `build-N` con `PureLock.apk`.
Borra los Releases antiguos (build-1…14): son APK de depuración.

## 3. Comprobar que el APK corresponde al código
En el Release se listan el **commit**, el **SHA-256 del APK** y el **certificado de firma**.
En tu computadora: `sha256sum PureLock.apk` debe coincidir, y `gh attestation verify PureLock.apk -R angelpinedapaulino-pixel/Purelock`
confirma que salió de ese repositorio y commit (si falla la atestación el build sigue, pero entonces no hay esa prueba).

## 4. Instalar en el Xiaomi (Android 12, MIUI)
**A. Preparar**
1. Si hay un PureLock anterior instalado, quítalo antes (mismo paquete, otra firma: no se puede actualizar encima). Si estuvo activo, usa su desbloqueo.
2. Haz respaldo de tus datos.
3. Ajustes > Cuentas y sincronización: quita las cuentas (Google, Mi…). Elimina **Segundo espacio** y **Apps duales**.
4. Ajustes > Acerca del teléfono: toca 7 veces "Versión de MIUI". En Ajustes adicionales > Opciones de desarrollador activa **Depuración USB**, **Instalar mediante USB** y **Depuración USB (ajustes de seguridad)**.

**B. Instalar el APK** desde el Release (permite instalar desde tu navegador/gestor cuando Android lo pida). Si Play Protect vuelve a bloquearla, no lo esquives: copia el texto exacto del aviso y lo revisamos.

**C. Activar Device Owner** (computadora con [platform-tools](https://developer.android.com/tools/releases/platform-tools), teléfono por USB, acepta la huella de depuración):
```
adb devices
adb shell dpm set-device-owner com.purelock/.AdminReceiver
```
Debe decir `Success`. Errores típicos: *"already some accounts"* → faltan cuentas/usuarios por quitar; *"MANAGE_DEVICE_ADMINS"* → falta "Depuración USB (ajustes de seguridad)".
Después puedes volver a añadir tus cuentas.

**D. Configurar la app:** ábrela → tu persona de confianza escribe nombre, teléfono y PIN (6+ dígitos) → **Activar protección**.
MIUI: Ajustes > Aplicaciones > Administrar apps > PureLock → **Inicio automático** activado y Ahorro de batería **Sin restricciones** (para que la vigilancia no la mate MIUI).

**5. Prueba:** con la protección activa, intenta cambiar Ajustes > Conexión y uso compartido > DNS privado. O sale bloqueado, o lo cambias y PureLock lo restaura (el contador "cambios revertidos" sube).
Si lo ves cambiado más de ~15 min, avísame con el modelo exacto.

## 5. Permisos
| Permiso | Para qué | Pide al usuario |
|---|---|---|
| `RECEIVE_BOOT_COMPLETED` | reaplicar al reiniciar | Nada (permiso normal) |
| Device Owner (comando ADB) | fijar DNS y restricciones | Lo concedes tú una vez con el comando |

Notificaciones, VPN, Accesibilidad, internet, contactos, SMS, llamadas, archivos y ubicación: **no se piden**.
Los botones "Pedir ayuda" y "Llamar" solo abren tu app de mensajes/teléfono con el texto o número ya puesto.

## 6. Limitaciones (sin promesas falsas)
- Un borrado desde el **modo recovery**, otro teléfono o un servicio técnico lo evade. Si pierdes el PIN esa es la salida (pierdes los datos).
- Con la depuración USB bloqueada no podrás reparar la app por ADB mientras esté activa.
- Los navegadores/apps con DNS propio no listados siguen fuera de control. Un filtro DNS no cubre todo el contenido.
- No pude compilar con Gradle ni probar en un Xiaomi desde mi entorno: verifiqué que el Kotlin compila contra el SDK de Android, que los XML/YAML son válidos y que el flujo de la clave funciona.
