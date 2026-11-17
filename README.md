## 2. API externa seleccionada: Divine Pride

Se seleccionó **Divine pride**, una plataforma que proporciona información sobre Ragnarok Online y ofrece una API REST para consultar datos de monstruos, objetos y otros recursos del juego.

Sitio web: https://www.divine-pride.net/

La API permite consultar un monstruo mediante su identificador:

`GET https://www.divine-pride.net/api/database/Monster/{id}`

Por ejemplo, el monstruo Poring corresponde al ID `1002`.

La respuesta de Divine Pride contiene información detallada en formato JSON, incluyendo estadísticas, habilidades, objetos que puede entregar y ubicaciones donde aparece.

Sin embargo, para nuestra aplicación solamente necesitamos una parte de esos datos.

### Autenticación mediante API key

Para acceder a Divine Pride se obtuvo una API key desde una cuenta propia del servicio.

Esta clave identifica las solicitudes realizadas a la API y debe mantenerse privada.

Por razones de seguridad, decidimos **no incorporar la API key directamente en la aplicación Android**, sino almacenarla en el servidor AWS para que Spring Boot se encargue de utilizarla.

De esta manera, los usuarios pueden consultar información desde Kaiju sin recibir ni conocer la clave privada.

## 3. Configuración del servidor AWS EC2

Para alojar nuestro backend utilizamos una instancia de Amazon EC2 con Ubuntu.

En este servidor instalamos Java 21 y configuramos un proyecto Spring Boot con Maven.

El backend se encarga de recibir las solicitudes realizadas por Kaiju, consultar Divine Pride cuando sea necesario y devolver únicamente la información requerida por la aplicación.

### 3.1. Problema de la dirección IPv4 pública

Durante la configuración identificamos que la dirección IPv4 pública de una instancia EC2 puede cambiar después de detenerla y volver a iniciarla, si no se utiliza una dirección IP estática reservada.

Esto representa un problema para una aplicación móvil, porque no resulta conveniente modificar su URL de conexión cada vez que cambia la IP del servidor.

Para resolverlo, utilizamos un servicio de DNS dinámico.

### 3.2. Configuración de DuckDNS

Se utilizó **DuckDNS**, un servicio de DNS dinámico que permite asociar un nombre de dominio a una dirección IPv4 pública.

Registramos el siguiente subdominio:

`apisnecoarc.duckdns.org`

Este dominio apunta a la dirección pública de nuestra instancia AWS.

De esta manera, Kaiju utiliza un nombre de dominio estable en lugar de conectarse directamente a una dirección IP que podría cambiar.

### 3.3. Actualización automática de la dirección IP

Para evitar actualizar manualmente la dirección IPv4 en DuckDNS, configuramos un script de actualización en Ubuntu.

El procedimiento fue el siguiente:

1. Crear el subdominio en DuckDNS.
2. Asociarlo a la dirección IPv4 pública de la instancia EC2.
3. Configurar un script que consulta la dirección pública y actualiza el registro de DuckDNS.
4. Guardar el token de DuckDNS en un archivo de configuración protegido.
5. Crear un servicio y un temporizador de `systemd` para ejecutar automáticamente la actualización.

El temporizador fue configurado para realizar actualizaciones periódicas, aproximadamente cada cinco minutos, además de ejecutarse tras el arranque.

Finalmente, verificamos que la dirección resuelta mediante DuckDNS coincidiera con la IPv4 pública de nuestra instancia.

Con esta solución, si la dirección cambia, el sistema puede actualizar el dominio sin necesidad de modificar la configuración de la aplicación Android.

## 4. Desarrollo del backend con Spring Boot

Se desarrolló una API REST utilizando **Java 21 y Spring Boot**, con el propósito de actuar como intermediaria entre Kaiju y Divine Pride.

Se organizaron las responsabilidades utilizando distintas capas:

### Controller

`MonsterController` recibe las solicitudes HTTP realizadas desde Postman o la aplicación Android.

Se implementaron dos endpoints principales:

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/monster/test` | Devuelve la información del Poring para comprobar la conexión. |
| GET | `/api/monster/random` | Selecciona aleatoriamente un monstruo de una lista de IDs configurados. |

### Service

`MonsterService` contiene la lógica de selección de monstruos.

Entre sus responsabilidades se encuentran:

- Seleccionar aleatoriamente un ID de monstruo.
- Comprobar si sus datos están almacenados en caché.
- Solicitar información a Divine Pride cuando no existe en caché.
- Seleccionar los atributos necesarios para construir una respuesta simplificada.
- Controlar la frecuencia de solicitudes externas.

### Client

`DivinePrideClient` utiliza `RestClient` de Spring Boot para realizar solicitudes HTTP a Divine Pride.

Este componente obtiene la API key desde una variable de entorno configurada en Ubuntu y la incorpora a las solicitudes dirigidas al proveedor.

También se configuraron tiempos máximos de espera y manejo de errores de comunicación.

### Ejemplo de respuesta de nuestro backend

```json
{
  "id": 1002,
  "nombre": "Poring",
  "nivel": 1,
  "raza": "Plant",
  "elemento": "Water 1",
  "vida": 55,
  "spriteUrl": "https://static.divine-pride.net/images/mobs/png/1002.png"
}
```

Esta respuesta contiene solamente siete propiedades, en lugar de enviar el JSON completo de Divine Pride.

## 5. ¿Por qué utilizamos un backend intermediario?

Aunque Android permite consultar APIs REST directamente mediante Retrofit, decidimos desarrollar un backend propio principalmente por razones de seguridad y control.

**Protección de credenciales:** la API key de Divine Pride permanece en AWS y no se incorpora al APK ni al repositorio público.

**Reducción de información:** Spring Boot selecciona únicamente los atributos que necesita Kaiju, evitando enviar datos innecesarios.

**Caché:** los monstruos consultados se almacenan temporalmente en memoria. Si otro usuario solicita el mismo monstruo, el backend puede devolver sus datos sin repetir la consulta externa.

**Control de solicitudes:** Divine Pride establece restricciones sobre la frecuencia y simultaneidad de las consultas. Nuestro servicio incorpora un intervalo mínimo de 1,5 segundos entre solicitudes externas nuevas y evita ejecutarlas en paralelo dentro de la instancia del servicio. (la restricción de divine pride solicitaba 1 segundo, ya que según dicen, llegaban a tener al rededor de 300 peticiones por segundo)

**Manejo de errores:** se incorporaron mecanismos para controlar errores HTTP, tiempos de espera y respuestas `429 Too Many Requests`, considerando la cabecera `Retry-After` cuando está disponible.

Estas medidas permiten mantener el consumo de la API externa centralizado en nuestro servidor.

## 6. Configuración de HTTPS mediante Caddy

Inicialmente, el backend Spring Boot se encontraba disponible mediante HTTP a través del puerto `8080`.

Posteriormente se decidió configurar HTTPS para cifrar las comunicaciones entre la aplicación Android y nuestro servidor.

Para ello instalamos **Caddy**, un servidor web que funciona como proxy inverso.

Caddy recibe las peticiones HTTPS y las redirige hacia Spring Boot, que continúa escuchando en el puerto `8080`.

Se configuró el dominio `apisnecoarc.duckdns.org` y se obtuvo automáticamente un certificado TLS válido mediante Let's Encrypt.

La instalación quedó administrada mediante `systemd`, permitiendo que Caddy se inicie automáticamente con Ubuntu y gestione la renovación de certificados.

### Flujo de comunicación

```text
Usuario
   |
   v
Kaiju (Android)
   |
   | HTTPS
   v
apisnecoarc.duckdns.org
   |
   v
Caddy (puerto 443)
   |
   | HTTP local
   v
Spring Boot (puerto 8080)
   |
   | API key privada
   | HTTPS
   v
Divine Pride
   |
   v
Respuesta JSON
```

La conexión desde Android hasta Caddy utiliza HTTPS, y la comunicación entre Caddy y Spring Boot se realiza dentro de la misma instancia.

### Endpoint público para Kaiju

`GET https://apisnecoarc.duckdns.org/api/monster/random`

Este endpoint fue probado correctamente mediante Postman, recibiendo respuestas HTTP `200 OK` con información real de monstruos.

**Nota de infraestructura:** actualmente Spring Boot se inicia manualmente mediante un script que carga la API key desde un archivo protegido. El inicio automático del backend y la restricción definitiva del acceso público directo al puerto `8080` quedan como mejoras pendientes.
