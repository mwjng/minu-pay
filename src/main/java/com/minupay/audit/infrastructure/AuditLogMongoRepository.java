package com.minupay.audit.infrastructure;

import org.springframework.data.mongodb.repository.MongoRepository;

interface AuditLogMongoRepository extends MongoRepository<AuditLogDocument, String> {
}
