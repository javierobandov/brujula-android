# Configuración gratuita de Firebase para Brújula

Este documento describe la configuración necesaria para activar la sincronización cloud. La app sigue funcionando localmente si Firebase no está configurado.

## 1. Crear el proyecto

1. Crear un proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Mantenerlo en el plan Spark gratuito.
3. Registrar una aplicación Android con el paquete `cl.brujula.app`.
4. Activar **Authentication → Sign-in method → Email/Password**.
5. Crear la base **Firestore** en modo producción.
6. Publicar las reglas de `firestore.rules`.

Las reglas limitan cada usuario a su ruta `users/{uid}/events`, impiden borrar desde la app y exigen que el fingerprint coincida con el ID del documento.

## 2. Configurar la compilación

La app recibe estos valores como propiedades Gradle o variables de entorno:

- `BRUJULA_FIREBASE_API_KEY`
- `BRUJULA_FIREBASE_APP_ID`
- `BRUJULA_FIREBASE_PROJECT_ID`

No guardar contraseñas bancarias, contraseñas de Firebase, tokens ni secretos en el repositorio. Estos tres valores son identificadores de configuración de Firebase; aun así, la seguridad depende de Authentication y de las reglas de Firestore.

## 3. Primera prueba

1. Instalar una build configurada.
2. Abrir **Configurar cuenta cloud** y crear una cuenta de prueba.
3. Registrar algunos gastos locales.
4. Iniciar **Sincronizar con la nube**.
5. Instalar la misma build en un segundo teléfono.
6. Iniciar sesión con la misma cuenta.
7. Sincronizar y comprobar que se recuperan los movimientos sin duplicarlos.

Si Firebase falla o se alcanza una cuota, los datos locales no se eliminan y siguen disponibles mediante CSV y JSON.
