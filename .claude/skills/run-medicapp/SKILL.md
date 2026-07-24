---
name: run-medicapp
description: Lanza MedicApp en el emulador Android y la recorre para comprobar un cambio de verdad en la app (no solo con tests). Úsalo cuando se pida arrancar, instalar, probar o hacer capturas de la app, o confirmar que algo funciona en el emulador.
---

# Ejecutar MedicApp en el emulador

Receta verificada de punta a punta. Los tres puntos que hacen perder tiempo si no se
conocen están marcados con ⚠.

## Entorno

- **`adb` no está en el PATH.** Hay que usar rutas absolutas. La raíz del SDK está en
  `local.properties` (`sdk.dir`), hoy `C:\Users\Propietario\AppData\Local\Android\Sdk`.
- El shell es **PowerShell 5.1**: no existen `&&` ni `||`. Encadena con `;` o `if ($?) { }`.
- AVD disponible: **`medicapp_avd`** (android-36, imagen `google_apis`). Esa imagen trae
  Google Play services, que es lo que Firebase Auth necesita; no hace falta `playstore`.
- Pantalla del AVD: **1080x2400**. Si tomas coordenadas de una captura reescalada,
  acuérdate de multiplicar por el factor correspondiente.

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$emu = "$sdk\emulator\emulator.exe"
```

## 1. Arrancar el emulador

```powershell
& $emu -avd medicapp_avd -no-snapshot-load    # lánzalo en segundo plano
```

Espera al arranque real, no a que aparezca la ventana:

```powershell
& $adb wait-for-device
$booted = "0"; $i = 0
while ($booted -ne "1" -and $i -lt 60) {
    Start-Sleep -Seconds 5
    $booted = (& $adb shell getprop sys.boot_completed) -replace '\s',''
    $i++
}
```

Suele tardar ~20 s.

## 2. Instalar

```powershell
cd D:\repositorios\medicapp
& .\gradlew.bat installDebug
```

La app es de uso personal y **se distribuye en debug a propósito**; no hay configuración de
firma para release. No propongas `assembleRelease` salvo que te lo pidan expresamente.

## 3. Permisos ⚠

Dos permisos cortan el recorrido en una instalación limpia:

**Notificaciones** — sale un diálogo del sistema al entrar en "Crear recordatorio". Acéptalo
tocándolo, o concédelo antes:

```powershell
& $adb shell pm grant com.medicapp android.permission.POST_NOTIFICATIONS
```

**⚠ Alarmas exactas** — este es el que engaña. Al pulsar "Guardar" en un recordatorio, la
app muestra "Permiso necesario … Actívalo en Ajustes y vuelve a guardar" y **no guarda**.
El diálogo solo ofrece ir a Ajustes, así que por adb hay que hacerlo con `appops`, no con
`pm grant`:

```powershell
& $adb shell appops set com.medicapp SCHEDULE_EXACT_ALARM allow
```

Concédelo **antes** de intentar guardar y te ahorras el rodeo.

## 4. Sesión

Todo lo interesante está detrás del login. Cuenta de test ya creada en Firebase:

- **qa.medicapp@example.com** / **Test1234**

Si necesitas partir de cero (`pm clear com.medicapp` borra la sesión y la caché de
Firestore), vuelve a entrar con esa cuenta: los datos se recuperan del servidor.

Crear cuentas nuevas escribe usuarios reales en el Firebase del proyecto. No lo hagas sin
pedir permiso.

## 5. Conducir la app

```powershell
& $adb shell am start -n com.medicapp/.MainActivity
& $adb shell input tap <x> <y>
& $adb shell input text "texto%scon%sespacios"    # %s = espacio
& $adb shell input keyevent 4                     # atrás / cerrar teclado
```

Capturar y mirar:

```powershell
& $adb shell screencap -p /sdcard/s.png
& $adb pull /sdcard/s.png "$env:TEMP\claude\s.png"
```

**Mira la captura siempre.** Una pantalla en blanco o un spinner es un fallo, no un éxito.

⚠ `input text` a veces **escribe el campo a medias** (pasó con una contraseña de 8
caracteres: entraron 4). Captura y comprueba antes de enviar el formulario; si falta texto,
limpia con `keyevent 123` + varios `keyevent 67` y reescribe.

## 6. Recorrido de humo

Toca las cuatro pantallas y las consultas a Firestore:

1. **Buscar** → escribe "ibuprofeno" (espera el debounce de 350 ms + red). Debe listar
   resultados con foto, traídos de la API de CIMA.
2. Abre una ficha → **Crear recordatorio** → 3 tomas, un motivo → Guardar.
3. Vuelve a **Crear recordatorio** del mismo medicamento → debe salir el aviso rojo de
   duplicado y "Guardar" deshabilitado.
4. Vuelve a **Buscar** → ese medicamento debe llevar el distintivo "Ya lo tienes guardado",
   y **solo** ese.
5. **Recordatorios** → abre la pauta → "Marcar tomada" en la Toma 1 → la cabecera debe
   pasar de 0/3 a 1/3 **sola**, sin recargar.
6. **Medicamentos** → la toma debe aparecer en el historial.

Comprueba que no hay errores de la app:

```powershell
& $adb logcat -d -v brief *:E | Select-String -Pattern "medicapp|Firestore"
```

## Cosas conocidas que NO son fallos tuyos

- **"Sin conexión · mostrando datos guardados"** aparece a veces en el historial aun con red.
  Es intermitente y previo: `isFromCache` se queda en `true` mientras una escritura recién
  hecha aún no está confirmada por el servidor, y la UI lo pinta como falta de conexión.
  Verificado en `develop`. No lo atribuyas al cambio que estés probando.
- Logcat escupe ruido de Google Play services (`CastAuth`, `ServiceEntitlement`, `ggdc`) sin
  relación con la app. Filtra por `medicapp`.
