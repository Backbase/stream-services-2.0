package com.backbase.stream;

import static com.backbase.stream.product.utils.StreamUtils.nullableCollectionToStream;
import static org.springframework.util.CollectionUtils.isEmpty;


import com.backbase.dbs.accesscontrol.api.service.v2.model.FunctionGroupItem;
import com.backbase.dbs.user.api.service.v2.model.GetUser;
import com.backbase.dbs.user.profile.api.service.v2.model.CreateUserProfile;
import com.backbase.stream.configuration.LegalEntitySagaConfigurationProperties;
import com.backbase.stream.exceptions.AccessGroupException;
import com.backbase.stream.exceptions.LegalEntityException;
import com.backbase.stream.legalentity.model.BaseProduct;
import com.backbase.stream.legalentity.model.BaseProductGroup;
import com.backbase.stream.legalentity.model.BatchProductGroup;
import com.backbase.stream.legalentity.model.BusinessFunction;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.IdentityUserLinkStrategy;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.legalentity.model.LegalEntityParticipant;
import com.backbase.stream.legalentity.model.LegalEntityReference;
import com.backbase.stream.legalentity.model.LegalEntityStatus;
import com.backbase.stream.legalentity.model.ProductGroup;
import com.backbase.stream.legalentity.model.ServiceAgreement;
import com.backbase.stream.legalentity.model.ServiceAgreementUserAction;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.legalentity.model.UserProfile;
import com.backbase.stream.mapper.UserProfileMapper;
import com.backbase.stream.product.BatchProductIngestionSaga;
import com.backbase.stream.product.BusinessFunctionGroupMapper;
import com.backbase.stream.product.ProductIngestionSaga;
import com.backbase.stream.product.task.BatchProductGroupTask;
import com.backbase.stream.product.task.ProductGroupTask;
import com.backbase.stream.product.utils.StreamUtils;
import com.backbase.stream.service.AccessGroupService;
import com.backbase.stream.service.LegalEntityService;
import com.backbase.stream.service.UserProfileService;
import com.backbase.stream.service.UserService;
import com.backbase.stream.worker.StreamTaskExecutor;
import com.backbase.stream.worker.exception.StreamTaskException;
import com.backbase.stream.worker.model.StreamTask;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/**
 * Legal Entity Saga. This Service creates Legal Entities and their supporting objects from a {@link LegalEntity}
 * aggregate object. For each Legal Entity object it will either retrieve the existing Legal Entity or create a new one.
 * Next it will either create of update the Administrator users which are used to create a Master Service agreement.
 * After the users are created / retrieved and enriched with their internal Ids we can setup the Master Service
 */
@Slf4j
public class LegalEntitySaga implements StreamTaskExecutor<LegalEntityTask> {

    public static final String LEGAL_ENTITY = "LEGAL_ENTITY";
    public static final String IDENTITY_USER = "IDENTITY_USER";
    public static final String SERVICE_AGREEMENT = "SERVICE_AGREEMENT";
    public static final String BUSINESS_FUNCTION_GROUP = "BUSINESS_FUNCTION_GROUP";
    public static final String USER = "USER";
    private static final String DEFAULT_DATA_GROUP = "Default data group";
    private static final String DEFAULT_DATA_DESCRIPTION = "Default data group description";
    public static final String UPSERT_LEGAL_ENTITY = "upsert-legal-entity";
    public static final String FAILED = "failed";
    public static final String EXISTS = "exists";
    public static final String CREATED = "created";
    public static final String PROCESS_PRODUCTS = "process-products";
    public static final String PROCESS_JOB_PROFILES = "process-job-profiles";
    public static final String REJECTED = "rejected";
    public static final String UPSERT = "upsert";
    public static final String SETUP_SERVICE_AGREEMENT = "setup-service-agreement";
    private static final String BATCH_PRODUCT_GROUP_ID = "batch_product_group_task-";

    private final BusinessFunctionGroupMapper businessFunctionGroupMapper = Mappers.getMapper(BusinessFunctionGroupMapper.class);
    private final UserProfileMapper userProfileMapper = Mappers.getMapper(UserProfileMapper.class);

    private final LegalEntityService legalEntityService;
    private final UserService userService;
    private final UserProfileService userProfileService;
    private final AccessGroupService accessGroupService;
    private final BatchProductIngestionSaga batchProductIngestionSaga;

    private final LegalEntitySagaConfigurationProperties legalEntitySagaConfigurationProperties;

    public LegalEntitySaga(LegalEntityService legalEntityService,
                           UserService userService,
                           UserProfileService userProfileService,
                           AccessGroupService accessGroupService,
                           ProductIngestionSaga productIngestionSaga,
                           BatchProductIngestionSaga batchProductIngestionSaga, LegalEntitySagaConfigurationProperties legalEntitySagaConfigurationProperties) {
        this.legalEntityService = legalEntityService;
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.accessGroupService = accessGroupService;
        this.batchProductIngestionSaga = batchProductIngestionSaga;
        this.legalEntitySagaConfigurationProperties = legalEntitySagaConfigurationProperties;
    }

    @Override
    public Mono<LegalEntityTask> executeTask(@SpanTag(value = "streamTask") LegalEntityTask streamTask) {
        return upsertLegalEntity(streamTask)
            .flatMap(this::setupAdministrators)
            .flatMap(this::setupUsers)
            .flatMap(this::setupServiceAgreement)
            .flatMap(this::createJobRoles)
            .flatMap(this::processJobProfiles)
            .flatMap(this::setupAdministratorPermissions)
            .flatMap(this::processProducts)
            .flatMap(this::processSubsidiaries)
            .flatMap(this::linkLegalEntityToRealm);
    }

    @Override
    public Mono<LegalEntityTask> rollBack(LegalEntityTask streamTask) {
        // GET CREATED AND EVENTS AND CALL DELETE ENDPOINTS IN REVERSE
        return Mono.just(streamTask);
    }

    /**
     * Delete Legal Entity by provided Legal Entity external ID.
     * <i>This call doesn't cover arrangements and transactions removal. Should be done before caling this method.</i>
     * </br>
     * Flow is the following:
     * <ul>
     *     <li>Remove permissions for all Function Groups for all users in LE</li>
     *     <li>Delete all Function Groups for Master Service Agreement</li>
     *     <li>Delete all Administrators</li>
     *     <li>'Archive' all Users in LE</li>
     *     <li>Remove Legal Entity itself.</li>
     * </ul>
     *
     * @param legalEntityExternalId legal entity external ID.
     * @return Mono<Void>
     */
    public Mono<Void> deleteLegalEntity(String legalEntityExternalId) {
        return Mono.zip(
            legalEntityService.getMasterServiceAgreementForExternalLegalEntityId(legalEntityExternalId),
            legalEntityService.getLegalEntityByExternalId(legalEntityExternalId))
            .flatMap(data -> {
                LegalEntity le = data.getT2();
                return userService.getUsersByLegalEntity(le.getInternalId())
                    .map(usersByLegalEntityIdsResponse -> {
                        List<GetUser> users = usersByLegalEntityIdsResponse.getUsers();
                        return Tuples.of(data.getT1(), le, users);
                    });
            })
            .flatMap(data -> {
                ServiceAgreement sa = data.getT1();
                List<GetUser> users = data.getT3();
                return Flux.fromIterable(users)
                    .flatMap(user -> accessGroupService.removePermissionsForUser(sa.getInternalId(), user.getId())
                        .thenReturn(user.getExternalId()))
                    .collectList()
                    .map(userIds -> Tuples.of(sa, data.getT2(), userIds));

            })
            .flatMap(data -> {
                ServiceAgreement sa = data.getT1();
                LegalEntity le = data.getT2();
                List<String> userIds = data.getT3();
                return accessGroupService.deleteFunctionGroupsForServiceAgreement(sa.getInternalId())
                    .then(accessGroupService.deleteAdmins(sa))
                    .then(userService.archiveUsers(le.getInternalId(), userIds))
                    .then(legalEntityService.deleteLegalEntity(legalEntityExternalId));
            });
    }

    @ContinueSpan(log = UPSERT_LEGAL_ENTITY)
    private Mono<LegalEntityTask> upsertLegalEntity(@SpanTag(value = "streamTask") LegalEntityTask task) {
        task.info(LEGAL_ENTITY, UPSERT, "", task.getData().getExternalId(), null, "Upsert Legal Entity with External ID: %s", task.getData().getExternalId());
        LegalEntity legalEntity = task.getData();
        // Pipeline for Existing Legal Entity
        Mono<LegalEntityTask> existingLegalEntity = legalEntityService.getLegalEntityByExternalId(legalEntity.getExternalId())
            .onErrorResume(throwable -> {
                task.error(LEGAL_ENTITY, UPSERT_LEGAL_ENTITY, FAILED, legalEntity.getExternalId(), null, throwable, throwable.getMessage(), "Unexpected Error");
                return Mono.error(new StreamTaskException(task, throwable, "Failed to get Legal Entity: " + throwable.getMessage()));
            })
            .onErrorResume(WebClientResponseException.class, throwable -> {
                task.error(LEGAL_ENTITY, UPSERT_LEGAL_ENTITY, FAILED, legalEntity.getExternalId(), null, throwable, throwable.getResponseBodyAsString(), "Unexpected Web Client Exception");
                return Mono.error(new StreamTaskException(task, throwable, "Failed to get Legal Entity: " + throwable.getMessage()));
            })
            .flatMap(actual -> {
                task.getData().setInternalId(actual.getInternalId());
                // TODO: Add Update Legal Entity Logic
                task.info(LEGAL_ENTITY, UPSERT_LEGAL_ENTITY, EXISTS, legalEntity.getExternalId(), actual.getInternalId(), "Legal Entity: %s already exists", legalEntity.getName());
                return Mono.just(task);
            });
        // Pipeline for Creating New Legal Entity
        Mono<LegalEntityTask> createNewLegalEntity = legalEntityService.createLegalEntity(legalEntity)
            .flatMap(actual -> {
                task.getData().setInternalId(legalEntity.getInternalId());
                task.info(LEGAL_ENTITY, UPSERT_LEGAL_ENTITY, CREATED, legalEntity.getExternalId(), legalEntity.getInternalId(), "Created new Legal Entity");
                return Mono.just(task);
            })
            .onErrorResume(LegalEntityException.class, legalEntityException -> {
                task.error(LEGAL_ENTITY, UPSERT_LEGAL_ENTITY, FAILED, legalEntity.getExternalId(), legalEntity.getInternalId(), legalEntityException, legalEntityException.getHttpResponse(), legalEntityException.getMessage());
                return Mono.error(new StreamTaskException(task, legalEntityException));
            });
        return existingLegalEntity.switchIfEmpty(createNewLegalEntity);
    }

    @ContinueSpan(log = "processProducts")
    private Mono<LegalEntityTask> processProducts(LegalEntityTask streamTask) {
        LegalEntity legalEntity = streamTask.getData();
        if (legalEntity.getProductGroups() == null || legalEntity.getProductGroups().isEmpty()) {
            streamTask.info(LEGAL_ENTITY, PROCESS_PRODUCTS, FAILED, legalEntity.getInternalId(), legalEntity.getExternalId(), "Legal Entity: %s does not have any products defied", legalEntity.getExternalId());
            return Mono.just(streamTask);
        }

        return Flux.fromIterable(legalEntity.getProductGroups())
            .map(actual -> createProductGroupTask(streamTask, actual))
            .flatMap(productGroupStreamTask -> batchProductIngestionSaga.process(productGroupStreamTask)
                .onErrorResume(throwable -> {
                    String message = throwable.getMessage();
                    if (throwable.getClass().isAssignableFrom(WebClientResponseException.class)) {
                        message = ((WebClientResponseException) throwable).getResponseBodyAsString();
                    }
                    streamTask.error(LEGAL_ENTITY, PROCESS_PRODUCTS, FAILED, legalEntity.getInternalId(), legalEntity.getExternalId(), throwable, message, "Unexpected error processing");
                    log.error("Unexpected error processing product group {}: {}", productGroupStreamTask.getData().getName(), message);
                    productGroupStreamTask.setState(StreamTask.State.FAILED);
                    return Mono.error(throwable);
                }))
            .map(productGroupTask -> streamTask.addHistory(productGroupTask.getHistory()))
            .collectList()
            .map(tasks -> {
                boolean failed = tasks.stream().anyMatch(StreamTask::isFailed);

                if (failed) {
                    streamTask.setState(StreamTask.State.FAILED);
                } else {
                    streamTask.setState(StreamTask.State.COMPLETED);
                }
                return streamTask;
            });
    }

    private ProductGroupTask createProductGroupTask(LegalEntityTask streamTask, ProductGroup productGroup) {
        LegalEntity legalEntity = streamTask.getData();

        if (productGroup.getUsers() == null) {
            productGroup.setUsers(streamTask.getData().getUsers());
        }
        StreamUtils.nullableCollectionToStream(productGroup.getUsers())
            .forEach(jobProfileUser -> {
                if (jobProfileUser.getLegalEntityReference() == null) {
                    jobProfileUser.setLegalEntityReference(new LegalEntityReference()
                        .externalId(legalEntity.getExternalId())
                        .internalId(legalEntity.getInternalId()));
                }
            });

        log.info("Setting up process data access groups");
        if (productGroup.getName() == null) {
            productGroup.setName(DEFAULT_DATA_GROUP);
        }
        if (productGroup.getDescription() == null) {
            productGroup.setDescription(DEFAULT_DATA_DESCRIPTION);
        }
        productGroup.setServiceAgreement(retrieveServiceAgreement(legalEntity));

        StreamUtils.getAllProducts(productGroup)
            .forEach((BaseProduct bp) -> {
                if(CollectionUtils.isEmpty(bp.getLegalEntities())
                || bp.getLegalEntities().stream().map(LegalEntityReference::getExternalId).filter(Objects::nonNull)
                    .noneMatch(le->le.equals(legalEntity.getExternalId()))) {
                    bp.addLegalEntitiesItem(new LegalEntityReference().externalId(legalEntity.getExternalId())
                        .internalId(legalEntity.getInternalId()));
                }
            });

        return new ProductGroupTask(streamTask.getId() + "-" + productGroup.getName(), productGroup);
    }

    @ContinueSpan(log = "createJobRoles")
    private Mono<LegalEntityTask> createJobRoles(LegalEntityTask streamTask) {

        LegalEntity legalEntity = streamTask.getData();
        ServiceAgreement serviceAgreement = retrieveServiceAgreement(legalEntity);

        if (isEmpty(streamTask.getData().getReferenceJobRoles())
            && (serviceAgreement == null || isEmpty(serviceAgreement.getJobRoles()))) {
            log.debug("Skipping creation of job roles.");
            return Mono.just(streamTask);
        }

        log.info("Creating Job Roles...");

        return Flux.fromStream(Stream.of(serviceAgreement.getJobRoles(), legalEntity.getReferenceJobRoles())
            .filter(Objects::nonNull)
            .flatMap(Collection::stream))
            .flatMap(jobRole -> accessGroupService.setupJobRole(streamTask, serviceAgreement, jobRole))
            .flatMap(jobRole -> {
                log.debug("Job Role: {}", jobRole.getName());
                return Mono.just(streamTask);
            })
            .collectList()
            .map(actual -> streamTask);
    }

    @ContinueSpan(log = "processJobProfiles")
    private Mono<LegalEntityTask> processJobProfiles(LegalEntityTask streamTask) {
        log.info("Processing Job Profiles for: {}", streamTask.getName());
        LegalEntity legalEntity = streamTask.getData();
        if (legalEntity.getUsers() == null) {
            streamTask.warn(BUSINESS_FUNCTION_GROUP, PROCESS_JOB_PROFILES, REJECTED, legalEntity.getExternalId(), legalEntity.getInternalId(), "No Job Profile Users defined in Legal Entity. No Business Function Groups will be assigned between a User and Legal Entity. ");
            return Mono.just(streamTask);
        }
        if (legalEntity.getUsers().stream().allMatch(jobProfileUser -> jobProfileUser.getUser() == null)) {
            streamTask.warn(BUSINESS_FUNCTION_GROUP, PROCESS_JOB_PROFILES, REJECTED, legalEntity.getExternalId(), legalEntity.getInternalId(), "No Users defined in Job Profiles");
            return Mono.just(streamTask);
        }
        return Flux.fromStream(nullableCollectionToStream(legalEntity.getUsers()))
            .flatMap(jobProfileUser -> {
                ServiceAgreement serviceAgreement = retrieveServiceAgreement(legalEntity);
                return getBusinessFunctionGroupTemplates(streamTask, jobProfileUser)
                    .flatMap(businessFunctionGroups -> accessGroupService.setupFunctionGroups(streamTask, serviceAgreement, businessFunctionGroups))
                    .flatMap(list -> {
                        log.info("Assigning {} Business Function Groups to Job Profile User: {}", list.size(), jobProfileUser.getUser().getExternalId());
                        jobProfileUser.setBusinessFunctionGroups(list);
                        list.forEach(bfg -> {
                            streamTask.info(BUSINESS_FUNCTION_GROUP, PROCESS_JOB_PROFILES, "assigned", legalEntity.getExternalId(), legalEntity.getInternalId(), "Assigned Business Function Group: %s with functions: %s to Service Agreement: %s", bfg.getName(),
                                Optional.ofNullable(bfg.getFunctions()).orElse(Collections.singletonList(new BusinessFunction().name("<not loaded>"))).stream().map(BusinessFunction::getFunctionCode).collect(Collectors.joining(", ")), serviceAgreement.getExternalId());
                        });
                        return setupUserPermissions(streamTask, jobProfileUser.getBusinessFunctionGroups());
                    })
                    .map(actual -> jobProfileUser);
            })
            .collectList()
            .map(jobProfileUsers -> {
                if (!jobProfileUsers.isEmpty())
                    streamTask.getData().setUsers(jobProfileUsers);
                return streamTask;
            });

    }

    private Mono<List<BusinessFunctionGroup>> getBusinessFunctionGroupTemplates(LegalEntityTask streamTask, JobProfileUser jobProfileUser) {
        streamTask.info(LEGAL_ENTITY, BUSINESS_FUNCTION_GROUP, "getBusinessFunctionGroupTemplates", "", "", "Using Reference Job Roles and Custom Job Roles defined in Job Profile User");
        List<BusinessFunctionGroup> businessFunctionGroups = jobProfileUser.getBusinessFunctionGroups();
        if (!isEmpty(jobProfileUser.getReferenceJobRoleNames())) {
            return accessGroupService.getFunctionGroupsForServiceAgreement(retrieveServiceAgreement(streamTask.getData()).getInternalId())
                .map(functionGroups -> {
                    Map<String, FunctionGroupItem> idByFunctionGroupName = functionGroups
                        .stream()
                        .filter(fg -> Objects.nonNull(fg.getId()))
                        .collect(Collectors.toMap(FunctionGroupItem::getName, Function.identity()));
                    return jobProfileUser.getReferenceJobRoleNames().stream()
                        .map(idByFunctionGroupName::get)
                        .filter(Objects::nonNull)
                        .map(businessFunctionGroupMapper::map)
                        .collect(Collectors.toList());
                })
                .map(bf -> {
                    if (!isEmpty(businessFunctionGroups))
                        bf.addAll(businessFunctionGroups);
                    return bf;
                });
        }
        return Mono.justOrEmpty(businessFunctionGroups);
    }

    private Mono<LegalEntityTask> setupAdministrators(LegalEntityTask streamTask) {
        LegalEntity legalEntity = streamTask.getData();
        Flux<User> administrators = Flux.fromStream(nullableCollectionToStream(legalEntity.getAdministrators()));

        return administrators.flatMap(user -> upsertUser(streamTask, user))
            .collectList()
            .map(legalEntity::administrators)
            .map(streamTask::data);
    }

    private Mono<LegalEntityTask> setupUsers(LegalEntityTask streamTask) {
        LegalEntity legalEntity = streamTask.getData();
        Flux<JobProfileUser> jobProfileUsers = Flux.fromStream(nullableCollectionToStream(legalEntity.getUsers()));

        return jobProfileUsers
            .flatMap(jobProfileUser -> upsertUser(streamTask, jobProfileUser.getUser())
                .map(upsertedUser -> {
                    User inputUser = nullableCollectionToStream(legalEntity.getUsers())
                        .filter(jpu -> jpu.getUser().getExternalId().equalsIgnoreCase(
                            upsertedUser.getExternalId()))
                        .findFirst().get().getUser();
                    inputUser.setInternalId(upsertedUser.getInternalId());
                    upsertUserProfile(inputUser).subscribe(upsertedUser::setUserProfile);
                    jobProfileUser.setUser(upsertedUser);
                    log.trace("upsert user {}", jobProfileUser);
                    return jobProfileUser;
                }))
            .collectList()
            .thenReturn(streamTask);
    }

    private Mono<UserProfile> upsertUserProfile(User user) {
        if (legalEntitySagaConfigurationProperties.isUserProfileEnabled()) {
            if (user.getUserProfile() != null) {
                CreateUserProfile mappedUserProfile = userProfileMapper.toCreate(user);
                return userProfileService.upsertUserProfile(mappedUserProfile)
                    .map(userProfileMapper::toUserProfile);
            } else {
                log.debug("User Profile for {} is null. Skipping User Profile creation", user.getExternalId());
                return Mono.empty();
            }
        } else {
            log.debug("Skipping User Profile creation as config property is set to false");
            return Mono.empty();
        }
    }

    public Mono<LegalEntityTask> setupUserPermissions(LegalEntityTask legalEntityTask, List<BusinessFunctionGroup> businessFunctionGroups) {
        LegalEntity legalEntity = legalEntityTask.getData();
        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> request = nullableCollectionToStream(legalEntity.getUsers())
            // Ensure internal Id present.
            .filter(jobProfileUser -> Objects.nonNull(jobProfileUser.getUser().getInternalId()))
            .collect(Collectors.toMap(
                JobProfileUser::getUser,
                jobProfileUser -> businessFunctionGroups.stream()
                    .collect(Collectors.toMap(
                        bfg -> bfg,
                        bfg -> Collections.emptyList()
                    ))
            ));
        log.trace("Permissions {}", request);
        return accessGroupService.assignPermissionsBatch(
            new BatchProductGroupTask(BATCH_PRODUCT_GROUP_ID + System.currentTimeMillis(), new BatchProductGroup()
                .serviceAgreement(retrieveServiceAgreement(legalEntity)), BatchProductGroupTask.IngestionMode.UPDATE), request)
            .thenReturn(legalEntityTask);

    }

    public Mono<LegalEntityTask> setupAdministratorPermissions(LegalEntityTask legalEntityTask) {
        // Assign permissions for the user for all business function groups.
        LegalEntity legalEntity = legalEntityTask.getData();
        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> request = nullableCollectionToStream(legalEntity.getUsers())
            .filter(jobProfileUser -> !isEmpty(jobProfileUser.getBusinessFunctionGroups()))
            .collect(Collectors.toMap(
                jobProfileUser -> setupAdminInternalId(legalEntity, jobProfileUser),
                jobProfileUser -> {
                    // Map each business function group to empty list of products.
                    return jobProfileUser.getBusinessFunctionGroups().stream()
                        .collect(Collectors.toMap(
                            bfg -> bfg,
                            bfg -> Collections.emptyList()
                        ));
                }
            ));

        if (request.isEmpty()) {
            log.info("Skipping setup of permissions since no declarative business functions were found.");
            return Mono.just(legalEntityTask);
        }

        return accessGroupService.assignPermissionsBatch(
            new BatchProductGroupTask(BATCH_PRODUCT_GROUP_ID + System.currentTimeMillis(), new BatchProductGroup()
                .serviceAgreement(retrieveServiceAgreement(legalEntity)), BatchProductGroupTask.IngestionMode.UPDATE),
            request)
            .thenReturn(legalEntityTask);
    }

    private User setupAdminInternalId(LegalEntity legalEntity, JobProfileUser jobProfileUser) {
        User user = jobProfileUser.getUser();
        // Ensure internal Id present.
        if (user.getInternalId() == null) {
            legalEntity.getAdministrators().stream()
                .filter(admin -> admin.getExternalId().equals(user.getExternalId()))
                .findFirst()
                .ifPresent(admin -> user.setInternalId(admin.getInternalId()));
        }
        return user;
    }

    public Mono<User> upsertUser(LegalEntityTask streamTask, User user) {
        if (legalEntitySagaConfigurationProperties.isUseIdentityIntegration()
            && !IdentityUserLinkStrategy.IDENTITY_AGNOSTIC.equals(user.getIdentityLinkStrategy())) {
            return upsertIdentityUser(streamTask, user);
        } else {
            log.debug("Fallback to Identity Agnostic identityLinkStrategy. Either identity integration is disabled or User identityLinkStrategy is not set to identity.");
            return upsertUser(user, streamTask);
        }
    }

    private Mono<User> upsertUser(User user, LegalEntityTask streamTask) {
        LegalEntity legalEntity = streamTask.getData();
        streamTask.info(USER, UPSERT, "", user.getExternalId(), "Upsert User with External ID: %s", user.getExternalId());

        Mono<User> getExistingUser = userService.getUserByExternalId(user.getExternalId())
            .doOnNext(existingUser -> {
                user.setInternalId(existingUser.getInternalId());
                streamTask.info(USER, EXISTS, user.getExternalId(), user.getInternalId(), "User %s already exists", existingUser.getExternalId());
            });
        Mono<User> createNewUser = userService.createUser(user, legalEntity.getExternalId())
            .doOnNext(existingUser -> {
                user.setInternalId(existingUser.getInternalId());
                streamTask.info(USER, CREATED, user.getExternalId(), user.getInternalId(), "User %s created", existingUser.getExternalId());
            });
        return getExistingUser.switchIfEmpty(createNewUser);
    }

    private Mono<User> upsertIdentityUser(LegalEntityTask streamTask, User user) {
        streamTask.info(IDENTITY_USER, UPSERT, "", user.getExternalId(), "Upsert User to Identity with External ID: %s", user.getExternalId());
        LegalEntity legalEntity = streamTask.getData();
        Mono<User> getExistingIdentityUser = userService.getUserByExternalId(user.getExternalId())
            .doOnNext(existingUser -> {
                user.setInternalId(existingUser.getInternalId());
                streamTask.info(IDENTITY_USER, EXISTS, user.getExternalId(), user.getInternalId(), "User %s already exists", existingUser.getExternalId());
            });
        Mono<User> createNewIdentityUser =
            userService.setupRealm(legalEntity)
                .switchIfEmpty(Mono.error(new StreamTaskException(streamTask, "Realm: " + legalEntity.getRealmName() + " not found!")))
                .then(userService.linkLegalEntityToRealm(legalEntity))
                .then(userService.createOrImportIdentityUser(user, legalEntity.getInternalId()))
                .flatMap(u -> updateUserStatus(u, legalEntity.getRealmName()))
                .doOnNext(existingUser -> {
                    user.setInternalId(existingUser.getInternalId());
                    streamTask.info(IDENTITY_USER, CREATED, user.getExternalId(), user.getInternalId(), "User %s created", existingUser.getExternalId());
                });
        return getExistingIdentityUser.switchIfEmpty(createNewIdentityUser);
    }

    private Mono<User> updateUserStatus(User user, String realm) {
        log.info("changing user {} status to locked {}", user.getInternalId(), user.getLocked());
        return userService.changeEnableStatus(user, realm)
            .thenReturn(user);
    }

    private Mono<LegalEntityTask> setupServiceAgreement(LegalEntityTask streamTask) {
        LegalEntity legalEntity = streamTask.getData();

        if (legalEntity.getCustomServiceAgreement() != null) {

            return setupCustomServiceAgreement(streamTask, legalEntity);
        } else if (legalEntity.getMasterServiceAgreement() == null || StringUtils.isEmpty(legalEntity.getMasterServiceAgreement().getInternalId())) {

            Mono<LegalEntityTask> existingServiceAgreement = legalEntityService.getMasterServiceAgreementForInternalLegalEntityId(legalEntity.getInternalId())
                .flatMap(serviceAgreement -> {
                    if(legalEntity.getMasterServiceAgreement() != null && legalEntity.getMasterServiceAgreement().getJobRoles() != null)
                        serviceAgreement.setJobRoles(legalEntity.getMasterServiceAgreement().getJobRoles());
                    streamTask.getData().setMasterServiceAgreement(serviceAgreement);
                    streamTask.info(SERVICE_AGREEMENT, SETUP_SERVICE_AGREEMENT, EXISTS, serviceAgreement.getExternalId(), serviceAgreement.getInternalId(), "Existing Service Agreement: %s found for Legal Entity: %s", serviceAgreement.getExternalId(), legalEntity.getExternalId());
                    return Mono.just(streamTask);
                });
            ServiceAgreement newServiceAgreement = createMasterServiceAgreement(legalEntity, legalEntity.getAdministrators());

            Mono<LegalEntityTask> createServiceAgreement = accessGroupService.createServiceAgreement(streamTask, newServiceAgreement)
                .onErrorMap(AccessGroupException.class, accessGroupException -> {
                    streamTask.error(SERVICE_AGREEMENT, SETUP_SERVICE_AGREEMENT, FAILED, newServiceAgreement.getExternalId(), null, accessGroupException, accessGroupException.getMessage(), accessGroupException.getHttpResponse());
                    return new StreamTaskException(streamTask, accessGroupException);
                })
                .flatMap(serviceAgreement -> {
                    streamTask.getData().setMasterServiceAgreement(serviceAgreement);
                    streamTask.info(SERVICE_AGREEMENT, SETUP_SERVICE_AGREEMENT, CREATED, serviceAgreement.getExternalId(), serviceAgreement.getInternalId(), "Created new Service Agreement: %s with Administrators: %s for Legal Entity: %s", serviceAgreement.getExternalId(), legalEntity.getAdministrators().stream().map(User::getExternalId).collect(Collectors.joining(", ")), legalEntity.getExternalId());
                    return Mono.just(streamTask);
                });
            return existingServiceAgreement.switchIfEmpty(createServiceAgreement);
        } else {
            return Mono.just(streamTask);
        }
    }

    private Mono<LegalEntityTask> setupCustomServiceAgreement(LegalEntityTask streamTask, LegalEntity legalEntity) {
        ServiceAgreement newSa = legalEntity.getCustomServiceAgreement();
        if (newSa.getExternalId() == null) {
            log.error("Defined service agreement contains no external Id");
            return Mono.error(new StreamTaskException(streamTask, "Defined service agreement contains no external Id"));
        }

        List<ServiceAgreementUserAction> userActions = nullableCollectionToStream(legalEntity.getUsers())
            .map(JobProfileUser::getUser).map(User::getExternalId)
            .map(id -> new ServiceAgreementUserAction().action(ServiceAgreementUserAction.ActionEnum.ADD)
                .userProfile(new JobProfileUser().user(new User().externalId(id)))).collect(Collectors.toList());

        Mono<LegalEntityTask> existingServiceAgreement = accessGroupService
            .getServiceAgreementByExternalId(newSa.getExternalId())
            .flatMap(sa -> {
                newSa.setInternalId(sa.getInternalId());
                streamTask.info(SERVICE_AGREEMENT, SETUP_SERVICE_AGREEMENT, EXISTS, sa.getExternalId(), sa.getInternalId(),
                    "Existing Service Agreement: %s found for Legal Entity: %s", sa.getExternalId(),
                    legalEntity.getExternalId());
                return accessGroupService.updateServiceAgreementAssociations(streamTask, newSa, userActions)
                    .thenReturn(streamTask);
            });

        Mono<LegalEntityTask> createServiceAgreement = accessGroupService.createServiceAgreement(streamTask, newSa)
                .onErrorMap(AccessGroupException.class, accessGroupException -> {
                    streamTask.error(SERVICE_AGREEMENT, SETUP_SERVICE_AGREEMENT, FAILED, newSa.getExternalId(), null,
                        accessGroupException, accessGroupException.getMessage(),
                        accessGroupException.getHttpResponse());
                    return new StreamTaskException(streamTask, accessGroupException);
                })
                .flatMap(createdSa -> {
                    newSa.setInternalId(createdSa.getInternalId());
                    streamTask.info(SERVICE_AGREEMENT, SETUP_SERVICE_AGREEMENT, CREATED, createdSa.getExternalId(),
                        createdSa.getInternalId(),
                        "Created new Service Agreement: %s with Administrators: %s for Legal Entity: %s",
                        createdSa.getExternalId(), legalEntity.getAdministrators().stream().map(
                            User::getExternalId).collect(Collectors.joining(", ")), legalEntity.getExternalId());
                    return Mono.just(streamTask);
                })
                .then(accessGroupService.updateServiceAgreementRegularUsers(streamTask, newSa, userActions)
                    .thenReturn(streamTask));

        return existingServiceAgreement.switchIfEmpty(createServiceAgreement);
    }

    private ServiceAgreement createMasterServiceAgreement(LegalEntity legalEntity, @Valid List<User> admins) {

        List<String> adminExternalIds = admins != null
            ? admins.stream().map(User::getExternalId).collect(Collectors.toList()) :
            null;

        LegalEntityParticipant legalEntityParticipant = new LegalEntityParticipant();
        legalEntityParticipant.setExternalId(legalEntity.getExternalId());
        legalEntityParticipant.setSharingAccounts(true);
        legalEntityParticipant.setSharingUsers(true);
        legalEntityParticipant.setAdmins(adminExternalIds);
        legalEntityParticipant.setUsers(Collections.emptyList());

        ServiceAgreement serviceAgreement;

        if (legalEntity.getMasterServiceAgreement() == null) {
            serviceAgreement = new ServiceAgreement();
            serviceAgreement.setExternalId("sa_" + legalEntity.getExternalId());
            serviceAgreement.setName(legalEntity.getName());
            serviceAgreement.setDescription("Master Service Agreement for " + legalEntity.getName());
            serviceAgreement.setStatus(LegalEntityStatus.ENABLED);
        } else {
            serviceAgreement = legalEntity.getMasterServiceAgreement();
        }

        serviceAgreement.setIsMaster(true);
        serviceAgreement.addParticipantsItem(legalEntityParticipant);

        return serviceAgreement;
    }


    @ContinueSpan(log = "processSubsidiaries")
    private Mono<LegalEntityTask> processSubsidiaries(LegalEntityTask streamTask) {

        LegalEntity parentLegalEntity = streamTask.getData();

        Flux<LegalEntity> subsidiaries = parentLegalEntity.getSubsidiaries() == null
            ? Flux.empty()
            : Flux.fromIterable(parentLegalEntity.getSubsidiaries());

        Flux<LegalEntityTask> subsidiariesWithParentId = setSubsidiaryParentLegalEntityId(parentLegalEntity,
            subsidiaries).map(LegalEntityTask::new);
        return subsidiariesWithParentId
            .flatMap(this::executeTask)
            .map(childTask -> streamTask.addHistory(childTask.getHistory()))
            .collectList()
            .zipWith(Mono.just(streamTask), (legalEntityResults, childStreamTask) -> {
                // Do Something With The Children
                return streamTask;
            });
    }

    private Mono<LegalEntityTask> linkLegalEntityToRealm(LegalEntityTask streamTask) {
        return Mono.just(streamTask)
            .filter(task -> legalEntitySagaConfigurationProperties.isUseIdentityIntegration())
            .filter(task -> CollectionUtils.isEmpty(task.getData().getAdministrators())
                && CollectionUtils.isEmpty(task.getData().getUsers()))
            .flatMap(task ->
                userService.setupRealm(task.getLegalEntity())
                    .then(userService.linkLegalEntityToRealm(task.getLegalEntity()))
                    .map(legalEntity -> streamTask)
            );
    }

    private ServiceAgreement retrieveServiceAgreement(LegalEntity legalEntity) {
        if (legalEntity.getCustomServiceAgreement() != null) {
            return legalEntity.getCustomServiceAgreement();
        }
        return legalEntity.getMasterServiceAgreement();
    }

    private Flux<LegalEntity> setSubsidiaryParentLegalEntityId(LegalEntity parentLegalEntity,
                                                               Flux<LegalEntity> subsidiaries) {
        return subsidiaries.map(subsidiary -> {
            subsidiary.setParentExternalId(parentLegalEntity.getExternalId());
            return subsidiary;
        });
    }
}
