// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import static com.meterware.simplestub.Stub.createStrictStub;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.models.V1TokenReview;
import io.kubernetes.client.models.V1TokenReviewStatus;
import io.kubernetes.client.models.V1UserInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.ws.rs.WebApplicationException;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.BodyMatcher;
import oracle.kubernetes.operator.work.CallTestSupport;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class RestBackendImplTest {

  private static final String DOMAIN = "domain";
  private static final String NS = "namespace1";
  private static final String UID = "uid1";
  private static List<Domain> domains = new ArrayList<>();
  private WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN);

  private List<Memento> mementos = new ArrayList<>();
  private RestBackend restBackend;
  private Domain domain = createDomain(NS, UID);
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private CallTestSupport testSupport = new CallTestSupport();
  private Domain updatedDomain;
  private SecurityControl securityControl = new SecurityControl();
  private BodyMatcher fetchDomain =
      actualBody -> {
        updatedDomain = (Domain) actualBody;
        return true;
      };

  private static Domain createDomain(String namespace, String uid) {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(namespace))
        .withSpec(new DomainSpec().withDomainUID(uid));
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(WlsRetrievalExecutor.install(configSupport));
    mementos.add(testSupport.installSynchronousCallDispatcher());

    expectSecurityCalls();
    expectPossibleListDomainCall();
    expectPossibleReplaceDomainCall();

    domains.clear();
    domains.add(domain);
    configSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5", "ms6");
    restBackend = new RestBackendImpl("", "", Collections.singletonList(NS));
  }

  private void expectSecurityCalls() {
    testSupport
        .createCannedResponse("createTokenReview")
        .ignoringBody()
        .returning(securityControl.getTokenReviewResponse());
    testSupport
        .createCannedResponse("createSubjectAccessReview")
        .ignoringBody()
        .returning(securityControl.getSubjectAccessResponse());
  }

  private void expectPossibleListDomainCall() {
    testSupport
        .createOptionalCannedResponse("listDomain")
        .withNamespace(NS)
        .returning(new DomainList().withItems(domains));
  }

  private void expectPossibleReplaceDomainCall() {
    testSupport
        .createOptionalCannedResponse("replaceDomain")
        .withNamespace(NS)
        .withUid(UID)
        .withBody(fetchDomain)
        .returning(new Domain());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test(expected = WebApplicationException.class)
  public void whenNegativeScaleSpecified_throwException() {
    restBackend.scaleCluster(UID, "cluster1", -1);
  }

  @Test
  public void whenPerClusterReplicaSettingMatchesScaleRequest_doNothing() {
    configureCluster("cluster1").withReplicas(5);

    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain(), nullValue());
  }

  private Domain getUpdatedDomain() {
    return updatedDomain;
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName);
  }

  @Test
  public void whenPerClusterReplicaSetting_scaleClusterUpdatesSetting() {
    configureCluster("cluster1").withReplicas(1);

    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoPerClusterReplicaSetting_scaleClusterCreatesOne() {
    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoPerClusterReplicaSettingAndDefaultMatchesRequest_doNothing() {
    if (DomainConfiguratorFactory.useDomainV1())
      configurator.withDefaultReplicaCount(Domain.DEFAULT_REPLICA_LIMIT);

    restBackend.scaleCluster(UID, "cluster1", Domain.DEFAULT_REPLICA_LIMIT);

    assertThat(getUpdatedDomain(), nullValue());
  }

  private static class SecurityControl {
    private final boolean allowed = true;
    private final boolean authenticated = true;

    private V1TokenReview getTokenReviewResponse() {
      return new V1TokenReview().status(getTokenReviewStatus());
    }

    private V1TokenReviewStatus getTokenReviewStatus() {
      return new V1TokenReviewStatus().authenticated(authenticated).user(new V1UserInfo());
    }

    private V1SubjectAccessReview getSubjectAccessResponse() {
      return new V1SubjectAccessReview().status(new V1SubjectAccessReviewStatus().allowed(allowed));
    }
  }

  abstract static class WlsRetrievalExecutor implements ScheduledExecutorService {
    private WlsDomainConfigSupport configSupport;

    static Memento install(WlsDomainConfigSupport configSupport) {
      return new MapMemento<>(
          ContainerResolver.getInstance().getContainer().getComponents(),
          "test",
          Component.createFor(ScheduledExecutorService.class, newExecutor(configSupport)));
    }

    private static WlsRetrievalExecutor newExecutor(WlsDomainConfigSupport response) {
      return createStrictStub(WlsRetrievalExecutor.class, response);
    }

    WlsRetrievalExecutor(WlsDomainConfigSupport configSupport) {
      this.configSupport = configSupport;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T> Future<T> submit(@Nonnull Callable<T> task) {
      return (Future<T>) createStrictStub(WlsDomainConfigFuture.class, configSupport);
    }
  }

  abstract static class WlsDomainConfigFuture implements Future<WlsDomainConfig> {
    private WlsDomainConfigSupport configSupport;

    WlsDomainConfigFuture(WlsDomainConfigSupport configSupport) {
      this.configSupport = configSupport;
    }

    @Override
    public WlsDomainConfig get(long timeout, @Nonnull TimeUnit unit) {
      return configSupport.createDomainConfig();
    }
  }

  static class MapMemento<K, V> implements Memento {
    private final Map<K, V> map;
    private final K key;
    private final V originalValue;

    MapMemento(Map<K, V> map, K key, V value) {
      this.map = map;
      this.key = key;
      this.originalValue = map.get(key);
      map.put(key, value);
    }

    @Override
    public void revert() {
      if (originalValue == null) map.remove(key);
      else map.put(key, originalValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOriginalValue() {
      return (T) originalValue;
    }
  }
}