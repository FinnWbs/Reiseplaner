package de.travelmate.datasource;

import java.util.List;

public interface ActivityCandidateSource {
    List<ExternalActivityCandidate> fetch(String city);
}
