package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.Site;

import java.util.List;

public interface SiteRepository {

    /** All sites, including any with no current alerts. */
    List<Site> findAll();

    void insert(Site site);
}
