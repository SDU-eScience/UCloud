/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
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
@Entity
@Table(name = "dataobject_directory")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataobjectDirectory.findAll", query = "SELECT d FROM DataobjectDirectory d")
    , @NamedQuery(name = "DataobjectDirectory.findById", query = "SELECT d FROM DataobjectDirectory d WHERE d.id = :id")
    , @NamedQuery(name = "DataobjectDirectory.findByDataobjectdirectoryurl", query = "SELECT d FROM DataobjectDirectory d WHERE d.dataobjectdirectoryurl = :dataobjectdirectoryurl")
    , @NamedQuery(name = "DataobjectDirectory.findByActive", query = "SELECT d FROM DataobjectDirectory d WHERE d.active = :active")
    , @NamedQuery(name = "DataobjectDirectory.findByMarkedfordelete", query = "SELECT d FROM DataobjectDirectory d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "DataobjectDirectory.findByModifiedTs", query = "SELECT d FROM DataobjectDirectory d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataobjectDirectory.findByCreatedTs", query = "SELECT d FROM DataobjectDirectory d WHERE d.createdTs = :createdTs")
    , @NamedQuery(name = "DataobjectDirectory.findByVolatility", query = "SELECT d FROM DataobjectDirectory d WHERE d.volatility = :volatility")})
public class DataobjectDirectory implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "dataobjectdirectoryurl")
    private String dataobjectdirectoryurl;
    @Column(name = "active")
    private Integer active;
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
    @Column(name = "volatility")
    private Integer volatility;
    @OneToMany(mappedBy = "dataobjectdirectyrefid")
    private List<DataobjectDirectoryProjectrolePermissionset> dataobjectDirectoryProjectrolePermissionsetList;
    @OneToMany(mappedBy = "dataobjectdirectoryrefid")
    private List<PersonDataobjectAcl> personDataobjectAclList;
    @OneToMany(mappedBy = "dataobjectdirectoryrefid")
    private List<DataobjectDirectoryRelation> dataobjectDirectoryRelationList;
    @JoinColumn(name = "dataobjectdirectorytyperefid", referencedColumnName = "id")
    @ManyToOne
    private DataobjectDirectoryType dataobjectdirectorytyperefid;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;
    @OneToMany(mappedBy = "dataobjectcollectionrefid")
    private List<DataTransferHeader> dataTransferHeaderList;
    @OneToMany(mappedBy = "dataobjectdirectoryrefid")
    private List<PersonDataobjectSpecialShareRelation> personDataobjectSpecialShareRelationList;

    public DataobjectDirectory() {
    }

    public DataobjectDirectory(Integer id) {
        this.id = id;
    }

    public DataobjectDirectory(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDataobjectdirectoryurl() {
        return dataobjectdirectoryurl;
    }

    public void setDataobjectdirectoryurl(String dataobjectdirectoryurl) {
        this.dataobjectdirectoryurl = dataobjectdirectoryurl;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
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

    public Integer getVolatility() {
        return volatility;
    }

    public void setVolatility(Integer volatility) {
        this.volatility = volatility;
    }

    @XmlTransient
    public List<DataobjectDirectoryProjectrolePermissionset> getDataobjectDirectoryProjectrolePermissionsetList() {
        return dataobjectDirectoryProjectrolePermissionsetList;
    }

    public void setDataobjectDirectoryProjectrolePermissionsetList(List<DataobjectDirectoryProjectrolePermissionset> dataobjectDirectoryProjectrolePermissionsetList) {
        this.dataobjectDirectoryProjectrolePermissionsetList = dataobjectDirectoryProjectrolePermissionsetList;
    }

    @XmlTransient
    public List<PersonDataobjectAcl> getPersonDataobjectAclList() {
        return personDataobjectAclList;
    }

    public void setPersonDataobjectAclList(List<PersonDataobjectAcl> personDataobjectAclList) {
        this.personDataobjectAclList = personDataobjectAclList;
    }

    @XmlTransient
    public List<DataobjectDirectoryRelation> getDataobjectDirectoryRelationList() {
        return dataobjectDirectoryRelationList;
    }

    public void setDataobjectDirectoryRelationList(List<DataobjectDirectoryRelation> dataobjectDirectoryRelationList) {
        this.dataobjectDirectoryRelationList = dataobjectDirectoryRelationList;
    }

    public DataobjectDirectoryType getDataobjectdirectorytyperefid() {
        return dataobjectdirectorytyperefid;
    }

    public void setDataobjectdirectorytyperefid(DataobjectDirectoryType dataobjectdirectorytyperefid) {
        this.dataobjectdirectorytyperefid = dataobjectdirectorytyperefid;
    }

    public Project getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Project projectrefid) {
        this.projectrefid = projectrefid;
    }

    @XmlTransient
    public List<DataTransferHeader> getDataTransferHeaderList() {
        return dataTransferHeaderList;
    }

    public void setDataTransferHeaderList(List<DataTransferHeader> dataTransferHeaderList) {
        this.dataTransferHeaderList = dataTransferHeaderList;
    }

    @XmlTransient
    public List<PersonDataobjectSpecialShareRelation> getPersonDataobjectSpecialShareRelationList() {
        return personDataobjectSpecialShareRelationList;
    }

    public void setPersonDataobjectSpecialShareRelationList(List<PersonDataobjectSpecialShareRelation> personDataobjectSpecialShareRelationList) {
        this.personDataobjectSpecialShareRelationList = personDataobjectSpecialShareRelationList;
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
        if (!(object instanceof DataobjectDirectory)) {
            return false;
        }
        DataobjectDirectory other = (DataobjectDirectory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectory[ id=" + id + " ]";
    }
    
}
