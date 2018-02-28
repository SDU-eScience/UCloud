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
import javax.persistence.Lob;
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
@Table(name = "project_document")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "ProjectDocument.findAll", query = "SELECT p FROM ProjectDocument p")
    , @NamedQuery(name = "ProjectDocument.findById", query = "SELECT p FROM ProjectDocument p WHERE p.id = :id")
    , @NamedQuery(name = "ProjectDocument.findByProjectdocumentfilename", query = "SELECT p FROM ProjectDocument p WHERE p.projectdocumentfilename = :projectdocumentfilename")
    , @NamedQuery(name = "ProjectDocument.findByDocumenttypedescription", query = "SELECT p FROM ProjectDocument p WHERE p.documenttypedescription = :documenttypedescription")
    , @NamedQuery(name = "ProjectDocument.findByActive", query = "SELECT p FROM ProjectDocument p WHERE p.active = :active")
    , @NamedQuery(name = "ProjectDocument.findByMarkedfordelete", query = "SELECT p FROM ProjectDocument p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "ProjectDocument.findByModifiedTs", query = "SELECT p FROM ProjectDocument p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "ProjectDocument.findByCreatedTs", query = "SELECT p FROM ProjectDocument p WHERE p.createdTs = :createdTs")})
public class ProjectDocument implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectdocumentfilename")
    private String projectdocumentfilename;
    @Column(name = "documenttypedescription")
    private String documenttypedescription;
    @Lob
    @Column(name = "projectdocumentbin")
    private byte[] projectdocumentbin;
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
    @OneToMany(mappedBy = "projectdocumentrefid")
    private List<ProjectProjectdocumentRelation> projectProjectdocumentRelationList;

    public ProjectDocument() {
    }

    public ProjectDocument(Integer id) {
        this.id = id;
    }

    public ProjectDocument(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getProjectdocumentfilename() {
        return projectdocumentfilename;
    }

    public void setProjectdocumentfilename(String projectdocumentfilename) {
        this.projectdocumentfilename = projectdocumentfilename;
    }

    public String getDocumenttypedescription() {
        return documenttypedescription;
    }

    public void setDocumenttypedescription(String documenttypedescription) {
        this.documenttypedescription = documenttypedescription;
    }

    public byte[] getProjectdocumentbin() {
        return projectdocumentbin;
    }

    public void setProjectdocumentbin(byte[] projectdocumentbin) {
        this.projectdocumentbin = projectdocumentbin;
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
    public List<ProjectProjectdocumentRelation> getProjectProjectdocumentRelationList() {
        return projectProjectdocumentRelationList;
    }

    public void setProjectProjectdocumentRelationList(List<ProjectProjectdocumentRelation> projectProjectdocumentRelationList) {
        this.projectProjectdocumentRelationList = projectProjectdocumentRelationList;
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
        if (!(object instanceof ProjectDocument)) {
            return false;
        }
        ProjectDocument other = (ProjectDocument) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.ProjectDocument[ id=" + id + " ]";
    }
    
}
