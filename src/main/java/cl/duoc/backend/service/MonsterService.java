
package cl.duoc.backend.service;

import cl.duoc.backend.client.DivinePrideClient;
import cl.duoc.backend.client.DivinePrideRateLimitException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MonsterService {

    private final DivinePrideClient divinePrideClient;

    // lista de mob
    private final List<Integer> monstruos = List.of(
            1002, //Poring
            1007, //Fabre
            1014,  //Spore
            1095,  //Andre
            20610,  //Valky reginleif
            1765,  //mami valky
            20611, //valky ingrid
            20222, //Heart hunter
            1388,  //Arc angeling
            1582, //Deviling
            1070, //kukre
            1492,  //Inca
            1986  //Tatacho para el yunis

    );

    // Caché de monstruos consultados.
    private final Map<Integer, Map<String, Object>> cache = new HashMap<>();

    // Intervalo mínimo entre consultas externas: 1,5 segundos.
    private static final long INTERVALO_MINIMO_MS = 1500;

    // Momento de la última consulta externa.
    private long ultimaConsultaNanos = 0;

    // Momento hasta el que Divine Pride solicita esperar.
    private long bloqueoHastaNanos = 0;

    public MonsterService(DivinePrideClient divinePrideClient) {
        this.divinePrideClient = divinePrideClient;
    }

    // Obtiene un monstruo aleatorio de nuestra lista.
    public Map<String, Object> obtenerMonstruoAleatorio() {

        int posicion = ThreadLocalRandom.current()
                .nextInt(monstruos.size());

        int id = monstruos.get(posicion);

        return obtenerMonstruo(id);
    }

    // Endpoint de prueba: siempre obtiene Poring.
    public Map<String, Object> obtenerPoring() {
        return obtenerMonstruo(1002);
    }

    // Método compartido para consultar y almacenar monstruos.
    private synchronized Map<String, Object> obtenerMonstruo(int id) {

        // 1. Si está en caché, devolvemos la respuesta guardada.
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        // 2. Comprobamos si Divine Pride solicitó una pausa.
        if (bloqueoHastaNanos != 0
                && System.nanoTime() - bloqueoHastaNanos < 0) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Divine Pride está temporalmente limitado. Intenta más tarde."
            );
        }

        // 3. Respetamos el intervalo mínimo de 1,5 segundos.
        if (ultimaConsultaNanos != 0) {

            long transcurridoMs =
                    (System.nanoTime() - ultimaConsultaNanos) / 1_000_000;

            long esperaMs = INTERVALO_MINIMO_MS - transcurridoMs;

            if (esperaMs > 0) {
                try {
                    Thread.sleep(esperaMs);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Solicitud interrumpida"
                    );
                }
            }
        }

        // 4. Registramos el inicio de la consulta externa.
        ultimaConsultaNanos = System.nanoTime();

        // 5. Consultamos Divine Pride.
        Map<String, Object> datos;

        try {
            datos = divinePrideClient.obtenerMonstruo(id);

        } catch (DivinePrideRateLimitException e) {

            // Convertimos Retry-After de segundos a nanosegundos.
            long esperaNanos = Duration
                    .ofSeconds(e.getEsperaSegundos())
                    .toNanos();

            // Guardamos hasta cuándo debemos evitar nuevas consultas.
            bloqueoHastaNanos = System.nanoTime() + esperaNanos;

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Divine Pride ha limitado las consultas. Intenta más tarde."
            );
        }

        // 6. Validamos que Divine Pride devolvió información.
        if (datos == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Divine Pride no devolvió información"
            );
        }

        // 7. Construimos un JSON reducido para Kaiju.
        Map<String, Object> resultado = new HashMap<>();

        resultado.put("id", datos.get("id"));
        resultado.put("nombre", datos.get("name"));
        resultado.put("nivel", datos.get("level"));
        resultado.put("raza", datos.get("race"));
        resultado.put("elemento", datos.get("element"));
        resultado.put("vida", datos.get("health"));
        resultado.put("spriteUrl", datos.get("spriteUrl"));

        // 8. Guardamos el monstruo en caché.
        cache.put(id, resultado);

        return resultado;
    }
}


