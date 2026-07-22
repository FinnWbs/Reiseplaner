package de.travelmate.datasource;

import java.util.List;

/**
 * Kept as a compatibility alias while providers are split into candidate sources and enrichers.
 */
@Deprecated(forRemoval = false)
public interface ActivityProvider extends ActivityCandidateSource {
    @Override
    List<ExternalActivityCandidate> fetch(String city);
}
