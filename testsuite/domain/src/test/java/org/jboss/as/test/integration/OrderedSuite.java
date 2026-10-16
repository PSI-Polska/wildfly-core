package org.jboss.as.test.integration;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * TODO: Describe this class (The first line - until the first dot - will interpret as the brief description).
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({
        // Podejrzane testy z listy
////        org.jboss.as.test.integration.respawn.RespawnTestCase.class,
////        org.jboss.as.test.integration.respawn.RespawnHttpTestCase.class,
//        org.jboss.as.test.integration.domain.ConfigurationChangesTestCase.class,
//        org.jboss.as.test.integration.domain.HostControllerBootOperationsTestCase.class,
//        org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.class,
//        org.jboss.as.test.integration.domain.management.ServerAutoStartTestCase.class,
//        org.jboss.as.test.integration.domain.management.HttpManagementConstantHeadersTestCase.class,
//        org.jboss.as.test.integration.domain.management.ReadConfigAsFeaturesDomainTestCase.class,
//        org.jboss.as.test.integration.domain.management.ServerStartFailureTestCase.class,
//        org.jboss.as.test.integration.domain.DefaultInterfaceOverridingDomainTestCase.class,
////        org.jboss.as.test.integration.domain.management.cli.UndeployWildcardDomainTestCase.class,
//        org.jboss.as.test.integration.domain.rbac.PropertiesRoleMappingStandardRolesTestCase.class,
//        org.jboss.as.test.integration.domain.rbac.PropertiesRoleMappingServerGroupScopedRolesTestCase.class,
//        org.jboss.as.test.integration.domain.rbac.PropertiesRoleMappingHostScopedRolesTestCase.class,
        org.jboss.as.test.integration.domain.rbac.RBACProviderRunAsHostScopedRolesTestCase.class,
//        org.jboss.as.test.integration.domain.rbac.RBACProviderRunAsServerGroupScopedRolesTestCase.class,
//        org.jboss.as.test.integration.domain.rbac.RBACProviderRunAsStandardRolesTestCase.class,

        // TEST DOCELOWY ZAWSZE NA KOŃCU:
        org.jboss.as.test.integration.domain.suites.ManagementReadXmlTestCase.class
})

public class OrderedSuite {
}
