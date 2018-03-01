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
@Table(name = "project_research_type")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "ProjectResearchType.findAll", query = "SELECT p FROM ProjectResearchType p")
    , @NamedQuery(name = "ProjectResearchType.findById", query = "SELECT p FROM ProjectResearchType p WHERE p.id = :id")
    , @NamedQuery(name = "ProjectResearchType.findByProjectresearchtypetext", query = "SELECT p FROM ProjectResearchType p WHERE p.projectresearchtypetext = :projectresearchtypetext")
    , @NamedQuery(name = "ProjectResearchType.findByActive", query = "SELECT p FROM ProjectResearchType p WHERE p.active = :active")
    , @NamedQuery(name = "ProjectResearchType.findByMarkedfordelete", query = "SELECT p FROM ProjectResearchType p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "ProjectResearchType.findByModifiedTs", query = "SELECT p FROM ProjectResearchType p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "ProjectResearchType.findByCreatedTs", query = "SELECT p FROM ProjectResearchType p WHERE p.createdTs = :createdTs")})
public class ProjectResearchType implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectresearchtypetext")
    private String projectresearchtypetext;
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
    @OneToMany(mappedBy = "projectresearchtyperefid")
    private List<ProjectProjectresearchtypeRelation> projectProjectresearchtypeRelationList;

    public ProjectResearchType() {
    }

    public ProjectResearchType(Integer id) {
        this.id = id;
    }

    public ProjectResearchType(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getProjectresearchtypetext() {
        return projectresearchtypetext;
    }

    public void setProjectresearchtypetext(String projectresearchtypetext) {
        this.projectresearchtypetext = projectresearchtypetext;
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
    public List<ProjectProjectresearchtypeRelation> getProjectProjectresearchtypeRelationList() {
        return projectProjectresearchtypeRelationList;
    }

    public void setProjectProjectresearchtypeRelationList(List<ProjectProjectresearchtypeRelation> projectProjectresearchtypeRelationList) {
        this.projectProjectresearchtypeRelationList = projectProjectresearchtypeRelationList;
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
        if (!(object instanceof ProjectResearchType)) {
            return false;
        }
        ProjectResearchType other = (ProjectResearchType) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.ProjectResearchType[ id=" + id + " ]";
    }
    
}
