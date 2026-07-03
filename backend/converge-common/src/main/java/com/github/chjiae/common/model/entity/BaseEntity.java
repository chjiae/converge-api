package com.github.chjiae.common.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base entity with common audit fields.
 * Extend this class for all persistent entities.
 */
@Data
public abstract class BaseEntity implements Serializable {

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
