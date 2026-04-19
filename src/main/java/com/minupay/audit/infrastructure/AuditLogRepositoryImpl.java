package com.minupay.audit.infrastructure;

import com.minupay.audit.domain.AuditLog;
import com.minupay.audit.domain.AuditLogPage;
import com.minupay.audit.domain.AuditLogRepository;
import com.minupay.audit.domain.AuditLogSearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public boolean saveIfAbsent(AuditLog auditLog) {
        try {
            mongoRepository.save(AuditLogDocument.from(auditLog));
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Audit log already exists eventId={}", auditLog.getEventId());
            return false;
        }
    }

    @Override
    public Optional<AuditLog> findById(String id) {
        return mongoRepository.findById(id).map(AuditLogDocument::toDomain);
    }

    @Override
    public AuditLogPage search(AuditLogSearchCriteria criteria, int page, int size) {
        Query query = new Query();
        applyFilters(query, criteria);
        long total = mongoTemplate.count(query, AuditLogDocument.class);

        query.with(Sort.by(Sort.Direction.DESC, "recordedAt"));
        query.skip((long) page * size);
        query.limit(size);
        List<AuditLog> content = mongoTemplate.find(query, AuditLogDocument.class).stream()
                .map(AuditLogDocument::toDomain)
                .toList();
        return new AuditLogPage(content, page, size, total);
    }

    private void applyFilters(Query query, AuditLogSearchCriteria criteria) {
        if (StringUtils.hasText(criteria.eventType())) {
            query.addCriteria(Criteria.where("eventType").is(criteria.eventType()));
        }
        if (StringUtils.hasText(criteria.aggregateType())) {
            query.addCriteria(Criteria.where("aggregateType").is(criteria.aggregateType()));
        }
        if (StringUtils.hasText(criteria.aggregateId())) {
            query.addCriteria(Criteria.where("aggregateId").is(criteria.aggregateId()));
        }
        if (StringUtils.hasText(criteria.traceId())) {
            query.addCriteria(Criteria.where("traceId").is(criteria.traceId()));
        }
        if (criteria.occurredFrom() != null || criteria.occurredTo() != null) {
            Criteria range = Criteria.where("occurredAt");
            if (criteria.occurredFrom() != null) range = range.gte(criteria.occurredFrom());
            if (criteria.occurredTo() != null) range = range.lt(criteria.occurredTo());
            query.addCriteria(range);
        }
    }
}
