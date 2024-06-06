package org.egov.works.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.works.util.EnrichmentUtil;
import org.egov.works.util.MdmsUtil;
import org.egov.works.util.ResponseInfoFactory;
import org.egov.works.validator.RateAnalysisValidator;
import org.egov.works.web.models.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RateAnalysisService {

    private final MdmsUtil mdmsUtil;
    private final CalculatorService calculatorService;
    private final EnrichmentService enrichmentService;
    private final RateAnalysisValidator  rateAnalysisValidator;
    private final ResponseInfoFactory responseInfoFactory;
    private final EnrichmentUtil enrichmentUtil;
    private final MdmsService mdmsService;


    public RateAnalysisService(MdmsUtil mdmsUtil, CalculatorService calculatorService, EnrichmentService enrichmentService, RateAnalysisValidator rateAnalysisValidator, ResponseInfoFactory responseInfoFactory, EnrichmentUtil enrichmentUtil, MdmsService mdmsService) {
        this.mdmsUtil = mdmsUtil;
        this.calculatorService = calculatorService;
        this.enrichmentService = enrichmentService;
        this.rateAnalysisValidator = rateAnalysisValidator;
        this.responseInfoFactory = responseInfoFactory;
        this.enrichmentUtil = enrichmentUtil;
        this.mdmsService = mdmsService;
    }

    public RateAnalysisResponse calculateRate(AnalysisRequest analysisRequest) {
        Map<String, SorComposition> sorIdCompositionMap = mdmsUtil.fetchSorComposition(analysisRequest);
        Map<String, List<Rates>> basicRatesMap = mdmsUtil.fetchBasicRates(analysisRequest, sorIdCompositionMap);
        Map<String, JsonNode> sorMap = mdmsUtil.fetchSor(analysisRequest, sorIdCompositionMap);
        List<RateAnalysis> rateAnalysis = calculatorService.calculateRateAnalysis(analysisRequest, sorIdCompositionMap,
                basicRatesMap, sorMap, false);
        RateAnalysisResponse rateAnalysisResponse = RateAnalysisResponse.builder()
                .rateAnalysis(rateAnalysis)
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(analysisRequest.getRequestInfo(), true))
                .build();
        return rateAnalysisResponse;
    }

    public List<Rates> createRateAnalysis(AnalysisRequest analysisRequest) {
        Map<String, SorComposition> sorIdCompositionMap = mdmsUtil.fetchSorComposition(analysisRequest);
        Map<String, List<Rates>> basicRatesMap = mdmsUtil.fetchBasicRates(analysisRequest, sorIdCompositionMap);
        Map<String, JsonNode> sorMap = mdmsUtil.fetchSor(analysisRequest, sorIdCompositionMap);
        rateAnalysisValidator.validateRevisionOfRates(analysisRequest, sorIdCompositionMap, basicRatesMap, sorMap);
        List<RateAnalysis> rateAnalysis = calculatorService.calculateRateAnalysis(analysisRequest, sorIdCompositionMap,
                basicRatesMap, sorMap, true);

        List<Rates> calculatedrates = enrichmentService.enrichRates(rateAnalysis);
        Map<String, Rates> worksRatesMap = mdmsUtil.fetchWorksRates(analysisRequest);
        rateAnalysisValidator.validateNewRates(worksRatesMap, calculatedrates);
        mdmsService.createRevisedRates(calculatedrates, worksRatesMap, analysisRequest.getRequestInfo());

        return calculatedrates;
    }

}
