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
 *
 * @author bjhj
 */
@Entity
@Table(name = "project_role")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "ProjectRole.findAll", query = "SELECT p FROM ProjectRole p")
    , @NamedQuery(name = "ProjectRole.findById", query = "SELECT p FROM ProjectRole p WHERE p.id = :id")
    , @NamedQuery(name = "ProjectRole.findByProjectrolename", query = "SELECT p FROM ProjectRole p WHERE p.projectrolename = :projectrolename")
    , @NamedQuery(name = "ProjectRole.findByActive", query = "SELECT p FROM ProjectRole p WHERE p.active = :active")
    , @NamedQuery(name = "ProjectRole.findByMarkedfordelete", query = "SELECT p FROM ProjectRole p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "ProjectRole.findByModifiedTs", query = "SELECT p FROM ProjectRole p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "ProjectRole.findByCreatedTs", query = "SELECT p FROM ProjectRole p WHERE p.createdTs = :createdTs")})
public class ProjectRole implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectrolename")
    private String projectrolename;
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
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrolerefid")
    private List<ProjectPrincipalRelation> projectPrincipalRelationList;

    public ProjectRole() {
    }

    public ProjectRole(Integer id) {
        this.id = id;
    }

    public ProjectRole(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getProjectrolename() {
        return projectrolename;
    }

    public void setProjectrolename(String projectrolename) {
        this.projectrolename = projectrolename;
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
    public List<ProjectPrincipalRelation> getProjectPrincipalRelationList() {
        return projectPrincipalRelationList;
    }

    public void setProjectPrincipalRelationList(List<ProjectPrincipalRelation> projectPrincipalRelationList) {
        this.projectPrincipalRelationList = projectPrincipalRelationList;
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
        if (!(object instanceof ProjectRole)) {
            return false;
        }
        ProjectRole other = (ProjectRole) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.ProjectRole[ id=" + id + " ]";
    }
    
}
