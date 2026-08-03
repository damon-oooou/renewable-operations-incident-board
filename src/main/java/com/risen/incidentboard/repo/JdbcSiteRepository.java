package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.DbValues;
import com.risen.incidentboard.domain.Site;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcSiteRepository implements SiteRepository {

    private final JdbcClient jdbc;

    public JdbcSiteRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Site> findAll() {
        return jdbc.sql("SELECT id, name, region, technology, capacity FROM sites ORDER BY name")
                .query(RowMappers.SITE)
                .list();
    }

    @Override
    public void insert(Site site) {
        jdbc.sql("INSERT INTO sites (id, name, region, technology, capacity) "
                        + "VALUES (:id, :name, :region, :technology, :capacity)")
                .param("id", site.id())
                .param("name", site.name())
                .param("region", site.region())
                .param("technology", DbValues.toDb(site.technology()))
                .param("capacity", site.capacity())
                .update();
    }
}
