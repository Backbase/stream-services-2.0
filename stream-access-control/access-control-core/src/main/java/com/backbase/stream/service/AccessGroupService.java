package com.backbase.stream.service;

import static com.backbase.dbs.accesscontrol.api.service.v2.model.BatchResponseItemExtended.StatusEnum.HTTP_STATUS_OK;
import static com.backbase.stream.legalentity.model.ServiceAgreementUserAction.ActionEnum.ADD;
import static com.backbase.stream.legalentity.model.ServiceAgreementUserAction.ActionEnum.REMOVE;
import static org.springframework.util.StringUtils.isEmpty;

import com.backbase.dbs.accesscontrol.api.service.v2.DataGroupApi;
import com.backbase.dbs.accesscontrol.api.service.v2.DataGroupsApi;
import com.backbase.dbs.accesscontrol.api.service.v2.FunctionGroupApi;
import com.backbase.dbs.accesscontrol.api.service.v2.FunctionGroupsApi;
import com.backbase.dbs.accesscontrol.api.service.v2.ServiceAgreementApi;
import com.backbase.dbs.accesscontrol.api.service.v2.ServiceAgreementQueryApi;
import com.backbase.dbs.accesscontrol.api.service.v2.ServiceAgreementsApi;
import com.backbase.dbs.accesscontrol.api.service.v2.UserQueryApi;
import com.backbase.dbs.accesscontrol.api.service.v2.UsersApi;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ArrangementPrivilegesGetResponseBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.BatchResponseItemExtended;
import com.backbase.dbs.accesscontrol.api.service.v2.model.DataGroupItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.DataGroupItemSystemBase;
import com.backbase.dbs.accesscontrol.api.service.v2.model.FunctionGroupItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.Functiongroupupdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.IdItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ListOfFunctionGroupsWithDataGroups;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PersistenceApprovalPermissions;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationAction;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationApprovalStatus;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationAssignUserPermissions;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationDataGroupItemPutRequestBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationDataGroupUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationFunctionDataGroup;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationFunctionGroupDataGroup;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationFunctionGroupPutRequestBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationGenericObjectId;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationIdentifier;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationIngestFunctionGroup;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationItemIdentifier;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationParticipantBatchUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationPermission;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationPermissionFunctionGroupUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationSearchDataGroupsRequest;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationServiceAgreementIdentifier;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationServiceAgreementUserPair;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationServiceAgreementUsersBatchUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementParticipantsGetResponseBody;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementUsersQuery;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServicesAgreementIngest;
import com.backbase.dbs.user.api.service.StringUtil;
import com.backbase.dbs.user.api.service.v2.UserManagementApi;
import com.backbase.dbs.user.api.service.v2.model.GetUser;
import com.backbase.stream.legalentity.model.ApprovalStatus;
import com.backbase.stream.legalentity.model.AssignedPermission;
import com.backbase.stream.legalentity.model.BaseProductGroup;
import com.backbase.stream.legalentity.model.BusinessFunction;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.JobRole;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.legalentity.model.LegalEntityParticipant;
import com.backbase.stream.legalentity.model.Privilege;
import com.backbase.stream.legalentity.model.ProductGroup;
import com.backbase.stream.legalentity.model.ReferenceJobRole;
import com.backbase.stream.legalentity.model.ServiceAgreement;
import com.backbase.stream.legalentity.model.ServiceAgreementUserAction;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.mapper.AccessGroupMapper;
import com.backbase.stream.mapper.ParticipantMapper;
import com.backbase.stream.product.task.BatchProductGroupTask;
import com.backbase.stream.product.task.ProductGroupTask;
import com.backbase.stream.product.utils.BatchResponseUtils;
import com.backbase.stream.product.utils.StreamUtils;
import com.backbase.stream.worker.exception.StreamTaskException;
import com.backbase.stream.worker.model.StreamTask;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.factory.Mappers;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Access Group Service provide access to Access Control, Data Groups and Function Groups from a single service.
 */
@Slf4j
@RequiredArgsConstructor
public class AccessGroupService {

    public static final String CREATE_ACCESS_GROUP = "create-access-group";
    private static final String SERVICE_AGREEMENT = "service-agreement";
    public static final String ACCESS_GROUP = "access-group";
    public static final String REJECTED = "rejected";
    public static final String CREATED = "created";
    public static final String FUNCTION_GROUP = "function-group";
    public static final String JOB_ROLE = "job-role";
    public static final String SETUP_FUNCTION_GROUP = "setup-function-group";
    public static final String CREATE_FUNCTION_GROUP = "create-function-group";
    public static final String SETUP_JOB_ROLE = "setup-job-role";
    private static final String FAILED = "failed";

    @NonNull
    private final UserManagementApi usersApi;
    @NonNull
    private final UserQueryApi userQueryApi;
    @NonNull
    private final UsersApi accessControlUsersApi;
    @NonNull
    private final DataGroupApi dataGroupApi;
    @NonNull
    private final DataGroupsApi dataGroupsApi;
    @NonNull
    private final FunctionGroupApi functionGroupApi;
    @NonNull
    private final FunctionGroupsApi functionGroupsApi;
    @NonNull
    private final ServiceAgreementQueryApi serviceAgreementQueryApi;
    @NonNull
    private final ServiceAgreementApi serviceAgreementApi;
    @NonNull
    private final ServiceAgreementsApi serviceAgreementsApi;

    private final AccessGroupMapper accessGroupMapper = Mappers.getMapper(AccessGroupMapper.class);

    private final ParticipantMapper participantMapper = Mappers.getMapper(ParticipantMapper.class);

    /**
     * Create Service Agreement.
     *
     * @param streamTask       The task invoking the call
     * @param serviceAgreement Service Agreement
     * @return Created Service Agreement
     */
    public Mono<ServiceAgreement> createServiceAgreement(StreamTask streamTask, ServiceAgreement serviceAgreement) {
        ServicesAgreementIngest servicesAgreementIngest = accessGroupMapper.toPresentation(serviceAgreement);
        return serviceAgreementApi.postServiceAgreementIngest(servicesAgreementIngest)
            .onErrorResume(WebClientResponseException.class, throwable -> {
                streamTask.error(SERVICE_AGREEMENT, "create", "failed", serviceAgreement.getExternalId(),
                    "", throwable, throwable.getResponseBodyAsString(), "Failed to create Service Agreement");
                return Mono.error(new StreamTaskException(streamTask, throwable, "Failed to create Service Agreement"));
            })
            .zipWith(Mono.just(serviceAgreement), storeIdInServiceAgreement());
    }

    /**
     * Get Service Agreement by external ID.
     *
     * @param externalId External Service Agreement ID
     * @return Service Agreement
     */
    public Mono<ServiceAgreement> getServiceAgreementByExternalId(String externalId) {
        log.info("setting up getting Service Agreement with external Id: {} flow", externalId);
        return serviceAgreementApi.getServiceAgreementExternalId(externalId)
            .doOnNext(serviceAgreementItem -> log
                .info("Service Agreement: {} found", serviceAgreementItem.getExternalId()))
            .onErrorResume(WebClientResponseException.NotFound.class, throwable -> {
                log.info("Service Agreement with external Id {} not found. Request:[{}] {}  Response: {}",
                    externalId, throwable.getRequest().getMethod(), throwable.getRequest().getURI(),
                    throwable.getResponseBodyAsString());
                return Mono.empty();
            })
            .map(accessGroupMapper::toStream);
    }

    /**
     * Update Service Agreement.
     *
     * @param streamTask          Stream task
     * @param serviceAgreement    Service agreement
     * @param regularUsersActions Service Agreement regular users actions
     * @return Service Agreement
     */
    public Mono<ServiceAgreement> updateServiceAgreementAssociations(StreamTask streamTask, ServiceAgreement serviceAgreement,
                                                                     List<ServiceAgreementUserAction> regularUsersActions) {
        log.info("setting up Service Agreement with external Id: {}, associations update flow", serviceAgreement.getExternalId());

        Mono<Map<LegalEntityParticipant.ActionEnum, Mono<ServiceAgreement>>> updateParticipantsByActionMono =
            updateParticipants(streamTask, serviceAgreement);
        return updateParticipantsByActionMono.flatMap(updateParticipantsByAction ->
                updateParticipantsByAction.get(LegalEntityParticipant.ActionEnum.ADD)
                    .then(updateServiceAgreementRegularUsers(streamTask, serviceAgreement, regularUsersActions))
                    .then(updateParticipantsByAction.get(LegalEntityParticipant.ActionEnum.REMOVE))
            );
    }

    /**
     * Update regular users of service agreement.
     *
     * @param streamTask          Stream task
     * @param serviceAgreement    Service agreement
     * @param actions Service Agreement regular users actions
     * @return Service Agreement
     */
    public Mono<ServiceAgreement> updateServiceAgreementRegularUsers(StreamTask streamTask,
                                                                      ServiceAgreement serviceAgreement,
                                                                      List<ServiceAgreementUserAction> actions) {
        if (CollectionUtils.isEmpty(actions)) {
            return Mono.just(serviceAgreement);
        }

        log.debug("setting up Service Agreement's regular users association flow.");

        return enrichUsersWithInternalUserId(streamTask, actions.stream()
                .map(ServiceAgreementUserAction::getUserProfile).map(JobProfileUser::getUser)
                .collect(Collectors.toList()))
            .flatMap(task -> getServiceAgreementUsers(serviceAgreement))
            .flatMapMany(existingUsers -> {

                Map<String, String> existingMap = existingUsers.getUserIds().stream()
                    .collect(Collectors.toMap(Function.identity(), Function.identity()));

                Predicate<ServiceAgreementUserAction> existing = ac ->
                    ac.getUserProfile().getUser().getInternalId() != null
                    && existingMap.get(ac.getUserProfile().getUser().getInternalId()) != null;

                Predicate<ServiceAgreementUserAction> notExistingToAdd = existing.negate()
                    .and(ac -> ac.getAction() == ADD);

                Predicate<ServiceAgreementUserAction> existingToRemove = existing
                    .and(ac -> ac.getAction() == REMOVE);

                List<ServiceAgreementUserAction> toAffect = actions.stream()
                    .filter(notExistingToAdd.or(existingToRemove))
                    .collect(Collectors.toList());

                List<PresentationServiceAgreementUsersBatchUpdate> actionsGroups =
                    buildServiceAgreementUserActionGroups(serviceAgreement, toAffect);

                log.debug("associating users to service agreement {}, request: {}", serviceAgreement.getExternalId(),
                    Arrays.toString(actionsGroups.toArray()));

                return Flux.fromIterable(actionsGroups);
            })
            .flatMap(actionGroup -> {
                log.info("Update regular users of Service Agreement with external Id: {}",
                    serviceAgreement.getExternalId());
                return serviceAgreementApi.putPresentationServiceAgreementUsersBatchUpdate(actionGroup)
                    .onErrorResume(WebClientResponseException.class,
                        e -> Mono.error(new StreamTaskException(streamTask, e,
                            MessageFormat
                                .format("Failed to update user for Service Agreement with external id: {0}",
                                    serviceAgreement.getExternalId()))))
                    .collectList();
            })
            .collectList()
            .flatMap(lists -> Mono.just(lists.stream().flatMap(List::stream).collect(Collectors.toList())))
            .flatMap(list -> {
                list.stream().filter(r -> r.getStatus() != HTTP_STATUS_OK).forEach(r -> {
                    String errorMessage = "error associating user to Service Agreement" + r.toString();
                    log.error(errorMessage);
                    streamTask.error(SERVICE_AGREEMENT, "update-regular-users", "failed",
                        serviceAgreement.getExternalId(), serviceAgreement.getInternalId(), errorMessage);
                    streamTask.setState(StreamTask.State.FAILED);
                });
                if (streamTask.isFailed()) {
                    return Mono.error(
                        new StreamTaskException(streamTask, "failed to associate regular users to Service Agreement"));
                }
                return Mono.just(serviceAgreement);
            });
    }

    private <T extends StreamTask> Mono<T> enrichUsersWithInternalUserId(T task, List<User> users) {
        List<User> usersMissingInternalId = users.stream().filter(u -> u.getInternalId() == null)
            .collect(Collectors.toList());
        return Flux.fromIterable(usersMissingInternalId)
            .flatMap(u -> getUserByExternalId(u.getExternalId(), true).doOnNext(gu -> u.setInternalId(gu.getId())))
            .collectList()
            .thenReturn(task);
    }

    private Mono<ServiceAgreementUsersQuery> getServiceAgreementUsers(ServiceAgreement serviceAgreement) {
        return serviceAgreementQueryApi.getServiceAgreementUsers(serviceAgreement.getInternalId())
            .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                log.info("users not found");
                return Mono.just(new ServiceAgreementUsersQuery());
            });
    }

    @NotNull
    private List<PresentationServiceAgreementUsersBatchUpdate> buildServiceAgreementUserActionGroups(
        ServiceAgreement serviceAgreement, List<ServiceAgreementUserAction> actions) {
        return actions.stream()
                .filter(saUa -> saUa.getAction() != null)
                .collect(Collectors.groupingBy(ServiceAgreementUserAction::getAction))
                .entrySet().stream().map(actionGroup ->
                    new PresentationServiceAgreementUsersBatchUpdate()
                        .action(PresentationAction.valueOf(actionGroup.getKey().getValue()))
                        .users(actionGroup.getValue().stream().map(ServiceAgreementUserAction::getUserProfile)
                            .map(JobProfileUser::getUser).map(User::getExternalId)
                            .map(id -> new PresentationServiceAgreementUserPair()
                                .externalServiceAgreementId(serviceAgreement.getExternalId())
                                .externalUserId(id)).collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    /**
     * Update Service Agreement's participants.
     *
     * @param streamTask       Stream task
     * @param serviceAgreement Service agreement
     * @return Service Agreement
     */
    public Mono<Map<LegalEntityParticipant.ActionEnum, Mono<ServiceAgreement>>> updateParticipants(
            StreamTask streamTask, ServiceAgreement serviceAgreement) {

        Map<LegalEntityParticipant.ActionEnum, Mono<ServiceAgreement>> monoMap =
            new EnumMap<>(LegalEntityParticipant.ActionEnum.class);
        if (CollectionUtils.isEmpty(serviceAgreement.getParticipants())) {
            monoMap.put(LegalEntityParticipant.ActionEnum.REMOVE, Mono.just(serviceAgreement));
            monoMap.put(LegalEntityParticipant.ActionEnum.ADD, Mono.just(serviceAgreement));
            return Mono.just(monoMap);
        }
        serviceAgreement.getParticipants().stream().forEach(p -> {
            if (p.getAction() == null) {
                p.setAction(LegalEntityParticipant.ActionEnum.ADD);
            }
        });

        return Mono.fromCallable(() -> {
            log.info("Updating participants of Service Agreement with external Id: {}",
                serviceAgreement.getExternalId());
            return serviceAgreement;
        })
            .flatMap(sa -> getServiceAgreementParticipants(streamTask, serviceAgreement)
                .collectMap(ServiceAgreementParticipantsGetResponseBody::getExternalId, Function.identity()))
            .flatMap(existingMap -> {

                log.debug("existing participants:" + Arrays.asList(existingMap.values().toArray()));

                List<LegalEntityParticipant> toRemove = serviceAgreement.getParticipants().stream()
                    .filter(p -> existingMap.get(p.getExternalId()) != null
                        && p.getAction() == LegalEntityParticipant.ActionEnum.REMOVE)
                    .collect(Collectors.toList());

                List<LegalEntityParticipant> toAdd = serviceAgreement.getParticipants().stream()
                    .filter(p -> existingMap.get(p.getExternalId()) == null
                        && p.getAction() == LegalEntityParticipant.ActionEnum.ADD)
                    .collect(Collectors.toList());

                PresentationParticipantBatchUpdate removeRequest =
                    participantMapper.toPresentation(new ServiceAgreement()
                        .externalId(serviceAgreement.getExternalId()).participants(toRemove));

                PresentationParticipantBatchUpdate addRequest = participantMapper.toPresentation(new ServiceAgreement()
                    .externalId(serviceAgreement.getExternalId()).participants(toAdd));

                monoMap.put(LegalEntityParticipant.ActionEnum.ADD, putServiceAgreementParticipants(streamTask,
                    serviceAgreement, addRequest));
                monoMap.put(LegalEntityParticipant.ActionEnum.REMOVE, putServiceAgreementParticipants(streamTask,
                    serviceAgreement, removeRequest));

                return Mono.just(monoMap);
            });
    }

    private Flux<ServiceAgreementParticipantsGetResponseBody> getServiceAgreementParticipants(
        StreamTask streamTask, ServiceAgreement serviceAgreement) {
        return serviceAgreementsApi.getServiceAgreementParticipants(serviceAgreement.getInternalId())
            .onErrorResume(WebClientResponseException.NotFound.class, e -> Flux.empty())
            .onErrorResume(WebClientResponseException.class, e -> {
                streamTask.error("participant", "update-participant", "failed",
                    serviceAgreement.getExternalId(), serviceAgreement.getInternalId(), e, e.getMessage(),
                    "error retrieving participants for Service Agreement %s", serviceAgreement.getExternalId());
                return Mono.error(new StreamTaskException(streamTask, MessageFormat
                    .format("error retrieving participants for Service Agreement {0}",
                        serviceAgreement.getExternalId())));
            });
    }

    private Mono<ServiceAgreement> putServiceAgreementParticipants(StreamTask streamTask,
                                                                   ServiceAgreement serviceAgreement,
                                                                   PresentationParticipantBatchUpdate request) {
        if (CollectionUtils.isEmpty(request.getParticipants())) {
            return Mono.just(serviceAgreement);
        }

        log.debug("updating participants: " + request.toString());

        return serviceAgreementApi.putPresentationIngestServiceAgreementParticipants(request)
            .onErrorResume(WebClientResponseException.class, e -> {
                streamTask.error("participant", "update-participants", "failed",
                    serviceAgreement.getExternalId(), serviceAgreement.getInternalId(), e, e.getResponseBodyAsString(),
                    "Failed to update participants");
                return Mono.error(new StreamTaskException(streamTask, e,
                    MessageFormat
                        .format("Failed to update participants for Service Agreement with external id: {0}",
                            serviceAgreement.getExternalId())));
            })
            .collectList()
            .flatMap(resultList -> {
                resultList.stream().forEach(r -> {
                    if (r.getStatus() != HTTP_STATUS_OK) {
                        streamTask.error("participant", "update-participant", "failed", r.getResourceId(),
                            null, "Error updating Participant {} for Service Agreement: {}", r.getResourceId(),
                            serviceAgreement.getExternalId());
                        log.error("Error updating Participant {} for Service Agreement: {}", r.getResourceId(),
                            serviceAgreement.getExternalId());
                        streamTask.setState(StreamTask.State.FAILED);
                    }
                });
                if (streamTask.isFailed()) {
                    return Mono.error(new StreamTaskException(streamTask, "Failed to update participant"));
                }
                return Mono.just(serviceAgreement);
            });
    }

    /**
     * Retrieve user by external id.
     *
     * @param externalId user external id
     * @param skipHierarchyCheck skip hierarchy check
     * @return User
     */
    public Mono<GetUser> getUserByExternalId(String externalId, boolean skipHierarchyCheck) {
        return usersApi.getUserByExternalId(externalId, skipHierarchyCheck)
            .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

    private BiFunction<IdItem, ServiceAgreement, ServiceAgreement> storeIdInServiceAgreement() {
        return (idItem, serviceAgreement1) -> {
            serviceAgreement1.setInternalId(idItem.getId());
            log.info("Created Service Agreement: {} with id: {}", serviceAgreement1.getName(), idItem.getId());
            return serviceAgreement1;
        };
    }

    public Mono<LegalEntity> setAdministrators(LegalEntity legalEntity) {

        List<PresentationServiceAgreementUserPair> userPairs = legalEntity.getAdministrators().stream().map(user -> {
            PresentationServiceAgreementUserPair usersItem = new PresentationServiceAgreementUserPair();
            usersItem.setExternalServiceAgreementId(legalEntity.getMasterServiceAgreement().getExternalId());
            usersItem.setExternalUserId(user.getExternalId());
            return usersItem;
        }).collect(Collectors.toList());

        PresentationServiceAgreementUsersBatchUpdate presentationServiceAgreementUsersBatchUpdate = new PresentationServiceAgreementUsersBatchUpdate();
        presentationServiceAgreementUsersBatchUpdate.users(userPairs);
        presentationServiceAgreementUsersBatchUpdate.setAction(PresentationAction.ADD);

        return serviceAgreementApi.putPresentationServiceAgreementAdminsBatchUpdate(presentationServiceAgreementUsersBatchUpdate)
            .doOnError(WebClientResponseException.BadRequest.class, this::handleError)
            .collectList()
            .map(batchResponseItemExtendeds -> {
                log.info("Setup Bank Admins: {}", batchResponseItemExtendeds);
                return legalEntity;
            });
    }

    /**
     * Assign permissions between user, business function groups and product groups for a Service Agreement.
     *
     * @param streamTask     Stream Task
     * @param jobProfileUser The user and business functions to assign permissions for
     * @return An approval status if approvals is enabled or an empty mojo on completion.
     */
    public Mono<JobProfileUser> assignPermissions(ProductGroupTask streamTask, JobProfileUser jobProfileUser) {
        streamTask.info(ACCESS_GROUP, "assign-product-permissions", "", streamTask.getProductGroup().getName(), null,
            "Assigning permissions for Data Group for: %s and Service Agreement: %s", streamTask.getData().getName(),
            streamTask.getData().getServiceAgreement().getExternalId());

        ProductGroup productGroup = streamTask.getData();
        ServiceAgreement serviceAgreement = productGroup.getServiceAgreement();

        ListOfFunctionGroupsWithDataGroups functionGroups = functionGroupsWithDataGroup(jobProfileUser, productGroup);

        return assignPermissions(streamTask, serviceAgreement, jobProfileUser, functionGroups);
    }

    /**
     * Assign permissions between user, business function groups for a Service Agreement.
     *
     * @param streamTask     Stream Task
     * @param jobProfileUser The user and business functions to assign permissions for
     * @return An approval status if approvals is enabled or an empty mojo on completion.
     */
    public Mono<JobProfileUser> assignPermissions(StreamTask streamTask, ServiceAgreement serviceAgreement,
                                                  JobProfileUser jobProfileUser, ListOfFunctionGroupsWithDataGroups functionGroups) {

        streamTask.info(ACCESS_GROUP, "assign-permissions", "",
            streamTask.getName(), null,
            "Assigning permissions for Service Agreement: %s", serviceAgreement.getExternalId());

        String serviceAgreementId = serviceAgreement.getInternalId();
        String userId = jobProfileUser.getUser().getInternalId();

        return serviceAgreementsApi.putAssignUsersPermissions(serviceAgreementId, userId, functionGroups)
            .onErrorResume(WebClientResponseException.class, e -> {
                streamTask.error(ACCESS_GROUP, "assign-permissions", "failed", serviceAgreement.getExternalId(),
                    serviceAgreementId, e, e.getResponseBodyAsString(), "Failed to to assign permissions");
                return Mono.error(new StreamTaskException(streamTask, e,
                    "Failed to Assign permissions: " + e.getResponseBodyAsString()));
            })
            .flatMap(this::processApprovalStatus)
            .map(jobProfileUser::approvalStatus);

    }

    public Mono<BatchProductGroupTask> assignPermissionsBatch(BatchProductGroupTask task, Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> usersPermissions) {

        List<PresentationAssignUserPermissions> request = usersPermissions.keySet().stream()
            .map(user -> new PresentationAssignUserPermissions()
                .externalUserId(user.getExternalId())
                .externalServiceAgreementId(task.getData().getServiceAgreement().getExternalId())
                .functionGroupDataGroups(
                    usersPermissions.get(user).keySet().stream()
                        .map(bfg -> new PresentationFunctionGroupDataGroup()
                            .functionGroupIdentifier(mapId(bfg.getId()))
                            .dataGroupIdentifiers(
                                usersPermissions.get(user).get(bfg).stream()
                                    .map(pg -> mapId(pg.getInternalId()))
                                    .collect(Collectors.toList())))
                        .collect(Collectors.toList())))
            .collect(Collectors.toList());

        if (request.stream().anyMatch(usersPermission -> usersPermission.getFunctionGroupDataGroups().stream()
            .anyMatch(this::hasDataGroupIdentifiers))) {
            log.error("You are assigning permissions without data groups!!");
        }


        return Mono.just(request)
            .flatMap(userPermissionsRequest -> {
                if (task.getIngestionMode().equals(BatchProductGroupTask.IngestionMode.REPLACE)) {
                    task.info(ACCESS_GROUP, "assign-permissions", "", "", null, "Replacing assigned permissions for users: %s with: %s", prettyPrintExternalIds(userPermissionsRequest), prettyPrintDataGroups(userPermissionsRequest));
                    return Mono.just(userPermissionsRequest);
                }

                return getAssociatedSystemFunctionsIds(task)
                    .flatMap(systemFunctionGroupIds -> mergeUserPermissions(task, usersPermissions.keySet(), request, systemFunctionGroupIds))
                    .map(userPermissionsList -> {
                        log.info("Updated assigned permissions for users: {} with: {}", prettyPrintExternalIds(userPermissionsList), prettyPrintDataGroups(userPermissionsList));
                        return userPermissionsList;
                    });
            })
            .flatMap(userPermissionsList -> {
                task.info(ACCESS_GROUP, "assign-permissions", task.getName(), null, task.getId(), "Assigning permissions: %s", userPermissionsList.stream().map(this::prettyPrintUserAssignedPermissions).collect(Collectors.joining(",")));
                return accessControlUsersApi.putAssignUserPermissions(userPermissionsList)
                    .map(r -> BatchResponseUtils.checkBatchResponseItem(r, "Permissions Update", r.getStatus().toString(), r.getResourceId(), r.getErrors()))
                    .doOnNext(r -> task.info(ACCESS_GROUP, "assign-permissions", r.getExternalServiceAgreementId(), null, "Assigned permissions for: %s and Service Agreement: %s", r.getResourceId(), r.getExternalServiceAgreementId()))
                    .onErrorResume(WebClientResponseException.class, e -> {
                        task.error(ACCESS_GROUP, "assign-permissions", "failed", task.getData().getServiceAgreement().getExternalId(), task.getData().getServiceAgreement().getInternalId(), e, e.getResponseBodyAsString(), "Failed to execute Batch Permissions assignment request.");
                        return Mono.error(new StreamTaskException(task, e, "Failed  to assign permissions: " + e.getResponseBodyAsString()));
                    })
                    .collectList();
            })
            .thenReturn(task);
    }

    /**
     * Retrieves function groups by service agreement id, filter any non-system and convert resulting list into a set of ids.
     */
    private Mono<Set<String>> getAssociatedSystemFunctionsIds(BatchProductGroupTask task) {
        return functionGroupApi.getFunctionGroups(task.getData().getServiceAgreement().getInternalId())
            .onErrorResume(WebClientResponseException.class, e -> {
                task.error(ACCESS_GROUP, "assign-permissions", "failed", task.getData().getServiceAgreement().getExternalId(), task.getData().getServiceAgreement().getInternalId(), e, e.getResponseBodyAsString(), "Failed to fetch function groups");
                return Mono.error(new StreamTaskException(task, e, "Failed to fetch function groups: " + e.getResponseBodyAsString()));
            })
            .collectList()
            .switchIfEmpty(Mono.just(Collections.emptyList()))
            .map(functionGroups  -> functionGroups.stream()
                .filter(functionGroup -> FunctionGroupItem.TypeEnum.SYSTEM.equals(functionGroup.getType()))
                .map(FunctionGroupItem::getId)
                .collect(Collectors.toSet())
            );
    }

    /**
     * Request each user permissions and add those to the request.
     *
     * @param task - Current task
     * @param users - All users mentioned in permissions update
     * @param request - Input user permissions list
     * @param systemFunctionGroupIds - A set of system function group ids that belong to service agreement
     */
    private Mono<List<PresentationAssignUserPermissions>> mergeUserPermissions(BatchProductGroupTask task,
                                                                               Collection<User> users,
                                                                               List<PresentationAssignUserPermissions> request,
                                                                               Set<String> systemFunctionGroupIds) {
        return Flux.fromIterable(users)
            .flatMap(user -> userQueryApi.getPersistenceApprovalPermissions(user.getInternalId(), task.getData().getServiceAgreement().getInternalId())
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .map(PersistenceApprovalPermissions::getItems)
                .map(items -> items.stream()
                    // System Function Group should not be part of PUT permissions request as it cannot be modified
                    .filter(existingPermission -> !systemFunctionGroupIds.contains(existingPermission.getFunctionGroupId()))
                    .collect(Collectors.toList())
                )
                .map(existingUserPermissions -> {
                    log.info("Retrieved permissions for user with externalId {} : {}", user.getExternalId(), existingUserPermissions.stream().map(p -> p.getFunctionGroupId() + " : [" + p.getDataGroupIds() + "] ").collect(Collectors.toList()));

                    PresentationAssignUserPermissions requestUserPermissions = request.stream()
                        .filter(up -> up.getExternalUserId().equalsIgnoreCase(user.getExternalId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Permissions for user not present in request?"));

                    PresentationAssignUserPermissions mergedUserPermissions = new PresentationAssignUserPermissions();
                    mergedUserPermissions.setExternalServiceAgreementId(requestUserPermissions.getExternalServiceAgreementId());
                    mergedUserPermissions.setExternalUserId(requestUserPermissions.getExternalUserId());

                    if (existingUserPermissions.isEmpty()) {
                        mergedUserPermissions.setFunctionGroupDataGroups(requestUserPermissions.getFunctionGroupDataGroups());
                    } else {
                        // merge user permissions in  DBS with the ones already  in request.
                        existingUserPermissions.forEach(userPermission -> {
                            String functionGroupId = userPermission.getFunctionGroupId();
                            Set<String> dataGroupIds = new HashSet<>();

                            if (userPermission.getDataGroupIds() != null) {
                                dataGroupIds.addAll(userPermission.getDataGroupIds());
                            }

                            Optional<PresentationFunctionGroupDataGroup> requestedFunctionGroupOptional = requestUserPermissions.getFunctionGroupDataGroups().stream()
                                .filter(requestFunctionGroup -> hasTheSameFunctionGroupId(functionGroupId, requestFunctionGroup))
                                .findFirst();

                            // If requested function group is already ingested, merge the request and existing function group
                            if (requestedFunctionGroupOptional.isPresent()) {
                                PresentationFunctionGroupDataGroup requestedFunctionGroup = requestedFunctionGroupOptional.get();

                                if (requestedFunctionGroup.getDataGroupIdentifiers() != null) {
                                    dataGroupIds.addAll(requestedFunctionGroup.getDataGroupIdentifiers().stream().map(
                                        PresentationIdentifier::getIdIdentifier).collect(Collectors.toList()));
                                }
                            }

                            // Transform existing permissions to PresentationFunctionGroupDataGroup
                            PresentationFunctionGroupDataGroup functionGroup = new PresentationFunctionGroupDataGroup();
                            functionGroup.setFunctionGroupIdentifier(mapId(functionGroupId));
                            functionGroup.setDataGroupIdentifiers(dataGroupIds.stream().map(this::mapId).collect(Collectors.toList()));

                            mergedUserPermissions.addFunctionGroupDataGroupsItem(functionGroup);
                        });
                    }
                    return mergedUserPermissions;
                }))
            .collectList();
    }

    private boolean hasTheSameFunctionGroupId(String functionGroupId, PresentationFunctionGroupDataGroup requestFunctionGroup) {
        return functionGroupId.equals(requestFunctionGroup.getFunctionGroupIdentifier().getIdIdentifier());
    }

    private String prettyPrint(PresentationFunctionGroupDataGroup functionGroup) {
        return " functionGroup: " + functionGroup.getFunctionGroupIdentifier().getIdIdentifier() +
            " dataGroupIds: " + prettyPrintPresentationDataGroups(functionGroup.getDataGroupIdentifiers());
    }

    private String prettyPrintUserAssignedPermissions(PresentationAssignUserPermissions presentationAssignUserPermissions) {
        return "User: " + presentationAssignUserPermissions.getExternalUserId() +
            " Service Agreement: " + presentationAssignUserPermissions.getExternalServiceAgreementId() +
            " Function & Data Groups: " + presentationAssignUserPermissions.getFunctionGroupDataGroups().stream().map(this::prettyPrint).collect(Collectors.joining(", "));

    }


    private String prettyPrintPresentationDataGroups(List<PresentationIdentifier> dataGroupIdentifiers) {
        if (dataGroupIdentifiers == null) {
            return "NO DATA GROUP IDS!";
        }
        return dataGroupIdentifiers.stream().map(PresentationIdentifier::getIdIdentifier).collect(Collectors.joining(","));
    }

    private boolean hasDataGroupIdentifiers(PresentationFunctionGroupDataGroup functionWithDataGroup) {
        return functionWithDataGroup.getDataGroupIdentifiers() != null
            && !functionWithDataGroup.getDataGroupIdentifiers().isEmpty();
    }

    private PresentationIdentifier mapId(String id) {
        return new PresentationIdentifier().idIdentifier(id);
    }

    private String prettyPrintDataGroups(List<PresentationAssignUserPermissions> r) {
        return r.stream().flatMap(presentationAssignUserPermissions ->
            presentationAssignUserPermissions.getFunctionGroupDataGroups().stream())
            .map(fdgd -> "functionGroupIdentifier: "
                + fdgd.getFunctionGroupIdentifier().getIdIdentifier()
                + " dataGroupItems: [" + fdgd.getDataGroupIdentifiers().stream()
                .map(PresentationIdentifier::getIdIdentifier)
                .collect(Collectors.joining(",")) + "]").collect(Collectors.joining(", "));
    }

    private String prettyPrintExternalIds(List<PresentationAssignUserPermissions> r) {
        return r.stream().map(PresentationAssignUserPermissions::getExternalUserId).collect(Collectors.joining(", "));
    }

    private ListOfFunctionGroupsWithDataGroups functionGroupsWithDataGroup(JobProfileUser jobProfileUser, ProductGroup productGroup) {
        /// Must also include existing function groupss!!

        ListOfFunctionGroupsWithDataGroups functionGroups = new ListOfFunctionGroupsWithDataGroups();
        List<PresentationFunctionDataGroup> collect = jobProfileUser.getBusinessFunctionGroups().stream()
            .map(businessFunctionGroup -> getPresentationFunctionDataGroup(productGroup, businessFunctionGroup))
            .collect(Collectors.toList());


        functionGroups.setItems(collect);
        return functionGroups;
    }

    private Mono<? extends ApprovalStatus> processApprovalStatus(
        PresentationApprovalStatus presentationApprovalStatus) {
        if (presentationApprovalStatus.getApprovalStatus() != null) {
            return Mono.just(accessGroupMapper.map(presentationApprovalStatus.getApprovalStatus()));
        } else {
            return Mono.empty();
        }
    }

    private PresentationFunctionDataGroup getPresentationFunctionDataGroup(ProductGroup productGroup,
                                                                           BusinessFunctionGroup businessFunctionGroup) {
        return new PresentationFunctionDataGroup()
            .functionGroupId(businessFunctionGroup.getId())
            .dataGroupIds(Collections.singletonList(new PresentationGenericObjectId().id(productGroup.getInternalId())));
    }

    /**
     * Link all products in Product Group with Service Agreement.
     *
     * @param streamTask Stream Task containing Product Group to link to Service Agreement
     * @return Product Group
     */
    public Mono<ProductGroupTask> setupProductGroups(ProductGroupTask streamTask) {
        streamTask.info(ACCESS_GROUP, "setup-product-group", "", streamTask.getProductGroup().getName(), null, "Setting up Data Group for: %s and Service Agreement: %s", streamTask.getData().getName(), streamTask.getData().getServiceAgreement().getExternalId());
        ProductGroup productGroup = streamTask.getData();
        ServiceAgreement serviceAgreement = productGroup.getServiceAgreement();

        return getExistingDataGroups(serviceAgreement.getInternalId(), productGroup.getProductGroupType().name())
            .doOnNext(dataGroupItem -> streamTask.info(ACCESS_GROUP, "setup-product-group", "exists", productGroup.getProductGroupType().name(), dataGroupItem.getId(), "Data Group already exists"))
            .onErrorResume(WebClientResponseException.BadRequest.class, e -> {
                streamTask.error(ACCESS_GROUP, "setup-product-group", "failed", serviceAgreement.getExternalId(), serviceAgreement.getInternalId(), "Failed to setup Access Group groups for Service Agreement: " + serviceAgreement.getInternalId());
                return Mono.error(new StreamTaskException(streamTask, e, "Failed to setup Access Group for Service Agreement: " + serviceAgreement.getInternalId()));

            })
//            .filter(item -> isEquals(productGroup, item))
            .collectList()
            .doOnNext(dataGroupItem -> log.debug("Dataground found: {}", dataGroupItem))
            .flatMap(existingAccessGroups -> {
                Optional<DataGroupItem> first = existingAccessGroups.stream().filter(dataGroupItem -> dataGroupItem.getName().equals(productGroup.getName())).findFirst();
                return first.map(dataGroupItem -> updateAccessGroupWithArrangementIds(dataGroupItem, productGroup, streamTask))
                    .orElseGet(() -> createArrangementDataAccessGroup(serviceAgreement, streamTask.getProductGroup(), streamTask).cast(ProductGroup.class));
            }).map(streamTask::data);
    }

    public Mono<BatchProductGroupTask> updateExistingDataGroupsBatch(BatchProductGroupTask task, List<DataGroupItem> existingDataGroups, List<BaseProductGroup> productGroups) {
        List<PresentationDataGroupItemPutRequestBody> batchUpdateRequest = new ArrayList<>();
        final Set<String> affectedArrangements = productGroups.stream()
            .map(StreamUtils::getInternalProductIds)
            .flatMap(List::stream)
            .collect(Collectors.toSet());
        if (BatchProductGroupTask.IngestionMode.REPLACE.equals(task.getIngestionMode())) {
            // if REPLACE mode, existing products (not sent in the request) also need to be added to the set of affected arrangements.
            affectedArrangements.addAll(existingDataGroups.stream()
                .map(DataGroupItem::getItems)
                .flatMap(List::stream)
                .collect(Collectors.toSet())
            );
        }

        existingDataGroups.forEach(dbsDataGroup -> {
            // get group matching  DBS one.
            Optional<BaseProductGroup> pg = productGroups.stream()
                .filter(it -> isEquals(it, dbsDataGroup))
                .findFirst();
            List<String> arrangementsToAdd = new ArrayList<>();
            List<String> arrangementsToRemove = new ArrayList<>();
            affectedArrangements.forEach(arrangement -> {
                boolean shouldBeInGroup = pg.isPresent() && StreamUtils.getInternalProductIds(pg.get()).contains(arrangement);
                if (!dbsDataGroup.getItems().contains(arrangement) && shouldBeInGroup) {
                    // ADD.
                    log.debug("Arrangement item {} to be added to Data Group {}", arrangement, dbsDataGroup.getName());
                    arrangementsToAdd.add(arrangement);
                }
                if (dbsDataGroup.getItems().contains(arrangement) && !shouldBeInGroup) {
                    // remove.
                    log.debug("Arrangement item {} to be removed from Data Group {}", arrangement, dbsDataGroup.getName());
                    arrangementsToRemove.add(arrangement);
                }
            });
            if (!CollectionUtils.isEmpty(arrangementsToAdd)) {
                batchUpdateRequest.add(new PresentationDataGroupItemPutRequestBody()
                    .dataGroupIdentifier(mapId(dbsDataGroup.getId()))
                    .type(dbsDataGroup.getType())
                    .action(PresentationAction.ADD)
                    .dataItems(arrangementsToAdd.stream().map(id -> new PresentationItemIdentifier().internalIdIdentifier(id)).collect(Collectors.toList()))
                );
            }
            if (!CollectionUtils.isEmpty(arrangementsToRemove)) {
                batchUpdateRequest.add(new PresentationDataGroupItemPutRequestBody()
                    .dataGroupIdentifier(mapId(dbsDataGroup.getId()))
                    .type(dbsDataGroup.getType())
                    .action(PresentationAction.REMOVE)
                    .dataItems(arrangementsToRemove.stream().map(id -> new PresentationItemIdentifier().internalIdIdentifier(id)).collect(Collectors.toList()))
                );
            }
        });
        if (!CollectionUtils.isEmpty(batchUpdateRequest)) {
            return updateDataGroupItems(batchUpdateRequest)
                .doOnNext(response ->
                    task.info(ACCESS_GROUP, "update", response.getStatus().toString(), response.getResourceId(), null, "Product group updated.")
                )
                .onErrorResume(WebClientResponseException.class, e -> {
                    task.error(ACCESS_GROUP, "product-group", "failed", task.getData().getServiceAgreement().getExternalId(),
                        task.getData().getServiceAgreement().getInternalId(), e, e.getResponseBodyAsString(), "Failed to update product groups");
                    return Mono.error(new StreamTaskException(task, e, "Failed Update Product groups: " + e.getResponseBodyAsString()));
                })
                .collectList()
                .thenReturn(task);
        } else {
            log.debug("All Product Groups are up to date.");
            task.info(ACCESS_GROUP, "update", "SUCCESS", prettyPrintProductGroupNames(task), null, "All Product Groups are up to date.");
            return Mono.just(task);
        }
    }

    @NotNull
    private String prettyPrintProductGroupNames(BatchProductGroupTask task) {
        return task.getBatchProductGroup().getProductGroups().stream().map(BaseProductGroup::getName).collect(Collectors.joining(","));
    }

    public Flux<BatchResponseItemExtended> updateDataGroupItems(List<PresentationDataGroupItemPutRequestBody> request) {
        return dataGroupsApi.putDataGroupItemsUpdate(request)
            .map(r -> BatchResponseUtils.checkBatchResponseItem(r, "Product Groups Update", r.getStatus().toString(), r.getResourceId(), r.getErrors()));
    }

    public Flux<DataGroupItem> getExistingDataGroups(String serviceAgreementInternalId, String type) {
        return dataGroupApi.getDataGroups(serviceAgreementInternalId, type, true);
    }

    /**
     * Return Data Group Items linked to a Service Agreement.
     *
     * @param serviceAgreementId Service Agreement Internal ID
     * @return List of IDs
     */
    @SuppressWarnings("WeakerAccess")
    public Flux<String> getDataGroupItemIdsByServiceAgreementId(String serviceAgreementId) {
        PresentationServiceAgreementIdentifier serviceAgreementIdentifier =
            new PresentationServiceAgreementIdentifier().idIdentifier(serviceAgreementId);
        return getDataGroupItemIds(null, serviceAgreementIdentifier);
    }

    /**
     * Setup Business Function Groups and link them to the Service Agreement.
     *
     * @param streamTask
     * @param serviceAgreement       Service Agreement
     * @param businessFunctionGroups Business function groups to connect to service agreement.
     * @return Job Profile User with updated Function Groups
     */
    public Mono<List<BusinessFunctionGroup>> setupFunctionGroups(StreamTask streamTask,
        ServiceAgreement serviceAgreement, List<BusinessFunctionGroup> businessFunctionGroups) {

        streamTask.info(FUNCTION_GROUP, SETUP_FUNCTION_GROUP, "", serviceAgreement.getExternalId(),
            serviceAgreement.getInternalId(), "Setting up %s Business Functions for Service Agreement: %s",
            businessFunctionGroups.size(), serviceAgreement.getName());
        log.info("Setup {} Business Function for Service Agreement: {}", businessFunctionGroups.size(),
            serviceAgreement.getExternalId());
        return getFunctionGroups(streamTask, serviceAgreement)
            .collectList()
            .flatMap(functionGroups -> {
                    log.debug("functionGroups: {}", functionGroups);
                    List<BusinessFunctionGroup> newBusinessGroups =
                        getNewBusinessGroups(businessFunctionGroups, functionGroups);
                    return Flux.fromIterable(newBusinessGroups)
                        .flatMap(bfg -> createBusinessFunctionGroup(streamTask, serviceAgreement, bfg))
                        .collectList()
                        .flatMap(bfg -> {
                            List<BusinessFunctionGroup> existingBusinessGroups =
                                getExistingBusinessGroups(businessFunctionGroups, functionGroups);
                            log.debug("existingBusinessGroups: {}", existingBusinessGroups);
                            return updateBatchBusinessFunctionGroup(streamTask, serviceAgreement, existingBusinessGroups)
                                .flatMap(updated -> {
                                    bfg.addAll(existingBusinessGroups);
                                    return Mono.just(bfg);
                                });
                        });
                }
            );
    }

    /**
     * Retrieve list of IDs from external Service Agreement ID.
     *
     * @param externalServiceAgreementId External Service Agreement ID
     * @param type                       Type of objects to retrieve
     * @return list of ids
     */

    public Flux<String> getDataGroupItemIdsByExternalServiceAgreementId(String externalServiceAgreementId,
                                                                        String type) {
        PresentationServiceAgreementIdentifier serviceAgreementIdentifier =
            new PresentationServiceAgreementIdentifier().externalIdIdentifier(externalServiceAgreementId);
        return getDataGroupItemIds(type, serviceAgreementIdentifier);
    }

    public boolean isEquals(BaseProductGroup productGroup, DataGroupItem item) {
        return item.getName().equals(productGroup.getName());
    }

    private boolean productGroupAndDataGrouItemEquals(BusinessFunctionGroup businessFunctionGroup,
                                                      FunctionGroupItem getFunctionGroupsFunctionGroupItem) {
        return getFunctionGroupsFunctionGroupItem.getName().equals(businessFunctionGroup.getName());
    }


    private Mono<ProductGroup> updateAccessGroupWithArrangementIds(DataGroupItem dataGroupsDataGroupItem,
                                                                   ProductGroup productGroup, StreamTask streamTask) {

        streamTask.info(ACCESS_GROUP, "update-access-group", "", dataGroupsDataGroupItem.getName(), dataGroupsDataGroupItem.getId(), "Updating Data Group");

        log.info("Updating Data Access Group: {}", dataGroupsDataGroupItem.getId());

        List<PresentationItemIdentifier> dataItems = Stream.concat(StreamUtils.getInternalProductIds(productGroup).stream(), StreamUtils.getCustomDataGroupItems(productGroup).stream())
            .map(id -> new PresentationItemIdentifier().internalIdIdentifier(id)).collect(Collectors.toList());

        PresentationDataGroupUpdate presentationDataGroupUpdate = new PresentationDataGroupUpdate();
        presentationDataGroupUpdate.setDataGroupIdentifier(mapId(dataGroupsDataGroupItem.getId()));
        presentationDataGroupUpdate.setDataItems(dataItems);
        presentationDataGroupUpdate.setDescription(dataGroupsDataGroupItem.getDescription());
        presentationDataGroupUpdate.setName(dataGroupsDataGroupItem.getName());
        presentationDataGroupUpdate.setType(dataGroupsDataGroupItem.getType());

        return dataGroupsApi.putDataGroups(presentationDataGroupUpdate)
            .onErrorResume(WebClientResponseException.class, badRequest -> {
                streamTask.error(ACCESS_GROUP, "update-access-group", "failed", productGroup.getName(), dataGroupsDataGroupItem.getId(), badRequest, badRequest.getResponseBodyAsString(), "Failed to update access group");
                log.error("Error Updating data access group: {}", badRequest.getResponseBodyAsString());
                return Mono.error(new StreamTaskException(streamTask, badRequest, "Failed to update Data Access Group"));
            })
            .thenReturn(productGroup)
            .map(pg -> {
                streamTask.info(ACCESS_GROUP, "update-access-group", "updated", pg.getName(), dataGroupsDataGroupItem.getId(),
                    "Updated Data Access Group: %s", pg.getName());
                log.info("Updated Data Access Group: {}", pg.getName());
                pg.setInternalId(dataGroupsDataGroupItem.getId());
                return pg;
            });
    }


    public Mono<BaseProductGroup> createArrangementDataAccessGroup(ServiceAgreement serviceAgreement, BaseProductGroup productGroup, StreamTask streamTask) {
        streamTask.info(ACCESS_GROUP, CREATE_ACCESS_GROUP, "", productGroup.getName(), null, "Create new Data Group");

        log.info("Creating Data Access Group: {}", productGroup.getName());

        List<String> dataItems = Stream.concat(StreamUtils.getInternalProductIds(productGroup).stream(), StreamUtils.getCustomDataGroupItems(productGroup).stream())
            .collect(Collectors.toList());
        DataGroupItemSystemBase dataGroupItemSystemBase = new DataGroupItemSystemBase();
        dataGroupItemSystemBase.setName(productGroup.getName());
        dataGroupItemSystemBase.setDescription(productGroup.getDescription());
        dataGroupItemSystemBase.setServiceAgreementId(serviceAgreement.getInternalId());
        dataGroupItemSystemBase.setAreItemsInternalIds(true);
        dataGroupItemSystemBase.setItems(dataItems);
        dataGroupItemSystemBase.setType(productGroup.getProductGroupType().name());
        if (dataGroupItemSystemBase.getItems().stream().anyMatch(Objects::isNull)) {
            streamTask.error(ACCESS_GROUP, CREATE_ACCESS_GROUP, REJECTED, productGroup.getName(), null, "Data group items cannot have null items");
            throw new StreamTaskException(streamTask, "Data Group Items cannot have null items");
        }

        return dataGroupsApi.postDataGroups(dataGroupItemSystemBase)
            .onErrorResume(WebClientResponseException.class, badRequest -> {
                streamTask.error(ACCESS_GROUP, CREATE_ACCESS_GROUP, REJECTED, productGroup.getName(), null, "Data group items cannot have null items");
                return Mono.error(new StreamTaskException(streamTask, badRequest, "Data Group Items cannot have null items"));
            })
            .map(idItem -> {
                    streamTask.info(ACCESS_GROUP, CREATE_ACCESS_GROUP, CREATED, productGroup.getName(), idItem.getId(), "Create new Data Group");
                    productGroup.setInternalId(idItem.getId());
                    return productGroup;
                }
            );

    }

    /**
     * Remove all permissions in service agreement  for specified user.
     *
     * @param serviceAgreementInternalId internal id of Service Agreement.
     * @param userInternalId             internal id of user.
     * @return Mono<Void>
     */
    public Mono<Void> removePermissionsForUser(String serviceAgreementInternalId, String userInternalId) {
        log.debug("Removing permissions from all Data Groups for user: {} in service agreement {}", userInternalId, serviceAgreementInternalId);
        return serviceAgreementsApi.putAssignUsersPermissions(
            serviceAgreementInternalId,
            userInternalId,
            new ListOfFunctionGroupsWithDataGroups())
            .then();
    }

    /**
     * Delete all Function Groups  defined in  service agreement.
     *
     * @param serviceAgreementInternalId Sevice Agreement internal idenetifier.
     * @return Mono<Void>
     */
    public Mono<Void> deleteFunctionGroupsForServiceAgreement(String serviceAgreementInternalId) {
        log.debug("Retrieving Function Groups for Service Agreement {}", serviceAgreementInternalId);
        return functionGroupApi.getFunctionGroups(serviceAgreementInternalId)
            .collectList()
            .flatMap(functionGroups ->
                functionGroupsApi.postFunctionGroupsDelete(
                        functionGroups.stream().filter(f->!FunctionGroupItem.TypeEnum.TEMPLATE.equals(f.getType())).map(fg -> mapId(fg.getId())).collect(Collectors.toList()))
                        .map(r -> BatchResponseUtils.checkBatchResponseItem(r, "Function  Group Removal", r.getStatus().getValue(), r.getResourceId(), r.getErrors()))
                        .collectList())
            .then();
    }

    /**
     * Retrieve all Reference Job Roles defined in  service agreement.
     *
     * @param serviceAgreementInternalId Sevice Agreement internal idenetifier.
     * @return Mono<List < GetFunctionGroupsFunctionGroupItem>>
     */
    public Mono<List<FunctionGroupItem>> getFunctionGroupsForServiceAgreement(String serviceAgreementInternalId) {
        log.debug("Retrieving Function Groups for Service Agreement {}", serviceAgreementInternalId);
        return functionGroupApi.getFunctionGroups(serviceAgreementInternalId)
            .collectList();
    }


    /**
     * Delete all administrators for specified Service Agreement.
     *
     * @param serviceAgreement Service Agreement object with internal and external Ids specified.
     * @return Mono<Void>
     */
    public Mono<Void> deleteAdmins(ServiceAgreement serviceAgreement) {
        log.debug("Removing admins for Service Agreement {}", serviceAgreement.getName());
        return serviceAgreementQueryApi.getServiceAgreementAdmins(serviceAgreement.getInternalId())
            .flatMapMany(admins -> Flux.fromIterable(admins.getAdmins()))
            // get External  ID for each admin.
            // We need to  get the user by using the internal id to facilitate the delete for issue #46
            .flatMap(userId -> usersApi.getUserById(userId, true)).map(GetUser::getExternalId)
            .collectList()
            .doOnNext(adminIds -> log.debug("Found  admins: {}", adminIds))
            .map(adminsExternalIds -> adminsExternalIds.stream()
                .map(adminId -> new PresentationServiceAgreementUserPair()
                    .externalServiceAgreementId(serviceAgreement.getExternalId())
                    .externalUserId(adminId))
                .collect(Collectors.toList()))
            .flatMap(admins -> {
                if (CollectionUtils.isEmpty(admins)) {
                    return Mono.empty();
                } else {
                    return serviceAgreementApi.putPresentationServiceAgreementAdminsBatchUpdate(
                        new PresentationServiceAgreementUsersBatchUpdate()
                            .action(PresentationAction.REMOVE)
                            .users(admins))
                        .map(r -> BatchResponseUtils.checkBatchResponseItem(r, "Delete Admin", r.getStatus().getValue(), r.getResourceId(), r.getErrors()))
                        .collectList()
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.error("Failed to delete admin: {}", e.getResponseBodyAsString(), e);
                            return Mono.error(e);
                        }).then();
                }
            });
    }

    /**
     * Get internal identifiers of all arrangements mentioned in all Data Groups for specified Service Agreement.
     *
     * @param serviceAgreementInternalId internal identifier of Service Agreement.
     * @return flux of arrangements internal ids.
     */
    public Flux<String> getArrangementInternalIdsForServiceAgreement(String serviceAgreementInternalId) {
        return dataGroupApi.getDataGroups(serviceAgreementInternalId, null, true)
            .collectList()
            .map(dataGroupItems -> {
                // get all internal arrangement IDs present in data groups.
                Set<String> internalIds = new HashSet<>();
                dataGroupItems.forEach(dataGroupItem -> internalIds.addAll(dataGroupItem.getItems()));
                return new ArrayList<>(internalIds);
            })
            .flatMapMany(Flux::fromIterable);
    }

    private List<BusinessFunctionGroup> getExistingBusinessGroups(
        List<BusinessFunctionGroup> businessFunctionGroups,
        List<FunctionGroupItem> functionGroups) {

        return businessFunctionGroups.stream()
            .filter(businessFunctionGroup -> filter(functionGroups, businessFunctionGroup))
            .map(businessFunctionGroup -> {
                enrich(functionGroups, businessFunctionGroup);
                return businessFunctionGroup;
            })
            .collect(Collectors.toList());
    }

    private void enrich(List<FunctionGroupItem> functionGroups,
                        BusinessFunctionGroup businessFunctionGroup) {
        functionGroups.stream().filter(item -> productGroupAndDataGrouItemEquals(businessFunctionGroup, item)).findFirst()
            .ifPresent(item -> businessFunctionGroup.setId(item.getId()));
    }

    private boolean filter(List<FunctionGroupItem> functionGroups, BusinessFunctionGroup businessFunctionGroup) {
        return functionGroups.stream()
            .anyMatch(item -> productGroupAndDataGrouItemEquals(businessFunctionGroup, item));
    }

    private List<BusinessFunctionGroup> getNewBusinessGroups(List<BusinessFunctionGroup> businessFunctionGroups,
                                                             List<FunctionGroupItem> functionGroups) {
        return businessFunctionGroups.stream()
            .filter(businessFunctionGroup -> isNewBusinessFunctionGroup(functionGroups, businessFunctionGroup))
            .collect(Collectors.toList());
    }

    private boolean isNewBusinessFunctionGroup(List<FunctionGroupItem> functionGroups,
                                               BusinessFunctionGroup businessFunctionGroup) {
        return functionGroups.stream().noneMatch(item -> productGroupAndDataGrouItemEquals(businessFunctionGroup, item));
    }

    private Mono<BusinessFunctionGroup> createBusinessFunctionGroup(StreamTask streamTask, ServiceAgreement serviceAgreement,
                                                                    BusinessFunctionGroup businessFunctionGroup) {

        streamTask.info(FUNCTION_GROUP, SETUP_FUNCTION_GROUP, "create", serviceAgreement.getExternalId(), null, "Create new business function group: %s for service agreement: %s", businessFunctionGroup.getName(), serviceAgreement.getName());

        if (businessFunctionGroup.getDescription() == null) {
            businessFunctionGroup.setDescription(businessFunctionGroup.getName());
        }

        PresentationIngestFunctionGroup presentationIngestFunctionGroup = new PresentationIngestFunctionGroup();
        presentationIngestFunctionGroup.setExternalServiceAgreementId(serviceAgreement.getExternalId());
        presentationIngestFunctionGroup.setDescription(businessFunctionGroup.getDescription());
        presentationIngestFunctionGroup.setName(businessFunctionGroup.getName());
        presentationIngestFunctionGroup.setPermissions(getPresentationPermissions(businessFunctionGroup));
        presentationIngestFunctionGroup.setType(PresentationIngestFunctionGroup.TypeEnum.REGULAR);

        return functionGroupsApi.postPresentationIngestFunctionGroup(presentationIngestFunctionGroup)
            .doOnError(WebClientResponseException.BadRequest.class, badRequest ->
                handleError(businessFunctionGroup, badRequest))
            .onErrorResume(WebClientResponseException.class, badRequest -> {
                streamTask.error(FUNCTION_GROUP, "ingest-function-groups", FAILED, streamTask.getName(), null, badRequest, badRequest.getResponseBodyAsString(), "Failed to setup Business Function Group");
                return Mono.error(new StreamTaskException(streamTask, badRequest, "Failed to setup Business Function Group: " + badRequest.getResponseBodyAsString()));
            })
            .doOnNext(idItem -> log.info("Created Business Function Group: {} with id: {}",
                businessFunctionGroup.getName(), idItem.getId()))
            .map(idItem -> {
                businessFunctionGroup.setId(idItem.getId());
                return businessFunctionGroup;
            });

    }

    private Mono<JobRole> createJobRole(StreamTask streamTask, ServiceAgreement serviceAgreement, JobRole jobRole) {

        streamTask.info(JOB_ROLE, SETUP_JOB_ROLE, "create", serviceAgreement.getExternalId(), null, "Create new job role: %s for service agreement: %s", jobRole.getName(), serviceAgreement.getName());

        if (jobRole.getDescription() == null) {
            jobRole.setDescription(jobRole.getName());
        }

        PresentationIngestFunctionGroup presentationIngestFunctionGroup = accessGroupMapper.toPresentation(jobRole);
        presentationIngestFunctionGroup.setPermissions(accessGroupMapper.toPresentation(jobRole.getFunctionGroups()));
        presentationIngestFunctionGroup.setExternalServiceAgreementId(serviceAgreement.getExternalId());
        if(jobRole instanceof ReferenceJobRole) {
            log.debug("Creating a Reference Job Role.");
            presentationIngestFunctionGroup.setType(PresentationIngestFunctionGroup.TypeEnum.TEMPLATE);
        }

        // Removing constant from mapper and adding default APS here to avoid issues with apsName.
        if(jobRole.getApsId() == null && isEmpty(jobRole.getApsName())){
            log.warn("Adding default APS '1 - User APS' to job role since it wasn't previously set.");
            presentationIngestFunctionGroup.setApsId(BigDecimal.ONE);
        }

        return functionGroupsApi.postPresentationIngestFunctionGroup(presentationIngestFunctionGroup)
            .doOnError(WebClientResponseException.BadRequest.class, badRequest ->
                handleError(jobRole, badRequest))
            .onErrorResume(WebClientResponseException.class, badRequest -> {
                streamTask.error(JOB_ROLE, "ingest-reference-job-role", FAILED, streamTask.getName(), null, badRequest, badRequest.getResponseBodyAsString(), "Failed to setup Job Role");
                return Mono.error(new StreamTaskException(streamTask, badRequest, "Failed to setup Job Role: " + badRequest.getResponseBodyAsString()));
            })
            .doOnNext(idItem -> log.info("Created Business Function Group: {} with id: {}",
                jobRole.getName(), idItem.getId()))
            .map(idItem -> {
                jobRole.setId(idItem.getId());
                return jobRole;
            });
    }

    private List<PresentationPermission> getPresentationPermissions(BusinessFunctionGroup businessFunctionGroup) {
        return businessFunctionGroup.getFunctions().stream()
            .map(this::mapPresentationBusinessFunction)
            .collect(Collectors.toList());

    }

    private PresentationPermission mapPresentationBusinessFunction(BusinessFunction businessFunction) {

        List<String> privileges;
        privileges = businessFunction.getPrivileges().stream()
            .map(Privilege::getPrivilege)
            .collect(Collectors.toList());

        return new PresentationPermission()
            .functionId(businessFunction.getFunctionId())
            .privileges(privileges);
    }

    private PresentationPermissionFunctionGroupUpdate mapUpdateBusinessFunction(BusinessFunction businessFunction) {

        List<String> privileges;
        privileges = businessFunction.getPrivileges().stream()
            .map(Privilege::getPrivilege)
            .collect(Collectors.toList());

        return new PresentationPermissionFunctionGroupUpdate()
            .privileges(privileges)
            .functionName(businessFunction.getName());
    }


    Flux<AssignedPermission> getAssignedPermissions(
        ServiceAgreement serviceAgreement, User user, String
        resourceName, String functionName, String privilege) {


        return accessControlUsersApi.getArrangementPrivileges(user.getInternalId(), functionName, resourceName, serviceAgreement.getInternalId(), privilege)
            .doOnError(WebClientResponseException.InternalServerError.class, this::handleError)
            .map(arrangementPrivilege -> mapAssignedPermission(
                resourceName,
                functionName,
                privilege,
                arrangementPrivilege)
            );
    }

    private AssignedPermission mapAssignedPermission(
        String resourceName,
        String functionName,
        String privilege,
        ArrangementPrivilegesGetResponseBody arrangementPrivilege) {
        AssignedPermission assignedPermission = new AssignedPermission();
        List<String> permittedObjectInternalIds = Collections.singletonList(arrangementPrivilege.getArrangementId());
        assignedPermission.setPermittedObjectInternalIds(permittedObjectInternalIds);
        assignedPermission.setFunctionName(functionName);
        assignedPermission.setResourceName(resourceName);
        assignedPermission.setPrivileges(new com.backbase.stream.legalentity.model.Privilege().privilege(privilege));
        return assignedPermission;
    }


    @SuppressWarnings("ConstantConditions")
    private Flux<String> getDataGroupItemIds(String type,
                                             PresentationServiceAgreementIdentifier serviceAgreementIdentifier) {
        return dataGroupsApi.postSearch(type,
            new PresentationSearchDataGroupsRequest().serviceAgreementIdentifier(serviceAgreementIdentifier))
            .flatMap(item -> {
                if (item.getDataGroups() != null) {
                    return Flux.fromIterable(item.getDataGroups());
                } else {
                    return Flux.empty();
                }
            })
            .flatMap(details -> getExistingDataGroups(details.getId(), null))
            .flatMap(dataGroupItem -> Flux.fromIterable(dataGroupItem.getItems()));
    }

    public Mono<JobRole> setupJobRole(StreamTask streamTask, ServiceAgreement masterServiceAgreement, JobRole jobRole) {
        streamTask.info(JOB_ROLE, SETUP_JOB_ROLE, "", masterServiceAgreement.getExternalId(),
            masterServiceAgreement.getInternalId(), "Setting up %s Job Role for Service Agreement: %s",
            jobRole.getName(), masterServiceAgreement.getName());
        log.info("Setup {} job role for Service Agreement: {}", jobRole.getName(),
            masterServiceAgreement.getExternalId());

        return getFunctionGroups(streamTask, masterServiceAgreement)
            .collectList()
            .flatMap(functionGroups -> {
                if (functionGroups.stream().noneMatch(fg -> fg.getName().equals(jobRole.getName()))) {
                    log.debug("New Job Role to create: {}", jobRole.getName());
                    return createJobRole(streamTask, masterServiceAgreement, jobRole);
                } else {
                    log.debug("Job Role Already exists: {}; Should be updated", jobRole.getName());
                    List<FunctionGroupItem> collect =
                        functionGroups.stream().filter(fg -> fg.getName().equals(jobRole.getName()))
                            .collect(Collectors.toList());
                    if (collect.isEmpty()) {
                        return Mono.just(jobRole);
                    }
                    if (collect.size() > 1) {
                        return Mono.error(new RuntimeException("More than one same job role"));
                    }
                    return updateJobRole(streamTask, masterServiceAgreement, jobRole, collect.get(0));
                }
            });
    }

    private Mono<? extends JobRole> updateJobRole(StreamTask streamTask, ServiceAgreement serviceAgreement,
        JobRole jobRole, FunctionGroupItem functionGroupItem) {

        log.warn("Start Job Role updating: {}", functionGroupItem);
        streamTask.info(JOB_ROLE, SETUP_JOB_ROLE, "update", serviceAgreement.getExternalId(), null,
            "Update job role: %s for service agreement: %s", jobRole.getName(), serviceAgreement.getName());

        if (jobRole.getDescription() == null) {
            jobRole.setDescription(jobRole.getName());
        }

        PresentationFunctionGroupPutRequestBody putRequestBody = new PresentationFunctionGroupPutRequestBody();
        putRequestBody.functionGroup(new Functiongroupupdate()
            .name(jobRole.getName())
            .description(jobRole.getDescription())
            .validFromDate(jobRole.getValidFromDate())
            .validFromTime(jobRole.getValidFromTime())
            .validUntilDate(jobRole.getValidUntilDate())
            .validUntilTime(jobRole.getValidUntilTime())
            .permissions(accessGroupMapper.toUpdate(jobRole.getFunctionGroups()))
        );
        putRequestBody.setIdentifier(new PresentationIdentifier().idIdentifier(functionGroupItem.getId()));

        if (isEmptyFunctionName(Collections.singletonList(putRequestBody))) {
            log.warn("Functions to Update doesn't contain functionName(it's required): {}",
                Collections.singletonList(putRequestBody));
            return Mono.just(jobRole);
        }
        log.debug("Function to Update: {}", Collections.singletonList(putRequestBody));

        return functionGroupsApi.putFunctionGroupsUpdate(Collections.singletonList(putRequestBody))
            .doOnError(WebClientResponseException.BadRequest.class, badRequest -> handleError(jobRole, badRequest))
            .onErrorResume(WebClientResponseException.class, badRequest -> {
                streamTask.error(JOB_ROLE, "ingest-reference-job-role", FAILED, streamTask.getName(), null, badRequest,
                    badRequest.getResponseBodyAsString(), "Failed to setup Job Role");
                return Mono.error(new StreamTaskException(streamTask, badRequest,
                    "Failed to setup Job Role: " + badRequest.getResponseBodyAsString()));
            })
            .collectList()
            .map(idItems -> jobRole);
    }

    private Mono<List<BatchResponseItemExtended>> updateBatchBusinessFunctionGroup(StreamTask streamTask,
        ServiceAgreement serviceAgreement,
        List<BusinessFunctionGroup> existingBusinessGroups) {

        log.warn("Start Job Role updating: {}", existingBusinessGroups);

        streamTask.info(FUNCTION_GROUP, SETUP_FUNCTION_GROUP, "update", serviceAgreement.getExternalId(), null,
            "Update business function groups for service agreement: %s",
            serviceAgreement.getName());

        List<PresentationFunctionGroupPutRequestBody> presentationFunctionGroupPutRequestBody =
            existingBusinessGroups.stream()
                .map(bfg -> {
                    PresentationFunctionGroupPutRequestBody putRequestBody =
                        new PresentationFunctionGroupPutRequestBody();

                    putRequestBody.setFunctionGroup(new Functiongroupupdate()
                        .description(Optional.ofNullable(bfg.getDescription()).orElse(bfg.getName()))
                        .permissions(getUpdatePermissions(bfg))
                        .name(bfg.getName())
                    );
                    putRequestBody.setIdentifier(new PresentationIdentifier().idIdentifier(bfg.getId()));
                    return putRequestBody;
                })
                .collect(Collectors.toList());

        if (isEmptyFunctionName(presentationFunctionGroupPutRequestBody)) {
            log.warn("Functions to Update doesn't contain functionName(it's required): {}",
                presentationFunctionGroupPutRequestBody);
            return Mono.just(Collections.emptyList());
        }
        log.debug("Functions to Update: {}", presentationFunctionGroupPutRequestBody);

        return functionGroupsApi.putFunctionGroupsUpdate(presentationFunctionGroupPutRequestBody)
            .doOnError(WebClientResponseException.BadRequest.class, this::handleError)
            .onErrorResume(WebClientResponseException.class, badRequest -> {
                streamTask
                    .error(FUNCTION_GROUP, "update-function-groups", FAILED, streamTask.getName(), null, badRequest,
                        badRequest.getResponseBodyAsString(), "Failed to update Business Function Group");
                return Mono.error(new StreamTaskException(streamTask, badRequest,
                    "Failed to update Business Function Group: " + badRequest.getResponseBodyAsString()));
            })
            .collectList();
    }

    private boolean isEmptyFunctionName(List<PresentationFunctionGroupPutRequestBody> putRequestBodies) {
        return putRequestBodies.stream()
            .map(PresentationFunctionGroupPutRequestBody::getFunctionGroup)
            .filter(Objects::nonNull)
            .map(Functiongroupupdate::getPermissions)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .map(PresentationPermissionFunctionGroupUpdate::getFunctionName)
            .anyMatch(Predicate.not(StringUtils::hasText));
    }

    private List<PresentationPermissionFunctionGroupUpdate> getUpdatePermissions(BusinessFunctionGroup bfg) {
        return bfg.getFunctions().stream()
            .map(this::mapUpdateBusinessFunction)
            .collect(Collectors.toList());
    }

    @NotNull
    private Flux<FunctionGroupItem> getFunctionGroups(StreamTask streamTask, ServiceAgreement serviceAgreement) {
        return functionGroupApi.getFunctionGroups(serviceAgreement.getInternalId())
            .onErrorResume(WebClientResponseException.class, e -> {
                log.error("Failed to get Function Groups for Service Agreement: {} Response: {}",
                    serviceAgreement.getExternalId(), e.getResponseBodyAsString());
                streamTask.error(FUNCTION_GROUP, SETUP_FUNCTION_GROUP, "failed", serviceAgreement.getExternalId(),
                    serviceAgreement.getInternalId(),
                    "Failed to get function groups for Service Agreement: " + serviceAgreement.getInternalId());
                return Mono.error(new StreamTaskException(streamTask, e,
                    "Failed to get function groups for Service Agreement: " + serviceAgreement.getInternalId()));
            });
    }

    private void handleError(WebClientResponseException badRequest) {
        log.warn("Error executing request: [{}] {}", badRequest.getRawStatusCode(), badRequest.getResponseBodyAsString());
    }

    private void handleError(BusinessFunctionGroup businessFunctionGroup, WebClientResponseException badRequest) {
        log.warn("Failed to create function group: {} Response: {}", businessFunctionGroup, badRequest.getResponseBodyAsString());
    }

    private void handleError(JobRole jobRole, WebClientResponseException badRequest) {
        log.warn("Failed to create job role: {} Response: {}", jobRole, badRequest.getResponseBodyAsString());
    }

}
