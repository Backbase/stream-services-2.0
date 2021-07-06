package com.backbase.stream.service;

import static com.backbase.dbs.accesscontrol.api.service.v2.model.BatchResponseItemExtended.StatusEnum.HTTP_STATUS_INTERNAL_SERVER_ERROR;
import static com.backbase.dbs.accesscontrol.api.service.v2.model.BatchResponseItemExtended.StatusEnum.HTTP_STATUS_OK;
import static com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationAction.ADD;
import static com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationAction.REMOVE;
import static com.backbase.stream.legalentity.model.LegalEntityStatus.ENABLED;
import static com.backbase.stream.test.LambdaAssertions.assertEqualsTo;
import static com.backbase.stream.test.WebClientTestUtils.buildWebResponseExceptionMono;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backbase.dbs.accesscontrol.api.service.v2.DataGroupApi;
import com.backbase.dbs.accesscontrol.api.service.v2.DataGroupsApi;
import com.backbase.dbs.accesscontrol.api.service.v2.FunctionGroupApi;
import com.backbase.dbs.accesscontrol.api.service.v2.FunctionGroupsApi;
import com.backbase.dbs.accesscontrol.api.service.v2.ServiceAgreementApi;
import com.backbase.dbs.accesscontrol.api.service.v2.ServiceAgreementQueryApi;
import com.backbase.dbs.accesscontrol.api.service.v2.ServiceAgreementsApi;
import com.backbase.dbs.accesscontrol.api.service.v2.UserQueryApi;
import com.backbase.dbs.accesscontrol.api.service.v2.UsersApi;
import com.backbase.dbs.accesscontrol.api.service.v2.model.BatchResponseItemExtended;
import com.backbase.dbs.accesscontrol.api.service.v2.model.FunctionGroupItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PersistenceApprovalPermissions;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PersistenceApprovalPermissionsGetResponseBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationAction;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationAssignUserPermissions;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationFunctionGroupDataGroup;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationIdentifier;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationParticipantBatchUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationParticipantPutBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationServiceAgreementUserPair;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationServiceAgreementUsersBatchUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementParticipantsGetResponseBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementUsersQuery;
import com.backbase.dbs.user.api.service.v2.UserManagementApi;
import com.backbase.stream.legalentity.model.BaseProductGroup;
import com.backbase.stream.legalentity.model.BatchProductGroup;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.LegalEntityParticipant;
import com.backbase.stream.legalentity.model.ServiceAgreement;
import com.backbase.stream.legalentity.model.ServiceAgreementUserAction;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.product.task.BatchProductGroupTask;
import com.backbase.stream.worker.exception.StreamTaskException;
import com.backbase.stream.worker.model.StreamTask;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccessGroupServiceTest {

    @InjectMocks
    private AccessGroupService subject;

    @Mock
    private UserManagementApi usersApi;

    @Mock
    private UserQueryApi userQueryApi;

    @Mock
    private UsersApi accessControlUsersApi;

    @Mock
    private DataGroupApi dataGroupApi;

    @Mock
    private DataGroupsApi dataGroupsApi;

    @Mock
    private FunctionGroupApi functionGroupApi;

    @Mock
    private FunctionGroupsApi functionGroupsApi;

    @Mock
    private ServiceAgreementQueryApi serviceAgreementQueryApi;

    @Mock
    private ServiceAgreementApi serviceAgreementApi;

    @Mock
    private ServiceAgreementsApi serviceAgreementsApi;

    @Test
    void getServiceAgreementByExternalIdRetrievesServiceAgreementByExternalId() {
        final String externalId = "someExternalId";
        final Mono<ServiceAgreementItem> dbsSa = Mono.just(new ServiceAgreementItem().externalId(externalId));

        when(serviceAgreementApi.getServiceAgreementExternalId(eq(externalId))).thenReturn(dbsSa);


        Mono<ServiceAgreement> result = subject.getServiceAgreementByExternalId(externalId);


        ServiceAgreement expected = new ServiceAgreement().externalId(externalId);
        result.subscribe(assertEqualsTo(expected));
    }

    @Test
    void getServiceAgreementByExternalIdReturnsEmptyOnServiceAgreementNotFound() {
        final String externalId = "someExternalId";

        Mono<ServiceAgreementItem> response =
            buildWebResponseExceptionMono(WebClientResponseException.NotFound.class, HttpMethod.GET);
        when(serviceAgreementApi.getServiceAgreementExternalId(eq(externalId)))
            .thenReturn(response);


        Mono<ServiceAgreement> actual = subject.getServiceAgreementByExternalId(externalId);


        actual.subscribe(assertEqualsTo(null));
    }

    @Test
    void updateServiceAgreement() {
        final String saInternalId = "someSaInternalId";
        final String saExternalId = "someSaExternalId";
        final String description = "someDescription";
        final String name = "someName";
        final String validFromDate = "2021-03-08";
        final String validFromTime = "00:00:00";
        final String validUntilDate = "2022-03-08";
        final String validUntilTime = "23:59:59";
        final List<ServiceAgreementUserAction> regularUsers = asList("userId1", "userId2").stream()
            .map(u -> new ServiceAgreementUserAction().userProfile(new JobProfileUser().user(new User()
                .externalId("ex_" + u).internalId("in_" + u)))
                .action(ServiceAgreementUserAction.ActionEnum.ADD))
            .collect(Collectors.toList());

        StreamTask streamTask = Mockito.mock(StreamTask.class);

        ServiceAgreement serviceAgreement = buildInputServiceAgreement(saInternalId, saExternalId, description, name,
            LocalDate.parse(validFromDate), validFromTime, LocalDate.parse(validUntilDate), validUntilTime);

        serviceAgreement
            .addParticipantsItem(new LegalEntityParticipant().externalId("p1").sharingAccounts(true)
                .sharingUsers(true).action(LegalEntityParticipant.ActionEnum.ADD))
            .addParticipantsItem(new LegalEntityParticipant().externalId("p2").sharingAccounts(false)
                .sharingUsers(false).action(LegalEntityParticipant.ActionEnum.ADD));

        when(serviceAgreementApi.putPresentationIngestServiceAgreementParticipants(any()))
            .thenReturn(Flux.concat(
                Mono.just(new BatchResponseItemExtended().action(ADD).resourceId("p1").status(HTTP_STATUS_OK)),
                Mono.just(new BatchResponseItemExtended().action(ADD).resourceId("p2").status(HTTP_STATUS_OK))
            ));

        Flux<BatchResponseItemExtended> usersResponse = Flux.fromIterable(regularUsers.stream()
            .map(u -> new BatchResponseItemExtended().status(HTTP_STATUS_OK)
                .resourceId(u.getUserProfile().getUser().getExternalId()))
            .collect(Collectors.toList()));
        when(serviceAgreementApi.putPresentationServiceAgreementUsersBatchUpdate(any())).thenReturn(usersResponse);

        when(serviceAgreementsApi.getServiceAgreementParticipants(eq(saInternalId)))
            .thenReturn(Flux.fromIterable(Collections.emptyList()));

        Mono<ServiceAgreementUsersQuery> emptyExistingUsersList = Mono.just(new ServiceAgreementUsersQuery());
        when(serviceAgreementQueryApi.getServiceAgreementUsers(eq(saInternalId))).thenReturn(emptyExistingUsersList);


        Mono<ServiceAgreement> result = subject.updateServiceAgreementAssociations(streamTask, serviceAgreement, regularUsers);
        result.block();


        InOrder inOrderValidator = Mockito.inOrder(serviceAgreementApi);
        thenUpdateParticipantsCall(inOrderValidator, saExternalId, ADD,
            new ExpectedParticipantUpdate("p1", true, true),
            new ExpectedParticipantUpdate("p2", false, false));

        thenRegularUsersUpdateCall(saExternalId, ADD, "ex_userId1", "ex_userId2");
    }

    @Test
    void updateServiceAgreementWithExistingParticipants() {
        final String saInternalId = "someSaInternalId";
        final String saExternalId = "someSaExternalId";
        final String description = "someDescription";
        final String name = "someName";
        final String validFromDate = "2021-03-08";
        final String validFromTime = "00:00:00";
        final String validUntilDate = "2022-03-08";
        final String validUntilTime = "23:59:59";
        final List<ServiceAgreementUserAction> regularUsersToAdd = asList("userId1", "userId2").stream()
            .map(u -> new ServiceAgreementUserAction().userProfile(new JobProfileUser().user(new User()
                .externalId("ex_" + u).internalId("in_" + u)))
                .action(ServiceAgreementUserAction.ActionEnum.ADD))
            .collect(Collectors.toList());
        final List<ServiceAgreementUserAction> regularUsersToRemove = asList("userId3", "userId4").stream()
            .map(u -> new ServiceAgreementUserAction().userProfile(new JobProfileUser().user(new User()
                .externalId("ex_" + u).internalId("in_" + u)))
                .action(ServiceAgreementUserAction.ActionEnum.REMOVE))
            .collect(Collectors.toList());
        final List<ServiceAgreementUserAction> regularUsers =
            Stream.concat(regularUsersToAdd.stream(), regularUsersToRemove.stream()).collect(Collectors.toList());

        StreamTask streamTask = Mockito.mock(StreamTask.class);

        ServiceAgreement serviceAgreement = buildInputServiceAgreement(saInternalId, saExternalId, description, name,
            LocalDate.parse(validFromDate), validFromTime, LocalDate.parse(validUntilDate), validUntilTime);

        // participants
        serviceAgreement
            .addParticipantsItem(new LegalEntityParticipant().externalId("p1").sharingAccounts(true)
                .sharingUsers(true).action(LegalEntityParticipant.ActionEnum.ADD))
            .addParticipantsItem(new LegalEntityParticipant().externalId("p2").sharingAccounts(false)
                .sharingUsers(false).action(LegalEntityParticipant.ActionEnum.REMOVE))
            .addParticipantsItem(new LegalEntityParticipant().externalId("p3").sharingAccounts(false)
                .sharingUsers(false).action(LegalEntityParticipant.ActionEnum.ADD));

        when(serviceAgreementApi.putPresentationIngestServiceAgreementParticipants(any()))
            .thenReturn(Flux.concat(Mono.just(new BatchResponseItemExtended().status(HTTP_STATUS_OK))));

        ServiceAgreementParticipantsGetResponseBody existingPar1 =
            new ServiceAgreementParticipantsGetResponseBody().externalId("p1");
        ServiceAgreementParticipantsGetResponseBody existingPar2 =
            new ServiceAgreementParticipantsGetResponseBody().externalId("p2");
        when(serviceAgreementsApi.getServiceAgreementParticipants(eq(saInternalId)))
            .thenReturn(Flux.fromIterable(asList(existingPar1, existingPar2)));

        // users
        Flux<BatchResponseItemExtended> usersResponse = Flux.fromIterable(regularUsers.stream()
            .map(u -> new BatchResponseItemExtended().status(HTTP_STATUS_OK)
                .resourceId(u.getUserProfile().getUser().getExternalId()))
            .collect(Collectors.toList()));
        when(serviceAgreementApi.putPresentationServiceAgreementUsersBatchUpdate(any())).thenReturn(usersResponse);

        Mono<ServiceAgreementUsersQuery> existingUsersList =
            Mono.just(new ServiceAgreementUsersQuery().addUserIdsItem("in_userId1").addUserIdsItem("in_userId3"));
        when(serviceAgreementQueryApi.getServiceAgreementUsers(eq(saInternalId))).thenReturn(existingUsersList);


        Mono<ServiceAgreement> result = subject.updateServiceAgreementAssociations(streamTask, serviceAgreement, regularUsers);
        result.block();


        InOrder inOrderValidator = Mockito.inOrder(serviceAgreementApi);
        thenUpdateParticipantsCall(inOrderValidator, saExternalId, ADD,
            new ExpectedParticipantUpdate("p3", false, false));
        thenUpdateParticipantsCall(inOrderValidator, saExternalId, REMOVE,
            new ExpectedParticipantUpdate("p2", false, false));

        thenRegularUsersUpdateCall(saExternalId, REMOVE, "ex_userId3");
        thenRegularUsersUpdateCall(saExternalId, ADD, "ex_userId2");
    }

    @Test
    void updateParticipantsLogsAllErrors() {
        final String saExternalId = "someSaExternalId";
        final String saInternalId = "someSaInternalId";

        StreamTask streamTask = Mockito.spy(StreamTask.class);

        ServiceAgreement serviceAgreement = buildInputServiceAgreement(saInternalId, saExternalId,
            "", "", null, null, null, null);

        serviceAgreement
            .addParticipantsItem(new LegalEntityParticipant().externalId("p1").sharingAccounts(true)
                .sharingUsers(true).action(LegalEntityParticipant.ActionEnum.ADD))
            .addParticipantsItem(new LegalEntityParticipant().externalId("p2").sharingAccounts(false)
                .sharingUsers(false).action(LegalEntityParticipant.ActionEnum.ADD))
            .addParticipantsItem(new LegalEntityParticipant().externalId("p3").sharingAccounts(false)
                .sharingUsers(false).action(LegalEntityParticipant.ActionEnum.ADD))
            .addParticipantsItem(new LegalEntityParticipant().externalId("p4").sharingAccounts(false)
                .sharingUsers(false).action(LegalEntityParticipant.ActionEnum.ADD));

        when(serviceAgreementApi.putPresentationIngestServiceAgreementParticipants(any()))
            .thenReturn(Flux.concat(
                Mono.just(new BatchResponseItemExtended().action(ADD).resourceId("p1")
                    .status(HTTP_STATUS_OK)),
                Mono.just(new BatchResponseItemExtended().action(ADD).resourceId("p2")
                    .status(HTTP_STATUS_INTERNAL_SERVER_ERROR)),
                Mono.just(new BatchResponseItemExtended().action(ADD).resourceId("p3")
                    .status(HTTP_STATUS_INTERNAL_SERVER_ERROR)),
                Mono.just(new BatchResponseItemExtended().action(ADD).resourceId("p4")
                    .status(HTTP_STATUS_OK))
            ));

        when(serviceAgreementsApi.getServiceAgreementParticipants(eq(saInternalId)))
            .thenReturn(Flux.fromIterable(Collections.emptyList()));


        Mono<Map<LegalEntityParticipant.ActionEnum, Mono<ServiceAgreement>>> result =
            subject.updateParticipants(streamTask, serviceAgreement);
        assertThrows(StreamTaskException.class, () -> {
            Map<LegalEntityParticipant.ActionEnum, Mono<ServiceAgreement>> map = result.block();
            map.get(LegalEntityParticipant.ActionEnum.ADD)
                .then(map.get(LegalEntityParticipant.ActionEnum.REMOVE))
                .block();
        });


        InOrder verifier = inOrder(streamTask);
        verifier.verify(streamTask).error(eq("participant"), eq("update-participant"), eq("failed"),
            eq("p2"), eq(null), any(String.class), any(String.class), any(String.class));
        verifier.verify(streamTask).error(eq("participant"), eq("update-participant"), eq("failed"),
            eq("p3"), eq(null), any(String.class), any(String.class), any(String.class));
    }

    @Test
    void assignPermissionsBatch() {
        // Given
        BatchProductGroupTask batchProductGroupTask = new BatchProductGroupTask().data(
            new BatchProductGroup().serviceAgreement(new ServiceAgreement().externalId("sa_benedict").internalId("sa-internal-id"))
        );
        batchProductGroupTask.setIngestionMode(BatchProductGroupTask.IngestionMode.UPDATE);

        Map<BusinessFunctionGroup, List<BaseProductGroup>> baseProductGroupMap = new HashMap<>();
        baseProductGroupMap.put(new BusinessFunctionGroup().id("business-function-group-id-1"), Collections.emptyList());

        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> usersPermissions = new HashMap<>();
        usersPermissions.put(
            new User().internalId("user-internal-id").externalId("benedict"),
            baseProductGroupMap
        );

        List<PresentationAssignUserPermissions> expectedPermissions = Collections.singletonList(
            new PresentationAssignUserPermissions()
                .externalUserId("benedict")
                .externalServiceAgreementId("sa_benedict")
                .functionGroupDataGroups(Arrays.asList(
                    new PresentationFunctionGroupDataGroup().functionGroupIdentifier(
                        new PresentationIdentifier().idIdentifier("system-group-id-2")
                    ).dataGroupIdentifiers(Collections.emptyList()),
                    new PresentationFunctionGroupDataGroup().functionGroupIdentifier(
                        new PresentationIdentifier().idIdentifier("system-group-id-3")
                    ).dataGroupIdentifiers(Collections.emptyList())
                ))
        );

        when(functionGroupApi.getFunctionGroups("sa-internal-id"))
            .thenReturn(Flux.just(
                new FunctionGroupItem().id("system-group-id-1").name("SYSTEM_FUNCTION_GROUP").type(FunctionGroupItem.TypeEnum.SYSTEM),
                new FunctionGroupItem().id("system-group-id-2").name("Full access").type(FunctionGroupItem.TypeEnum.TEMPLATE)
            ));

        when(userQueryApi.getPersistenceApprovalPermissions("user-internal-id", "sa-internal-id"))
            .thenReturn(Mono.just(new PersistenceApprovalPermissions().items(Arrays.asList(
                new PersistenceApprovalPermissionsGetResponseBody().functionGroupId("system-group-id-1").dataGroupIds(Collections.emptyList()),
                new PersistenceApprovalPermissionsGetResponseBody().functionGroupId("system-group-id-2").dataGroupIds(Collections.emptyList()),
                new PersistenceApprovalPermissionsGetResponseBody().functionGroupId("system-group-id-3").dataGroupIds(Collections.emptyList())
            ))));

        when(accessControlUsersApi.putAssignUserPermissions(expectedPermissions))
            .thenReturn(Flux.just(
                new BatchResponseItemExtended().resourceId("resource-id").status(HTTP_STATUS_OK).errors(Collections.emptyList())
            ));

        // When
        BatchProductGroupTask result = subject.assignPermissionsBatch(batchProductGroupTask, usersPermissions)
            .block();

        // Then
        Assertions.assertSame(batchProductGroupTask, result);

        verify(accessControlUsersApi).putAssignUserPermissions(expectedPermissions);
    }

    @Test
    void assignPermissionsBatchNoExistingFunctionGroups() {
        // Given
        BatchProductGroupTask batchProductGroupTask = new BatchProductGroupTask().data(
            new BatchProductGroup().serviceAgreement(new ServiceAgreement().externalId("sa_benedict").internalId("sa-internal-id"))
        );
        batchProductGroupTask.setIngestionMode(BatchProductGroupTask.IngestionMode.UPDATE);

        Map<BusinessFunctionGroup, List<BaseProductGroup>> baseProductGroupMap = new HashMap<>();
        baseProductGroupMap.put(new BusinessFunctionGroup().id("business-function-group-id-1"), Collections.emptyList());

        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> usersPermissions = new HashMap<>();
        usersPermissions.put(
            new User().internalId("user-internal-id").externalId("benedict"),
            baseProductGroupMap
        );

        List<PresentationAssignUserPermissions> expectedPermissions = Collections.singletonList(
            new PresentationAssignUserPermissions()
                .externalUserId("benedict")
                .externalServiceAgreementId("sa_benedict")
                .functionGroupDataGroups(Arrays.asList(
                    new PresentationFunctionGroupDataGroup().functionGroupIdentifier(
                        new PresentationIdentifier().idIdentifier("system-group-id-1")
                    ).dataGroupIdentifiers(Collections.emptyList()),
                    new PresentationFunctionGroupDataGroup().functionGroupIdentifier(
                        new PresentationIdentifier().idIdentifier("system-group-id-2")
                    ).dataGroupIdentifiers(Collections.emptyList())
                ))
        );

        when(functionGroupApi.getFunctionGroups("sa-internal-id"))
            .thenReturn(Flux.just());

        when(userQueryApi.getPersistenceApprovalPermissions("user-internal-id", "sa-internal-id"))
            .thenReturn(Mono.just(new PersistenceApprovalPermissions().items(Arrays.asList(
                new PersistenceApprovalPermissionsGetResponseBody().functionGroupId("system-group-id-1").dataGroupIds(Collections.emptyList()),
                new PersistenceApprovalPermissionsGetResponseBody().functionGroupId("system-group-id-2").dataGroupIds(Collections.emptyList())
            ))));

        when(accessControlUsersApi.putAssignUserPermissions(expectedPermissions))
            .thenReturn(Flux.just(
                new BatchResponseItemExtended().resourceId("resource-id").status(HTTP_STATUS_OK).errors(Collections.emptyList())
            ));

        // When
        BatchProductGroupTask result = subject.assignPermissionsBatch(batchProductGroupTask, usersPermissions)
            .block();

        // Then
        Assertions.assertSame(batchProductGroupTask, result);

        verify(accessControlUsersApi).putAssignUserPermissions(expectedPermissions);
    }

    @Test
    void assignPermissionsBatchIngestionModeReplace() {
        // Given
        BatchProductGroupTask batchProductGroupTask = new BatchProductGroupTask().data(
            new BatchProductGroup().serviceAgreement(new ServiceAgreement().externalId("sa_benedict").internalId("sa-internal-id"))
        );
        batchProductGroupTask.setIngestionMode(BatchProductGroupTask.IngestionMode.REPLACE);

        Map<BusinessFunctionGroup, List<BaseProductGroup>> baseProductGroupMap = new HashMap<>();
        baseProductGroupMap.put(new BusinessFunctionGroup().id("business-function-group-id-1"), Collections.emptyList());

        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> usersPermissions = new HashMap<>();
        usersPermissions.put(
            new User().internalId("user-internal-id").externalId("benedict"),
            baseProductGroupMap
        );

        List<PresentationAssignUserPermissions> expectedPermissions = Collections.singletonList(
            new PresentationAssignUserPermissions()
                .externalUserId("benedict")
                .externalServiceAgreementId("sa_benedict")
                .functionGroupDataGroups(Collections.singletonList(
                    new PresentationFunctionGroupDataGroup().functionGroupIdentifier(
                        new PresentationIdentifier().idIdentifier("business-function-group-id-1")
                    ).dataGroupIdentifiers(Collections.emptyList())
                ))
        );

        when(accessControlUsersApi.putAssignUserPermissions(expectedPermissions))
            .thenReturn(Flux.just(
                new BatchResponseItemExtended().resourceId("resource-id").status(HTTP_STATUS_OK).errors(Collections.emptyList())
            ));

        // When
        BatchProductGroupTask result = subject.assignPermissionsBatch(batchProductGroupTask, usersPermissions)
            .block();

        // Then
        Assertions.assertSame(batchProductGroupTask, result);

        verify(accessControlUsersApi).putAssignUserPermissions(expectedPermissions);
    }

    @Test
    void assignPermissionsBatchEmptyExistingPermissions() {
        // Given
        BatchProductGroupTask batchProductGroupTask = new BatchProductGroupTask().data(
            new BatchProductGroup().serviceAgreement(new ServiceAgreement().externalId("sa_benedict").internalId("sa-internal-id"))
        );
        batchProductGroupTask.setIngestionMode(BatchProductGroupTask.IngestionMode.UPDATE);

        Map<BusinessFunctionGroup, List<BaseProductGroup>> baseProductGroupMap = new HashMap<>();
        baseProductGroupMap.put(new BusinessFunctionGroup().id("business-function-group-id-1"), Collections.emptyList());

        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> usersPermissions = new HashMap<>();
        usersPermissions.put(
            new User().internalId("user-internal-id").externalId("benedict"),
            baseProductGroupMap
        );

        List<PresentationAssignUserPermissions> expectedPermissions = Collections.singletonList(
            new PresentationAssignUserPermissions()
                .externalUserId("benedict")
                .externalServiceAgreementId("sa_benedict")
                .functionGroupDataGroups(Collections.singletonList(
                    new PresentationFunctionGroupDataGroup().functionGroupIdentifier(
                        new PresentationIdentifier().idIdentifier("business-function-group-id-1")
                    ).dataGroupIdentifiers(Collections.emptyList())
                ))
        );

        when(functionGroupApi.getFunctionGroups("sa-internal-id"))
            .thenReturn(Flux.just(
                new FunctionGroupItem().id("system-group-id-1").name("SYSTEM_FUNCTION_GROUP").type(FunctionGroupItem.TypeEnum.SYSTEM),
                new FunctionGroupItem().id("system-group-id-2").name("Full access").type(FunctionGroupItem.TypeEnum.TEMPLATE)
            ));

        when(userQueryApi.getPersistenceApprovalPermissions("user-internal-id", "sa-internal-id"))
            .thenReturn(Mono.just(new PersistenceApprovalPermissions().items(Collections.emptyList())));

        when(accessControlUsersApi.putAssignUserPermissions(expectedPermissions))
            .thenReturn(Flux.just(
                new BatchResponseItemExtended().resourceId("resource-id").status(HTTP_STATUS_OK).errors(Collections.emptyList())
            ));

        // When
        BatchProductGroupTask result = subject.assignPermissionsBatch(batchProductGroupTask, usersPermissions)
            .block();

        // Then
        Assertions.assertSame(batchProductGroupTask, result);

        verify(accessControlUsersApi).putAssignUserPermissions(expectedPermissions);
    }

    private void thenRegularUsersUpdateCall(String expectedSaExId, PresentationAction expectedAction,
                                            String... expectedUserIds) {
        PresentationServiceAgreementUsersBatchUpdate expectedRegularUserAddUpdate =
            new PresentationServiceAgreementUsersBatchUpdate().action(expectedAction)
                .users(Stream.of(expectedUserIds).map(userId -> new PresentationServiceAgreementUserPair()
                    .externalUserId(userId).externalServiceAgreementId(expectedSaExId)).collect(Collectors.toList()));
        verify(serviceAgreementApi, times(1))
            .putPresentationServiceAgreementUsersBatchUpdate(eq(expectedRegularUserAddUpdate));
    }

    private void thenUpdateParticipantsCall(InOrder validator, String expectedSaExId, PresentationAction expectedAction,
                                            ExpectedParticipantUpdate... expectedParticipants) {
        PresentationParticipantBatchUpdate expectedRequest = new PresentationParticipantBatchUpdate()
            .participants(Stream.of(expectedParticipants).map(ep -> new PresentationParticipantPutBody()
                .externalServiceAgreementId(expectedSaExId).externalParticipantId(ep.exId)
                .sharingAccounts(ep.sharingAccounts).sharingUsers(ep.sharingAccounts).action(expectedAction))
                .collect(Collectors.toList()));
        validator.verify(serviceAgreementApi).putPresentationIngestServiceAgreementParticipants(eq(expectedRequest));
    }

    private ServiceAgreement buildInputServiceAgreement(String saInternalId, String saExternalId, String description,
                                                        String name, LocalDate validFromDate, String validFromTime,
                                                        LocalDate validUntilDate, String validUntilTime) {
        return new ServiceAgreement()
            .internalId(saInternalId)
            .externalId(saExternalId)
            .description(description)
            .status(ENABLED)
            .name(name)
            .validFromDate(validFromDate)
            .validFromTime(validFromTime)
            .validUntilDate(validUntilDate)
            .validUntilTime(validUntilTime);
    }

    @AllArgsConstructor
    private static class ExpectedParticipantUpdate {
        String exId;
        boolean sharingAccounts;
        boolean sharingUsers;
    }

}