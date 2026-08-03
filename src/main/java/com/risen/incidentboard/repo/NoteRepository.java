package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.Note;

import java.util.List;

public interface NoteRepository {

    /** Oldest first. There is deliberately no update or delete method. */
    List<Note> findByAlertId(String alertId);

    Note insert(Note note);
}
