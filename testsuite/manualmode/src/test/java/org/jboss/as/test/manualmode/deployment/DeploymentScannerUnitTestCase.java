/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.repository.PathUtil;
import org.jboss.as.server.deployment.DeploymentUndeployHandler;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerUnitTestCase extends AbstractDeploymentUnitTestCase {

    private static final String JAR_ONE = "deployment-startup-one.jar";
    private static final String JAR_TWO = "deployment-startup-two.jar";
    private static final PathAddress DEPLOYMENT_ONE = PathAddress.pathAddress(DEPLOYMENT, JAR_ONE);
    private static final PathAddress DEPLOYMENT_TWO = PathAddress.pathAddress(DEPLOYMENT, JAR_TWO);
    private static final int TIMEOUT = TimeoutUtil.adjust(30000);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss,SSS");

    @Inject
    private ServerController container;

    private ModelControllerClient client;

    private static Path deployDir;

    @Before
    public void before() throws IOException {
        deployDir = Files.createTempDirectory("deployment-test-" + UUID.randomUUID());
        if (Files.exists(deployDir)) {
            PathUtil.deleteRecursively(deployDir);
        }
        Files.createDirectories(deployDir);
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(deployDir.toFile());
    }

    @Test
    public void testStartup() throws Exception {
        final Path oneDeployed = deployDir.resolve(JAR_ONE + ".deployed");
        final Path twoFailed = deployDir.resolve(JAR_TWO + ".failed");
        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();
            //set the logging to debug
            addDebugDeploymentLogger();
            try {
                final Path deploymentOne = deployDir.resolve(JAR_ONE);
                final Path deploymentTwo = deployDir.resolve(JAR_TWO);

                createDeployment(deploymentOne, "org.jboss.modules");
                createDeployment(deploymentTwo, "non.existing.dependency");
                addDeploymentScanner(0);
                try {
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while (!(exists(DEPLOYMENT_ONE) && exists(DEPLOYMENT_TWO)) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(100);
                    }
                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_ONE));
                    Assert.assertTrue(exists(DEPLOYMENT_TWO));
                    Assert.assertEquals("FAILED", deploymentState(DEPLOYMENT_TWO));

                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    // Restart ...
                    client.close();
                    container.stop();
                    container.start();
                    client = TestSuiteEnvironment.getModelControllerClient();

                    // Wait until started ...
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while (!isRunning() && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }

                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while (exists(DEPLOYMENT_TWO) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }
                    Assert.assertFalse("Deployment two shouldn't exist at " + TIME_FORMATTER.format(LocalDateTime.now()), exists(DEPLOYMENT_TWO));
                    ModelNode disableScanner = Util.getWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=deployment-scanner/scanner=testScanner"), "scan-interval", 300000);
                    ModelNode result = executeOperation(disableScanner);
                    assertEquals("Unexpected outcome of disabling the test deployment scanner: " + disableScanner, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());

                    final ModelNode undeployOp = Util.getEmptyOperation(DeploymentUndeployHandler.OPERATION_NAME, DEPLOYMENT_ONE.toModelNode());
                    result = executeOperation(undeployOp);
                    assertEquals("Unexpected outcome of undeploying deployment one: " + undeployOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("STOPPED", deploymentState(DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TIMEOUT;

                    while (Files.exists(oneDeployed) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }
                    Assert.assertFalse(Files.exists(oneDeployed));
                } finally {
                    removeDeploymentScanner();
                    removeDebugDeploymentLogger();
                }

            } finally {
                StreamUtils.safeClose(client);
            }
        } finally {
            container.stop();
        }
    }


    /**
     * https://bugzilla.redhat.com/show_bug.cgi?id=1291710
     *
     * When FS deployment failed during boot, persistent deployments were removed too.
     */
    @Test
    public void testFailedDeploymentWithPersistentDeployment() throws Exception {
        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();
            try {
                // deploy a persistent deployment

                Path persistentDeploymentPath = deployDir.resolve(JAR_ONE);
                PathAddress persistentDeploymentAddress = PathAddress.pathAddress(DEPLOYMENT, JAR_ONE);
                Archive<?> validDeployment = createDeploymentArchive();
                deployPersistent(JAR_ONE, validDeployment);
                Assert.assertTrue(String.format("%s not deployed", persistentDeploymentPath),
                        exists(persistentDeploymentAddress));


                // deploy an invalid file-system deployment

                addDeploymentScanner(0);
                try {
                    container.stop();
                    createDeployment(deployDir.resolve(JAR_TWO), "not.existing.dependency");
                    container.start();

                    Path failedMarker = deployDir.resolve(JAR_TWO + ".failed");
                    waitFor(() -> Files.exists(failedMarker));

                    Assert.assertTrue(String.format("%s should be deployed", JAR_ONE),
                            exists(persistentDeploymentAddress));
                    Assert.assertFalse(String.format("%s should not be deployed", JAR_TWO),
                            exists(PathAddress.pathAddress(DEPLOYMENT, JAR_TWO)));
                    Assert.assertTrue(String.format("Missing .failed marker for %s", JAR_TWO),
                            Files.exists(failedMarker));
                } finally {
                    removeDeploymentScanner();
                }
            } finally {
                StreamUtils.safeClose(client);
            }
        } finally {
            container.stop();
        }
    }

    /**
     * https://bugzilla.redhat.com/show_bug.cgi?id=997583
     *
     * FS deployments that failed during boot were not removed.
     */
    @Test
    public void testFailedFileSystemDeploymentDuringBoot() throws Exception {
        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();

            addDeploymentScanner(0);
            try {
                container.stop();

                createDeployment(deployDir.resolve(JAR_ONE), "not.existing.dependency");
                container.start();
                waitFor(() -> Files.exists(deployDir.resolve(JAR_ONE + ".failed")));

                assertFailedMarkerCreated(JAR_ONE);
                Assert.assertFalse(exists(PathAddress.pathAddress(DEPLOYMENT, JAR_ONE)));
            } finally {
                removeDeploymentScanner();
            }
        } finally {
            StreamUtils.safeClose(client);
            container.stop();
        }
    }

    @Test
    public void testFailedDeploymentWithCorrectDeploymentDuringBoot() throws Exception {
        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();

            addDeploymentScanner(0);
            try {
                container.stop();

                createDeployment(deployDir.resolve(JAR_ONE), "not.existing.dependency");
                createDeployment(deployDir.resolve(JAR_TWO), "org.jboss.modules");

                container.start();
                waitFor(this::isRunning);

                assertFailedMarkerCreated(JAR_ONE);
                Assert.assertFalse(exists(PathAddress.pathAddress(DEPLOYMENT, JAR_ONE)));
                Assert.assertTrue(exists(PathAddress.pathAddress(DEPLOYMENT, JAR_TWO)));
            } finally {
                removeDeploymentScanner();
            }
        } finally {
            StreamUtils.safeClose(client);
            container.stop();
        }
    }

    private void addDebugDeploymentLogger() throws Exception {
        boolean ok = false;
        try {
            final ModelNode op = Util.createAddOperation(getScannerLoggerResourcePath());
            op.get("category").set("org.jboss.as.server.deployment.scanner");
            op.get("level").set("TRACE");
            op.get("use-parent-handlers").set(true);
            ModelNode result = executeOperation(op);
            assertEquals("Unexpected outcome of setting the test deployment logger to debug: " + op, SUCCESS, result.get(OUTCOME).asString());
            ok = true;
        } finally {
            if (!ok) {
                ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
                ModelNode result = executeOperation(removeOp);
                assertEquals("Unexpected outcome of removing the test deployment logger: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
            }
        }
    }

    private void removeDebugDeploymentLogger() throws Exception {
        ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
        ModelNode result = executeOperation(removeOp);
        assertEquals("Unexpected outcome of removing the test deployment logger: " + result, SUCCESS, result.get(OUTCOME).asString());
    }

    private PathAddress getScannerLoggerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "logging"), PathElement.pathElement("logger", "org.jboss.as.server.deployment.scanner"));
    }

    private Archive<?> createDeploymentArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        final String dependencies = "Dependencies: org.jboss.modules";
        archive.add(new StringAsset(dependencies), "META-INF/MANIFEST.MF");
        return archive;
    }

    private void assertFailedMarkerCreated(String deployment) {
        Assert.assertTrue(String.format("Failed marker for deployment %s was not created.", deployment),
                Files.exists(deployDir.resolve(deployment + ".failed")));
    }

    private void waitFor(ExceptionWrappingSupplier<Boolean> condition) throws Exception {
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
        while (!condition.get() && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
    }

    /**
     * Creates managed deployment
     *
     * @param name deployment runtime name
     * @param archive archive to deploy
     */
    private void deployPersistent(String name, Archive archive) throws IOException {
        PathAddress address = PathAddress.pathAddress(DEPLOYMENT, name);

        ModelNode operation = Util.createOperation(ADD, address);
        operation.get(CONTENT).get(0).get(INPUT_STREAM_INDEX).set(0);
        OperationBuilder ob = new OperationBuilder(operation, true);
        ob.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());
        ModelNode result = client.execute(ob.build());
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        operation = Util.createOperation(DEPLOY, address);
        result = client.execute(operation);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
    }

    @Override
    protected ModelNode executeOperation(ModelNode op) throws IOException {
        return client.execute(op);
    }

    @Override
    protected File getDeployDir() {
        return deployDir.toFile();
    }


    /**
     * A Supplier that wraps eventual checked exception into runtime exception.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    private interface ExceptionWrappingSupplier<T> extends Supplier<T> {
        @Override
        default T get() {
            try {
                return throwingGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        T throwingGet() throws Exception;
    }

}
