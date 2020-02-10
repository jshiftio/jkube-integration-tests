package org.eclipse.jkube.integrationtests.webapp.zeroconfig;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.eclipse.jkube.integrationtests.cli.CliUtils.runCommand;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Tag(OPEN_SHIFT)
@TestMethodOrder(OrderAnnotation.class)
class ZeroConfigOcITCase extends ZeroConfig {

  private OpenShiftClient oc;

  @BeforeEach
  void setUp() {
    oc = new DefaultKubernetesClient().adapt(OpenShiftClient.class);
  }

  @AfterEach
  void tearDown() {
    oc.close();
    oc = null;
  }

  @Test
  @Order(1)
  @DisplayName("oc:build, in docker mode, should create image")
  void ocBuild() throws Exception {
    // Given
    hackToPreventNullPointerInRegistryServiceCreateAuthConfig();
    final Properties properties = new Properties();
    properties.setProperty("jkube.mode", "kubernetes"); // S2I doesn't support webapp yet
    // When
    final InvocationResult invocationResult = maven("oc:build", properties);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertImageWasRecentlyBuilt("integration-tests", "webapp-zero-config");
  }

  @Test
  @Order(2)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // Given
    final Properties properties = new Properties();
    properties.setProperty("jkube.mode", "kubernetes"); // S2I doesn't support webapp yet
    // When
    final InvocationResult invocationResult = maven("oc:resource", properties);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", PROJECT_ZERO_CONFIG));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-zero-config-deploymentconfig.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-zero-config-route.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-zero-config-service.yml"). exists(), equalTo(true));
  }

  @Test
  @Order(3)
  @DisplayName("oc:apply, should deploy pod and service")
  void ocApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldApplyResources(oc);
  }

  @Test
  @Order(4)
  @DisplayName("oc:undeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(oc);
  }

  /**
   * Source docker image is not pullable from OpenShift Build (fabric8/tomcat-9:1.2.1).
   *
   * This is caused by a bug in JKube when using Kuberentes mode with oc-maven-plugin.
   *
   * This hacks pulls the image using docker cli previously so that the build won't fail.
   * TODO: Remove once issue is fixed in JKube (NullPointerException: RegistryService#createAuthConfig)
   * <pre>
   *  Caused by: java.lang.NullPointerException
   *       at org.eclipse.jkube.kit.build.service.docker.RegistryService.createAuthConfig (RegistryService.java:153)
   *       at org.eclipse.jkube.kit.build.service.docker.RegistryService.pullImageWithPolicy (RegistryService.java:112)
   *       at org.eclipse.jkube.kit.build.service.docker.BuildService.autoPullBaseImage (BuildService.java:262)
   *       at org.eclipse.jkube.kit.build.service.docker.BuildService.buildImage (BuildService.java:74)
   *       at org.eclipse.jkube.kit.config.service.kubernetes.DockerBuildService.build (DockerBuildService.java:49)
   *       at org.eclipse.jkube.maven.plugin.mojo.build.AbstractDockerMojo.buildAndTag (AbstractDockerMojo.java:687)
   * </pre>
   */
  private static void hackToPreventNullPointerInRegistryServiceCreateAuthConfig() throws Exception {
    runCommand("docker pull fabric8/tomcat-9:1.2.1");
  }
}