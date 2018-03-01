/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.models.arangodb;

import com.arangodb.entity.DocumentField;

import java.time.LocalDateTime;

/**
 *
 * @author bjhj
 */

//_id, _key, _rev, _from, _to
public class DataObject {

    @DocumentField(DocumentField.Type.ID)
    private int id;
    @DocumentField(DocumentField.Type.KEY)
    private String key;
    private String data_object_name;
    private Integer data_object_size;
    private String data_object_checksum;
    private java.time.LocalDateTime modified_datetime;
    private java.time.LocalDateTime created_datetime;
    private java.time.LocalDateTime last_accessed_datetime;
    private String data_object_classification;
    private String data_object_file_extension;

    public DataObject() {
        super();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getData_object_name() {
        return data_object_name;
    }

    public void setData_object_name(String data_object_name) {
        this.data_object_name = data_object_name;
    }

    public Integer getData_object_size() {
        return data_object_size;
    }

    public void setData_object_size(Integer data_object_size) {
        this.data_object_size = data_object_size;
    }

    public String getData_object_checksum() {
        return data_object_checksum;
    }

    public void setData_object_checksum(String data_object_checksum) {
        this.data_object_checksum = data_object_checksum;
    }

    public LocalDateTime getModified_datetime() {
        return modified_datetime;
    }

    public void setModified_datetime(LocalDateTime modified_datetime) {
        this.modified_datetime = modified_datetime;
    }

    public LocalDateTime getCreated_datetime() {
        return created_datetime;
    }

    public void setCreated_datetime(LocalDateTime created_datetime) {
        this.created_datetime = created_datetime;
    }

    public LocalDateTime getLast_accessed_datetime() {
        return last_accessed_datetime;
    }

    public void setLast_accessed_datetime(LocalDateTime last_accessed_datetime) {
        this.last_accessed_datetime = last_accessed_datetime;
    }

    public String getData_object_classification() {
        return data_object_classification;
    }

    public void setData_object_classification(String data_object_classification) {
        this.data_object_classification = data_object_classification;
    }

    public String getData_object_file_extension() {
        return data_object_file_extension;
    }

    public void setData_object_file_extension(String data_object_file_extension) {
        this.data_object_file_extension = data_object_file_extension;
    }
}
