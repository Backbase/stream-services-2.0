package com.backbase.stream;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backbase.dbs.accesscontrol.api.service.v2.model.FunctionGroupItem;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountArrangementItem;
import com.backbase.dbs.user.api.service.v2.model.GetUser;
import com.backbase.stream.legalentity.model.BaseProductGroup;
import com.backbase.stream.legalentity.model.BatchProductGroup;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.LegalEntityParticipant;
import com.backbase.stream.legalentity.model.Loan;
import com.backbase.stream.legalentity.model.ProductGroup;
import com.backbase.stream.legalentity.model.ServiceAgreement;
import com.backbase.stream.legalentity.model.ServiceAgreementUserAction;
import com.backbase.stream.legalentity.model.UpdatedServiceAgreement;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.product.service.ArrangementService;
import com.backbase.stream.product.task.BatchProductGroupTask;
import com.backbase.stream.product.task.ProductGroupTask;
import com.backbase.stream.service.AccessGroupService;
import com.backbase.stream.worker.model.StreamTask;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UpdatedServiceAgreementSagaTest {

    @InjectMocks
    private UpdatedServiceAgreementSaga updatedServiceAgreementSaga;

    @Mock
    private AccessGroupService accessGroupService;

    @Mock
    private ArrangementService arrangementService;

    @Captor
    private ArgumentCaptor<ProductGroupTask> productGroupTaskCaptor;

    @Test
    void updateServiceAgreement() {
        final String saExternalId = "someSaExternalId";
        final String saInternalId = "someSaInternalId";
        final String loanExId = "someLoanExId";
        final String loanInId = "someLoanInId";
        User user1 = new User().externalId("someUserExId1");
        User user2 = new User().externalId("someUserExId2");
        Loan loanProduct = new Loan();
        loanProduct.setExternalId(loanExId);
        LegalEntityParticipant participant =
            new LegalEntityParticipant().externalId("someLeExId").sharingAccounts(true).sharingUsers(true);
        BaseProductGroup baseProductGroup = new BaseProductGroup().addLoansItem(loanProduct);
        JobProfileUser jobProfileUser1 = new JobProfileUser().user(user1).addReferenceJobRoleNamesItem("someJobRole1");
        JobProfileUser jobProfileUser2 = new JobProfileUser().user(user2).addReferenceJobRoleNamesItem("someJobRole2");
        UpdatedServiceAgreement serviceAgreement = new UpdatedServiceAgreement().addProductGroupsItem(baseProductGroup)
            .addSaAdminsItem(new ServiceAgreementUserAction().userProfile(jobProfileUser1))
            .addSaUsersItem(new ServiceAgreementUserAction().userProfile(jobProfileUser2));
        serviceAgreement.externalId(saExternalId).internalId(saInternalId).name("someSa")
            .addParticipantsItem(participant);
        ServiceAgreement internalSA = new ServiceAgreement().externalId(saExternalId).internalId(saInternalId);
        UpdatedServiceAgreementTask task = new UpdatedServiceAgreementTask(serviceAgreement);
        List<FunctionGroupItem> serviceAgreementFunctionGroups = asList(
            new FunctionGroupItem().name("someJobRole1").type(FunctionGroupItem.TypeEnum.DEFAULT),
            new FunctionGroupItem().name("someJobRole2").type(FunctionGroupItem.TypeEnum.DEFAULT),
            new FunctionGroupItem().name("someJobRole3").type(FunctionGroupItem.TypeEnum.DEFAULT));
        ProductGroup productGroup = new ProductGroup().serviceAgreement(serviceAgreement);
        productGroup.loans(baseProductGroup.getLoans());

        when(accessGroupService.updateServiceAgreementAssociations(eq(task), eq(serviceAgreement), any()))
            .thenReturn(Mono.just(serviceAgreement));

        Mono<ProductGroupTask> productGroupTaskMono = Mono.just(new ProductGroupTask(productGroup));
        when(accessGroupService.setupProductGroups(any())).thenReturn(productGroupTaskMono);

        when(accessGroupService.getUserByExternalId(eq("someUserExId1"), eq(true)))
            .thenReturn(Mono.just(new GetUser().id("someUserInId1").externalId("someUserExId1")));
        when(accessGroupService.getUserByExternalId(eq("someUserExId2"), eq(true)))
            .thenReturn(Mono.just(new GetUser().id("someUserInId2").externalId("someUserExId2")));

        when(accessGroupService.getFunctionGroupsForServiceAgreement(eq(saInternalId)))
            .thenReturn(Mono.just(serviceAgreementFunctionGroups));

        BatchProductGroupTask bpgTask =
            new BatchProductGroupTask().data(new BatchProductGroup().serviceAgreement(serviceAgreement));
        Mono<BatchProductGroupTask> bpgTaskMono = Mono.just(bpgTask);
        when(accessGroupService.assignPermissionsBatch(any(), any())).thenReturn(bpgTaskMono);

        when(accessGroupService.getServiceAgreementByExternalId(eq(saExternalId))).thenReturn(Mono.just(internalSA));

        when(arrangementService.getArrangementByExternalId(ArgumentMatchers.<List<String>>any()))
            .thenReturn(Flux.fromIterable(
                Collections.singletonList(new AccountArrangementItem().id(loanInId).externalArrangementId(loanExId))));


        UpdatedServiceAgreementTask actual = updatedServiceAgreementSaga.executeTask(task).block();


        assertEquals(StreamTask.State.COMPLETED, actual.getState());

        verify(accessGroupService).updateServiceAgreementAssociations(any(), eq(serviceAgreement), any());

        verify(accessGroupService).setupProductGroups(productGroupTaskCaptor.capture());
        ProductGroupTask productGroupTask = productGroupTaskCaptor.getValue();
        assertEquals(productGroup, productGroupTask.getData());

        verify(accessGroupService).getUserByExternalId(eq("someUserExId1"), eq(true));
        verify(accessGroupService).getUserByExternalId(eq("someUserExId2"), eq(true));

        verify(accessGroupService).getFunctionGroupsForServiceAgreement(eq(saInternalId));

        Map<BusinessFunctionGroup, List<BaseProductGroup>> permissionUser1 = new HashMap<>();
        permissionUser1.put(new BusinessFunctionGroup().name("someJobRole1"), asList(baseProductGroup));
        Map<BusinessFunctionGroup, List<BaseProductGroup>> permissionUser2 = new HashMap<>();
        permissionUser2.put(new BusinessFunctionGroup().name("someJobRole2"), asList(baseProductGroup));
        Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> permissionsRequest = new HashMap<>();
        permissionsRequest.put(user1, permissionUser1);
        permissionsRequest.put(user2, permissionUser2);
        verify(accessGroupService).assignPermissionsBatch(any(), eq(permissionsRequest));
    }
}