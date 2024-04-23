package org.egov.works.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.works.config.ContractServiceConfiguration;
import org.egov.works.enrichment.ContractEnrichment;
import org.egov.works.kafka.ContractProducer;
import org.egov.works.repository.ContractRepository;
import org.egov.works.repository.LineItemsRepository;
import org.egov.works.util.ResponseInfoFactory;
import org.egov.works.validator.ContractServiceValidator;
import org.egov.works.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContractService {

    private final ContractServiceValidator contractServiceValidator;

    private final ResponseInfoFactory responseInfoFactory;

    private final ContractServiceConfiguration contractServiceConfiguration;

    private final ContractEnrichment contractEnrichment;

    private final ContractProducer contractProducer;

    private final ContractRepository contractRepository;

    private final LineItemsRepository lineItemsRepository;

    private final WorkflowService workflowService;

    private final NotificationService notificationService;

    @Autowired
    public ContractService(ContractServiceValidator contractServiceValidator, ResponseInfoFactory responseInfoFactory, ContractServiceConfiguration contractServiceConfiguration, ContractEnrichment contractEnrichment, ContractProducer contractProducer, ContractRepository contractRepository, LineItemsRepository lineItemsRepository, WorkflowService workflowService, NotificationService notificationService) {
        this.contractServiceValidator = contractServiceValidator;
        this.responseInfoFactory = responseInfoFactory;
        this.contractServiceConfiguration = contractServiceConfiguration;
        this.contractEnrichment = contractEnrichment;
        this.contractProducer = contractProducer;
        this.contractRepository = contractRepository;
        this.lineItemsRepository = lineItemsRepository;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
    }

    public ContractResponse createContract(ContractRequest contractRequest) {
        log.info("Create contract");
        contractServiceValidator.validateCreateContractRequest(contractRequest);
        contractEnrichment.enrichContractOnCreate(contractRequest);
        workflowService.updateWorkflowStatus(contractRequest);
        contractProducer.push(contractServiceConfiguration.getCreateContractTopic(), contractRequest);

        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(contractRequest.getRequestInfo(), true);
        ContractResponse contractResponse = ContractResponse.builder().responseInfo(responseInfo).contracts(Collections.singletonList(contractRequest.getContract())).build();
        log.info("Contract created");
        return contractResponse;
    }

    public ContractResponse updateContract(ContractRequest contractRequest) {
        log.info("Update contract ["+contractRequest.getContract().getId()+"]");
        // Validate contract request
        contractServiceValidator.validateUpdateContractRequest(contractRequest);
        contractEnrichment.enrichContractOnUpdate(contractRequest);
        workflowService.updateWorkflowStatus(contractRequest);
        contractEnrichment.enrichPreviousContractLineItems(contractRequest);
        contractProducer.push(contractServiceConfiguration.getUpdateContractTopic(), contractRequest);
        try {
            notificationService.sendNotification(contractRequest);
        }catch (Exception e){
            log.error("Exception while sending notification: " + e);
        }


        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(contractRequest.getRequestInfo(), true);
        ContractResponse contractResponse = ContractResponse.builder().responseInfo(responseInfo).contracts(Collections.singletonList(contractRequest.getContract())).build();
        log.info("Contract updated ["+contractRequest.getContract().getId()+"]");
        return contractResponse;
    }


    public List<Contract> searchContracts(ContractCriteria contractCriteria) {
        log.info("Search contracts");

        //Validate the requested parameters
        log.info("Validate the search request parameters");
        contractServiceValidator.validateSearchContractRequest(contractCriteria);

        //Enrich requested search criteria
        log.info("Enrich requested search criteria");
        contractEnrichment.enrichSearchContractRequest(contractCriteria);

        //get contracts from db
        log.info("get enriched contracts list");
        List<Contract> contracts = getContracts(contractCriteria);

        log.info("Contracts searched");
        return contracts;
    }

    public List<Contract> getContracts(ContractCriteria contractCriteria) {

        //get lineItems from db
        log.info("get lineItems from db");
        List<LineItems> lineItems = lineItemsRepository.getLineItems(contractCriteria);

        //collect lineItems for each contract
        Map<String, List<LineItems>> lineItemsMap = lineItems.stream().collect(Collectors.groupingBy(LineItems::getContractId));

        //get contract ids from lineItems
        if (contractCriteria.getIds() == null || contractCriteria.getIds().isEmpty()) {
            contractCriteria.setIds(new ArrayList<>(lineItemsMap.keySet()));
        } else contractCriteria.getIds().addAll(lineItemsMap.keySet());

        //get contracts from db
        log.info("get contracts from db");
        List<Contract> contracts = contractRepository.getContracts(contractCriteria);

        log.info("enrich contract with lineItems");
        //filtering : contracts which have a lineItem from db
        List<Contract> filteredContracts = new ArrayList<>();
        for (Contract contract : contracts) {
            if (lineItemsMap.containsKey(contract.getId())) {
                contract.setLineItems(lineItemsMap.get(contract.getId()));
                filteredContracts.add(contract);
            }
        }

        //set total contracts and  sorting order count in pagination object
        contractCriteria.getPagination().setTotalCount(filteredContracts.size());

        return filteredContracts;
    }

}
