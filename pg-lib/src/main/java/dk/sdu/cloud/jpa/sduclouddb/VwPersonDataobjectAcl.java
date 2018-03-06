/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
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
@Entity
@Table(name = "vw_person_dataobject_acl")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "VwPersonDataobjectAcl.findAll", query = "SELECT v FROM VwPersonDataobjectAcl v")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByPersonrefid", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.personrefid = :personrefid")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByPersonFullname", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.personFullname = :personFullname")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByProjectrefid", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.projectrefid = :projectrefid")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByProjectname", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.projectname = :projectname")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByProjectshortname", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.projectshortname = :projectshortname")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDataobjectdirectoryrefid", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.dataobjectdirectoryrefid = :dataobjectdirectoryrefid")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDataobjectdirectoryurl", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.dataobjectdirectoryurl = :dataobjectdirectoryurl")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByProjectrolerefid", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.projectrolerefid = :projectrolerefid")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByProjectrolename", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.projectrolename = :projectrolename")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByReadDataobjectSystemMetadata", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.readDataobjectSystemMetadata = :readDataobjectSystemMetadata")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByReadDataobjectMetadata", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.readDataobjectMetadata = :readDataobjectMetadata")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByReadDataobject", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.readDataobject = :readDataobject")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByCreatedataobjectMetadata", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.createdataobjectMetadata = :createdataobjectMetadata")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByModifyDataobjectMetadata", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.modifyDataobjectMetadata = :modifyDataobjectMetadata")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDeleteDataobject", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.deleteDataobject = :deleteDataobject")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByAdministerDataobject", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.administerDataobject = :administerDataobject")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByModifyDataobject", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.modifyDataobject = :modifyDataobject")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDownloadDataobject", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.downloadDataobject = :downloadDataobject")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByEnheritedFromParent", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.enheritedFromParent = :enheritedFromParent")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDataobjectrefid", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.dataobjectrefid = :dataobjectrefid")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDataobjectname", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.dataobjectname = :dataobjectname")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDataobjectsize", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.dataobjectsize = :dataobjectsize")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByDataobjectchecksum", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.dataobjectchecksum = :dataobjectchecksum")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByModifiedTs", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "VwPersonDataobjectAcl.findByCreatedTs", query = "SELECT v FROM VwPersonDataobjectAcl v WHERE v.createdTs = :createdTs")})
public class VwPersonDataobjectAcl implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "personrefid")
    private Integer personrefid;
    @Column(name = "person_fullname")
    private String personFullname;
    @Column(name = "projectrefid")
    private Integer projectrefid;
    @Column(name = "projectname")
    private String projectname;
    @Column(name = "projectshortname")
    private String projectshortname;
    @Column(name = "dataobjectdirectoryrefid")
    private Integer dataobjectdirectoryrefid;
    @Column(name = "dataobjectdirectoryurl")
    private String dataobjectdirectoryurl;
    @Column(name = "projectrolerefid")
    private Integer projectrolerefid;
    @Column(name = "projectrolename")
    private String projectrolename;
    @Column(name = "read_dataobject_system_metadata")
    private Integer readDataobjectSystemMetadata;
    @Column(name = "read_dataobject_metadata")
    private Integer readDataobjectMetadata;
    @Column(name = "read_dataobject")
    private Integer readDataobject;
    @Column(name = "createdataobject_metadata")
    private Integer createdataobjectMetadata;
    @Column(name = "modify_dataobject_metadata")
    private Integer modifyDataobjectMetadata;
    @Column(name = "delete_dataobject")
    private Integer deleteDataobject;
    @Column(name = "administer_dataobject")
    private Integer administerDataobject;
    @Column(name = "modify_dataobject")
    private Integer modifyDataobject;
    @Column(name = "download_dataobject")
    private Integer downloadDataobject;
    @Column(name = "enherited_from_parent")
    private Integer enheritedFromParent;
    @Id
    @Column(name = "dataobjectrefid")
    private String dataobjectrefid;
    @Column(name = "dataobjectname")
    private String dataobjectname;
    @Column(name = "dataobjectsize")
    private Integer dataobjectsize;
    @Column(name = "dataobjectchecksum")
    private String dataobjectchecksum;
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;

    public VwPersonDataobjectAcl() {
    }

    public Integer getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Integer personrefid) {
        this.personrefid = personrefid;
    }

    public String getPersonFullname() {
        return personFullname;
    }

    public void setPersonFullname(String personFullname) {
        this.personFullname = personFullname;
    }

    public Integer getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Integer projectrefid) {
        this.projectrefid = projectrefid;
    }

    public String getProjectname() {
        return projectname;
    }

    public void setProjectname(String projectname) {
        this.projectname = projectname;
    }

    public String getProjectshortname() {
        return projectshortname;
    }

    public void setProjectshortname(String projectshortname) {
        this.projectshortname = projectshortname;
    }

    public Integer getDataobjectdirectoryrefid() {
        return dataobjectdirectoryrefid;
    }

    public void setDataobjectdirectoryrefid(Integer dataobjectdirectoryrefid) {
        this.dataobjectdirectoryrefid = dataobjectdirectoryrefid;
    }

    public String getDataobjectdirectoryurl() {
        return dataobjectdirectoryurl;
    }

    public void setDataobjectdirectoryurl(String dataobjectdirectoryurl) {
        this.dataobjectdirectoryurl = dataobjectdirectoryurl;
    }

    public Integer getProjectrolerefid() {
        return projectrolerefid;
    }

    public void setProjectrolerefid(Integer projectrolerefid) {
        this.projectrolerefid = projectrolerefid;
    }

    public String getProjectrolename() {
        return projectrolename;
    }

    public void setProjectrolename(String projectrolename) {
        this.projectrolename = projectrolename;
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

    public String getDataobjectrefid() {
        return dataobjectrefid;
    }

    public void setDataobjectrefid(String dataobjectrefid) {
        this.dataobjectrefid = dataobjectrefid;
    }

    public String getDataobjectname() {
        return dataobjectname;
    }

    public void setDataobjectname(String dataobjectname) {
        this.dataobjectname = dataobjectname;
    }

    public Integer getDataobjectsize() {
        return dataobjectsize;
    }

    public void setDataobjectsize(Integer dataobjectsize) {
        this.dataobjectsize = dataobjectsize;
    }

    public String getDataobjectchecksum() {
        return dataobjectchecksum;
    }

    public void setDataobjectchecksum(String dataobjectchecksum) {
        this.dataobjectchecksum = dataobjectchecksum;
    }

    public Date getModifiedTs() {
        return modifiedTs;
    }

    public void setModifiedTs(Date modifiedTs) {
        this.modifiedTs = modifiedTs;
    }

    public Date getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(Date createdTs) {
        this.createdTs = createdTs;
    }
    
}
