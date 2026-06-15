package de.travelmate.datasource;

import java.util.List;

public interface ActivityProvider {
    List<ExternalActivityCandidate> fetch(String city);
}
