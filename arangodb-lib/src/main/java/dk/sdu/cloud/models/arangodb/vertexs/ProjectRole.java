/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.models.arangodb.vertexs;

import com.arangodb.entity.DocumentField;
import dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectoryProjectrolePermissionset;
import dk.sdu.cloud.jpa.sduclouddb.ProjectPersonRelation;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 * @author bjhj
 */

public class ProjectRole implements Serializable {

    @DocumentField(DocumentField.Type.KEY)
    private String key;
    @DocumentField(DocumentField.Type.ID)
    private int project_role_id;
    private String projectrolename;
    private java.time.LocalDateTime modified_datetime;
    private java.time.LocalDateTime created_datetime;

    public ProjectRole()
    {}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getProject_role_id() {
        return project_role_id;
    }

    public void setProject_role_id(int project_role_id) {
        this.project_role_id = project_role_id;
    }

    public String getProjectrolename() {
        return projectrolename;
    }

    public void setProjectrolename(String projectrolename) {
        this.projectrolename = projectrolename;
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

    @Override
    public String toString() {
        return "ProjectRole{" +
                "key='" + key + '\'' +
                ", project_role_id=" + project_role_id +
                ", projectrolename='" + projectrolename + '\'' +
                ", modified_datetime=" + modified_datetime +
                ", created_datetime=" + created_datetime +
                '}';
    }
}
