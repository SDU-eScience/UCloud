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

public class Project {

    @DocumentField(DocumentField.Type.KEY)
    private String key;
    @DocumentField(DocumentField.Type.ID)
    private int id;
    private Integer project_id;
    private String project_name;
    private String project_shortname;
    private java.time.LocalDateTime projectstart;
    private java.time.LocalDateTime projectend;
    private java.time.LocalDateTime modified_datetime;
    private java.time.LocalDateTime created_datetime;
    private Integer visible;
    private String project_type;



    public Project() {
        super();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getProject_id() {
        return project_id;
    }

    public void setProject_id(Integer project_id) {
        this.project_id = project_id;
    }

    public String getProject_name() {
        return project_name;
    }

    public void setProject_name(String project_name) {
        this.project_name = project_name;
    }

    public String getProject_shortname() {
        return project_shortname;
    }

    public void setProject_shortname(String project_shortname) {
        this.project_shortname = project_shortname;
    }

    public LocalDateTime getProjectstart() {
        return projectstart;
    }

    public void setProjectstart(LocalDateTime projectstart) {
        this.projectstart = projectstart;
    }

    public LocalDateTime getProjectend() {
        return projectend;
    }

    public void setProjectend(LocalDateTime projectend) {
        this.projectend = projectend;
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

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }

    public String getProject_type() {
        return project_type;
    }

    public void setProject_type(String project_type) {
        this.project_type = project_type;
    }
}
