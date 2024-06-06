package org.egov.works.repository.queryBuilder;

import org.apache.commons.lang3.StringUtils;
import org.egov.works.config.Configuration;
import org.egov.works.web.models.JobSchedulerSearchCriteria;
import org.egov.works.web.models.Order;
import org.egov.works.web.models.Pagination;
import org.egov.works.web.models.SearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@Service
public class SchedulerQueryBuilder {

    private final Configuration configuration;

    @Autowired
    public SchedulerQueryBuilder(Configuration configuration) {
        this.configuration = configuration;
    }
    private static final String JOB_SCHEDULER_SEARCH_QUERY = "Select ras.id as rateAnalysisScheduleId, ras.jobid as rateAnalysisScheduleJobId, ras.tenantId as rateAnalysisScheduleTenantId, ras.rateeffectivefrom as rateAnalysisScheduleRateEffectiveFrom, ras.jobStatus as rateAnalysisScheduleJobStatus," +
            " ras.createdtime as rateAnalysisScheduleCreatedTime, ras.lastmodifiedtime as rateAnalysisScheduleLastModifiedTime, ras.lastmodifiedby as rateAnalysisScheduleLastModifiedBy, ras.createdby as rateAnalysisScheduleCreatedBy," +
            " rasd.id as rateAnalysisScheduleDetailsId, rasd.sorId as rateAnalysisScheduleDetailsSorId, rasd.ratesJobId as rateAnalysisScheduleDetailsRatesJobId, rasd.sorCode as rateAnalysisScheduleDetailsSorCode, rasd.status as rateAnalysisScheduleDetailsStatus," +
            " rasd.failureReason as rateAnalysisScheduleDetailsFailuerReason, rasd.additionaldetails as rateAnalysisScheduleDetailsAdditionalDetails From eg_rate_analysis_schedule ras INNER JOIN eg_rate_analysis_schedule_details rasd ON rasd.ratesjobid = ras.id";

    private static String WRAPPER_QUERY = "SELECT * FROM " +
            "(SELECT *, DENSE_RANK() OVER (ORDER BY rateAnalysisSchedule{sortBy} {orderBy}) offset_ FROM " +
            "({})" +
            " result) result_offset " +
            "WHERE offset_ > ? AND offset_ <= ?";

    private void addClauseIfRequired(StringBuilder query, List<Object> preparedStmtList) {
        if (preparedStmtList.isEmpty()) {
            query.append(" WHERE ");
        } else {
            query.append(" AND ");
        }
    }
    public String getJobSchedulerSearchQuery(JobSchedulerSearchCriteria jobSchedulerSearchCriteria, List<Object> preparedStmtList) {
        StringBuilder query = new StringBuilder(JOB_SCHEDULER_SEARCH_QUERY);
        SearchCriteria searchCriteria = jobSchedulerSearchCriteria.getSearchCriteria();
        Pagination pagination = jobSchedulerSearchCriteria.getPagination();

        if (searchCriteria.getTenantId() != null) {
            addClauseIfRequired(query, preparedStmtList);
            query.append("ras.tenantid = ?");
            preparedStmtList.add(searchCriteria.getTenantId());
        }
        if(!CollectionUtils.isEmpty(searchCriteria.getIds())){
            addClauseIfRequired(query, preparedStmtList);
            query.append("ras.id IN (").append(createQuery(searchCriteria.getIds())).append(")");
            addToPreparedStatement(preparedStmtList, searchCriteria.getIds());
        }

        if(!CollectionUtils.isEmpty(searchCriteria.getJobIds())){
            addClauseIfRequired(query, preparedStmtList);
            query.append("ras.jobid IN (").append(createQuery(searchCriteria.getJobIds())).append(")");
            addToPreparedStatement(preparedStmtList, searchCriteria.getJobIds());
        }
        return addPaginationWrapper(query, pagination, preparedStmtList);
    }

    public String addPaginationWrapper(StringBuilder query, Pagination pagination, List<Object> preparedStmtList) {
        String paginatedQuery = addOrderByClause(pagination);

        int limit = null != pagination.getLimit() ? pagination.getLimit() : configuration.getDefaultLimit();
        int offset = null != pagination.getOffSet() ? pagination.getOffSet() : configuration.getDefaultOffset();

        String finalQuery = paginatedQuery.replace("{}", query);

        preparedStmtList.add(offset);
        preparedStmtList.add(limit + offset);

        return finalQuery;
    }


    private String addOrderByClause(Pagination pagination) {

        String paginationWrapper = WRAPPER_QUERY;

        if (!StringUtils.isEmpty(pagination.getSortBy())) {
            paginationWrapper=paginationWrapper.replace("{sortBy}", pagination.getSortBy());
        }
        else{
            paginationWrapper=paginationWrapper.replace("{sortBy}", "createdtime");
        }

        if (pagination.getOrder() != null && Order.fromValue(pagination.getOrder().toString()) != null) {
            paginationWrapper=paginationWrapper.replace("{orderBy}", pagination.getOrder().name());
        }
        else{
            paginationWrapper=paginationWrapper.replace("{orderBy}", Order.DESC.name());
        }

        return paginationWrapper;
    }

    private String createQuery(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        int length = ids.size();
        for (int i = 0; i < length; i++) {
            builder.append(" ?");
            if (i != length - 1)
                builder.append(",");
        }
        return builder.toString();
    }

    private void addToPreparedStatement(List<Object> preparedStmtList, List<String> ids) {
        preparedStmtList.addAll(ids);
    }
}
