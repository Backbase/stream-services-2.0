package com.backbase.stream.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BackbaseStreamConfigurationPropertiesTest {
    @Test
    void shouldDoProperEquals() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties1 =
            new BackbaseStreamConfigurationProperties();

        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties2 =
            new BackbaseStreamConfigurationProperties();

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        dbs2.setAccessControlBaseUrl("http://test-access-control:8181");

        IdentityConnectionProperties identityConnectionProperties = new IdentityConnectionProperties();

        String modifiedIdentityIntegrationBaseUrl = "http://test-identity-integration-service:8181";

        identityConnectionProperties.setIdentityIntegrationBaseUrl(modifiedIdentityIntegrationBaseUrl);

        backbaseStreamConfigurationProperties2.setDbs(dbs2);
        backbaseStreamConfigurationProperties2.setIdentity(identityConnectionProperties);

        Assertions.assertNotEquals(backbaseStreamConfigurationProperties1, backbaseStreamConfigurationProperties2);
    }

    @Test
    void shouldDoProperEquals_Default() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties1 =
            new BackbaseStreamConfigurationProperties();

        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties2 =
            new BackbaseStreamConfigurationProperties();

        Assertions.assertEquals(backbaseStreamConfigurationProperties1, backbaseStreamConfigurationProperties2);
    }

    @Test
    void shouldDoProperEquals_DbsNotNull() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties1 =
            new BackbaseStreamConfigurationProperties();

        backbaseStreamConfigurationProperties1.setDbs(new DbsConnectionProperties());

        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties2 =
            new BackbaseStreamConfigurationProperties();

        Assertions.assertNotEquals(backbaseStreamConfigurationProperties1, backbaseStreamConfigurationProperties2);
    }

    @Test
    void shouldDoProperEquals_IdentityNotNull() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties1 =
            new BackbaseStreamConfigurationProperties();

        backbaseStreamConfigurationProperties1.setIdentity(new IdentityConnectionProperties());

        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties2 =
            new BackbaseStreamConfigurationProperties();

        Assertions.assertNotEquals(backbaseStreamConfigurationProperties1, backbaseStreamConfigurationProperties2);
    }

    @Test
    void shouldDoProperEquals_OneObject_Default() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties1 =
            new BackbaseStreamConfigurationProperties();

        Assertions.assertNotEquals(null, backbaseStreamConfigurationProperties1);
        Assertions.assertNotEquals("", backbaseStreamConfigurationProperties1);
        Assertions.assertEquals(backbaseStreamConfigurationProperties1, backbaseStreamConfigurationProperties1);
    }

    @Test
    void shouldGetHashCode() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();

        backbaseStreamConfigurationProperties.setDbs(new DbsConnectionProperties());
        backbaseStreamConfigurationProperties.setIdentity(new IdentityConnectionProperties());

        Assertions.assertNotEquals(0, backbaseStreamConfigurationProperties.hashCode());
    }

    @Test
    void shouldGetHashCode_Default() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();

        Assertions.assertNotEquals(0, backbaseStreamConfigurationProperties.hashCode());
    }

    @Test
    void shouldGetToString() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();

        Assertions.assertNotNull(backbaseStreamConfigurationProperties.toString());
    }

    @Test
    void shouldGetDefaultDbs() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();

        DbsConnectionProperties dbs = backbaseStreamConfigurationProperties.getDbs();

        Assertions.assertNull(dbs);
    }

    @Test
    void shouldGetModifiedDbs() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();
        DbsConnectionProperties modifiedDbs = new DbsConnectionProperties();

        backbaseStreamConfigurationProperties.setDbs(modifiedDbs);

        DbsConnectionProperties dbs = backbaseStreamConfigurationProperties.getDbs();

        Assertions.assertNotNull(dbs);
        Assertions.assertEquals(modifiedDbs, dbs);
    }

    @Test
    void shouldDoProperEqualsDbs() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();
        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_AccessControlBaseUrl() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_AccessControlBaseUrlNull() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        dbs1.setAccessControlBaseUrl(null);
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_ArrangementManagerBaseUrlNull() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        dbs1.setArrangementManagerBaseUrl(null);
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_TransactionManagerBaseUrl() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_TransactionManagerBaseUrlNull() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        dbs1.setTransactionManagerBaseUrl(null);
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_LimitsManagerBaseUrl() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }
    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_LimitsManagerBaseUrlNull() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();
        
        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        dbs1.setLimitsManagerBaseUrl(null);
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");
        
        DbsConnectionProperties dbs2 = new DbsConnectionProperties();
        
        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_UserManagerBaseUrl() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }
    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_UserManagerBaseUrlNull() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();
        
        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        dbs1.setUserManagerBaseUrl(null);
        // dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");
        
        DbsConnectionProperties dbs2 = new DbsConnectionProperties();
        
        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_UserProfileManagerBaseUrl() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        dbs1.setUserProfileManagerBaseUrl("http://test-user-profile-manager:8181");

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }
    @Test
    void shouldNotEqualsDbs_TwoObjects_Different_UserProfileManagerBaseUrlNull() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();
        
        // dbs1.setAccessControlBaseUrl("http://test-access-control:8181");
        // dbs1.setArrangementManagerBaseUrl("http://test-arrangement-manager:8181");
        // dbs1.setTransactionManagerBaseUrl("http://test-transaction-manager:8181");
        // dbs1.setLimitsManagerBaseUrl("http://test-limits-manager:8181");
        // dbs1.setUserManagerBaseUrl("http://test-user-manager:8181");
        dbs1.setUserProfileManagerBaseUrl(null);
        
        DbsConnectionProperties dbs2 = new DbsConnectionProperties();
        
        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldEqualsDbs_TwoObjects_Nulls() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        dbs1.setAccessControlBaseUrl(null);
        dbs1.setArrangementManagerBaseUrl(null);
        dbs1.setTransactionManagerBaseUrl(null);
        dbs1.setLimitsManagerBaseUrl(null);
        dbs1.setUserManagerBaseUrl(null);
        dbs1.setUserProfileManagerBaseUrl(null);

        DbsConnectionProperties dbs2 = new DbsConnectionProperties();

        Assertions.assertNotEquals(dbs1, dbs2);
    }

    @Test
    void shouldDoProperEqualsDbs_OneObject_Default() {
        DbsConnectionProperties dbs1 = new DbsConnectionProperties();

        Assertions.assertNotEquals("", dbs1);
        Assertions.assertNotEquals(null, dbs1);
        Assertions.assertEquals(dbs1, dbs1);
    }

    @Test
    void shouldGetHashCodeDbs() {
        DbsConnectionProperties dbs = new DbsConnectionProperties();

        Assertions.assertNotEquals(0, dbs.hashCode());
    }
    
    @Test
    void shouldGetHashCodeDbs_props_null() {
        DbsConnectionProperties dbs = new DbsConnectionProperties();
        
        dbs.setAccessControlBaseUrl(null);
        dbs.setArrangementManagerBaseUrl(null);
        dbs.setLimitsManagerBaseUrl(null);
        dbs.setTransactionManagerBaseUrl(null);
        dbs.setUserManagerBaseUrl(null);
        dbs.setUserProfileManagerBaseUrl(null);
        
        Assertions.assertNotEquals(0, dbs.hashCode());
    }

    @Test
    void shouldGetToStringDbs() {
        DbsConnectionProperties dbs = new DbsConnectionProperties();

        Assertions.assertNotNull(dbs.toString());
    }
    
    @Test
    void shouldGetToStringDbs_props_null() {
        DbsConnectionProperties dbs = new DbsConnectionProperties();
        dbs.setAccessControlBaseUrl(null);
        dbs.setArrangementManagerBaseUrl(null);
        dbs.setLimitsManagerBaseUrl(null);
        dbs.setTransactionManagerBaseUrl(null);
        dbs.setUserManagerBaseUrl(null);
        dbs.setUserProfileManagerBaseUrl(null);
        
        Assertions.assertNotNull(dbs.toString());
    }

    @Test
    void shouldGetDefaultIdentity() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();

        IdentityConnectionProperties identity = backbaseStreamConfigurationProperties.getIdentity();

        Assertions.assertNull(identity);
    }

    @Test
    void shouldGetModifiedIdentity() {
        BackbaseStreamConfigurationProperties backbaseStreamConfigurationProperties =
            new BackbaseStreamConfigurationProperties();
        IdentityConnectionProperties modifiedIdentity = new IdentityConnectionProperties();

        backbaseStreamConfigurationProperties.setIdentity(modifiedIdentity);

        IdentityConnectionProperties identity = backbaseStreamConfigurationProperties.getIdentity();

        Assertions.assertNotNull(identity);
        Assertions.assertEquals(modifiedIdentity, identity);
    }

    @Test
    void shouldDoProperEqualsIdentity() {
        IdentityConnectionProperties identity1 = new IdentityConnectionProperties();

        identity1.setIdentityIntegrationBaseUrl("http://identity-integration-service:8181");

        IdentityConnectionProperties identity2 = new IdentityConnectionProperties();

        Assertions.assertNotEquals(identity1, identity2);
        Assertions.assertNotEquals(null, identity1);
        Assertions.assertEquals(identity1, identity1);
    }

    @Test
    void shouldDoProperEqualsIdentity_FirstIdentityIntegrationBaseUrlNull() {
        IdentityConnectionProperties identity1 = new IdentityConnectionProperties();

        identity1.setIdentityIntegrationBaseUrl(null);

        IdentityConnectionProperties identity2 = new IdentityConnectionProperties();

        Assertions.assertNotEquals(identity1, identity2);
    }

    @Test
    void shouldDoProperEqualsIdentity_BothIdentityIntegrationBaseUrlNull() {
        IdentityConnectionProperties identity1 = new IdentityConnectionProperties();

        identity1.setIdentityIntegrationBaseUrl(null);

        IdentityConnectionProperties identity2 = new IdentityConnectionProperties();
        identity2.setIdentityIntegrationBaseUrl(null);

        Assertions.assertEquals(identity1, identity2);
    }

    @Test
    void shouldGetHashCodeIdentity() {
        IdentityConnectionProperties identity = new IdentityConnectionProperties();

        Assertions.assertNotEquals(0, identity.hashCode());
    }
    
    @Test
    void shouldGetHashCodeIdentity_props_null() {
        IdentityConnectionProperties identity = new IdentityConnectionProperties();
        
        identity.setIdentityIntegrationBaseUrl(null);
        
        Assertions.assertNotEquals(0, identity.hashCode());
    }

    @Test
    void shouldGetToStringIdentity() {
        IdentityConnectionProperties identity = new IdentityConnectionProperties();

        Assertions.assertNotNull(identity.toString());
    }

    @Test
    void shouldGetDefaultDbsAccessControlBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String accessControlBaseUrl = dbsConnectionProperties.getAccessControlBaseUrl();

        Assertions.assertEquals("http://access-control:8080", accessControlBaseUrl);
    }

    @Test
    void shouldGetModifiedDbsAccessControlBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String modifiedAccessControlBaseUrl = "http://test-access-control:8181";

        dbsConnectionProperties.setAccessControlBaseUrl(modifiedAccessControlBaseUrl);

        String accessControlBaseUrl = dbsConnectionProperties.getAccessControlBaseUrl();

        Assertions.assertEquals(modifiedAccessControlBaseUrl, accessControlBaseUrl);
    }

    @Test
    void shouldGetDefaultDbsArrangementManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String arrangementManagerBaseUrl = dbsConnectionProperties.getArrangementManagerBaseUrl();

        Assertions.assertEquals("http://arrangement-manager:8080", arrangementManagerBaseUrl);
    }

    @Test
    void shouldGetModifiedDbsArrangementManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String modifiedArangementManagerBaseUrl = "http://test-arrangement-manager:8181";

        dbsConnectionProperties.setArrangementManagerBaseUrl(modifiedArangementManagerBaseUrl);

        String arrangementManagerBaseUrl = dbsConnectionProperties.getArrangementManagerBaseUrl();

        Assertions.assertEquals(modifiedArangementManagerBaseUrl, arrangementManagerBaseUrl);
    }

    @Test
    void shouldGetDefaultDbsTransactionManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String transactionManagerBaseUrl = dbsConnectionProperties.getTransactionManagerBaseUrl();

        Assertions.assertEquals("http://transaction-manager:8080", transactionManagerBaseUrl);
    }

    @Test
    void shouldGetModifiedDbsTransactionManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String modifiedTransactionManagerBaseUrl = "http://test-transaction-manager:8181";

        dbsConnectionProperties.setTransactionManagerBaseUrl(modifiedTransactionManagerBaseUrl);

        String transactionManagerBaseUrl = dbsConnectionProperties.getTransactionManagerBaseUrl();

        Assertions.assertEquals(modifiedTransactionManagerBaseUrl, transactionManagerBaseUrl);
    }

    @Test
    void shouldGetDefaultDbsLimitsManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String limitsManagerBaseUrl = dbsConnectionProperties.getLimitsManagerBaseUrl();

        Assertions.assertEquals("http://limits-manager:8080", limitsManagerBaseUrl);
    }

    @Test
    void shouldGetModifiedDbsLimitsManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String modifiedLimitsManagerBaseUrl = "http://test-limits-manager:8181";

        dbsConnectionProperties.setLimitsManagerBaseUrl(modifiedLimitsManagerBaseUrl);

        String limitsManagerBaseUrl = dbsConnectionProperties.getLimitsManagerBaseUrl();

        Assertions.assertEquals(modifiedLimitsManagerBaseUrl, limitsManagerBaseUrl);
    }

    @Test
    void shouldGetDefaultDbsUserManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String userManagerBaseUrl = dbsConnectionProperties.getUserManagerBaseUrl();

        Assertions.assertEquals("http://user-manager:8080", userManagerBaseUrl);
    }

    @Test
    void shouldGetModifiedDbsUserManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String modifiedUserManagerBaseUrl = "http://test-user-manager:8181";

        dbsConnectionProperties.setUserManagerBaseUrl(modifiedUserManagerBaseUrl);

        String userManagerBaseUrl = dbsConnectionProperties.getUserManagerBaseUrl();

        Assertions.assertEquals(modifiedUserManagerBaseUrl, userManagerBaseUrl);
    }

    @Test
    void shouldGetDefaultDbsUserProfileManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String userProfileManagerBaseUrl = dbsConnectionProperties.getUserProfileManagerBaseUrl();

        Assertions.assertEquals("http://user-profile-manager:8080", userProfileManagerBaseUrl);
    }

    @Test
    void shouldGetModifiedDbsUserProfileManagerBaseUrl() {
        DbsConnectionProperties dbsConnectionProperties = new DbsConnectionProperties();

        String modifiedUserProfileManagerBaseUrl = "http://test-user-profile-manager:8181";

        dbsConnectionProperties.setUserProfileManagerBaseUrl(modifiedUserProfileManagerBaseUrl);

        String userProfileManagerBaseUrl = dbsConnectionProperties.getUserProfileManagerBaseUrl();

        Assertions.assertEquals(modifiedUserProfileManagerBaseUrl, userProfileManagerBaseUrl);
    }

    @Test
    void shouldGetDefaultIdentityIdentityIntegrationBaseUrl() {
        IdentityConnectionProperties identityConnectionProperties = new IdentityConnectionProperties();

        String identityIntegrationBaseUrl = identityConnectionProperties.getIdentityIntegrationBaseUrl();

        Assertions.assertEquals("http://identity-integration-service:8080", identityIntegrationBaseUrl);
    }

    @Test
    void shouldGetModifiedIdentityIdentityIntegrationBaseUrl() {
        IdentityConnectionProperties identityConnectionProperties = new IdentityConnectionProperties();

        String modifiedIdentityIntegrationBaseUrl = "http://test-identity-integration-service:8181";

        identityConnectionProperties.setIdentityIntegrationBaseUrl(modifiedIdentityIntegrationBaseUrl);

        String identityIntegrationBaseUrl = identityConnectionProperties.getIdentityIntegrationBaseUrl();

        Assertions.assertEquals(modifiedIdentityIntegrationBaseUrl, identityIntegrationBaseUrl);
    }

}
