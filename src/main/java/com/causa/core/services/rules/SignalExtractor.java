package com.causa.core.services.rules;

import java.util.List;

/**
 * Signal Extractor Interface.
 *
 * <p>Converts raw diagnostic context into normalized Signal objects
 * that can be evaluated by rules.
 *
 * @since 0.0.1
 */
public interface SignalExtractor {

    /**
     * Extract signals from diagnostic context.
     *
     * @param diagnosticContext raw diagnostic context (text format)
     * @return list of normalized signals
     */
    List<Signal> extractSignals(String diagnosticContext);
}
