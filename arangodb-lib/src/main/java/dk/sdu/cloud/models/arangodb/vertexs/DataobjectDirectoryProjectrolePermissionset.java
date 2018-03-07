/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.models.arangodb.vertexs;

import com.arangodb.entity.DocumentField;
import dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectory;
import dk.sdu.cloud.jpa.sduclouddb.ProjectRole;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */

public class DataobjectDirectoryProjectrolePermissionset implements Serializable {


    @DocumentField(DocumentField.Type.ID)
    private int id;
    @DocumentField(DocumentField.Type.KEY)
    private String key;
    private Integer readDataobjectSystemMetadata;
    private Integer readDataobjectMetadata;
    private Integer readDataobject;
    private Integer createdataobjectMetadata;
    private Integer modifyDataobjectMetadata;
    private Integer deleteDataobject;
    private Integer administerDataobject;
    private Integer modifyDataobject;
    private Integer downloadDataobject;
    private Integer enheritedFromParent;

    private java.time.LocalDateTime modified_datetime;
    private java.time.LocalDateTime created_datetime;


    public DataobjectDirectoryProjectrolePermissionset() {
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

    public Integer getReadDataobjectSystemMetadata() {
        return readDataobjectSystemMetadata;
    }

    public void setReadDataobjectSystemMetadata(Integer readDataobjectSystemMetadata) {
        this.readDataobjectSystemMetadata = readDataobjectSystemMetadata;
    }

    public Integer getReadDataobjectMetadata() {
        return readDataobjectMetadata;
    }

    public void setReadDataobjectMetadata(Integer readDataobjectMetadata) {
        this.readDataobjectMetadata = readDataobjectMetadata;
    }

    public Integer getReadDataobject() {
        return readDataobject;
    }

    public void setReadDataobject(Integer readDataobject) {
        this.readDataobject = readDataobject;
    }

    public Integer getCreatedataobjectMetadata() {
        return createdataobjectMetadata;
    }

    public void setCreatedataobjectMetadata(Integer createdataobjectMetadata) {
        this.createdataobjectMetadata = createdataobjectMetadata;
    }

    public Integer getModifyDataobjectMetadata() {
        return modifyDataobjectMetadata;
    }

    public void setModifyDataobjectMetadata(Integer modifyDataobjectMetadata) {
        this.modifyDataobjectMetadata = modifyDataobjectMetadata;
    }

    public Integer getDeleteDataobject() {
        return deleteDataobject;
    }

    public void setDeleteDataobject(Integer deleteDataobject) {
        this.deleteDataobject = deleteDataobject;
    }

    public Integer getAdministerDataobject() {
        return administerDataobject;
    }

    public void setAdministerDataobject(Integer administerDataobject) {
        this.administerDataobject = administerDataobject;
    }

    public Integer getModifyDataobject() {
        return modifyDataobject;
    }

    public void setModifyDataobject(Integer modifyDataobject) {
        this.modifyDataobject = modifyDataobject;
    }

    public Integer getDownloadDataobject() {
        return downloadDataobject;
    }

    public void setDownloadDataobject(Integer downloadDataobject) {
        this.downloadDataobject = downloadDataobject;
    }

    public Integer getEnheritedFromParent() {
        return enheritedFromParent;
    }

    public void setEnheritedFromParent(Integer enheritedFromParent) {
        this.enheritedFromParent = enheritedFromParent;
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
        return "DataobjectDirectoryProjectrolePermissionset{" +
                "id=" + id +
                ", key='" + key + '\'' +
                ", readDataobjectSystemMetadata=" + readDataobjectSystemMetadata +
                ", readDataobjectMetadata=" + readDataobjectMetadata +
                ", readDataobject=" + readDataobject +
                ", createdataobjectMetadata=" + createdataobjectMetadata +
                ", modifyDataobjectMetadata=" + modifyDataobjectMetadata +
                ", deleteDataobject=" + deleteDataobject +
                ", administerDataobject=" + administerDataobject +
                ", modifyDataobject=" + modifyDataobject +
                ", downloadDataobject=" + downloadDataobject +
                ", enheritedFromParent=" + enheritedFromParent +
                ", modified_datetime=" + modified_datetime +
                ", created_datetime=" + created_datetime +
                '}';
    }
}
