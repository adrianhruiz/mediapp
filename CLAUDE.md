# MedicApp

App Android nativa (Kotlin + Compose) para consultar medicamentos de la API pública de CIMA
(AEMPS), registrar cuáles se han tomado, marcar alergias y programar recordatorios de toma.
Los datos del usuario viven en Firebase (Auth + Firestore).

## Flujo de trabajo

El repo usa **git flow**, ya inicializado (`main` / `develop`, prefijo `feature/`).

Toda mejora empieza con su propia rama, nunca commiteando directo en `develop`:

```powershell
git flow feature start <nombre-descriptivo>
git flow feature finish <nombre-descriptivo>
```

**Una rama por asunto.** Si un trabajo mezcla cosas sin relación (una optimización de
consultas y utillaje del repo, por ejemplo), van separadas para poder revisarlas y
revertirlas por su cuenta.

Mensajes de commit **en español**, en imperativo, explicando **por qué** se hace el cambio y
no solo qué se toca. Los comentarios y el KDoc del código, también en español.

## Distribución: solo debug

La app es de uso personal y **no se publica**. Se instala la variante debug (`build-apk.ps1`
o `installDebug`). No hay configuración de firma para release.

No propongas activar R8, crear un keystore ni preparar la subida a Play salvo que se pida
expresamente: no aplica a este proyecto.

## Firestore: filtrar en el servidor

Regla aprendida a base de arreglarlo: **nunca leer una colección entera para responder algo
concreto.** Si hace falta un documento, se escucha ese documento; si hace falta un
subconjunto, se filtra en la consulta, no en el móvil.

Cada documento leído se paga y se sincroniza, así que un `whereEqualTo` de más en la consulta
ahorra lecturas, batería y memoria.

Antes de añadir un método al repositorio, mira si ya existe uno acotado que sirva.

## Estructura

- Sin framework de inyección: las dependencias se montan a mano en `di/AppContainer`, que
  cuelga de `MedicApp`.
- Los repositorios exponen `Flow`; los ViewModels los pasan a `StateFlow` con
  `stateIn(..., WhileSubscribed(5000), ...)`.
- Interfaz de repositorio + implementación Firestore + doble de test en `src/sharedTest`,
  que comparten los tests unitarios y los instrumentados. **Si cambias una interfaz de
  repositorio, actualiza también su Fake.**

## Comprobar el trabajo

```powershell
.\gradlew.bat testDebugUnitTest
```

Para verificarlo en la app de verdad y no solo con tests, hay un skill del proyecto con la
receta del emulador: `.claude/skills/run-medicapp/`.

## Fallo conocido

En el historial aparece a veces **"Sin conexión · mostrando datos guardados"** aun con red.
Es intermitente y previo: `isFromCache` sigue en `true` mientras una escritura recién hecha
no está confirmada por el servidor, y la UI lo pinta como falta de conexión. No lo atribuyas
al cambio que estés probando.
