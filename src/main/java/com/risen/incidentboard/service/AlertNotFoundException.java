package com.risen.incidentboard.service;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String id) {
        super("No alert with id " + id);
    }
}
