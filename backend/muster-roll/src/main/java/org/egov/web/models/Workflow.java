package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import digit.models.coremodels.Document;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

/**
 * MusterRollRequestWorkflow
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T19:58:09.415+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Workflow {
    @JsonProperty("action")
    private String action = null;

    @JsonProperty("comment")
    private String comment = null;

    @JsonProperty("assignees")
    @Valid
    private List<String> assignees = null;

    @JsonProperty("documents")
    @Valid
    private List<Document> documents = null;


    public Workflow addAssigneesItem(String assigneesItem) {
        if (this.assignees == null) {
            this.assignees = new ArrayList<>();
        }
        this.assignees.add(assigneesItem);
        return this;
    }

    public Workflow addDocumentsItem(Document documentsItem) {
        if (this.documents == null) {
            this.documents = new ArrayList<>();
        }
        if (!this.documents.contains(documentsItem))
            this.documents.add(documentsItem);

        return this;
    }
}

