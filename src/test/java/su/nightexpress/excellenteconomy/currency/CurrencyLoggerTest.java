package su.nightexpress.excellenteconomy.currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;

import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class CurrencyLoggerTest {

    @TempDir Path directory;

    @Test
    void shutdownSignalsTheWriterBeforeWaitingForItsMonitor() throws Exception {
        Field timeZoneField = su.nightexpress.nightcore.util.TimeUtil.class.getDeclaredField("timeZone");
        timeZoneField.setAccessible(true);
        timeZoneField.set(null, java.util.TimeZone.getTimeZone("UTC"));
        CurrencyLogger logger = new CurrencyLogger(mock(EconomyPlugin.class), DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            directory.resolve("operations.log"), false, true);
        Field writerField = CurrencyLogger.class.getDeclaredField("writer");
        writerField.setAccessible(true);
        ((BufferedWriter) writerField.get(logger)).close();
        BufferedWriter writer = mock(BufferedWriter.class, RETURNS_SELF);
        writerField.set(logger, writer);
        Field runningField = CurrencyLogger.class.getDeclaredField("running");
        runningField.setAccessible(true);
        CountDownLatch flushing = new CountDownLatch(1);
        CountDownLatch releaseFlush = new CountDownLatch(1);
        doAnswer(call -> {
            flushing.countDown();
            assertTrue(releaseFlush.await(5, TimeUnit.SECONDS));
            return null;
        }).when(writer).flush();
        for (int i = 0; i < 100; i++) logger.addEntry(OperationContext.custom("Test"), "Entry " + i);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var writing = executor.submit(logger::write);
            assertTrue(flushing.await(3, TimeUnit.SECONDS));
            var stopping = executor.submit(logger::shutdown);
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                    while (runningField.getBoolean(logger)) Thread.sleep(1);
                });
            }
            finally {
                releaseFlush.countDown();
            }
            writing.get(3, TimeUnit.SECONDS);
            stopping.get(3, TimeUnit.SECONDS);
        }
        verify(writer, times(1)).flush();
        verify(writer).close();
    }
}
