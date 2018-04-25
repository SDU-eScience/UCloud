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
@Table(name = "data_transfer_header")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataTransferHeader.findAll", query = "SELECT d FROM DataTransferHeader d")
    , @NamedQuery(name = "DataTransferHeader.findById", query = "SELECT d FROM DataTransferHeader d WHERE d.id = :id")
    , @NamedQuery(name = "DataTransferHeader.findByTotalbytes", query = "SELECT d FROM DataTransferHeader d WHERE d.totalbytes = :totalbytes")
    , @NamedQuery(name = "DataTransferHeader.findByTotalprogress", query = "SELECT d FROM DataTransferHeader d WHERE d.totalprogress = :totalprogress")
    , @NamedQuery(name = "DataTransferHeader.findByActive", query = "SELECT d FROM DataTransferHeader d WHERE d.active = :active")
    , @NamedQuery(name = "DataTransferHeader.findByMarkedFordelete", query = "SELECT d FROM DataTransferHeader d WHERE d.markedFordelete = :markedFordelete")
    , @NamedQuery(name = "DataTransferHeader.findByModifiedTs", query = "SELECT d FROM DataTransferHeader d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataTransferHeader.findByCreatedTs", query = "SELECT d FROM DataTransferHeader d WHERE d.createdTs = :createdTs")})
public class DataTransferHeader implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "totalbytes")
    private Integer totalbytes;
    @Column(name = "totalprogress")
    private Integer totalprogress;
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
    @JoinColumn(name = "data_transfer_state_refid", referencedColumnName = "id")
    @ManyToOne
    private DataTransferState dataTransferStateRefid;
    @JoinColumn(name = "data_transfer_type_refid", referencedColumnName = "id")
    @ManyToOne
    private DataTransferType dataTransferTypeRefid;
    @JoinColumn(name = "principalrefid", referencedColumnName = "id")
    @ManyToOne
    private Principal principalrefid;
    @OneToMany(mappedBy = "dataTransferHeaderRefid")
    private List<DataTransferDetail> dataTransferDetailList;

    public DataTransferHeader() {
    }

    public DataTransferHeader(Integer id) {
        this.id = id;
    }

    public DataTransferHeader(Integer id, Date modifiedTs, Date createdTs) {
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

    public Integer getTotalbytes() {
        return totalbytes;
    }

    public void setTotalbytes(Integer totalbytes) {
        this.totalbytes = totalbytes;
    }

    public Integer getTotalprogress() {
        return totalprogress;
    }

    public void setTotalprogress(Integer totalprogress) {
        this.totalprogress = totalprogress;
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

    public DataTransferState getDataTransferStateRefid() {
        return dataTransferStateRefid;
    }

    public void setDataTransferStateRefid(DataTransferState dataTransferStateRefid) {
        this.dataTransferStateRefid = dataTransferStateRefid;
    }

    public DataTransferType getDataTransferTypeRefid() {
        return dataTransferTypeRefid;
    }

    public void setDataTransferTypeRefid(DataTransferType dataTransferTypeRefid) {
        this.dataTransferTypeRefid = dataTransferTypeRefid;
    }

    public Principal getPrincipalrefid() {
        return principalrefid;
    }

    public void setPrincipalrefid(Principal principalrefid) {
        this.principalrefid = principalrefid;
    }

    @XmlTransient
    public List<DataTransferDetail> getDataTransferDetailList() {
        return dataTransferDetailList;
    }

    public void setDataTransferDetailList(List<DataTransferDetail> dataTransferDetailList) {
        this.dataTransferDetailList = dataTransferDetailList;
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
        if (!(object instanceof DataTransferHeader)) {
            return false;
        }
        DataTransferHeader other = (DataTransferHeader) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataTransferHeader[ id=" + id + " ]";
    }
    
}
