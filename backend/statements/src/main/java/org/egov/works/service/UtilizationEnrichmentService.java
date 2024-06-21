package org.egov.works.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.egov.works.services.common.models.contract.Contract;
import org.egov.works.services.common.models.contract.LineItems;
import org.egov.works.services.common.models.measurement.Measure;
import org.egov.works.services.common.models.measurement.Measurement;
import org.egov.works.util.EnrichmentUtil;
import org.egov.works.util.MdmsUtil;
import org.egov.works.web.models.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UtilizationEnrichmentService {

    private final EnrichmentUtil enrichmentUtil;
    private final MdmsUtil mdmsUtil;
    private final CalculatorService calculatorService;

    public UtilizationEnrichmentService(EnrichmentUtil enrichmentUtil, MdmsUtil mdmsUtil, CalculatorService calculatorService) {
        this.enrichmentUtil = enrichmentUtil;
        this.mdmsUtil = mdmsUtil;
        this.calculatorService = calculatorService;
    }

    public Statement createUtilizationStatement(StatementCreateRequest statementCreateRequest,
                                                Measurement measurement, Contract contract, Estimate estimate) {

        Statement statement = enrichmentUtil.getEnrichedStatement(statementCreateRequest);
//        Map<String, Measure> measureLineItemIdToMeasureMap = measurement.getMeasures().stream()
//                .collect(Collectors.toMap(Measure::getId, Function.identity()));
        List<Measure> measureList = measurement.getMeasures();
        Map<String, String> contractLineItemRefToEstDetailIdMap = contract.getLineItems().stream()
                .collect(Collectors.toMap(LineItems::getContractLineItemRef, LineItems::getEstimateLineItemId));
        Map<String, EstimateDetail> estimateDetailIdToEstimateDetailMap = estimate.getEstimateDetails().stream()
                .collect(Collectors.toMap(EstimateDetail::getId, Function.identity()));
        List<String> sorIdsFromEstimateDetail = estimate.getEstimateDetails().stream().filter(estimateDetail -> estimateDetail.getCategory()
                .equalsIgnoreCase("SOR")).map(EstimateDetail::getSorId).collect(Collectors.toList());

        Map<String, Sor> sorIdToSorMap = mdmsUtil.fetchSorData(statementCreateRequest.getRequestInfo(),
                statementCreateRequest.getStatementRequest().getTenantId(), sorIdsFromEstimateDetail, Boolean.TRUE);
        Set<String> worksSorIds = sorIdToSorMap.values().stream().filter(sor -> sor.getSorType()
                        .equalsIgnoreCase("W")).map(Sor::getId).collect(Collectors.toSet());
        Map<String, SorComposition> sorIdToCompositionMap = null;
        if (!worksSorIds.isEmpty())
            sorIdToCompositionMap = mdmsUtil.fetchSorComposition(statementCreateRequest.getRequestInfo()
                , worksSorIds, statementCreateRequest.getStatementRequest().getTenantId(), measurement.getAuditDetails().getCreatedTime());

//        Set<String> basicSorIds = new HashSet<>();
//        for (Map.Entry<String, SorComposition> entry : sorIdToCompositionMap.entrySet()) {
//            List<SorCompositionBasicSorDetail> sorCompositionBasicSorDetails = entry.getValue().getBasicSorDetails();
//            basicSorIds.addAll(sorCompositionBasicSorDetails.stream().map(sorCompositionBasicSorDetail -> sorCompositionBasicSorDetail.getSorId()).collect(Collectors.toSet()));
//        }
//        Set<String> basicSorIds = sorIdToCompositionMap.values().stream().map(sorComposition -> sorComposition.getBasicSorDetails().stream().map(sorCompositionBasicSorDetail -> sorCompositionBasicSorDetail.getSorId()).collect(Collectors.toSet()));
        Set<String> basicSorIds = sorIdToCompositionMap.values().stream()
                .flatMap(sorComposition -> sorComposition.getBasicSorDetails().stream())
                .map(SorCompositionBasicSorDetail::getSorId)
                .collect(Collectors.toSet());

        Map<String, Sor> basicSorIdFromCompositionToSorMap = mdmsUtil.fetchSorData(statementCreateRequest.getRequestInfo(),
                statementCreateRequest.getStatementRequest().getTenantId(), new ArrayList<>(basicSorIds), Boolean.TRUE);

        sorIdToSorMap.putAll(basicSorIdFromCompositionToSorMap);

        Map<String, List<Rates>> basicSorRateMap = mdmsUtil.fetchRateForNonWorksSor(new ArrayList<>(sorIdToSorMap.keySet()),
                statementCreateRequest.getRequestInfo(), statementCreateRequest.getStatementRequest().getTenantId());

        List<BasicSorDetails> basicSorDetails = getBasicSorDetailsList(sorIdToSorMap, sorIdToCompositionMap,
                estimateDetailIdToEstimateDetailMap, contractLineItemRefToEstDetailIdMap, measureList, basicSorRateMap);

        statement.setBasicSorDetails(basicSorDetails);

        List<SorDetail> sorDetails = getSorDetails(sorIdToSorMap, sorIdToCompositionMap,
                estimateDetailIdToEstimateDetailMap, contractLineItemRefToEstDetailIdMap, measureList, basicSorRateMap,
                statement.getId(), statementCreateRequest.getStatementRequest().getTenantId());
        statement.setSorDetails(sorDetails);

        return statement;
    }

    List<BasicSorDetails> getBasicSorDetailsList(Map<String, Sor> sorIdToSorMap, Map<String, SorComposition> sorIdToCompositionMap,
                                                 Map<String, EstimateDetail> estimateDetailIdToEstimateDetailMap,
                                                 Map<String, String> contractLineItemRefToEstDetailIdMap,
                                                 List<Measure> measureList, Map<String, List<Rates>> basicSorRateMap) {

        Map<String, BasicSorDetails> typeToBasicSorDetailsMap = new HashMap<>();
        for (Measure measure : measureList) {
            String estimateDetailId = contractLineItemRefToEstDetailIdMap.get(measure.getTargetId());
            EstimateDetail estimateDetail = estimateDetailIdToEstimateDetailMap.get(estimateDetailId);
            if (estimateDetail.getCategory().equalsIgnoreCase("SOR")) {
                Sor sor;
                if (sorIdToSorMap.get(estimateDetail.getSorId()) != null) {
                    sor = sorIdToSorMap.get(estimateDetail.getSorId());
                } else {
                    log.error("No SOR found for sorId : " + estimateDetail.getSorId());
                    continue;
                }
                if (sor.getSorType().equalsIgnoreCase("W")) {
                    calculatorService.calculateUtilizationForWorksSor(sorIdToCompositionMap, estimateDetail,
                            basicSorRateMap, typeToBasicSorDetailsMap, sor, measure, sorIdToSorMap);
                } else {
                    calculatorService.calculateUtilizationForBasicSor(estimateDetail, basicSorRateMap, typeToBasicSorDetailsMap,
                            sor, measure, estimateDetail.getSorId());
                }
            }
        }
        return typeToBasicSorDetailsMap.values().stream().collect(Collectors.toList());
    }


    List<SorDetail> getSorDetails(Map<String, Sor> sorIdToSorMap, Map<String, SorComposition> sorIdToCompositionMap,
                                    Map<String, EstimateDetail> estimateDetailIdToEstimateDetailMap,
                                    Map<String, String> contractLineItemRefToEstDetailIdMap,
                                    List<Measure> measureList, Map<String, List<Rates>> basicSorRateMap, String statementId,
                                  String tenantId) {


        List<SorDetail> sorDetails = new ArrayList<>();
        for (Measure measure : measureList) {

            String estimateDetailId = contractLineItemRefToEstDetailIdMap.get(measure.getTargetId());
            EstimateDetail estimateDetail = estimateDetailIdToEstimateDetailMap.get(estimateDetailId);
            if (!estimateDetail.getCategory().equalsIgnoreCase("SOR")) {
                continue;
            }
            Sor sor = sorIdToSorMap.get(estimateDetail.getSorId());
            Rates rates = basicSorRateMap.get(estimateDetail.getSorId()).get(0);// TODO fetch correct rates
            SorDetail sorDetail = enrichmentUtil.getEnrichedSorDetail(statementId, tenantId, sor, rates);


            Map<String, BasicSorDetails> typeToBasicSorDetailsMap = new HashMap<>();
            if (sor.getSorType().equalsIgnoreCase("W")) {
                calculatorService.calculateUtilizationForWorksSor(sorIdToCompositionMap, estimateDetail,
                        basicSorRateMap, typeToBasicSorDetailsMap, sor, measure, sorIdToSorMap);
            } else {
                calculatorService.calculateUtilizationForBasicSor(estimateDetail, basicSorRateMap, typeToBasicSorDetailsMap,
                        sor, measure, estimateDetail.getSorId());
            }
            sorDetail.setBasicSorDetails(typeToBasicSorDetailsMap.values().stream().collect(Collectors.toList()));

            List<BasicSor> lineItems = getLineItems(estimateDetail.getSorId(), sorIdToSorMap, sorIdToCompositionMap,
                    estimateDetail, measure, basicSorRateMap, sorDetail.getId());

            sorDetail.setLineItems(lineItems);

            sorDetails.add(sorDetail);


        }


        return sorDetails;
    }

    List<BasicSor> getLineItems(String sorId, Map<String, Sor> sorIdToSorMap, Map<String, SorComposition> sorIdToCompositionMap,
                                EstimateDetail estimateDetail,
                                Measure measure, Map<String, List<Rates>> basicSorRateMap, String referenceId) {
        List<BasicSor> lineItems = new ArrayList<>();
        if (sorIdToSorMap.get(sorId).getSorType().equalsIgnoreCase("W")) {
            for(SorCompositionBasicSorDetail compositionBasicSorDetail : sorIdToCompositionMap.get(sorId).getBasicSorDetails()) {
                BigDecimal quantity = measure.getCumulativeValue().multiply(compositionBasicSorDetail.getQuantity()).divide(sorIdToCompositionMap.get(sorId).getQuantity()).divide(sorIdToSorMap.get(compositionBasicSorDetail.getSorId()).getQuantity());
                lineItems.add(getBasicSorLineItem(estimateDetail, measure, basicSorRateMap, sorIdToSorMap.get(compositionBasicSorDetail.getSorId()), referenceId, quantity));
            }
        } else {
            BigDecimal quantity = measure.getCumulativeValue().divide(sorIdToSorMap.get(sorId).getQuantity());
            lineItems.add(getBasicSorLineItem(estimateDetail, measure, basicSorRateMap, sorIdToSorMap.get(sorId), referenceId, quantity));
        }
        return lineItems;
    }

    BasicSor getBasicSorLineItem(EstimateDetail estimateDetail, Measure measure,  Map<String, List<Rates>> basicSorRateMap,
                                 Sor sor, String referenceId, BigDecimal quantity) {

        Map<String, BasicSorDetails> typeToBasicSorDetailsMap = new HashMap<>();
        if (!basicSorRateMap.containsKey(sor.getId()))
            throw new CustomException("RATES_NOT_FOUND", "Rates not found for given SOR :: " + sor.getId());

        Rates rates = basicSorRateMap.get(sor.getId()).get(0);
        calculatorService.calculateUtilizationForBasicSor1(estimateDetail.getIsDeduction(), rates, typeToBasicSorDetailsMap,
                sor, quantity);
        List<BasicSorDetails> basicSorDetails = typeToBasicSorDetailsMap.values().stream().collect(Collectors.toList());
        return enrichmentUtil.getEnrichedLineItem(sor, basicSorDetails, referenceId, rates);

    }




}
