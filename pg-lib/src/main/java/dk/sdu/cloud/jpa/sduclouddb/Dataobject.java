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
@Table(name = "dataobject")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Dataobject.findAll", query = "SELECT d FROM Dataobject d")
    , @NamedQuery(name = "Dataobject.findById", query = "SELECT d FROM Dataobject d WHERE d.id = :id")
    , @NamedQuery(name = "Dataobject.findByDataobjectname", query = "SELECT d FROM Dataobject d WHERE d.dataobjectname = :dataobjectname")
    , @NamedQuery(name = "Dataobject.findByDataobjectsize", query = "SELECT d FROM Dataobject d WHERE d.dataobjectsize = :dataobjectsize")
    , @NamedQuery(name = "Dataobject.findByDataobjectchecksum", query = "SELECT d FROM Dataobject d WHERE d.dataobjectchecksum = :dataobjectchecksum")
    , @NamedQuery(name = "Dataobject.findByDataobjectmd5", query = "SELECT d FROM Dataobject d WHERE d.dataobjectmd5 = :dataobjectmd5")
    , @NamedQuery(name = "Dataobject.findByMarkedfordelete", query = "SELECT d FROM Dataobject d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Dataobject.findByModifiedTs", query = "SELECT d FROM Dataobject d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Dataobject.findByCreatedTs", query = "SELECT d FROM Dataobject d WHERE d.createdTs = :createdTs")
    , @NamedQuery(name = "Dataobject.findByLastAccessed", query = "SELECT d FROM Dataobject d WHERE d.lastAccessed = :lastAccessed")})
public class Dataobject implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Column(name = "id")
    private String id;
    @Column(name = "dataobjectname")
    private String dataobjectname;
    @Column(name = "dataobjectsize")
    private Integer dataobjectsize;
    @Column(name = "dataobjectchecksum")
    private String dataobjectchecksum;
    @Column(name = "dataobjectmd5")
    private Integer dataobjectmd5;
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
    @Column(name = "last_accessed")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastAccessed;
    @OneToMany(mappedBy = "dataobjectrefid")
    private List<Dataobjectsharerel> dataobjectsharerelList;
    @JoinColumn(name = "dataobjectclassificationrefid", referencedColumnName = "id")
    @ManyToOne
    private Dataobjectclassification dataobjectclassificationrefid;
    @JoinColumn(name = "dataobjectfileextensionrefid", referencedColumnName = "id")
    @ManyToOne
    private Dataobjectfileextension dataobjectfileextensionrefid;
    @JoinColumn(name = "publicationrefid", referencedColumnName = "id")
    @ManyToOne
    private Publication publicationrefid;
    @OneToMany(mappedBy = "dataobjectrefid")
    private List<Dataobjectcollectionrel> dataobjectcollectionrelList;
    @OneToMany(mappedBy = "dataobjectrefid")
    private List<PublicationDataobjectRel> publicationDataobjectRelList;
    @OneToMany(mappedBy = "dataobjectrefid")
    private List<DataTransferDetail> dataTransferDetailList;

    public Dataobject() {
    }

    public Dataobject(String id) {
        this.id = id;
    }

    public Dataobject(String id, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDataobjectname() {
        return dataobjectname;
    }

    public void setDataobjectname(String dataobjectname) {
        this.dataobjectname = dataobjectname;
    }

    public Integer getDataobjectsize() {
        return dataobjectsize;
    }

    public void setDataobjectsize(Integer dataobjectsize) {
        this.dataobjectsize = dataobjectsize;
    }

    public String getDataobjectchecksum() {
        return dataobjectchecksum;
    }

    public void setDataobjectchecksum(String dataobjectchecksum) {
        this.dataobjectchecksum = dataobjectchecksum;
    }

    public Integer getDataobjectmd5() {
        return dataobjectmd5;
    }

    public void setDataobjectmd5(Integer dataobjectmd5) {
        this.dataobjectmd5 = dataobjectmd5;
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

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    @XmlTransient
    public List<Dataobjectsharerel> getDataobjectsharerelList() {
        return dataobjectsharerelList;
    }

    public void setDataobjectsharerelList(List<Dataobjectsharerel> dataobjectsharerelList) {
        this.dataobjectsharerelList = dataobjectsharerelList;
    }

    public Dataobjectclassification getDataobjectclassificationrefid() {
        return dataobjectclassificationrefid;
    }

    public void setDataobjectclassificationrefid(Dataobjectclassification dataobjectclassificationrefid) {
        this.dataobjectclassificationrefid = dataobjectclassificationrefid;
    }

    public Dataobjectfileextension getDataobjectfileextensionrefid() {
        return dataobjectfileextensionrefid;
    }

    public void setDataobjectfileextensionrefid(Dataobjectfileextension dataobjectfileextensionrefid) {
        this.dataobjectfileextensionrefid = dataobjectfileextensionrefid;
    }

    public Publication getPublicationrefid() {
        return publicationrefid;
    }

    public void setPublicationrefid(Publication publicationrefid) {
        this.publicationrefid = publicationrefid;
    }

    @XmlTransient
    public List<Dataobjectcollectionrel> getDataobjectcollectionrelList() {
        return dataobjectcollectionrelList;
    }

    public void setDataobjectcollectionrelList(List<Dataobjectcollectionrel> dataobjectcollectionrelList) {
        this.dataobjectcollectionrelList = dataobjectcollectionrelList;
    }

    @XmlTransient
    public List<PublicationDataobjectRel> getPublicationDataobjectRelList() {
        return publicationDataobjectRelList;
    }

    public void setPublicationDataobjectRelList(List<PublicationDataobjectRel> publicationDataobjectRelList) {
        this.publicationDataobjectRelList = publicationDataobjectRelList;
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
        if (!(object instanceof Dataobject)) {
            return false;
        }
        Dataobject other = (Dataobject) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Dataobject[ id=" + id + " ]";
    }
    
}
