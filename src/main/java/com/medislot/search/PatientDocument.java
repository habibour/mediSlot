package com.medislot.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/** Name only: no DOB, phone, e-mail or national ID ever leaves the primary database (data minimisation). */
@Document(indexName = PatientDocument.INDEX, createIndex = false)
public class PatientDocument {

    public static final String INDEX = "medislot-patients";

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String fullName;

    protected PatientDocument() {
    }

    public PatientDocument(Long patientId, String fullName) {
        this.id = String.valueOf(patientId);
        this.fullName = fullName;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
}
