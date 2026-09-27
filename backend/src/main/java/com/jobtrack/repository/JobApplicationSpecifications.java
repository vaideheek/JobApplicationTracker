package com.jobtrack.repository;

import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JobApplicationSpecifications {

    private JobApplicationSpecifications() {
        // Utility class
    }

    public static Specification<JobApplication> withFilters(
            Long userId,
            String search,
            ApplicationStatus status,
            ApplicationPriority priority,
            LocalDate dateFrom,
            LocalDate dateTo,
            String documentState) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory current-user ownership
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            // 2. Case-insensitive search across companyName, jobTitle, location, source
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("companyName")), pattern),
                        cb.like(cb.lower(root.get("jobTitle")), pattern),
                        cb.like(cb.lower(root.get("location")), pattern),
                        cb.like(cb.lower(root.get("source")), pattern)
                );
                predicates.add(searchPredicate);
            }

            // 3. Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 4. Priority filter
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            // 5. Date From (inclusive: dateApplied >= dateFrom)
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateApplied"), dateFrom));
            }

            // 6. Date To (inclusive: dateApplied <= dateTo)
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dateApplied"), dateTo));
            }

            // 7. Document State filter
            if (documentState != null && !documentState.trim().isEmpty()) {
                String state = documentState.trim();
                if ("HAS_DOCUMENTS".equalsIgnoreCase(state)) {
                    Subquery<Long> subquery = query.subquery(Long.class);
                    var docRoot = subquery.from(ApplicationDocument.class);
                    subquery.select(cb.literal(1L))
                            .where(cb.equal(docRoot.get("jobApplication"), root));
                    predicates.add(cb.exists(subquery));
                } else if ("NO_DOCUMENTS".equalsIgnoreCase(state)) {
                    Subquery<Long> subquery = query.subquery(Long.class);
                    var docRoot = subquery.from(ApplicationDocument.class);
                    subquery.select(cb.literal(1L))
                            .where(cb.equal(docRoot.get("jobApplication"), root));
                    predicates.add(cb.not(cb.exists(subquery)));
                } else {
                    // Unsupported non-empty documentState must not match any application
                    predicates.add(cb.disjunction());
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
