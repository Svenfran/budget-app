package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authentication and authorization of inbound STOMP frames (clientInboundChannel):
 * <ul>
 *     <li>CONNECT (and its STOMP 1.2 alias STOMP, which Spring treats the same) needs the native headers {@code Authorization: Bearer <jwt>} and {@code DeviceId}, validated with the
 *     same rules as REST ({@link TokenValidator}). The session principal's name is the <b>userId</b>, because
 *     {@code NotificationService} addresses users by id ({@code convertAndSendToUser(userId, …)}).</li>
 *     <li>SUBSCRIBE is only allowed for an authenticated session and only to the own user destinations
 *     ({@code /user/notification/<action>}) or the health broadcast.</li>
 *     <li>SEND is always rejected: there are no {@code @MessageMapping} handlers, and clients must not publish to
 *     broker destinations (e.g. a forged health status).</li>
 * </ul>
 * A rejected frame throws a {@link MessagingException}; Spring answers with a STOMP ERROR frame.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    static final String USER_NOTIFICATION_PREFIX = "/user/notification/";

    static final String HEALTH_DESTINATION = "/notification/health";

    private final TokenValidator tokenValidator;

    public WebSocketAuthInterceptor(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> verifySubscription(accessor);
            case SEND -> {
                LOG.warn("STOMP SEND rejected (session {})", accessor.getSessionId());
                throw new MessagingException("SEND is not allowed");
            }
            default -> {
                // UNSUBSCRIBE, DISCONNECT etc.: nothing to check
            }
        }
        return message;
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        String deviceId = accessor.getFirstNativeHeader("DeviceId");
        if (authHeader == null || !authHeader.startsWith(JwtService.BEARER_PREFIX)) {
            return reject(accessor, "missing or malformed Authorization header");
        }
        var userDetails = tokenValidator.validate(authHeader.substring(JwtService.BEARER_PREFIX.length()), deviceId);
        if (userDetails.isEmpty() || !(userDetails.get() instanceof User user)) {
            return reject(accessor, "invalid token");
        }
        return new UsernamePasswordAuthenticationToken(user.getId().toString(), null, user.getAuthorities());
    }

    private Principal reject(StompHeaderAccessor accessor, String reason) {
        // no token content in the log
        LOG.warn("STOMP CONNECT rejected (session {}): {}", accessor.getSessionId(), reason);
        throw new MessagingException("Unauthorized");
    }

    private void verifySubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        boolean allowed = accessor.getUser() != null
                && destination != null
                && (destination.equals(HEALTH_DESTINATION)
                    || (destination.startsWith(USER_NOTIFICATION_PREFIX)
                        && destination.length() > USER_NOTIFICATION_PREFIX.length()));
        if (!allowed) {
            LOG.warn("STOMP SUBSCRIBE rejected (session {}): {}", accessor.getSessionId(), destination);
            throw new MessagingException("Subscription not allowed");
        }
    }
}
