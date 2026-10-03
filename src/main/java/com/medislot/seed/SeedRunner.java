package com.medislot.seed;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.medislot.doctor.Doctor;
import com.medislot.doctor.DoctorRepository;
import com.medislot.patient.Patient;
import com.medislot.patient.PatientRepository;
import com.medislot.search.ReindexService;
import com.medislot.user.Role;
import com.medislot.user.User;
import com.medislot.user.UserRepository;

/**
 * Dev/perf data: ~1k doctors and ~10k patients (all with password {@code Passw0rd1}), then a search reindex.
 * Run with {@code SPRING_PROFILES_ACTIVE=dev,seed}. Idempotent.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    static final int DOCTORS = 1_000;
    static final int PATIENTS = 10_000;
    private static final String[] FIRST = {"Aisha", "Rahim", "Nusrat", "Karim", "Farhana", "Tanvir", "Sadia", "Imran",
            "Mitu", "Sohel", "Lamia", "Arif", "Rima", "Jahid", "Tania", "Hasan", "Nadia", "Rafi", "Mou", "Shakib"};
    private static final String[] LAST = {"Ahmed", "Hossain", "Rahman", "Khan", "Akter", "Islam", "Chowdhury",
            "Uddin", "Sarker", "Begum", "Mahmud", "Siddique", "Talukder", "Mondal", "Haque"};
    private static final String[] SPECIALTIES = {"Cardiology", "Neurology", "Dermatology", "Pediatrics", "Orthopedics",
            "Oncology", "Psychiatry", "Radiology", "Nephrology", "Gastroenterology", "Endocrinology", "Urology"};

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final PasswordEncoder encoder;
    private final ObjectProvider<ReindexService> reindex;

    public SeedRunner(UserRepository users, DoctorRepository doctors, PatientRepository patients,
            PasswordEncoder encoder, ObjectProvider<ReindexService> reindex) {
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.encoder = encoder;
        this.reindex = reindex;
    }

    @Override
    public void run(String... args) {
        if (users.existsByEmailIgnoreCase("seed-doctor-1@seed.local")) {
            log.info("Seed data already present, skipping");
            return;
        }
        String hash = encoder.encode("Passw0rd1"); // one hash for everyone: bcrypt per row would take minutes
        for (int start = 1; start <= DOCTORS; start += 500) {
            List<User> batch = new ArrayList<>();
            for (int i = start; i < start + 500 && i <= DOCTORS; i++) {
                batch.add(new User("seed-doctor-" + i + "@seed.local", hash, Role.DOCTOR));
            }
            List<User> saved = users.saveAll(batch);
            List<Doctor> profiles = new ArrayList<>();
            for (int i = 0; i < saved.size(); i++) {
                int n = start + i;
                profiles.add(new Doctor(saved.get(i).getId(), "Dr " + name(n), SPECIALTIES[n % SPECIALTIES.length],
                        "Experienced " + SPECIALTIES[n % SPECIALTIES.length].toLowerCase() + " specialist",
                        LocalTime.of(9, 0), LocalTime.of(17, 0), 30));
            }
            doctors.saveAll(profiles);
        }
        for (int start = 1; start <= PATIENTS; start += 500) {
            List<User> batch = new ArrayList<>();
            for (int i = start; i < start + 500 && i <= PATIENTS; i++) {
                batch.add(new User("seed-patient-" + i + "@seed.local", hash, Role.PATIENT));
            }
            List<User> saved = users.saveAll(batch);
            List<Patient> profiles = new ArrayList<>();
            for (int i = 0; i < saved.size(); i++) {
                profiles.add(new Patient(saved.get(i).getId(), name(start + i), null, null, null));
            }
            patients.saveAll(profiles);
        }
        log.info("Seeded {} doctors and {} patients", DOCTORS, PATIENTS);
        reindex.ifAvailable(r -> {
            var result = r.reindex();
            log.info("Reindexed {} doctors and {} patients", result.doctors(), result.patients());
        });
    }

    private static String name(int n) {
        return FIRST[n % FIRST.length] + " " + LAST[(n / FIRST.length) % LAST.length] + " " + n;
    }
}
