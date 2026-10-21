/**
 * Diagnosis Validation Engine
 *
 * <p>Hybrid validation combining LLM evidence assertions with deterministic rules.
 *
 *
 * <h2>Validation Strategy</h2>
 * Two-pass validation to prevent LLM hallucinations:
 * <ol>
 *   <li><strong>LLM Evidence Assertions</strong> - LLM must cite data sources</li>
 *   <li><strong>Rule-Based Validation</strong> - Deterministic checks against metrics</li>
 * </ol>
 *
 *
 * @since 0.0.1
 */
package com.causa.validation;
