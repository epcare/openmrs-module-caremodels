package org.openmrs.module.caremodels.event;

import java.util.*;
import java.util.logging.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClinicalEventLoggingTest {

	@Test public void listenerFailuresMustNotLogPatientSourceOrExceptionDetails() {
        Logger logger = Logger.getLogger(ClinicalChangePublisher.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) { records.add(record); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        try {
            ClinicalChangePublisher publisher = new ClinicalChangePublisher();
            publisher.setListeners(Collections.singletonList(event -> { throw new IllegalStateException("sensitive-clinical-detail"); }));
            publisher.publishAfterCommit(new ClinicalChangeEvent(ClinicalChangeEvent.TriggerType.OBSERVATION_SAVED,
                "sensitive-patient", "sensitive-source", "concept", new Date()));
            assertEquals(1, records.size());
            LogRecord record = records.get(0);
            String output = record.getMessage() + Arrays.toString(record.getParameters());
            assertFalse(output.contains("sensitive-")); assertNull(record.getThrown());
        } finally { logger.removeHandler(handler); }
    }
}
