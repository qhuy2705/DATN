package com.PrimeCare.PrimeCare.shared.init;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitDatabase implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String @NonNull ... args) throws Exception {
        System.out.println("Init database!!!");
        long countUser = userRepository.count();
//        if (countUser == 0){
//            User admin = User.builder()
//                    .email("admin@gmail.com")
//                    .status(UserStatus.ACTIVE)
//                    .role(UserRole.SYSTEM_ADMIN)
//                    .passwordHash(passwordEncoder.encode("admin"))
//                    .phone("0867423357")
//                    .build();
//            userRepository.save(admin);
//
//            User staff = User.builder()
//                             .email("staff1@gmail.com")
//                             .status(UserStatus.ACTIVE)
//                             .role(UserRole.STAFF)
//                             .passwordHash(passwordEncoder.encode("staff1"))
//                             .phone("01223184943")
//                             .build();
//            userRepository.save(staff);
//
//            User doctor = User.builder()
//                             .email("doctor1@gmail.com")
//                             .status(UserStatus.ACTIVE)
//                             .role(UserRole.DOCTOR)
//                             .passwordHash(passwordEncoder.encode("doctor1"))
//                             .phone("01223184787")
//                             .build();
//            userRepository.save(doctor);
//
//            User cashier = User.builder()
//                              .email("cashier1@gmail.com")
//                              .status(UserStatus.ACTIVE)
//                              .role(UserRole.CASHIER)
//                              .passwordHash(passwordEncoder.encode("cashier1"))
//                              .phone("01223184700")
//                              .build();
//            userRepository.save(cashier);
//        }else{
//            System.out.println("Database already initialized");
//        }
    }
}
