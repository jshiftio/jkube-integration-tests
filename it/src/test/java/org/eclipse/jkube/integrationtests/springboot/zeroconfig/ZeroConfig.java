/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests.springboot.zeroconfig;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.PodReadyWatcher;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertGlobalLabels;
import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertLabels;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.assertService;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

abstract class ZeroConfig {

  static final String PROJECT_ZERO_CONFIG = "projects-to-be-tested/spring-boot/zero-config";

  final void assertThatShouldApplyResources(KubernetesClient kc) throws InterruptedException {
    final PodReadyWatcher podWatcher = new PodReadyWatcher();
    kc.pods().withLabel("app", "spring-boot-zero-config").watch(podWatcher);
    final Pod pod = podWatcher.await(30L, TimeUnit.SECONDS);
    assertThat(pod, notNullValue());
    assertThat(pod.getMetadata().getName(), startsWith("spring-boot-zero-config"));
    assertStandardLabels(pod.getMetadata()::getLabels);
    assertPod(kc, pod).logContains("Started ZeroConfigApplication in", 20);
    final Service service = awaitService(kc, pod.getMetadata().getNamespace(), "spring-boot-zero-config");
    assertStandardLabels(service.getMetadata()::getLabels);
    assertThat(service.getMetadata().getLabels(), hasEntry("expose", "true"));
    assertStandardLabels(service.getSpec()::getSelector);
    assertThat(service.getSpec().getPorts(), hasSize(1));
    assertService(kc, service).assertPort("http", 8080, false);
  }

  final void assertThatShouldDeleteAllAppliedResources(KubernetesClient kc) {
    final Optional<Pod> matchingPod = kc.pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith("spring-boot-zero-config"))
      .filter(((Predicate<Pod>)(p -> p.getMetadata().getName().endsWith("-build"))).negate())
      .findAny();
    final Function<Pod, Pod> refreshPod = pod ->
      kc.pods().withName(pod.getMetadata().getName()).get();
    matchingPod.map(refreshPod).ifPresent(updatedPod ->
      assertThat(updatedPod.getMetadata().getDeletionTimestamp(), notNullValue()));
    final boolean servicesExist = kc.services().list().getItems().stream()
      .anyMatch(s -> s.getMetadata().getName().startsWith("spring-boot-zero-config"));
    assertThat(servicesExist, equalTo(false));
  }

  InvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  final InvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return MavenUtils.execute(i -> {
      i.setBaseDirectory(new File("../"));
      i.setProjects(Collections.singletonList(PROJECT_ZERO_CONFIG));
      i.setGoals(Collections.singletonList(goal));
      i.setProperties(properties);
    });
  }

  static void assertStandardLabels(Supplier<Map<String, String>> labelSupplier) {
    assertGlobalLabels(labelSupplier);
    assertLabels(labelSupplier, hasEntry("app", "spring-boot-zero-config"));
  }
}