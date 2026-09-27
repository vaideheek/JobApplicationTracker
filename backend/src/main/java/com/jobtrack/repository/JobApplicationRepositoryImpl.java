package com.jobtrack.repository;

import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.criteria.JpaOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class JobApplicationRepositoryImpl implements JobApplicationRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<JobApplication> findWithFilters(
            String search,
            ApplicationStatus status,
            ApplicationPriority priority,
            LocalDate dateFrom,
            LocalDate dateTo,
            String documentState,
            Long userId,
            Pageable pageable) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        Specification<JobApplication> spec = JobApplicationSpecifications.withFilters(
                userId, search, status, priority, dateFrom, dateTo, documentState);

        // 1. Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<JobApplication> countRoot = countQuery.from(JobApplication.class);
        Predicate countPredicate = spec.toPredicate(countRoot, countQuery, cb);
        countQuery.select(cb.count(countRoot));
        if (countPredicate != null) {
            countQuery.where(countPredicate);
        }
        long total = em.createQuery(countQuery).getSingleResult();

        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }

        // 2. Data query
        CriteriaQuery<JobApplication> dataQuery = cb.createQuery(JobApplication.class);
        Root<JobApplication> root = dataQuery.from(JobApplication.class);
        Predicate dataPredicate = spec.toPredicate(root, dataQuery, cb);
        if (dataPredicate != null) {
            dataQuery.where(dataPredicate);
        }

        // Apply sort orders from pageable, supporting nullPrecedence
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order sortOrder : pageable.getSort()) {
                Expression<?> expr = root.get(sortOrder.getProperty());
                Order order = sortOrder.isAscending() ? cb.asc(expr) : cb.desc(expr);

                if (sortOrder.getNullHandling() == Sort.NullHandling.NULLS_FIRST) {
                    if (order instanceof JpaOrder jpaOrder) {
                        order = jpaOrder.nullPrecedence(NullPrecedence.FIRST);
                    }
                } else if (sortOrder.getNullHandling() == Sort.NullHandling.NULLS_LAST) {
                    if (order instanceof JpaOrder jpaOrder) {
                        order = jpaOrder.nullPrecedence(NullPrecedence.LAST);
                    }
                }
                orders.add(order);
            }
            dataQuery.orderBy(orders);
        }

        TypedQuery<JobApplication> typedQuery = em.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<JobApplication> content = typedQuery.getResultList();

        return new PageImpl<>(content, pageable, total);
    }
}
