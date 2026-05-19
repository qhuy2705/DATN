package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.modules.auth.repository.AccessTokenBlacklistRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.security.JwtService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageChannel channel;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(
                jwtService,
                userDetailsService,
                accessTokenBlacklistRepository,
                userRepository
        );
    }

    @Test
    void shouldRejectUnauthenticatedSubscribeWhenDestinationIsProtected() {
        Message<byte[]> message = subscribe("/topic/cashier/orders", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectPatientSubscribeWhenDestinationIsDoctorTopic() {
        stubAuthenticatedToken("patient-token", "101", "PATIENT");

        Message<byte[]> message = subscribe("/topic/doctor/20/encounters", "patient-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowDoctorSubscribeWhenDestinationIsOwnDoctorTopic() {
        stubAuthenticatedToken("doctor-token", "101", "DOCTOR");
        stubDoctorProfile(101L, 20L);

        Message<byte[]> message = subscribe("/topic/doctor/20/encounters", "doctor-token");

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void shouldRejectDoctorSubscribeWhenDestinationBelongsToAnotherDoctor() {
        stubAuthenticatedToken("doctor-token", "101", "DOCTOR");
        stubDoctorProfile(101L, 20L);

        Message<byte[]> message = subscribe("/topic/doctor/999/encounters", "doctor-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowOperationsAdminSubscribeWhenDestinationIsAdminDashboardTopic() {
        stubAuthenticatedToken("admin-token", "501", "OPERATIONS_ADMIN");

        Message<byte[]> message = subscribe("/topic/appointments/summary", "admin-token");

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void shouldRejectSystemAdminSubscribeWhenDestinationIsAppointmentOperationsTopic() {
        stubAuthenticatedToken("system-token", "502", "SYSTEM_ADMIN");

        Message<byte[]> message = subscribe("/topic/appointments/summary", "system-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowStaffSubscribeWhenDestinationIsReceptionBranchTopic() {
        stubAuthenticatedToken("staff-token", "201", "STAFF");
        stubStaffProfile(201L, 1L);

        Message<byte[]> message = subscribe("/topic/branches/1/reception/queue", "staff-token");

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void shouldRejectOperationsAdminSubscribeWhenDestinationIsReceptionBranchTopic() {
        stubAuthenticatedToken("ops-reception-token", "501", "OPERATIONS_ADMIN");

        Message<byte[]> message = subscribe("/topic/branches/1/reception/queue", "ops-reception-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowCashierSubscribeWhenDestinationIsCashierBranchTopic() {
        stubAuthenticatedToken("cashier-token", "301", "CASHIER");
        stubStaffProfile(301L, 1L);

        Message<byte[]> message = subscribe("/topic/branches/1/cashier/orders", "cashier-token");

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void shouldRejectOperationsAdminSubscribeWhenDestinationIsCashierBranchTopic() {
        stubAuthenticatedToken("ops-cashier-token", "501", "OPERATIONS_ADMIN");

        Message<byte[]> message = subscribe("/topic/branches/1/cashier/orders", "ops-cashier-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowPharmacistSubscribeWhenDestinationIsPharmacyBranchTopic() {
        stubAuthenticatedToken("pharmacist-token", "401", "PHARMACIST");
        stubStaffProfile(401L, 1L);

        Message<byte[]> message = subscribe("/topic/branches/1/pharmacy/updates", "pharmacist-token");

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void shouldRejectSystemAdminSubscribeWhenDestinationIsPharmacyBranchTopic() {
        stubAuthenticatedToken("system-pharmacy-token", "502", "SYSTEM_ADMIN");

        Message<byte[]> message = subscribe("/topic/branches/1/pharmacy/updates", "system-pharmacy-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowServiceTechnicianSubscribeWhenDestinationIsServiceDeskBranchTopic() {
        stubAuthenticatedToken("technician-token", "601", "SERVICE_TECHNICIAN");
        stubStaffProfile(601L, 1L);

        Message<byte[]> message = subscribe("/topic/branches/1/service-desk/updates", "technician-token");

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void shouldRejectOperationsAdminSubscribeWhenDestinationIsServiceDeskBranchTopic() {
        stubAuthenticatedToken("ops-service-token", "501", "OPERATIONS_ADMIN");

        Message<byte[]> message = subscribe("/topic/branches/1/service-desk/updates", "ops-service-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectOperationsAdminSubscribeWhenDestinationIsDoctorTopic() {
        stubAuthenticatedToken("ops-doctor-token", "501", "OPERATIONS_ADMIN");

        Message<byte[]> message = subscribe("/topic/doctor/20/encounters", "ops-doctor-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectServiceTechnicianSubscribeWhenDestinationIsEncounterTopic() {
        stubAuthenticatedToken("technician-encounter-token", "601", "SERVICE_TECHNICIAN");

        Message<byte[]> message = subscribe("/topic/encounter/100", "technician-encounter-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void stubAuthenticatedToken(String token, String subject, String role) {
        Claims claims = Jwts.claims()
                .setSubject(subject)
                .setId("jti-" + token);
        claims.put("typ", "access");
        when(jwtService.parseAndValidate(token)).thenReturn(claims);
        when(accessTokenBlacklistRepository.existsByJti("jti-" + token)).thenReturn(false);
        when(userDetailsService.loadUserByUsername(subject))
                .thenReturn(org.springframework.security.core.userdetails.User
                        .withUsername(subject)
                        .password("n/a")
                        .roles(role)
                        .build());
    }

    private void stubDoctorProfile(Long userId, Long doctorProfileId) {
        lenient().when(userRepository.findWithBranchProfilesById(userId))
                .thenReturn(Optional.of(User.builder()
                        .id(userId)
                        .doctorProfile(DoctorProfile.builder().id(doctorProfileId).fullName("Dr " + doctorProfileId).build())
                        .build()));
    }

    private void stubStaffProfile(Long userId, Long branchId) {
        when(userRepository.findWithBranchProfilesById(userId))
                .thenReturn(Optional.of(User.builder()
                        .id(userId)
                        .staffProfile(StaffProfile.builder()
                                .id(userId)
                                .fullName("Staff " + userId)
                                .branch(Branch.builder().id(branchId).build())
                                .build())
                        .build()));
    }

    private Message<byte[]> subscribe(String destination, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (token != null) {
            accessor.setNativeHeader("Authorization", "Bearer " + token);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
