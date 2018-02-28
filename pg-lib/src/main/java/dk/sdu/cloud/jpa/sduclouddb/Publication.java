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
@Table(name = "publication")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Publication.findAll", query = "SELECT p FROM Publication p")
    , @NamedQuery(name = "Publication.findById", query = "SELECT p FROM Publication p WHERE p.id = :id")
    , @NamedQuery(name = "Publication.findByPublicationname", query = "SELECT p FROM Publication p WHERE p.publicationname = :publicationname")
    , @NamedQuery(name = "Publication.findByPublicationextlink", query = "SELECT p FROM Publication p WHERE p.publicationextlink = :publicationextlink")
    , @NamedQuery(name = "Publication.findByPublicationdate", query = "SELECT p FROM Publication p WHERE p.publicationdate = :publicationdate")
    , @NamedQuery(name = "Publication.findByActive", query = "SELECT p FROM Publication p WHERE p.active = :active")
    , @NamedQuery(name = "Publication.findByMarkedfordelete", query = "SELECT p FROM Publication p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Publication.findByModifiedTs", query = "SELECT p FROM Publication p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Publication.findByCreatedTs", query = "SELECT p FROM Publication p WHERE p.createdTs = :createdTs")})
public class Publication implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "publicationname")
    private String publicationname;
    @Column(name = "publicationextlink")
    private String publicationextlink;
    @Column(name = "publicationdate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date publicationdate;
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
    @OneToMany(mappedBy = "publicationrefid")
    private List<Dataobject> dataobjectList;
    @OneToMany(mappedBy = "publicationrefid")
    private List<PublicationDataobjectRelation> publicationDataobjectRelationList;
    @OneToMany(mappedBy = "publicationrefid")
    private List<ProjectPublicationRelation> projectPublicationRelationList;

    public Publication() {
    }

    public Publication(Integer id) {
        this.id = id;
    }

    public Publication(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getPublicationname() {
        return publicationname;
    }

    public void setPublicationname(String publicationname) {
        this.publicationname = publicationname;
    }

    public String getPublicationextlink() {
        return publicationextlink;
    }

    public void setPublicationextlink(String publicationextlink) {
        this.publicationextlink = publicationextlink;
    }

    public Date getPublicationdate() {
        return publicationdate;
    }

    public void setPublicationdate(Date publicationdate) {
        this.publicationdate = publicationdate;
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
    public List<Dataobject> getDataobjectList() {
        return dataobjectList;
    }

    public void setDataobjectList(List<Dataobject> dataobjectList) {
        this.dataobjectList = dataobjectList;
    }

    @XmlTransient
    public List<PublicationDataobjectRelation> getPublicationDataobjectRelationList() {
        return publicationDataobjectRelationList;
    }

    public void setPublicationDataobjectRelationList(List<PublicationDataobjectRelation> publicationDataobjectRelationList) {
        this.publicationDataobjectRelationList = publicationDataobjectRelationList;
    }

    @XmlTransient
    public List<ProjectPublicationRelation> getProjectPublicationRelationList() {
        return projectPublicationRelationList;
    }

    public void setProjectPublicationRelationList(List<ProjectPublicationRelation> projectPublicationRelationList) {
        this.projectPublicationRelationList = projectPublicationRelationList;
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
        if (!(object instanceof Publication)) {
            return false;
        }
        Publication other = (Publication) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Publication[ id=" + id + " ]";
    }
    
}
