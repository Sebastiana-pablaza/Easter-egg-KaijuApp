
package cl.duoc.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

import java.net.SocketTimeoutException;
import java.util.Map;

@Component
public class DivinePrideClient {

    private final RestClient restClient;
    private final String apiKey;

    public DivinePrideClient(
            @Value("${divine-pride.api-key}") String apiKey
    ) {
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .baseUrl("https://www.divine-pride.net")
                .requestFactory(factory)
                .build();
    }

    public Map<String, Object> obtenerMonstruo(int id) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/database/Monster/{id}")
                            .queryParam("apiKey", apiKey)
                            .build(id))
                    .header("Accept-Language", "en")
                    .header("x-server", "kROM")
                    .retrieve()
                    .body(Map.class);

        } catch (RestClientResponseException e) {

            int codigo = e.getStatusCode().value();

            if (codigo == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Monstruo no encontrado"
                );
            }

            if (codigo == 429) {
                String retryAfter = e.getResponseHeaders() != null
                        ? e.getResponseHeaders().getFirst("Retry-After")
                        : null;

                long esperaSegundos = calcularEspera(retryAfter);

                throw new DivinePrideRateLimitException(esperaSegundos);
                }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Error al consultar el servicio externo"
            );

        } catch (ResourceAccessException e) {

            Throwable causa = e;
            while (causa != null) {
                if (causa instanceof SocketTimeoutException) {
                    throw new ResponseStatusException(
                            HttpStatus.GATEWAY_TIMEOUT,
                            "Tiempo de espera agotado"
                    );
                }
                causa = causa.getCause();
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con Divine Pride"
            );

        } catch (RestClientException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Error de comunicación con Divine Pride"
            );
        }
    }

    private long calcularEspera(String retryAfter) {

    // Si no existe la cabecera, esperamos 60 segundos.
    if (retryAfter == null || retryAfter.isBlank()) {
        return 60;
    }

    try {
        // Caso 1: Retry-After expresado en segundos.
        long segundos = Long.parseLong(retryAfter.trim());
        return Math.max(1, segundos);

    } catch (NumberFormatException e) {

        try {
            // Caso 2: Retry-After expresado como fecha HTTP.
            ZonedDateTime fecha = ZonedDateTime.parse(
                    retryAfter.trim(),
                    DateTimeFormatter.RFC_1123_DATE_TIME
            );

            long segundos = Duration.between(
                    ZonedDateTime.now(),
                    fecha
            ).getSeconds();

            return Math.max(1, segundos + 1);

        } catch (Exception ex) {
            // Cabecera desconocida o inválida.
            return 60;
        }
    }
}
}

