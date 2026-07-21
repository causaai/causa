package com.causa.api.dto.request;

import java.util.Map;

/**
 * Config Update Request DTO
 *
 * <p>Request body for {@code POST /api/v1/configs}.
 *
 * <p>Format: {@code {"configs": {"KEY": "value", ...}}}
 *
 * <p>Validation rules:
 * <ul>
 *   <li>All keys must be known (exist in ConfigConstants)</li>
 *   <li>All values must be non-blank</li>
 *   <li>Integer/double keys must have valid numeric values</li>
 * </ul>
 *
 * @param configs map of config key-value pairs to upsert
 * @since 0.0.1
 */
public record ConfigUpdateRequest(Map<String, String> configs) {}
