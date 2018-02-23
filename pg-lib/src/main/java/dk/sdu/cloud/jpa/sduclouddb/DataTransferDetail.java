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
@Table(name = "data_transfer_detail")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataTransferDetail.findAll", query = "SELECT d FROM DataTransferDetail d")
    , @NamedQuery(name = "DataTransferDetail.findById", query = "SELECT d FROM DataTransferDetail d WHERE d.id = :id")
    , @NamedQuery(name = "DataTransferDetail.findByPartbytes", query = "SELECT d FROM DataTransferDetail d WHERE d.partbytes = :partbytes")
    , @NamedQuery(name = "DataTransferDetail.findByPartprogress", query = "SELECT d FROM DataTransferDetail d WHERE d.partprogress = :partprogress")
    , @NamedQuery(name = "DataTransferDetail.findByActive", query = "SELECT d FROM DataTransferDetail d WHERE d.active = :active")
    , @NamedQuery(name = "DataTransferDetail.findByMarkedFordelete", query = "SELECT d FROM DataTransferDetail d WHERE d.markedFordelete = :markedFordelete")
    , @NamedQuery(name = "DataTransferDetail.findByModifiedTs", query = "SELECT d FROM DataTransferDetail d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataTransferDetail.findByCreatedTs", query = "SELECT d FROM DataTransferDetail d WHERE d.createdTs = :createdTs")})
public class DataTransferDetail implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "partbytes")
    private Integer partbytes;
    @Column(name = "partprogress")
    private Integer partprogress;
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
    @JoinColumn(name = "data_transfer_header_refid", referencedColumnName = "id")
    @ManyToOne
    private DataTransferHeader dataTransferHeaderRefid;
    @JoinColumn(name = "dataobjectrefid", referencedColumnName = "id")
    @ManyToOne
    private Dataobject dataobjectrefid;

    public DataTransferDetail() {
    }

    public DataTransferDetail(Integer id) {
        this.id = id;
    }

    public DataTransferDetail(Integer id, Date modifiedTs, Date createdTs) {
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

    public Integer getPartbytes() {
        return partbytes;
    }

    public void setPartbytes(Integer partbytes) {
        this.partbytes = partbytes;
    }

    public Integer getPartprogress() {
        return partprogress;
    }

    public void setPartprogress(Integer partprogress) {
        this.partprogress = partprogress;
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

    public DataTransferHeader getDataTransferHeaderRefid() {
        return dataTransferHeaderRefid;
    }

    public void setDataTransferHeaderRefid(DataTransferHeader dataTransferHeaderRefid) {
        this.dataTransferHeaderRefid = dataTransferHeaderRefid;
    }

    public Dataobject getDataobjectrefid() {
        return dataobjectrefid;
    }

    public void setDataobjectrefid(Dataobject dataobjectrefid) {
        this.dataobjectrefid = dataobjectrefid;
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
        if (!(object instanceof DataTransferDetail)) {
            return false;
        }
        DataTransferDetail other = (DataTransferDetail) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataTransferDetail[ id=" + id + " ]";
    }
    
}
