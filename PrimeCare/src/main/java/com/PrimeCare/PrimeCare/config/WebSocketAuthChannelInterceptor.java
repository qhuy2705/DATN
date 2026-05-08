package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.AccessTokenBlacklistRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements org.springframework.messaging.support.ChannelInterceptor {

    private static final String SESSION_TOKEN_KEY = "ws.accessToken";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            String token = resolveToken(accessor, true);
            Authentication authentication = authenticate(token);
            if (authentication != null) {
                accessor.setUser(authentication);
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null && StringUtils.hasText(token)) {
                    sessionAttributes.put(SESSION_TOKEN_KEY, token);
                }
            }
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
            if (StompCommand.SUBSCRIBE.equals(command) && isPublicAvailabilityDestination(accessor.getDestination())) {
                return message;
            }

            String token = resolveToken(accessor, false);
            Authentication authentication = authenticate(token);

            if (authentication == null || !authentication.isAuthenticated()) {
                throw new AccessDeniedException("WebSocket authentication required");
            }

            accessor.setUser(authentication);
            authorize(authentication, accessor.getDestination(), command);
            return message;
        }

        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor, boolean allowAnonymousConnect) {
        String raw = firstNonBlank(
                accessor.getFirstNativeHeader("Authorization"),
                accessor.getFirstNativeHeader("authorization"),
                accessor.getFirstNativeHeader("access_token")
        );

        if (!StringUtils.hasText(raw)) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                Object stored = sessionAttributes.get(SESSION_TOKEN_KEY);
                if (stored instanceof String storedToken && StringUtils.hasText(storedToken)) {
                    raw = storedToken;
                }
            }
        }

        if (!StringUtils.hasText(raw)) {
            if (allowAnonymousConnect) {
                return null;
            }
            throw new AccessDeniedException("Missing WebSocket token");
        }

        return raw.startsWith("Bearer ") ? raw.substring(7).trim() : raw.trim();
    }

    private Authentication authenticate(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        try {
            Claims claims = jwtService.parseAndValidate(token);

            String tokenType = claims.get("typ", String.class);
            if (!"access".equals(tokenType)) {
                throw new AccessDeniedException("Only access token is allowed for WebSocket");
            }

            String jti = claims.getId();
            if (StringUtils.hasText(jti) && accessTokenBlacklistRepository.existsByJti(jti)) {
                throw new AccessDeniedException("Blacklisted token");
            }

            String subject = claims.getSubject();
            if (!StringUtils.hasText(subject)) {
                throw new AccessDeniedException("Invalid token subject");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(subject);
            return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        } catch (Exception ex) {
            log.warn("WebSocket auth failed: {}", ex.getMessage());
            throw new AccessDeniedException("Invalid WebSocket token");
        }
    }

    private boolean isPublicAvailabilityDestination(String destination) {
        return StringUtils.hasText(destination) && destination.startsWith("/topic/public/availability/");
    }

    private void authorize(Authentication authentication, String destination, StompCommand command) {
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Missing destination");
        }

        if (StompCommand.SEND.equals(command)) {
            if (!destination.startsWith("/app")) {
                throw new AccessDeniedException("Only /app destinations are allowed for SEND");
            }
            return;
        }

        if (destination.startsWith("/topic/branches/")) {
            authorizeBranchDestination(authentication, destination);
            return;
        }

        if (destination.startsWith("/topic/appointments/summary")) {
            requireAnyRole(authentication, Set.of("ROLE_DOCTOR", "ROLE_STAFF", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            return;
        }

        if (destination.startsWith("/topic/cashier/")) {
            requireAnyRole(authentication, Set.of("ROLE_CASHIER", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            return;
        }

        if (destination.startsWith("/topic/service-desk/")) {
            requireAnyRole(authentication, Set.of("ROLE_SERVICE_TECHNICIAN", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            return;
        }

        if (destination.startsWith("/topic/doctor/")) {
            requireAnyRole(authentication, Set.of("ROLE_DOCTOR", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            return;
        }

        if (destination.startsWith("/topic/encounter/")) {
            requireAnyRole(authentication, Set.of("ROLE_DOCTOR", "ROLE_STAFF", "ROLE_SERVICE_TECHNICIAN", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            return;
        }

        if (destination.startsWith("/topic/notifications/role/")) {
            String role = destination.substring("/topic/notifications/role/".length()).trim();
            if (!StringUtils.hasText(role)) {
                throw new AccessDeniedException("Role notification destination is invalid");
            }

            if (hasAnyRole(authentication, Set.of("ROLE_SYSTEM_ADMIN"))) {
                return;
            }

            String expectedAuthority = "ROLE_" + role;
            if (!hasAnyRole(authentication, Set.of(expectedAuthority))) {
                throw new AccessDeniedException("You do not have permission for this notification channel");
            }
            return;
        }

        if (destination.startsWith("/topic/notifications/user/")) {
            String userId = destination.substring("/topic/notifications/user/".length()).trim();
            if (!StringUtils.hasText(userId)) {
                throw new AccessDeniedException("User notification destination is invalid");
            }

            if (hasAnyRole(authentication, Set.of("ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"))) {
                return;
            }

            if (!userId.equals(authentication.getName())) {
                throw new AccessDeniedException("You do not have permission for this personal notification channel");
            }
            return;
        }

        throw new AccessDeniedException("Subscription not allowed for destination: " + destination);
    }

    private void authorizeBranchDestination(Authentication authentication, String destination) {
        BranchDestination branchDestination = parseBranchDestination(destination);
        String topicPath = branchDestination.topicPath();

        if (topicPath.startsWith("appointments/summary")) {
            requireAnyRole(authentication, Set.of("ROLE_DOCTOR", "ROLE_STAFF", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            requireBranchAccess(authentication, branchDestination.branchId());
            return;
        }

        if (topicPath.startsWith("reception/queue")) {
            requireAnyRole(authentication, Set.of("ROLE_STAFF", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            requireBranchAccess(authentication, branchDestination.branchId());
            return;
        }

        if (topicPath.startsWith("cashier/orders")) {
            requireAnyRole(authentication, Set.of("ROLE_CASHIER", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            requireBranchAccess(authentication, branchDestination.branchId());
            return;
        }

        if (topicPath.startsWith("service-desk/")) {
            requireAnyRole(authentication, Set.of("ROLE_SERVICE_TECHNICIAN", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            requireBranchAccess(authentication, branchDestination.branchId());
            return;
        }

        if (topicPath.startsWith("pharmacy/updates")) {
            requireAnyRole(authentication, Set.of("ROLE_PHARMACIST", "ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"));
            requireBranchAccess(authentication, branchDestination.branchId());
            return;
        }

        throw new AccessDeniedException("Subscription not allowed for destination: " + destination);
    }

    private BranchDestination parseBranchDestination(String destination) {
        String remainder = destination.substring("/topic/branches/".length());
        int slash = remainder.indexOf('/');
        if (slash <= 0 || slash == remainder.length() - 1) {
            throw new AccessDeniedException("Branch destination is invalid");
        }

        try {
            Long branchId = Long.valueOf(remainder.substring(0, slash));
            return new BranchDestination(branchId, remainder.substring(slash + 1));
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Branch destination is invalid");
        }
    }

    private void requireBranchAccess(Authentication authentication, Long branchId) {
        if (hasAnyRole(authentication, Set.of("ROLE_OPERATIONS_ADMIN", "ROLE_SYSTEM_ADMIN"))) {
            return;
        }

        Long userId = Long.valueOf(authentication.getName());
        User user = userRepository.findWithBranchProfilesById(userId)
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        Long userBranchId = null;
        if (user.getStaffProfile() != null && user.getStaffProfile().getBranch() != null) {
            userBranchId = user.getStaffProfile().getBranch().getId();
        } else if (user.getDoctorProfile() != null && user.getDoctorProfile().getBranch() != null) {
            userBranchId = user.getDoctorProfile().getBranch().getId();
        }

        if (!branchId.equals(userBranchId)) {
            throw new AccessDeniedException("You do not have permission for this branch destination");
        }
    }

    private void requireAnyRole(Authentication authentication, Set<String> allowedAuthorities) {
        if (!hasAnyRole(authentication, allowedAuthorities)) {
            throw new AccessDeniedException("You do not have permission for this destination");
        }
    }

    private boolean hasAnyRole(Authentication authentication, Set<String> allowedAuthorities) {
        return authentication.getAuthorities().stream()
                              .map(a -> a.getAuthority())
                              .anyMatch(allowedAuthorities::contains);
    }

    private record BranchDestination(Long branchId, String topicPath) {
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
