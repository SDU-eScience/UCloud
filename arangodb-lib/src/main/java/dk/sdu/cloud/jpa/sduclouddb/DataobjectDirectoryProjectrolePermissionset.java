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
@Table(name = "dataobject_directory_projectrole_permissionset")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findAll", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findById", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.id = :id")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByReadDataobjectSystemMetadata", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.readDataobjectSystemMetadata = :readDataobjectSystemMetadata")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByReadDataobjectMetadata", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.readDataobjectMetadata = :readDataobjectMetadata")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByReadDataobject", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.readDataobject = :readDataobject")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByCreatedataobjectMetadata", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.createdataobjectMetadata = :createdataobjectMetadata")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByModifyDataobjectMetadata", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.modifyDataobjectMetadata = :modifyDataobjectMetadata")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByDeleteDataobject", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.deleteDataobject = :deleteDataobject")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByAdministerDataobject", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.administerDataobject = :administerDataobject")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByModifyDataobject", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.modifyDataobject = :modifyDataobject")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByDownloadDataobject", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.downloadDataobject = :downloadDataobject")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByEnheritedFromParent", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.enheritedFromParent = :enheritedFromParent")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByActive", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.active = :active")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByMarkedfordelete", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByModifiedTs", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByCreatedTs", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.createdTs = :createdTs")
    , @NamedQuery(name = "DataobjectDirectoryProjectrolePermissionset.findByVolatility", query = "SELECT d FROM DataobjectDirectoryProjectrolePermissionset d WHERE d.volatility = :volatility")})
public class DataobjectDirectoryProjectrolePermissionset implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
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
    @JoinColumn(name = "dataobjectdirectyrefid", referencedColumnName = "id")
    @ManyToOne
    private DataobjectDirectory dataobjectdirectyrefid;
    @JoinColumn(name = "projectrolerefid", referencedColumnName = "id")
    @ManyToOne
    private ProjectRole projectrolerefid;

    public DataobjectDirectoryProjectrolePermissionset() {
    }

    public DataobjectDirectoryProjectrolePermissionset(Integer id) {
        this.id = id;
    }

    public DataobjectDirectoryProjectrolePermissionset(Integer id, Date modifiedTs, Date createdTs) {
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

    public DataobjectDirectory getDataobjectdirectyrefid() {
        return dataobjectdirectyrefid;
    }

    public void setDataobjectdirectyrefid(DataobjectDirectory dataobjectdirectyrefid) {
        this.dataobjectdirectyrefid = dataobjectdirectyrefid;
    }

    public ProjectRole getProjectrolerefid() {
        return projectrolerefid;
    }

    public void setProjectrolerefid(ProjectRole projectrolerefid) {
        this.projectrolerefid = projectrolerefid;
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
        if (!(object instanceof DataobjectDirectoryProjectrolePermissionset)) {
            return false;
        }
        DataobjectDirectoryProjectrolePermissionset other = (DataobjectDirectoryProjectrolePermissionset) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectoryProjectrolePermissionset[ id=" + id + " ]";
    }
    
}
