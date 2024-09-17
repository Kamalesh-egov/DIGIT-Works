package org.egov.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.wms.repository.ServiceRequestRepository;
import org.egov.wms.repository.builder.ReportESQueryBuilder;
import org.egov.wms.repository.rowMapper.ElasticResponseMapper;
import org.egov.wms.util.MDMSUtil;
import org.egov.wms.validator.ValidatorDefaultImplementation;
import org.egov.wms.web.model.*;
import org.egov.wms.web.model.V2.SearchQueryConfiguration;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportService {

    private final ValidatorDefaultImplementation validatorDefaultImplementation;
    private final MDMSUtil mdmsUtil;
    private final ReportESQueryBuilder reportESQueryBuilder;
    private final ObjectMapper mapper;
    private final ElasticResponseMapper elasticResponseMapper;
    private final WMSSearchService wmsSearchService;
    private final ServiceRequestRepository serviceRequestRepository;
    public static final String PAYMENT_TRACKER = "payment-tracker";
    public static final String ESTIMATE = "estimate";

    public ReportService(ValidatorDefaultImplementation validatorDefaultImplementation, MDMSUtil mdmsUtil, ReportESQueryBuilder reportESQueryBuilder, ObjectMapper mapper, ElasticResponseMapper elasticResponseMapper, WMSSearchService wmsSearchService, ServiceRequestRepository serviceRequestRepository) {
        this.validatorDefaultImplementation = validatorDefaultImplementation;
        this.mdmsUtil = mdmsUtil;
        this.reportESQueryBuilder = reportESQueryBuilder;
        this.mapper = mapper;
        this.elasticResponseMapper = elasticResponseMapper;
        this.wmsSearchService = wmsSearchService;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    public AggsResponse getPaymentTracker(AggregationRequest aggregationRequest) {
        WMSSearchRequest searchRequest = getSearchRequest(aggregationRequest, aggregationRequest.getAggregationSearchCriteria().getModuleSearchCriteria());
        validatorDefaultImplementation.validateSearchCriteria(searchRequest, PAYMENT_TRACKER);
        SearchQueryConfiguration searchQueryConfiguration = mdmsUtil.getConfigFromMDMS(searchRequest, PAYMENT_TRACKER);
        Map<String, Object> reportQuery = reportESQueryBuilder.getReportEsQuery(aggregationRequest, searchRequest, PAYMENT_TRACKER);

//        StringBuilder uri = wmsSearchService.getURI(searchQueryConfiguration.getIndex(), SEARCH_PATH);
//        Object result = serviceRequestRepository.fetchESResult(uri, reportQuery);
        Object elasticResponse;
        try {
            elasticResponse = mapper.readValue(new File("../wms/src/main/resources/elastic_response_sample.json"), Object.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            String q = mapper.writeValueAsString(reportQuery);
            log.info("Query: "+q);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        log.info("Inside Payment Tracker Report");
        AggsResponse aggsResponse;
        try {
            aggsResponse = elasticResponseMapper.mapElasticResponse(elasticResponse);
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }

        // From the aggsResponse fetch all the projectNumbers present in projectPaymentDetails
        List<String> projectNumbers = getProjectNumbers(aggsResponse);




        WMSSearchResponse response = wmsSearchService.getInboxResponse(getSearchRequestForEstimate(aggregationRequest, projectNumbers), ESTIMATE);

        Map<String, Double> projectIdEstimateMap = getProjectEstimateMap(response);

        enrichAggsResponse(aggsResponse, projectIdEstimateMap);

        // Search the estimate index and fetch all the estimates with the projectNumbers

        return aggsResponse;
    }

    private void enrichAggsResponse(AggsResponse aggsResponse, Map<String, Double> projectIdEstimateMap) {

        for (int i = 0; i < aggsResponse.getProjectPaymentDetails().size(); i++) {
            ProjectPaymentDetails projectPaymentDetails = aggsResponse.getProjectPaymentDetails().get(i);
            String projectNumber = projectPaymentDetails.getProjectNumber();
            double totalEstimatedAmount = projectIdEstimateMap.get(projectNumber);
            projectPaymentDetails.setTotal(totalEstimatedAmount);
        }
    }

    private Map<String, Double> getProjectEstimateMap(WMSSearchResponse response) {
        Map<String, Double> projectEstimateMap = new HashMap<>();

        // Loop through each estimate
        for (int i = 0; i < response.getItems().size(); i++) {
            Map<String,Object> businessObject = response.getItems().get(i).getBusinessObject();
            Object additionalDetails = businessObject.get("additionalDetails");

            Map<String, Object> additionalDetailsMap = (Map<String, Object>) additionalDetails;

            // Extract projectNumber and totalEstimatedAmount
            String projectNumber = (String) additionalDetailsMap.get("projectNumber");
            double totalEstimatedAmount = (double) additionalDetailsMap.get("totalEstimatedAmount");

            // Put it in the map
            projectEstimateMap.put(projectNumber, totalEstimatedAmount);
        }
        return projectEstimateMap;
    }



    /**
     * Get the projectNumbers from the aggsResponse
     * @param aggsResponse
     * @return
     */
    private List<String> getProjectNumbers(AggsResponse aggsResponse) {
        return aggsResponse.getProjectPaymentDetails().stream().map(projectPaymentDetails -> projectPaymentDetails.getProjectNumber()).collect(Collectors.toList());
    }

    private WMSSearchRequest getSearchRequestForEstimate(AggregationRequest aggregationRequest, List<String> projectNumbers) {
        HashMap<String, Object> moduleSearchCriteria = new HashMap<>();
        moduleSearchCriteria.put("projectId", projectNumbers);

        return getSearchRequest(aggregationRequest, moduleSearchCriteria);
    }

    WMSSearchRequest getSearchRequest(AggregationRequest aggregationRequest, HashMap <String, Object> moduleSearchCriteria) {
        return WMSSearchRequest
                .builder()
                .RequestInfo(aggregationRequest.getRequestInfo())
                .inbox(WMSSearchCriteria.builder()
                        .tenantId(aggregationRequest.getAggregationSearchCriteria().getTenantId())
                        .moduleSearchCriteria(moduleSearchCriteria)
                        .limit(aggregationRequest.getAggregationSearchCriteria().getLimit())
                        .offset(0)
                        .build())
                .build();
    }


}
