package com.mesosphere.dcos.kafka.executor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

/**
 * This class tests the Main class.
 */
public class MainTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testMain() throws Exception {
        exit.expectSystemExitWithStatus(0);
        Main.start(new MockExecutorDriverFactory());
    }
}
