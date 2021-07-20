# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [2.44.0]
### Changed
- Use backbase bom pom instead of banking-service and identity boms
- Update to 2021.07 release (DBS 2.21.0 and Identity 1.9.0)
  - Optional parameter (`includeSubscriptions`) added to ArrangementsApi.getArrangementById.
  - Optional parameter (`xTransactionsUserId`) added to TransactionPresentationServiceApi.postTransactions.
  - Optional parameter (`xTransactionsUserId`) added to TransactionPresentationServiceApi.getTransactions.
  - Optional parameter (`xTransactionsUserId`) added to TransactionPresentationServiceApi.patchTransactions.
  - Optional parameter (`xTransactionsUserId`) added to TransactionPresentationServiceApi.postDelete.
  - Optional parameter (`xTransactionsUserId`) added to TransactionPresentationServiceApi.postRefresh.

## [2.43.0]
### Fixed
- bugfix NPE for AccessGroupService.getUpdatePermissions  

## [2.42.0]
### Fixed
- Permissions assignment for user marked as admin was working incorrectly due to fact that System Function Group was returned when no assigned groups expected

## [2.41.0]
### Changed
- Add filter to forward `X-TID` headers

## [2.40.0]
### Fixed
- Fix upserting existing Business function
> Note: The field `name` is mandatory for the object `function` is required for the update - alongside the `functionId`.

e.g.
```yaml
jobRoles:
- name: SUUS
  description: Manager of the online helpdesk and processing teams, able to set up/edit contracts, add accounts etc. Also able to set up Broadcast messages
  functionGroups:
    - name: Manage Product Summary
      functions:
        - functionId: 1006
          name: Product Summary
          privileges:
            - privilege: view
            - privilege: create
            - privilege: edit
```

## [2.39.0]
### Changed
- For *access-control-core* exclusion of system function group was added in order to avoid DB constraint triggering during permissions PUT

## [2.38.0]
### Fixed
- For *legal-entity-bootstrap-task* fix case when link le to realm task could end the flow because empty returned instead of chaining stream task


## [2.37.0]
### Added
- For *legal-entity-bootstrap-task*, in case of no users/administrators is specified and identity integration enabled, provided realm will be created (if not exists) and linked to legal entity


## [2.35.0]
### Removed
- Spring Config Server. Superseded by standardized Spring Kubernetes Configuration
### Upgraded
- Spring Boot from 2.3.3.RELEASE to 2.5.0
- Spring Cloud 2020.0.2


## [2.34.0]
### Added
- Included a new approvals saga implemented by the Approvals Bootstrap Task.
 > Please be aware that this saga is not idempotent due to some product limitations.
 > Mor information in the wiki page
 > an example on how to configure it is found below

```yaml
bootstrap:
  approvals:
    - name: 4 eye approval policy
        approvalTypes:
          - name: Supervisor
            description: Supervisor approval level
            rank: 1
          - name: HelpDesk
            description: Digital helpdesk and Operations User
            rank: 2
        policies:
          - name: 4 eye policy
            description: Policy that requires approval from supervisor
            logicalItems:
              - rank: 1
                items:
                  - approvalTypeName: Supervisor
                    numberOfApprovals: 1
              - rank: 2
                operator: OR
                items:
                  - approvalTypeName: HelpDesk
                    numberOfApprovals: 2
        policyAssignments:
          - externalServiceAgreementId: sa_backbase-bank
            policyAssignmentItems:
              - functions:
                  - Assign Permissions
                bounds:
                  - policyName: 4 eye policy
              - functions:
                  - Manage Data Groups
                bounds:
                  - policyName: 4 eye policy
            approvalTypeAssignments:
              - approvalTypeName: Supervisor
                jobProfileName: SUUS
              - approvalTypeName: HelpDesk
                jobProfileName: Digital helpdesk and Operations User
```

## [2.33.0]
### Added
- Add 'build-helper-maven-plugin' for adding the generated sources (i.e. Openapi) as project's source directories.

## [2.32.0]
### Added
- added lock identity user on creation flag.
> New conditionally mandatory property added in legal entity stream: `backbase.stream.identity.identity-integration-base-url` to indicate BackBase Identity base URL for clients that integrate with it. This property must be defined when: `backbase.stream.legalentity.sink.use-identity-integration=true`

## [2.31.0]
### Changed
- Fixing the function group delete to only happen when it is not template type (this is when using referenceJobRoles )

## [2.30.0]
### Changed
- Fixing NullPointerException while creating data group using products i.e. without custom data group id

## [2.29.0]
### Changed
- Checking the response recieved from Legal entity api , user  api to is2xxSuccessful as it  returns 207 response for success.

## [2.28.0]
### Changed
- Add "Custom Data Group Items" to Product Groups, require either Products or Custom Data Group Items (previously just Products)

## [2.27.0]
### Fixed
- Fix for issue https://github.com/Backbase/stream-services-2.0/issues/46
```
While deleting a legal entity , we are trying to get user information by sending an internal id to
service-api/v2/users/externalids/{externlaIId}?skipHierarchyCheck=true instead of /service-api/v2/users/{internalId}.
The service-api/v2/users/externalids/{externlaIId}?skipHierarchyCheck=true returns 404 user not found as it is an internal ID and the deleting of the user fails.
```

## [2.26.0]
### Changed
- Fix for null point exception when no users are informed during the legal entity bootstrap.
- Enhancements in utility for aggregating all products of a product group.
- Enhanced the documentation for Service Agreements endpoints.

## [2.25.0]
### Added
- Included possibility to use Custom Service Agreements. From now on, if a custom service agreement is declared, and a master service agreement is not explicitly declared, that legal entity will have a custom service agreement only. If none are declared a default master service agreement is created. e.g.:
```yaml
bootstrap:
  legalEntity:
    name: "Backbase Bank"
    realmName: "backbase"
    externalId: "backbase-bank"
    legalEntityType: "BANK"
    customServiceAgreement:
      name: "backbase-bank"
      description: "backbase bank custom service agreement"
      externalId: "sa_backbase-bank"
      jobRoles:
        - name: SUUS
          description: Manager of the online helpdesk and processing teams, able to set up/edit contracts, add accounts etc. Also able to set up Broadcast messages
          functionGroups:
            - name: "Manage Entitlements"
              functions:
                - functionId: "1019"
                  functionCode: "manage.data.groups"
                  privileges:
                    - privilege: "view"
                    - privilege: "create"
                    - privilege: "edit"
                    - privilege: "delete"
                    - privilege: "approve"
```
- Created Service Agreement endpoint do updated an existing service agreement. e.g. add more participants to it.
> - PUT <legal-entity-http>/service-agreement
> - PUT <legal-entity-http>/async/service-agreement
> - GET <legal-entity-http>/async/service-agreement/{unitOfWorkId}

## [2.24.0]
### Fixed
- Add missing mapping for `accountHolderNames` in Product Ingestion SAGA

## [2.23.0]
### Changed
- Replace DBS RAML specs (converted to OpenAPI with BOAT) to OOB OpenAPI specs for DBS 2.20.0
- Fix all the mismatches and changes for generated code from new specs
- Upgrade jib base image to distroless java 11
- Upgrade to BOAT 0.14.0

## [2.22.0]
- Wrong commit that was reverted later

## [2.21.0]
### Fixed
- Add missing 'User' schema to the 'additions' transformer in DBS clients

## [2.20.0]
### Fixed
- Make `upsertArrangements(ProductGroupTask streamTask)` in `ProductIngestionSaga` public

## [2.19.0]
### Changed
- Including user preferences in arrangements data

## [2.18.0]
### Changed
- Upgrade plugins `maven-surefire-plugin` and `maven-failsafe-plugin`

## [2.17.0]
### Fixed
- Fix for issue Product Catalog creation fails when either kinds or types are nullCLOSED (Product Catalog null pointer when either kinds or types are null)
- Fix for issue Dependency conflict for org.yaml.snakeyamlCLOSED  (dependency conflicts)
- Fix for null pointer in UserProfileMapper when additional emails/phones are null

## [2.16.0]
### Fixed
- Fix in pipelines (Github Actions) and compile with JDK 11

### Added
- Added UserProfile support (for DBS service user-profile-manager) in Legal Entity SAGA.
It’s disabled by default and can be enabled by setting flag backbase.stream.legalentity.sink.userProfileEnabled to true.
It’s implemented as a nested object under User model, and it will use some of its properties (like fullName, email and phoneNumber) when constructing the UserProfile DBS resource.
It performs an upsert so it supports both creation and update.
Example:

```yaml
- user:
    externalId: sara
    fullName: Sara Jones
    identityLinkStrategy: CREATE_IN_IDENTITY
    emailAddress:
      address: sara@email.com
    mobileNumber:
      number: '1234567890'
    userProfile:
      title: Miss
      personalInformation:
        gender: female
        dateOfBirth: '1995-12-30'
        countryOfBirth: Netherlands
      locale: nl-NL
      additionalEmails:
        - value: sara2@email.com
        - value: sara3@email.com
      additionalPhoneNumbers:
        - value: '012121212'
        - value: '01313713'
      addresses:
        - streetAddress: Fake Street 123
          locality: Utrecht
          country: Netherlands
          type: home
          primary: 'true'
```

## [2.15.0]
### Fixed
- Fixed implementation of Reference Job Roles where we can assign a list of reference job roles to a specific user. 
  * Example with legal-entity-bootstrap-task on how to create a Reference Job Role in the root legal entity and assign it to a user below the hierarchy (example with a subsidiary):
```yaml
bootstrap:
  legalEntity:
    name: "Backbase Bank"
    realmName: "backbase"
    externalId: "backbase-bank"
    legalEntityType: "BANK"
    referenceJobRoles:
      - name: "Entitlements - Manager"
        description: "Full Entitlements administration."
        functionGroups:
          - name: "Manage Entitlements"
            functions:
              - functionId: "1019"
                functionCode: "manage.data.groups"
                privileges:
                  - privilege: "view"
                  - privilege: "create"
                  - privilege: "edit"
                  - privilege: "delete"
                  - privilege: "approve"
              - functionId: "1020"
                functionCode: "manage.function.groups"
                privileges:
                  - privilege: "view"
                  - privilege: "create"
                  - privilege: "edit"
                  - privilege: "delete"
                  - privilege: "approve"
    administrators:
      - externalId: "bbadmin"
        fullname: "Backbase Root Admin"
        identityLinkStrategy: "CREATE_IN_IDENTITY"
        emailAddress:
          address: "bbadmin@email.com"
        mobileNumber:
          number: "1234567890"
    users:
      - user:
          externalId: "bbadmin"
        referenceJobRoleNames:
          - "Entitlements - Manager"
    subsidiaries:
      - name: "Backbase Subsidiary"
        realmName: "backbase"
        externalId: "backbase-sub"
        legalEntityType: "BANK"
        administrators:
          - externalId: "bbsubadmin"
            fullname: "Backbase Subsidiary Admin"
            identityLinkStrategy: "CREATE_IN_IDENTITY"
            emailAddress:
              address: "bbsubadmin@email.com"
            mobileNumber:
              number: "1234567890"
        users:
          - user:
              externalId: "bbsubadmin"
            referenceJobRoleNames:
              - "Entitlements - Manager"
```

## [2.14.0]
### Fixed
- Fix ingestion of arrangements with child/parent relation by ordering and sequencing DBS requests

## [2.13.0]
### Added
- Add debit and credit indicator for CustomProduct

## [2.12.0]
### Added
- Enabling Sleuth trace ids to be propagated to the api response headers

## [2.11.0]
### Added
- Packaging the Saga’s OpenAPI specs in a zip module:
```xml
<dependency>
    <groupId>com.backbase.stream</groupId>
    <artifactId>stream-models</artifactId>
    <version>2.11.0</version>
    <classifier>specs</classifier>
    <type>zip</type>
</dependency>
```

## [2.10.0]
### Fixed
- Fixed object mapper to not serialize null values
- BUGFIX: Product Ingestion Saga was replacing all permissions assigned to users and adding new ones, now they are merged: #14

### Changed
- Upgraded Spring Boot to the version in SSDK
- Upgraded BOAT and renamed lots of generated API operationIds that now make more sense (most of them)

### Added
- Added lots and more sensible logging and pretty-printing functions to keep track on what's happening

## [2.9.0]
### Changed
- The master service agreement now can be manipulated during the creation of the Legal Entity, so we can add Assignable Permission Sets to it.

## [2.8.0]
### Added
- Added BBAN to TermDeposit in LegalEntity spec

## [2.7.0]
### Fixed
- Fixed configuration for Loan Mapping

## [2.6.0]
### Added
- DBS 2.19 support
- DBS Lean Services
- Included spring configuration properties support

### Removed
- Removal of MongoDB dependencies

### Changed
- Removing Job Profile Templates
- Upgrade Spring Boot to 2.3.x
- Stream SDK part of Stream Services repository
- DBS Clients generated with Boat
- DBS Endpoints changed, defaults are now set to the defaults setup in Kubernetes.
  * Before:
```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          dbs:
            token-uri: https://stream-demo.proto.backbasecloud.com/api/token-converter/oauth/token
backbase:
  stream:
    dbs:
      access-control-pandp-base-url: https://stream-api.proto.backbasecloud.com/accesscontrol-pandp-service/service-api/v2
      access-group-presentation-base-url: https://stream-api.proto.backbasecloud.com/accessgroup-presentation-service/service-api/v2
      account-presentation-base-url: https://stream-api.proto.backbasecloud.com/account-presentation-service/service-api/v2
      legal-entity-presentation-base-url:  https://stream-api.proto.backbasecloud.com/legalentity-presentation-service/service-api/v2
      user-presentation-base-url: https://stream-api.proto.backbasecloud.com/user-presentation-service/service-api/v2
      transaction-presentation-base-url: https://stream-api.proto.backbasecloud.com/transaction-presentation-service/service-api/v2
```
  * After:
```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          dbs:
            token-uri: http://token-converter:8080/oauth/token
backbase:
  stream:
    dbs:
      user-manager-base-url: http://user-manager:8080
      access-control-base-url: http://access-control:8080
      arrangement-manager-base-url: http://arrangement-manager:8080
      transaction-manager-base-url: http://transaction-manager:8080
      limit-manager-base-url: http://limits-manager:8080
```

[2.34.0]: https://github.com/Backbase/stream-services-2.0/compare/2.33.0...2.34.0
[2.33.0]: https://github.com/Backbase/stream-services-2.0/compare/2.32.0...2.33.0
[2.32.0]: https://github.com/Backbase/stream-services-2.0/compare/2.31.0...2.32.0
[2.31.0]: https://github.com/Backbase/stream-services-2.0/compare/2.30.0...2.31.0
[2.30.0]: https://github.com/Backbase/stream-services-2.0/compare/2.29.0...2.30.0
[2.29.0]: https://github.com/Backbase/stream-services-2.0/compare/2.28.0...2.29.0
[2.28.0]: https://github.com/Backbase/stream-services-2.0/compare/2.27.0...2.28.0
[2.27.0]: https://github.com/Backbase/stream-services-2.0/compare/2.26.0...2.27.0
[2.26.0]: https://github.com/Backbase/stream-services-2.0/compare/2.25.0...2.26.0
[2.25.0]: https://github.com/Backbase/stream-services-2.0/compare/2.24.0...2.25.0
[2.24.0]: https://github.com/Backbase/stream-services-2.0/compare/2.23.0...2.24.0
[2.23.0]: https://github.com/Backbase/stream-services-2.0/compare/2.22.0...2.23.0
[2.22.0]: https://github.com/Backbase/stream-services-2.0/compare/2.21.0...2.22.0
[2.21.0]: https://github.com/Backbase/stream-services-2.0/compare/2.20.0...2.21.0
[2.20.0]: https://github.com/Backbase/stream-services-2.0/compare/2.19.0...2.20.0
[2.19.0]: https://github.com/Backbase/stream-services-2.0/compare/2.18.0...2.19.0
[2.18.0]: https://github.com/Backbase/stream-services-2.0/compare/2.17.0...2.18.0
[2.17.0]: https://github.com/Backbase/stream-services-2.0/compare/2.16.0...2.17.0
[2.16.0]: https://github.com/Backbase/stream-services-2.0/compare/2.15.0...2.16.0
[2.15.0]: https://github.com/Backbase/stream-services-2.0/compare/2.14.0...2.15.0
[2.14.0]: https://github.com/Backbase/stream-services-2.0/compare/2.13.0...2.14.0
[2.13.0]: https://github.com/Backbase/stream-services-2.0/compare/2.12.0...2.13.0
[2.12.0]: https://github.com/Backbase/stream-services-2.0/compare/2.11.0...2.12.0
[2.11.0]: https://github.com/Backbase/stream-services-2.0/compare/2.10.0...2.11.0
[2.10.0]: https://github.com/Backbase/stream-services-2.0/compare/2.9.0...2.10.0
[2.9.0]: https://github.com/Backbase/stream-services-2.0/compare/2.8.0...2.9.0
[2.8.0]: https://github.com/Backbase/stream-services-2.0/compare/2.7.0...2.8.0
[2.7.0]: https://github.com/Backbase/stream-services-2.0/compare/2.6.0...2.7.0
[2.6.0]: https://github.com/Backbase/stream-services-2.0/releases/tag/2.6.0
