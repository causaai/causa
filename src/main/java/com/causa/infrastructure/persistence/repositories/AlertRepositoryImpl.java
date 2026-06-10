package com.causa.infrastructure.persistence.repositories;

import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Alert Repository Implementation
 *
 * <p>Panache-based implementation of the AlertRepository port.
 * <p>Uses Panache Repository pattern for cleaner, type-safe queries.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertRepositoryImpl implements AlertRepository {

    @Override
    @Transactional
    public Alert save(Alert alert) {
        AlertEntity entity = AlertEntityMapper.toEntity(alert);
        entity.persist();
        return alert;
    }

    @Override
    public Optional<Alert> findById(String alertId) {
        return AlertEntity.findByIdOptional(alertId)
            .map(entity -> AlertEntityMapper.toDomain((AlertEntity) entity));
    }

    @Override
    public List<Alert> findByContainerName(String containerName) {
        return AlertEntity.<AlertEntity>find(
                AlertEntity.Fields.CONTAINER_NAME,
                Sort.descending(AlertEntity.Fields.TIMESTAMP),
                containerName)
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
    }

    @Override
    public List<Alert> findAll() {
        return AlertEntity.<AlertEntity>listAll(Sort.descending(AlertEntity.Fields.TIMESTAMP))
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void updateHasDiagnostics(String alertId, boolean hasDiagnostics) {
        AlertEntity.update(
            AlertEntity.Fields.HAS_DIAGNOSTICS + " = ?1 where " + AlertEntity.Fields.ALERT_ID + " = ?2",
            hasDiagnostics,
            alertId);
    }
}
