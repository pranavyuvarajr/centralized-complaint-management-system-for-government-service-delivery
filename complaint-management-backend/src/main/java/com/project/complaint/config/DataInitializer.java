package com.project.complaint.config;

import com.project.complaint.entity.*;
import com.project.complaint.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedCategories();
        Map<String, Department> departments = seedDepartments();
        seedUsersAndComplaints(departments);
    }

    private void seedCategories() {
        String[][] categories = {
            {"Water Supply", "Issues related to piped water supply and quality"},
            {"Electricity", "Power outages, faulty meters, and connection issues"},
            {"Roads and Infrastructure", "Potholes, damaged roads, and public infrastructure"},
            {"Sanitation", "Sewage, public toilets, and cleanliness issues"},
            {"Waste Management", "Garbage collection and disposal"},
            {"Public Transport", "Bus routes, stops, and transport services"},
            {"Street Lighting", "Non-functional or missing street lights"},
            {"Drainage", "Blocked or overflowing drains"},
            {"Public Health Services", "Issues with local health services and facilities"},
            {"Government Office Services", "Delays or issues at government offices"},
            {"Documentation Services", "Certificates, records, and documentation delays"},
            {"Public Safety", "Safety hazards in public spaces"},
            {"Other", "Complaints that do not fit other categories"},
        };
        for (String[] c : categories) {
            if (!categoryRepository.existsByName(c[0])) {
                categoryRepository.save(ComplaintCategory.builder()
                        .name(c[0]).description(c[1]).active(true).build());
            }
        }
        log.info("Complaint categories seeded");
    }

    private Map<String, Department> seedDepartments() {
        String[][] departments = {
            {"Water Supply Department", "Manages piped water supply and distribution", "water.supply@cms.example", "1800-100-0001"},
            {"Electricity Department", "Manages power distribution and connections", "electricity@cms.example", "1800-100-0002"},
            {"Public Works Department", "Handles roads, drainage, and public infrastructure", "public.works@cms.example", "1800-100-0003"},
            {"Sanitation Department", "Handles sanitation and waste management", "sanitation@cms.example", "1800-100-0004"},
            {"Transport Department", "Manages public transport services", "transport@cms.example", "1800-100-0005"},
        };
        Map<String, Department> result = new HashMap<>();
        for (String[] d : departments) {
            Department dept = departmentRepository.findByName(d[0]).orElseGet(() ->
                    departmentRepository.save(Department.builder()
                            .name(d[0]).description(d[1]).contactEmail(d[2]).contactPhone(d[3]).active(true).build()));
            result.put(d[0], dept);
        }
        log.info("Departments seeded");
        return result;
    }

    private void seedUsersAndComplaints(Map<String, Department> departments) {
        if (!userRepository.existsByEmail("admin@example.com")) {
            userRepository.save(User.builder()
                    .name("System Administrator")
                    .email("admin@example.com")
                    .phone("9000000001")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .active(true)
                    .build());
            log.info("Created default admin: admin@example.com / admin123");
        }

        User waterOfficial = userRepository.findByEmail("official.water@example.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Ravi Kumar")
                        .email("official.water@example.com")
                        .phone("9000000002")
                        .password(passwordEncoder.encode("official123"))
                        .role(Role.OFFICIAL)
                        .department(departments.get("Water Supply Department"))
                        .active(true)
                        .build()));

        User publicWorksOfficial = userRepository.findByEmail("official.publicworks@example.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Anitha Raj")
                        .email("official.publicworks@example.com")
                        .phone("9000000003")
                        .password(passwordEncoder.encode("official123"))
                        .role(Role.OFFICIAL)
                        .department(departments.get("Public Works Department"))
                        .active(true)
                        .build()));

        User citizen1 = userRepository.findByEmail("citizen@example.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Priya Sharma")
                        .email("citizen@example.com")
                        .phone("9000000004")
                        .password(passwordEncoder.encode("citizen123"))
                        .role(Role.CITIZEN)
                        .active(true)
                        .build()));

        User citizen2 = userRepository.findByEmail("citizen2@example.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Arjun Nair")
                        .email("citizen2@example.com")
                        .phone("9000000005")
                        .password(passwordEncoder.encode("citizen123"))
                        .role(Role.CITIZEN)
                        .active(true)
                        .build()));

        log.info("Demo accounts ready - see README for the full list and passwords");

        if (complaintRepository.count() > 0) return;

        seedComplaint(citizen1, "Frequent water supply disruption in Anna Nagar",
                "Water supply has been irregular for the past two weeks, often unavailable for more than "
                        + "eight hours a day. This is affecting daily routines for several households on our street.",
                "Water Supply", "Anna Nagar, Block C", departments.get("Water Supply Department"), waterOfficial,
                ComplaintStatus.RESOLVED, Priority.HIGH, "Supply line valve was repaired and pressure restored on 3 September.");

        seedComplaint(citizen1, "Large pothole near school entrance",
                "There is a large pothole right outside the primary school gate that has caused two "
                        + "bicycle accidents this month. It needs urgent repair before the monsoon.",
                "Roads and Infrastructure", "MG Road, near Municipal School", departments.get("Public Works Department"),
                publicWorksOfficial, ComplaintStatus.IN_PROGRESS, Priority.URGENT, null);

        seedComplaint(citizen2, "Overflowing drain near market street",
                "The storm water drain behind the vegetable market has been overflowing for three days, "
                        + "creating an unhygienic environment for shoppers and shop owners.",
                "Drainage", "Market Street", departments.get("Public Works Department"), publicWorksOfficial,
                ComplaintStatus.ASSIGNED, Priority.MEDIUM, null);

        seedComplaint(citizen2, "New water connection application pending for two months",
                "I applied for a new domestic water connection two months ago and have not received any "
                        + "update. The reference slip number is on file with the ward office.",
                "Water Supply", "Gandhi Nagar, House No. 24", null, null,
                ComplaintStatus.SUBMITTED, Priority.MEDIUM, null);

        log.info("Sample complaints seeded");
    }

    private void seedComplaint(User citizen, String title, String description, String category, String location,
                                Department department, User official, ComplaintStatus finalStatus, Priority priority,
                                String resolutionInfo) {
        Complaint complaint = Complaint.builder()
                .user(citizen)
                .title(title)
                .description(description)
                .category(category)
                .location(location)
                .department(department)
                .assignedOfficial(official)
                .status(ComplaintStatus.SUBMITTED)
                .priority(priority)
                .build();
        complaint = complaintRepository.save(complaint);
        complaint.setComplaintNumber(String.format("CMP-%d-%06d", LocalDateTime.now().getYear(), complaint.getId()));

        historyRepository.save(ComplaintHistory.builder()
                .complaint(complaint).status(ComplaintStatus.SUBMITTED)
                .remarks("Complaint submitted by citizen").updatedBy(citizen.getName()).build());

        if (finalStatus != ComplaintStatus.SUBMITTED) {
            historyRepository.save(ComplaintHistory.builder()
                    .complaint(complaint).status(ComplaintStatus.UNDER_REVIEW)
                    .remarks("Complaint reviewed by administrator").updatedBy("System Administrator").build());
        }
        if (department != null && (finalStatus == ComplaintStatus.ASSIGNED || finalStatus == ComplaintStatus.IN_PROGRESS
                || finalStatus == ComplaintStatus.RESOLVED)) {
            complaint.setStatus(ComplaintStatus.ASSIGNED);
            historyRepository.save(ComplaintHistory.builder()
                    .complaint(complaint).status(ComplaintStatus.ASSIGNED)
                    .remarks("Assigned to " + department.getName() + (official != null ? " (" + official.getName() + ")" : ""))
                    .updatedBy("System Administrator").build());
        }
        if (official != null && (finalStatus == ComplaintStatus.IN_PROGRESS || finalStatus == ComplaintStatus.RESOLVED)) {
            complaint.setStatus(ComplaintStatus.IN_PROGRESS);
            historyRepository.save(ComplaintHistory.builder()
                    .complaint(complaint).status(ComplaintStatus.IN_PROGRESS)
                    .remarks("Investigation started").updatedBy(official.getName()).build());
        }
        if (finalStatus == ComplaintStatus.RESOLVED) {
            complaint.setStatus(ComplaintStatus.RESOLVED);
            complaint.setResolutionInfo(resolutionInfo);
            complaint.setResolvedAt(LocalDateTime.now().minusDays(1));
            historyRepository.save(ComplaintHistory.builder()
                    .complaint(complaint).status(ComplaintStatus.RESOLVED)
                    .remarks(resolutionInfo != null ? resolutionInfo : "Issue resolved")
                    .updatedBy(official != null ? official.getName() : "System Administrator").build());
        }

        complaintRepository.save(complaint);
    }
}
