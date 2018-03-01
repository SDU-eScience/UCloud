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
public class DataObjectDirectory {

    @DocumentField(DocumentField.Type.KEY)
    private String key;
    @DocumentField(DocumentField.Type.ID)
    private int id;
    private Integer data_object_directory_id;
    private String data_object_directory_url;
    private java.time.LocalDateTime modified_datetime;
    private java.time.LocalDateTime created_datetime;
    private Integer volatility;

    public DataObjectDirectory() {
        super();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getData_object_directory_id() {
        return data_object_directory_id;
    }

    public void setData_object_directory_id(Integer data_object_directory_id) {
        this.data_object_directory_id = data_object_directory_id;
    }

    public String getData_object_directory_url() {
        return data_object_directory_url;
    }

    public void setData_object_directory_url(String data_object_directory_url) {
        this.data_object_directory_url = data_object_directory_url;
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

    public Integer getVolatility() {
        return volatility;
    }

    public void setVolatility(Integer volatility) {
        this.volatility = volatility;
    }
}
