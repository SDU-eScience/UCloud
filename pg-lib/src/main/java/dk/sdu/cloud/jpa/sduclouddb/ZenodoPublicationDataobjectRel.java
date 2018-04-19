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
@Table(name = "zenodo_publication_dataobject_rel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "ZenodoPublicationDataobjectRel.findAll", query = "SELECT z FROM ZenodoPublicationDataobjectRel z")
    , @NamedQuery(name = "ZenodoPublicationDataobjectRel.findByDataobjectRefId", query = "SELECT z FROM ZenodoPublicationDataobjectRel z WHERE z.dataobjectRefId = :dataobjectRefId")
    , @NamedQuery(name = "ZenodoPublicationDataobjectRel.findByModifiedTs", query = "SELECT z FROM ZenodoPublicationDataobjectRel z WHERE z.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "ZenodoPublicationDataobjectRel.findByCreatedTs", query = "SELECT z FROM ZenodoPublicationDataobjectRel z WHERE z.createdTs = :createdTs")
    , @NamedQuery(name = "ZenodoPublicationDataobjectRel.findByMarkedForDelete", query = "SELECT z FROM ZenodoPublicationDataobjectRel z WHERE z.markedForDelete = :markedForDelete")
    , @NamedQuery(name = "ZenodoPublicationDataobjectRel.findById", query = "SELECT z FROM ZenodoPublicationDataobjectRel z WHERE z.id = :id")
    , @NamedQuery(name = "ZenodoPublicationDataobjectRel.findByHasBeenTransmitted", query = "SELECT z FROM ZenodoPublicationDataobjectRel z WHERE z.hasBeenTransmitted = :hasBeenTransmitted")})
public class ZenodoPublicationDataobjectRel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "dataobject_ref_id")
    private String dataobjectRefId;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @Column(name = "marked_for_delete")
    private Boolean markedForDelete;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "has_been_transmitted")
    private Boolean hasBeenTransmitted;
    @JoinColumn(name = "publication_ref_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private ZenodoPublication publicationRefId;

    public ZenodoPublicationDataobjectRel() {
    }

    public ZenodoPublicationDataobjectRel(Integer id) {
        this.id = id;
    }

    public ZenodoPublicationDataobjectRel(Integer id, String dataobjectRefId, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.dataobjectRefId = dataobjectRefId;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
    }

    public String getDataobjectRefId() {
        return dataobjectRefId;
    }

    public void setDataobjectRefId(String dataobjectRefId) {
        this.dataobjectRefId = dataobjectRefId;
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

    public Boolean getMarkedForDelete() {
        return markedForDelete;
    }

    public void setMarkedForDelete(Boolean markedForDelete) {
        this.markedForDelete = markedForDelete;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getHasBeenTransmitted() {
        return hasBeenTransmitted;
    }

    public void setHasBeenTransmitted(Boolean hasBeenTransmitted) {
        this.hasBeenTransmitted = hasBeenTransmitted;
    }

    public ZenodoPublication getPublicationRefId() {
        return publicationRefId;
    }

    public void setPublicationRefId(ZenodoPublication publicationRefId) {
        this.publicationRefId = publicationRefId;
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
        if (!(object instanceof ZenodoPublicationDataobjectRel)) {
            return false;
        }
        ZenodoPublicationDataobjectRel other = (ZenodoPublicationDataobjectRel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.ZenodoPublicationDataobjectRel[ id=" + id + " ]";
    }
    
}
