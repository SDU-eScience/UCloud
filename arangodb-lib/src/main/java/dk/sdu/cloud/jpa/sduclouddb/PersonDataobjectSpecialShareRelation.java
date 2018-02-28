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
@Table(name = "person_dataobject_special_share_relation")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findAll", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findById", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.id = :id")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByReadDataobjectSystemMetadata", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.readDataobjectSystemMetadata = :readDataobjectSystemMetadata")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByReaddataobjectmetadata", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.readdataobjectmetadata = :readdataobjectmetadata")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByReadDataobject", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.readDataobject = :readDataobject")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByCreatedataobjectmetadata", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.createdataobjectmetadata = :createdataobjectmetadata")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByModifyDataobjectMetadata", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.modifyDataobjectMetadata = :modifyDataobjectMetadata")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByDeletedataobject", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.deletedataobject = :deletedataobject")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByAdministerDataobject", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.administerDataobject = :administerDataobject")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByModifydataobject", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.modifydataobject = :modifydataobject")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByDownloadDataobject", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.downloadDataobject = :downloadDataobject")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByMarkedfordelete", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByModifiedTs", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "PersonDataobjectSpecialShareRelation.findByCreatedTs", query = "SELECT p FROM PersonDataobjectSpecialShareRelation p WHERE p.createdTs = :createdTs")})
public class PersonDataobjectSpecialShareRelation implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "read_dataobject_system_metadata")
    private Integer readDataobjectSystemMetadata;
    @Column(name = "readdataobjectmetadata")
    private Integer readdataobjectmetadata;
    @Column(name = "read_dataobject")
    private Integer readDataobject;
    @Column(name = "createdataobjectmetadata")
    private Integer createdataobjectmetadata;
    @Column(name = "modify_dataobject_metadata")
    private Integer modifyDataobjectMetadata;
    @Column(name = "deletedataobject")
    private Integer deletedataobject;
    @Column(name = "administer_dataobject")
    private Integer administerDataobject;
    @Column(name = "modifydataobject")
    private Integer modifydataobject;
    @Column(name = "download_dataobject")
    private Integer downloadDataobject;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @JoinColumn(name = "dataobjectrefid", referencedColumnName = "id")
    @ManyToOne
    private Dataobject dataobjectrefid;
    @JoinColumn(name = "dataobjectdirectoryrefid", referencedColumnName = "id")
    @ManyToOne
    private DataobjectDirectory dataobjectdirectoryrefid;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;

    public PersonDataobjectSpecialShareRelation() {
    }

    public PersonDataobjectSpecialShareRelation(Integer id) {
        this.id = id;
    }

    public PersonDataobjectSpecialShareRelation(Integer id, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getReadDataobjectSystemMetadata() {
        return readDataobjectSystemMetadata;
    }

    public void setReadDataobjectSystemMetadata(Integer readDataobjectSystemMetadata) {
        this.readDataobjectSystemMetadata = readDataobjectSystemMetadata;
    }

    public Integer getReaddataobjectmetadata() {
        return readdataobjectmetadata;
    }

    public void setReaddataobjectmetadata(Integer readdataobjectmetadata) {
        this.readdataobjectmetadata = readdataobjectmetadata;
    }

    public Integer getReadDataobject() {
        return readDataobject;
    }

    public void setReadDataobject(Integer readDataobject) {
        this.readDataobject = readDataobject;
    }

    public Integer getCreatedataobjectmetadata() {
        return createdataobjectmetadata;
    }

    public void setCreatedataobjectmetadata(Integer createdataobjectmetadata) {
        this.createdataobjectmetadata = createdataobjectmetadata;
    }

    public Integer getModifyDataobjectMetadata() {
        return modifyDataobjectMetadata;
    }

    public void setModifyDataobjectMetadata(Integer modifyDataobjectMetadata) {
        this.modifyDataobjectMetadata = modifyDataobjectMetadata;
    }

    public Integer getDeletedataobject() {
        return deletedataobject;
    }

    public void setDeletedataobject(Integer deletedataobject) {
        this.deletedataobject = deletedataobject;
    }

    public Integer getAdministerDataobject() {
        return administerDataobject;
    }

    public void setAdministerDataobject(Integer administerDataobject) {
        this.administerDataobject = administerDataobject;
    }

    public Integer getModifydataobject() {
        return modifydataobject;
    }

    public void setModifydataobject(Integer modifydataobject) {
        this.modifydataobject = modifydataobject;
    }

    public Integer getDownloadDataobject() {
        return downloadDataobject;
    }

    public void setDownloadDataobject(Integer downloadDataobject) {
        this.downloadDataobject = downloadDataobject;
    }

    public Integer getMarkedfordelete() {
        return markedfordelete;
    }

    public void setMarkedfordelete(Integer markedfordelete) {
        this.markedfordelete = markedfordelete;
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

    public Dataobject getDataobjectrefid() {
        return dataobjectrefid;
    }

    public void setDataobjectrefid(Dataobject dataobjectrefid) {
        this.dataobjectrefid = dataobjectrefid;
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof PersonDataobjectSpecialShareRelation)) {
            return false;
        }
        PersonDataobjectSpecialShareRelation other = (PersonDataobjectSpecialShareRelation) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.PersonDataobjectSpecialShareRelation[ id=" + id + " ]";
    }
    
}
