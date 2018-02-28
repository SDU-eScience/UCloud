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
@Table(name = "dataobject_directory_type")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataobjectDirectoryType.findAll", query = "SELECT d FROM DataobjectDirectoryType d")
    , @NamedQuery(name = "DataobjectDirectoryType.findById", query = "SELECT d FROM DataobjectDirectoryType d WHERE d.id = :id")
    , @NamedQuery(name = "DataobjectDirectoryType.findByDataobjectdirectorytypename", query = "SELECT d FROM DataobjectDirectoryType d WHERE d.dataobjectdirectorytypename = :dataobjectdirectorytypename")
    , @NamedQuery(name = "DataobjectDirectoryType.findByActive", query = "SELECT d FROM DataobjectDirectoryType d WHERE d.active = :active")
    , @NamedQuery(name = "DataobjectDirectoryType.findByMarkedfordelete", query = "SELECT d FROM DataobjectDirectoryType d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "DataobjectDirectoryType.findByModifiedTs", query = "SELECT d FROM DataobjectDirectoryType d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataobjectDirectoryType.findByCreatedTs", query = "SELECT d FROM DataobjectDirectoryType d WHERE d.createdTs = :createdTs")})
public class DataobjectDirectoryType implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "dataobjectdirectorytypename")
    private String dataobjectdirectorytypename;
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
    @OneToMany(mappedBy = "dataobjectdirectorytyperefid")
    private List<DataobjectDirectory> dataobjectDirectoryList;

    public DataobjectDirectoryType() {
    }

    public DataobjectDirectoryType(Integer id) {
        this.id = id;
    }

    public DataobjectDirectoryType(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDataobjectdirectorytypename() {
        return dataobjectdirectorytypename;
    }

    public void setDataobjectdirectorytypename(String dataobjectdirectorytypename) {
        this.dataobjectdirectorytypename = dataobjectdirectorytypename;
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

    @XmlTransient
    public List<DataobjectDirectory> getDataobjectDirectoryList() {
        return dataobjectDirectoryList;
    }

    public void setDataobjectDirectoryList(List<DataobjectDirectory> dataobjectDirectoryList) {
        this.dataobjectDirectoryList = dataobjectDirectoryList;
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
        if (!(object instanceof DataobjectDirectoryType)) {
            return false;
        }
        DataobjectDirectoryType other = (DataobjectDirectoryType) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectoryType[ id=" + id + " ]";
    }
    
}
