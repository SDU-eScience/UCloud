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
@Table(name = "data_transfer_type")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataTransferType.findAll", query = "SELECT d FROM DataTransferType d")
    , @NamedQuery(name = "DataTransferType.findById", query = "SELECT d FROM DataTransferType d WHERE d.id = :id")
    , @NamedQuery(name = "DataTransferType.findByDataTransferTypeName", query = "SELECT d FROM DataTransferType d WHERE d.dataTransferTypeName = :dataTransferTypeName")
    , @NamedQuery(name = "DataTransferType.findByActive", query = "SELECT d FROM DataTransferType d WHERE d.active = :active")
    , @NamedQuery(name = "DataTransferType.findByMarkedFordelete", query = "SELECT d FROM DataTransferType d WHERE d.markedFordelete = :markedFordelete")
    , @NamedQuery(name = "DataTransferType.findByModifiedTs", query = "SELECT d FROM DataTransferType d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataTransferType.findByCreatedTs", query = "SELECT d FROM DataTransferType d WHERE d.createdTs = :createdTs")})
public class DataTransferType implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "data_transfer_type_name")
    private String dataTransferTypeName;
    @Column(name = "active")
    private Integer active;
    @Column(name = "marked_fordelete")
    private Integer markedFordelete;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @OneToMany(mappedBy = "dataTransferTypeRefid")
    private List<DataTransferHeader> dataTransferHeaderList;

    public DataTransferType() {
    }

    public DataTransferType(Integer id) {
        this.id = id;
    }

    public DataTransferType(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDataTransferTypeName() {
        return dataTransferTypeName;
    }

    public void setDataTransferTypeName(String dataTransferTypeName) {
        this.dataTransferTypeName = dataTransferTypeName;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public Integer getMarkedFordelete() {
        return markedFordelete;
    }

    public void setMarkedFordelete(Integer markedFordelete) {
        this.markedFordelete = markedFordelete;
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
    public List<DataTransferHeader> getDataTransferHeaderList() {
        return dataTransferHeaderList;
    }

    public void setDataTransferHeaderList(List<DataTransferHeader> dataTransferHeaderList) {
        this.dataTransferHeaderList = dataTransferHeaderList;
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
        if (!(object instanceof DataTransferType)) {
            return false;
        }
        DataTransferType other = (DataTransferType) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataTransferType[ id=" + id + " ]";
    }
    
}
