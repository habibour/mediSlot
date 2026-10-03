package com.medislot.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = DoctorDocument.INDEX, createIndex = false)
public class DoctorDocument {

    public static final String INDEX = "medislot-doctors";

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String fullName;

    @Field(type = FieldType.Text)
    private String specialty;

    @Field(type = FieldType.Text)
    private String bio;

    protected DoctorDocument() {
    }

    public DoctorDocument(Long doctorId, String fullName, String specialty, String bio) {
        this.id = String.valueOf(doctorId);
        this.fullName = fullName;
        this.specialty = specialty;
        this.bio = bio;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getSpecialty() { return specialty; }
    public String getBio() { return bio; }
}
