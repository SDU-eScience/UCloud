package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
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
 * AUTO-GENERATED FILE
 */
@Entity
@Table(name = "zenodo_publication")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "ZenodoPublication.findAll", query = "SELECT z FROM ZenodoPublication z")
        , @NamedQuery(name = "ZenodoPublication.findById", query = "SELECT z FROM ZenodoPublication z WHERE z.id = :id")
        , @NamedQuery(name = "ZenodoPublication.findByStatus", query = "SELECT z FROM ZenodoPublication z WHERE z.status = :status")
        , @NamedQuery(name = "ZenodoPublication.findByCreatedTs", query = "SELECT z FROM ZenodoPublication z WHERE z.createdTs = :createdTs")
        , @NamedQuery(name = "ZenodoPublication.findByModifiedAt", query = "SELECT z FROM ZenodoPublication z WHERE z.modifiedAt = :modifiedAt")})
public class ZenodoPublication implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Column(name = "id")
    private String id;
    @Basic(optional = false)
    @Column(name = "status")
    private String status;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.DATE)
    private Date createdTs;
    @Basic(optional = false)
    @Column(name = "modified_at")
    @Temporal(TemporalType.DATE)
    private Date modifiedAt;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "publicationRefId")
    private Collection<ZenodoPublicationDataobjectRel> zenodoPublicationDataobjectRelCollection;

    public ZenodoPublication() {
    }

    public ZenodoPublication(String id) {
        this.id = id;
    }

    public ZenodoPublication(String id, String status, Date createdTs, Date modifiedAt) {
        this.id = id;
        this.status = status;
        this.createdTs = createdTs;
        this.modifiedAt = modifiedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(Date createdTs) {
        this.createdTs = createdTs;
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    @XmlTransient
    public Collection<ZenodoPublicationDataobjectRel> getZenodoPublicationDataobjectRelCollection() {
        return zenodoPublicationDataobjectRelCollection;
    }

    public void setZenodoPublicationDataobjectRelCollection(Collection<ZenodoPublicationDataobjectRel> zenodoPublicationDataobjectRelCollection) {
        this.zenodoPublicationDataobjectRelCollection = zenodoPublicationDataobjectRelCollection;
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
        if (!(object instanceof ZenodoPublication)) {
            return false;
        }
        ZenodoPublication other = (ZenodoPublication) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.ZenodoPublication[ id=" + id + " ]";
    }

}
