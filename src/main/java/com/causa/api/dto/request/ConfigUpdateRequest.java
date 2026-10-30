package com.causa.api.dto.request;

import java.util.Map;

/**
 * Config Update Request DTO
 *
 * <p>Request body for {@code PUT /api/v1/configs/generic}.
 *
 * <p>Format: {@code {"configs": {"KEY": "value", ...}}}
 *
 * <p>Supports updating one or multiple keys in a single call.
 * Validation rules:
 * <ul>
 *   <li>All keys must be known (exist in ConfigConstants)</li>
 *   <li>All values must be non-blank</li>
 *   <li>Integer/double/boolean keys must have valid typed values</li>
 * </ul>
 *
 * @param configs map of config key-value pairs to upsert
 * @since 0.0.1
 */
public record ConfigUpdateRequest(Map<String, String> configs) {}
