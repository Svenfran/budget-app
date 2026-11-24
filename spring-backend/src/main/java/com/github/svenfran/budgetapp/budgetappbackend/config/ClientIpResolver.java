package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.github.svenfran.budgetapp.budgetappbackend.helper.IpAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Ermittelt die Client-IP für das Login-Rate-Limit (DIV-64), Property {@code app.security.client-ip.header}:
 * <ul>
 *   <li>leer (dev/test): {@code request.getRemoteAddr()}, alle Forwarding-Header werden ignoriert;</li>
 *   <li>Header-Name (Prod: {@code X-Real-IP}, laut Railway-Doku von der Edge gesetzt): genau dieser eine Header.
 *       Fehlt er, kommt er mehrfach vor, enthält er eine Liste oder kein gültiges IP-Literal, wird
 *       {@code getRemoteAddr()} verwendet ({@code ipSource=remoteAddr}).</li>
 * </ul>
 * {@code X-Forwarded-For} wird nie ausgewertet; die Spring-weite {@code server.forward-headers-strategy} bleibt aus.
 * Dass die Edge einen vom Client mitgeschickten Header überschreibt, ist vor dem Modus {@code ENFORCE} in Prod
 * nachzuweisen (Plan DIV-64, Phase 4).
 */
@Component
public class ClientIpResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9-]+");

    /** Header-Name oder null (= nur remoteAddr). */
    private final String header;

    public ClientIpResolver(@Value("${app.security.client-ip.header:}") String header) {
        var trimmed = header == null ? "" : header.trim();
        if (!trimmed.isEmpty() && !HEADER_NAME.matcher(trimmed).matches()) {
            throw new IllegalStateException("app.security.client-ip.header must be empty or a single header name but was '" + header + "'");
        }
        this.header = trimmed.isEmpty() ? null : trimmed;
    }

    public ClientIp resolve(HttpServletRequest request) {
        if (header != null) {
            var values = Collections.list(request.getHeaders(header));
            if (values.size() == 1) {
                var fromHeader = IpAddresses.canonicalize(values.get(0));
                if (fromHeader.isPresent()) {
                    return new ClientIp(fromHeader.get(), ClientIp.Source.HEADER);
                }
            }
            // Wert selbst nicht loggen (Klartext-IP bzw. vom Client kontrollierter Inhalt)
            LOG.debug("Client-IP header {} unusable ({} value(s)), falling back to remoteAddr", header, values.size());
        }
        var remoteAddr = request.getRemoteAddr();
        return new ClientIp(IpAddresses.canonicalize(remoteAddr).orElse(remoteAddr), ClientIp.Source.REMOTE_ADDR);
    }
}
