package com.github.svenfran.budgetapp.budgetappbackend.config;

/**
 * Client-IP eines Requests (kanonisches Literal) und ihre Quelle (DIV-64), ermittelt von {@link ClientIpResolver}.
 * Wird nie im Klartext geloggt, nur als Pseudonym.
 */
public record ClientIp(String address, Source source) {

    public enum Source {
        /** Aus dem konfigurierten Header der vertrauenswürdigen Edge (Prod: {@code X-Real-IP}). */
        HEADER("header"),
        /** {@code request.getRemoteAddr()} – ohne konfigurierten Header oder als Fallback bei ungültigem Header. */
        REMOTE_ADDR("remoteAddr");

        private final String logValue;

        Source(String logValue) {
            this.logValue = logValue;
        }

        /** Wert im Log-Feld {@code ipSource}. */
        public String logValue() {
            return logValue;
        }
    }
}
