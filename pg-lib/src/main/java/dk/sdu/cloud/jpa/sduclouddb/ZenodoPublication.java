package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
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
    , @NamedQuery(name = "ZenodoPublication.findByModifiedAt", query = "SELECT z FROM ZenodoPublication z WHERE z.modifiedAt = :modifiedAt")
    , @NamedQuery(name = "ZenodoPublication.findByZenodoId", query = "SELECT z FROM ZenodoPublication z WHERE z.zenodoId = :zenodoId")
    , @NamedQuery(name = "ZenodoPublication.findByPersonRefId", query = "SELECT z FROM ZenodoPublication z WHERE z.personRefId = :personRefId")
    , @NamedQuery(name = "ZenodoPublication.findByName", query = "SELECT z FROM ZenodoPublication z WHERE z.name = :name")})
public class ZenodoPublication implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Basic(optional = false)
    @Column(name = "status")
    private String status;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @Basic(optional = false)
    @Column(name = "modified_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedAt;
    @Column(name = "zenodo_id")
    private String zenodoId;
    @Basic(optional = false)
    @Column(name = "person_ref_id")
    private String personRefId;
    @Basic(optional = false)
    @Column(name = "name")
    private String name;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "publicationRefId")
    private Collection<ZenodoPublicationDataobjectRel> zenodoPublicationDataobjectRelCollection;

    public ZenodoPublication() {
    }

    public ZenodoPublication(Integer id) {
        this.id = id;
    }

    public ZenodoPublication(Integer id, String status, Date createdTs, Date modifiedAt, String personRefId, String name) {
        this.id = id;
        this.status = status;
        this.createdTs = createdTs;
        this.modifiedAt = modifiedAt;
        this.personRefId = personRefId;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public String getZenodoId() {
        return zenodoId;
    }

    public void setZenodoId(String zenodoId) {
        this.zenodoId = zenodoId;
    }

    public String getPersonRefId() {
        return personRefId;
    }

    public void setPersonRefId(String personRefId) {
        this.personRefId = personRefId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
