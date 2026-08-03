package com.risen.incidentboard;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.repo.AlertRepository;
import com.risen.incidentboard.repo.NoteRepository;
import com.risen.incidentboard.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertServiceTest {

    private AlertRepository alerts;
    private NoteRepository notes;
    private AlertService service;

    private Alert alertWithStatus(AlertStatus status) {
        Site site = new Site("SITE-01", "Test Site", "NSW", Technology.SOLAR, "85 MW");
        return Alert.unanalysed("ALT-0001", site, Instant.parse("2026-08-03T00:00:00Z"),
                "inverter_fault", Severity.HIGH, status, "A description.");
    }

    @BeforeEach
    void setUp() {
        alerts = mock(AlertRepository.class);
        notes = mock(NoteRepository.class);
        service = new AlertService(alerts, notes);
        when(alerts.findById("ALT-0001"))
                .thenReturn(Optional.of(alertWithStatus(AlertStatus.ACKNOWLEDGED)));
        when(notes.insert(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void statusChangeWritesExactlyOneSystemNote() {
        service.changeStatus("ALT-0001", AlertStatus.INVESTIGATING);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(notes, times(1)).insert(captor.capture());
        Note note = captor.getValue();
        assertThat(note.author()).isEqualTo(Note.SYSTEM_AUTHOR);
        assertThat(note.body()).isEqualTo("Status changed: acknowledged -> investigating");
        verify(alerts).updateStatus("ALT-0001", AlertStatus.INVESTIGATING);
    }

    @Test
    void backwardTransitionsAreAllowed() {
        when(alerts.findById("ALT-0001"))
                .thenReturn(Optional.of(alertWithStatus(AlertStatus.RESOLVED)));
        service.changeStatus("ALT-0001", AlertStatus.INVESTIGATING);
        verify(alerts).updateStatus("ALT-0001", AlertStatus.INVESTIGATING);
    }

    @Test
    void aNoOpTransitionIsRejectedAndWritesNothing() {
        // Otherwise the append-only log accumulates "acknowledged -> acknowledged"
        // entries that can never be cleaned up.
        assertThatThrownBy(() -> service.changeStatus("ALT-0001", AlertStatus.ACKNOWLEDGED))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notes);
        verify(alerts, never()).updateStatus(any(), any());
    }

    @Test
    void emptyAndWhitespaceNotesAreRejected() {
        assertThatThrownBy(() -> service.addNote("ALT-0001", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addNote("ALT-0001", "   \n  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addNote("ALT-0001", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overLengthNotesAreRejectedAtTheBoundary() {
        service.addNote("ALT-0001", "x".repeat(2000));
        assertThatThrownBy(() -> service.addNote("ALT-0001", "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notesCanBeAddedToAClosedAlert() {
        when(alerts.findById("ALT-0001"))
                .thenReturn(Optional.of(alertWithStatus(AlertStatus.RESOLVED)));
        Note note = service.addNote("ALT-0001", "Vendor confirmed the part shipped.");
        assertThat(note.author()).isEqualTo(Note.OPERATOR_AUTHOR);
    }
}
