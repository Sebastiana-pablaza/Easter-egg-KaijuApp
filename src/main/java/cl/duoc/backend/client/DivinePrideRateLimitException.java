
package cl.duoc.backend.client;



public class DivinePrideRateLimitException extends RuntimeException {

    private final long esperaSegundos;

    public DivinePrideRateLimitException(long esperaSegundos) {
        super("Divine Pride ha limitado temporalmente las solicitudes");
        this.esperaSegundos = esperaSegundos;
    }

    public long getEsperaSegundos() {
        return esperaSegundos;
    }
}
