package com.plateforme.covoiturage;

import com.plateforme.covoiturage.scheduling.TripCompletionScheduler;
import com.plateforme.covoiturage.service.TrajetService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TripCompletionSchedulerTests {
    @Test void springActuallySchedulesRepeatedPassesWithoutTheJ1Scheduler() throws Exception {
        var service = mock(TrajetService.class);
        var passes = new CountDownLatch(2);
        when(service.terminerTrajetsArrives()).thenAnswer(call -> { passes.countDown(); return 0; });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-delay",
                    Map.of("driveconnect.scheduling.completion.delay-ms", "25")));
            context.registerBean(TrajetService.class, () -> service);
            context.register(TripCompletionScheduler.class);
            context.refresh();

            assertThat(passes.await(10, TimeUnit.SECONDS)).isTrue();
        }
        verify(service, atLeast(2)).terminerTrajetsArrives();
    }

    @Test void disabledSchedulerDoesNotCallTheService() {
        var service = mock(TrajetService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-disabled",
                    Map.of("driveconnect.scheduling.completion.enabled", "false")));
            context.registerBean(TrajetService.class, () -> service);
            context.register(TripCompletionScheduler.class);
            context.refresh();

            assertThat(context.getBeansOfType(TripCompletionScheduler.class)).isEmpty();
        }
        verifyNoInteractions(service);
    }
}
