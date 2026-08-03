package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.DbValues;
import com.risen.incidentboard.domain.Note;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Append-only by construction: this class exposes an insert and two reads, and
 * nothing else. The absence of an update method is the point -- the invariant is
 * defended here, at the API layer, and by the database role, rather than only in
 * the UI.
 */
@Repository
public class JdbcNoteRepository implements NoteRepository {

    private final JdbcClient jdbc;

    public JdbcNoteRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Note> findByAlertId(String alertId) {
        // id breaks ties when a status change and its note share a second.
        return jdbc.sql("SELECT id, alert_id, body, author, created_at FROM notes "
                        + "WHERE alert_id = :alertId ORDER BY created_at ASC, id ASC")
                .param("alertId", alertId)
                .query(RowMappers.NOTE)
                .list();
    }

    @Override
    public Note insert(Note note) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO notes (alert_id, body, author, created_at) "
                        + "VALUES (:alertId, :body, :author, :createdAt)")
                .param("alertId", note.alertId())
                .param("body", note.body())
                .param("author", note.author())
                .param("createdAt", DbValues.toDb(note.createdAt()))
                .update(keys);

        Number key = keys.getKey();
        return key == null ? note : note.withId(key.longValue());
    }
}
