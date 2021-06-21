package com.backbase.stream;

import static com.backbase.stream.worker.model.StreamTask.State.COMPLETED;
import static com.backbase.stream.worker.model.StreamTask.State.FAILED;
import static java.util.Collections.unmodifiableMap;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.backbase.dbs.user.api.service.v2.model.GetUser;
import com.backbase.stream.legalentity.model.BaseProduct;
import com.backbase.stream.legalentity.model.BaseProductGroup;
import com.backbase.stream.legalentity.model.BatchProductGroup;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.ProductGroup;
import com.backbase.stream.legalentity.model.ServiceAgreementUserAction;
import com.backbase.stream.legalentity.model.UpdatedServiceAgreement;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.mapper.ProductGroupMapper;
import com.backbase.stream.product.BusinessFunctionGroupMapper;
import com.backbase.stream.product.service.ArrangementService;
import com.backbase.stream.product.task.BatchProductGroupTask;
import com.backbase.stream.product.task.ProductGroupTask;
import com.backbase.stream.service.AccessGroupService;
import com.backbase.stream.worker.StreamTaskExecutor;
import com.backbase.stream.worker.exception.StreamTaskException;
import com.backbase.stream.worker.model.StreamTask;
import com.backbase.stream.worker.model.TaskHistory;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.factory.Mappers;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class UpdatedServiceAgreementSaga implements StreamTaskExecutor<UpdatedServiceAgreementTask> {

    private static final String ENTITY_SERVICE_AGREEMENT = "SERVICE_AGREEMENT";

    private static final String OP_UPDATE_SERVICE_AGREEMENT = "update-service-agreement";
    private static final String OP_PROCESS_JOB_PROFILES = "process-job-profiles";
    private static final String OP_PROCESS_PRODUCTS = "process-product";

    private static final String RESULT_REJECTED = "rejected";
    private static final String RESULT_UPDATED = "updated";
    private static final String RESULT_FAILED = "failed";

    private static final String BATCH_PRODUCT_GROUP_ID = "batch_product_group_task-";

    private final AccessGroupService accessGroupService;

    private final ArrangementService arrangementService;

    private final BusinessFunctionGroupMapper businessFunctionGroupMapper =
        Mappers.getMapper(BusinessFunctionGroupMapper.class);
    private final ProductGroupMapper productGroupMapper = Mappers.getMapper(ProductGroupMapper.class);

    @Override
    public Mono<UpdatedServiceAgreementTask> executeTask(
        @SpanTag(value = "streamTask") UpdatedServiceAgreementTask streamTask) {
        return updateServiceAgreement(streamTask)
            .flatMap(this::retrieveInternalProducts)
            .flatMap(this::retrieveUserInternalIds)
            .flatMap(this::processProducts)
            .flatMap(this::processJobProfiles)
            .flatMap(this::checkFailure)
            .onErrorResume(StreamTaskException.class, e -> {
                String errors = streamTask.getHistory().stream()
                    .filter(th -> th.getSeverity() == TaskHistory.Severity.ERROR)
                    .map(TaskHistory::toDisplayString).collect(Collectors.joining(" - "));
                log.error("update of Service Agreement failed with errors: " + errors);
                return Mono.error(e);
            });
    }

    @Override
    public Mono<UpdatedServiceAgreementTask> rollBack(UpdatedServiceAgreementTask streamTask) {
        return Mono.just(streamTask);
    }

    @ContinueSpan(log = "updateServiceAgreement")
    private Mono<UpdatedServiceAgreementTask> updateServiceAgreement(UpdatedServiceAgreementTask streamTask) {
        UpdatedServiceAgreement serviceAgreement = streamTask.getData();
        return accessGroupService.getServiceAgreementByExternalId(serviceAgreement.getExternalId())
            .flatMap(existingSa -> {
                serviceAgreement.setInternalId(existingSa.getInternalId());
                return accessGroupService.updateServiceAgreementAssociations(streamTask, streamTask.getData(),
                    serviceAgreement.getSaUsers());
            })
            .flatMap(sa -> {
                streamTask.info(ENTITY_SERVICE_AGREEMENT, OP_UPDATE_SERVICE_AGREEMENT, RESULT_UPDATED,
                    sa.getExternalId(), sa.getInternalId(), "Updated Service Agreement: %s", sa.getExternalId());
                return Mono.just(streamTask);
            })
            .switchIfEmpty(serviceAgreementDoesntExistError(streamTask));
    }

    private Mono<UpdatedServiceAgreementTask> serviceAgreementDoesntExistError(UpdatedServiceAgreementTask streamTask) {
        UpdatedServiceAgreement sa = streamTask.getData();
        return Mono.error(() -> {
            streamTask.error(ENTITY_SERVICE_AGREEMENT, OP_UPDATE_SERVICE_AGREEMENT, RESULT_FAILED, sa.getExternalId(),
                null, "Service Agreement with external id: %s, was not found", sa.getExternalId());
            return new StreamTaskException(streamTask, MessageFormat
                .format("Service Agreement with external id: {0}, was not found", sa.getExternalId()));
        });
    }

    @ContinueSpan(log = "processProducts")
    private Mono<UpdatedServiceAgreementTask> processProducts(UpdatedServiceAgreementTask task) {
        UpdatedServiceAgreement sa = task.getData();
        if (isEmpty(sa.getProductGroups())) {
            task.info(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_PRODUCTS, RESULT_FAILED, sa.getInternalId(),
                sa.getExternalId(), "Service Agreement: %s does not have any products defied", sa.getExternalId());
            return Mono.just(task);
        }

        Map<BaseProductGroup, ProductGroup> productGroupByBasePg = unmodifiableMap(sa.getProductGroups().stream()
            .collect(Collectors.toMap(Function.identity(), productGroupMapper::map)));

        List<ProductGroupTask> productGroupTasks = productGroupByBasePg.values().stream()
            .map(pg -> pg.serviceAgreement(sa)).map(ProductGroupTask::new).collect(Collectors.toList());
        return Flux.fromIterable(productGroupTasks)
            .flatMap(t -> accessGroupService.setupProductGroups(t)
                .onErrorResume(StreamTaskException.class, e -> {
                    t.setState(FAILED);
                    return Mono.just(t);
                }))
            .collectList()
            .flatMap(list -> {
                list.forEach(t -> task.addHistory(t.getHistory()));
                if (list.stream().anyMatch(StreamTask::isFailed)) {
                    String ids =
                        list.stream().filter(StreamTask::isFailed).map(ProductGroupTask::getData)
                            .map(ProductGroup::getInternalId).collect(Collectors.joining(" "));
                    task.error(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_PRODUCTS, RESULT_FAILED, sa.getInternalId(),
                        sa.getExternalId(), "failed to setup Product Groups: %s for Service Agreement: %s", ids,
                        sa.getExternalId());
                    task.setState(FAILED);
                    return Mono.error(new StreamTaskException(task, "error on product group update"));
                } else {
                    productGroupByBasePg.entrySet().stream()
                        .forEach(e -> e.getKey().setInternalId(e.getValue().getInternalId()));
                    task.info(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_PRODUCTS, RESULT_UPDATED, sa.getExternalId(),
                        sa.getInternalId(), "Product Groups setup for Service Agreement: %s", sa.getExternalId());
                }
                return Mono.just(task);
            });
    }

    @ContinueSpan(log = "processJobProfiles")
    private Mono<UpdatedServiceAgreementTask> processJobProfiles(UpdatedServiceAgreementTask streamTask) {
        log.info("Processing Job Profiles for: {}", streamTask.getName());
        UpdatedServiceAgreement sa = streamTask.getData();
        if (isEmpty(sa.getSaUsers()) && isEmpty(sa.getSaAdmins())) {
            streamTask.warn(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_REJECTED, sa.getExternalId(),
                sa.getInternalId(), "No Job Profile Users defined for Service Agreement");
            return Mono.just(streamTask);
        }

        List<JobProfileUser> jobProfileUsers = getUserProfiles(sa);

        return getBusinessFunctionGroupTemplates(streamTask)
            .flatMap(bfgList -> setupUserPermissions(streamTask, bfgList, jobProfileUsers))
            .flatMap(task -> {
                if (!task.isFailed()) {
                    task.info(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_UPDATED, sa.getExternalId(),
                        sa.getInternalId(), "Job Profiles updated for Service Agreement: %s", sa.getExternalId());
                }
                return Mono.just(task);
            });
    }

    private Mono<UpdatedServiceAgreementTask> retrieveUserInternalIds(UpdatedServiceAgreementTask task) {
        UpdatedServiceAgreement sa = task.getData();
        List<JobProfileUser> users = getUserProfiles(sa);
        return Flux.fromIterable(users)
            .flatMap(jpu -> accessGroupService.getUserByExternalId(jpu.getUser().getExternalId(), true)
                .onErrorResume(WebClientResponseException.class, e -> {
                    task.setState(FAILED);
                    task.error(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_FAILED,
                        sa.getExternalId(), sa.getInternalId(), e, e.getMessage(),
                        "failure retrieving user for external id: %s", jpu.getUser().getExternalId());
                    return Mono.empty();
                }))
            .collectList()
            .flatMap(internalUsers -> {
                Map<String, GetUser> usersByExternalId =
                    internalUsers.stream().collect(Collectors.toMap(GetUser::getExternalId, Function.identity()));
                users.stream().forEach(jp -> {
                    String externalId = jp.getUser().getExternalId();
                    GetUser internalUser = usersByExternalId.get(externalId);
                    if (internalUser != null) {
                        jp.getUser().setInternalId(internalUser.getId());
                    } else {
                        task.error(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_FAILED, sa.getExternalId(),
                            sa.getInternalId(),
                            "unable to find internal user for external id: %s, this user will be ignored",
                            externalId);
                    }
                });
                return Mono.just(task);
            });
    }

    private Mono<UpdatedServiceAgreementTask> retrieveInternalProducts(UpdatedServiceAgreementTask task) {
        UpdatedServiceAgreement sa = task.getData();

        if (sa.getProductGroups() == null) {
            return Mono.just(task);
        }

        Map<String, BaseProduct> productByExternalId = unmodifiableMap(sa.getProductGroups().stream()
            .flatMap(pg -> Stream.of(
                pg.getLoans(),
                pg.getCreditCards(),
                pg.getCurrentAccounts(),
                pg.getCustomProducts(),
                pg.getDebitCards(),
                pg.getInvestmentAccounts(),
                pg.getSavingAccounts(),
                pg.getTermDeposits()))
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .collect(Collectors.toMap(BaseProduct::getExternalId, Function.identity())));
        return arrangementService.getArrangementByExternalId(new ArrayList<>(productByExternalId.keySet()))
            .doOnNext(arrangement -> {
                log.debug("setting internal id {} for product with external id {}", arrangement.getId(),
                    arrangement.getExternalArrangementId());
                BaseProduct product = productByExternalId.get(arrangement.getExternalArrangementId());
                product.setInternalId(arrangement.getId());
                product.setName(arrangement.getName());
            })
            .collectList()
            .flatMap(list -> {
                productByExternalId.values().stream()
                    .filter(bp -> bp.getInternalId() == null)
                    .forEach(bp -> {
                        log.error("Product with external id: {} not found", bp.getExternalId());
                        task.error(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_FAILED, sa.getExternalId(),
                            sa.getInternalId(), "product with external id: %s not found", bp.getExternalId());
                        task.setState(FAILED);
                    });
                if (task.isFailed()) {
                    return Mono.error(new StreamTaskException(task, "error retrieving product internal id"));
                }
                return Mono.just(task);
            });
    }

    private Mono<List<BusinessFunctionGroup>> getBusinessFunctionGroupTemplates(UpdatedServiceAgreementTask task) {
        UpdatedServiceAgreement sa = task.getData();
        return accessGroupService
            .getFunctionGroupsForServiceAgreement(sa.getInternalId())
            .onErrorResume(WebClientResponseException.class, e -> {
                task.setState(FAILED);
                task.error(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_FAILED, sa.getExternalId(),
                    sa.getInternalId(), e, e.getMessage(),
                    "Failed to retrieve Function Groups for Service Agreement: %s",
                    sa.getExternalId());
                return Mono.error(new StreamTaskException(task,
                    MessageFormat.format("Failed to retrieve Function Groups for Service Agreement: {0}",
                        sa.getExternalId())));
            })
            .map(list -> list.stream().map(businessFunctionGroupMapper::map).collect(Collectors.toList()));
    }

    private Mono<UpdatedServiceAgreementTask> setupUserPermissions(UpdatedServiceAgreementTask task,
                                                                   List<BusinessFunctionGroup> businessFunctionGroups,
                                                                   List<JobProfileUser> users) {
        UpdatedServiceAgreement sa = task.getData();
        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> request = users.stream()
            .collect(Collectors.toMap(
                JobProfileUser::getUser,
                jobProfileUser -> businessFunctionGroups.stream()
                    .filter(bfg -> !isEmpty(jobProfileUser.getReferenceJobRoleNames())
                        && jobProfileUser.getReferenceJobRoleNames().contains(bfg.getName()))
                    .collect(Collectors.toMap(bfg -> bfg, bfg -> sa.getProductGroups()))
            ));
        log.trace("Permissions {}", request);
        BatchProductGroupTask bpgTask = new BatchProductGroupTask(BATCH_PRODUCT_GROUP_ID + System.currentTimeMillis(),
            new BatchProductGroup().serviceAgreement(sa), BatchProductGroupTask.IngestionMode.UPDATE);
        return accessGroupService.assignPermissionsBatch(bpgTask, request)
            .onErrorResume(e -> e instanceof StreamTaskException || e instanceof WebClientResponseException, e -> {
                bpgTask.setState(FAILED);
                bpgTask.error(ENTITY_SERVICE_AGREEMENT, OP_PROCESS_JOB_PROFILES, RESULT_FAILED, sa.getExternalId(),
                    sa.getInternalId(), e, e.getMessage(), "failed to assign permissions for service agreement: %s",
                    sa.getExternalId());
                return Mono.just(bpgTask);
            })
            .flatMap(mergeTaskResult(task))
            .thenReturn(task);

    }

    @NotNull
    private List<JobProfileUser> getUserProfiles(UpdatedServiceAgreement sa) {
        return Stream.of(sa.getSaUsers(), sa.getSaAdmins())
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map(ServiceAgreementUserAction::getUserProfile)
            .filter(Objects::nonNull)
            .filter(jp -> !isEmpty(jp.getReferenceJobRoleNames()))
            .collect(Collectors.toList());
    }

    private Mono<UpdatedServiceAgreementTask> checkFailure(UpdatedServiceAgreementTask task) {
        if (task.isFailed()) {
            return Mono.error(new StreamTaskException(task, "update of Service Agreement failed"));
        } else {
            task.setState(COMPLETED);
        }
        return Mono.just(task);
    }

    private <T extends StreamTask> Function<StreamTask, Mono<T>> mergeTaskResult(T target) {
        return source -> Mono.just(mergeTaskResult(source, target));
    }

    private <T extends StreamTask> T mergeTaskResult(StreamTask source, T target) {
        target.addHistory(source.getHistory());
        if (source.isFailed()) {
            target.setState(FAILED);
        }
        return target;
    }

}
