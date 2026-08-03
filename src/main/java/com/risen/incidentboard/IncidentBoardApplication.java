package com.risen.incidentboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@SpringBootApplication
public class IncidentBoardApplication {

    private static final Logger log = LoggerFactory.getLogger(IncidentBoardApplication.class);
    private static final Path DB_FILE = Path.of("incident-board.db");

    public static void main(String[] args) {
        // --reset acts here, before Spring builds the DataSource, so ordering is
        // explicit rather than dependent on bean creation order. Deleting the
        // file is the whole reset: the next startup finds an empty alerts table
        // and the seeder runs normally.
        if (Arrays.asList(args).contains("--reset")) {
            reset();
        }
        SpringApplication.run(IncidentBoardApplication.class, args);
    }

    private static void reset() {
        try {
            boolean existed = Files.deleteIfExists(DB_FILE);
            // SQLite may leave these alongside the database in WAL mode.
            Files.deleteIfExists(Path.of("incident-board.db-wal"));
            Files.deleteIfExists(Path.of("incident-board.db-shm"));
            log.info(existed
                    ? "--reset: deleted {}, database will be recreated and reseeded"
                    : "--reset: no existing database at {}, nothing to delete", DB_FILE);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "--reset could not delete " + DB_FILE.toAbsolutePath()
                            + ". Close anything holding the file and try again.", e);
        }
    }
}
