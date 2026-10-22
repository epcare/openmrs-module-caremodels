package org.openmrs.module.caremodels.task;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.openmrs.api.context.Context;
import org.openmrs.scheduler.*;
import org.openmrs.module.caremodels.CareModelsActivator;
import static org.mockito.Mockito.*;

public class ReconciliationSchedulerTest {
	
	@Test
    public void moduleRestartMustResumeExistingEnabledTaskWithoutReplacingItsSettings() throws Exception {
        SchedulerService scheduler = mock(SchedulerService.class);
        TaskDefinition task = new TaskDefinition();
        task.setStartOnStartup(true);
        when(scheduler.getTaskByName("Care Models enrollment reconciliation")).thenReturn(task);
        try (MockedStatic<Context> context = mockStatic(Context.class)) {
            context.when(Context::getSchedulerService).thenReturn(scheduler);
            CareModelsActivator activator = new CareModelsActivator();
            activator.started();
            verify(scheduler).scheduleIfNotRunning(task);
            verify(scheduler, never()).saveTaskDefinition(any());
            activator.stopped();
            verify(scheduler).shutdownTask(task);
        }
    }
	
	@Test
    public void moduleStartMustRespectAdministrativelyDisabledTask() {
        SchedulerService scheduler = mock(SchedulerService.class);
        TaskDefinition task = new TaskDefinition();
        task.setStartOnStartup(false);
        when(scheduler.getTaskByName("Care Models enrollment reconciliation")).thenReturn(task);
        try (MockedStatic<Context> context = mockStatic(Context.class)) {
            context.when(Context::getSchedulerService).thenReturn(scheduler);
            new CareModelsActivator().started();
            verify(scheduler, never()).scheduleIfNotRunning(any());
        }
    }
}
