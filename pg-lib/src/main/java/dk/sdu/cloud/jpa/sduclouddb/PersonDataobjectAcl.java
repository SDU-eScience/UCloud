/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
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
@Entity
@Table(name = "person_dataobject_acl")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "PersonDataobjectAcl.findAll", query = "SELECT p FROM PersonDataobjectAcl p")
    , @NamedQuery(name = "PersonDataobjectAcl.findByPersonFullname", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.personFullname = :personFullname")
    , @NamedQuery(name = "PersonDataobjectAcl.findByProjectname", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.projectname = :projectname")
    , @NamedQuery(name = "PersonDataobjectAcl.findByProjectshortname", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.projectshortname = :projectshortname")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDataobjectdirectoryurl", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.dataobjectdirectoryurl = :dataobjectdirectoryurl")
    , @NamedQuery(name = "PersonDataobjectAcl.findByProjectrolerefid", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.projectrolerefid = :projectrolerefid")
    , @NamedQuery(name = "PersonDataobjectAcl.findByProjectrolename", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.projectrolename = :projectrolename")
    , @NamedQuery(name = "PersonDataobjectAcl.findByReadDataobjectSystemMetadata", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.readDataobjectSystemMetadata = :readDataobjectSystemMetadata")
    , @NamedQuery(name = "PersonDataobjectAcl.findByReadDataobjectMetadata", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.readDataobjectMetadata = :readDataobjectMetadata")
    , @NamedQuery(name = "PersonDataobjectAcl.findByReadDataobject", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.readDataobject = :readDataobject")
    , @NamedQuery(name = "PersonDataobjectAcl.findByCreatedataobjectMetadata", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.createdataobjectMetadata = :createdataobjectMetadata")
    , @NamedQuery(name = "PersonDataobjectAcl.findByModifyDataobjectMetadata", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.modifyDataobjectMetadata = :modifyDataobjectMetadata")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDeleteDataobject", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.deleteDataobject = :deleteDataobject")
    , @NamedQuery(name = "PersonDataobjectAcl.findByAdministerDataobject", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.administerDataobject = :administerDataobject")
    , @NamedQuery(name = "PersonDataobjectAcl.findByModifyDataobject", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.modifyDataobject = :modifyDataobject")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDownloadDataobject", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.downloadDataobject = :downloadDataobject")
    , @NamedQuery(name = "PersonDataobjectAcl.findByEnheritedFromParent", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.enheritedFromParent = :enheritedFromParent")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDataobjectrefid", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.dataobjectrefid = :dataobjectrefid")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDataobjectname", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.dataobjectname = :dataobjectname")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDataobjectsize", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.dataobjectsize = :dataobjectsize")
    , @NamedQuery(name = "PersonDataobjectAcl.findByDataobjectchecksum", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.dataobjectchecksum = :dataobjectchecksum")
    , @NamedQuery(name = "PersonDataobjectAcl.findByModifiedTs", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "PersonDataobjectAcl.findByCreatedTs", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.createdTs = :createdTs")
    , @NamedQuery(name = "PersonDataobjectAcl.findById", query = "SELECT p FROM PersonDataobjectAcl p WHERE p.id = :id")})
public class PersonDataobjectAcl implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "person_fullname")
    private String personFullname;
    @Column(name = "projectname")
    private String projectname;
    @Column(name = "projectshortname")
    private String projectshortname;
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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @JoinColumn(name = "dataobjectdirectoryrefid", referencedColumnName = "id")
    @ManyToOne
    private DataobjectDirectory dataobjectdirectoryrefid;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;

    public PersonDataobjectAcl() {
    }

    public PersonDataobjectAcl(Integer id) {
        this.id = id;
    }

    public String getPersonFullname() {
        return personFullname;
    }

    public void setPersonFullname(String personFullname) {
        this.personFullname = personFullname;
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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public DataobjectDirectory getDataobjectdirectoryrefid() {
        return dataobjectdirectoryrefid;
    }

    public void setDataobjectdirectoryrefid(DataobjectDirectory dataobjectdirectoryrefid) {
        this.dataobjectdirectoryrefid = dataobjectdirectoryrefid;
    }

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
    }

    public Project getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Project projectrefid) {
        this.projectrefid = projectrefid;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof PersonDataobjectAcl)) {
            return false;
        }
        PersonDataobjectAcl other = (PersonDataobjectAcl) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.PersonDataobjectAcl[ id=" + id + " ]";
    }
    
}
