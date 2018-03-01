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
@Table(name = "project_type")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "ProjectType.findAll", query = "SELECT p FROM ProjectType p")
    , @NamedQuery(name = "ProjectType.findById", query = "SELECT p FROM ProjectType p WHERE p.id = :id")
    , @NamedQuery(name = "ProjectType.findByProjecttypeename", query = "SELECT p FROM ProjectType p WHERE p.projecttypeename = :projecttypeename")
    , @NamedQuery(name = "ProjectType.findByActive", query = "SELECT p FROM ProjectType p WHERE p.active = :active")
    , @NamedQuery(name = "ProjectType.findByMarkedfordelete", query = "SELECT p FROM ProjectType p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "ProjectType.findByModifiedTs", query = "SELECT p FROM ProjectType p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "ProjectType.findByCreatedTs", query = "SELECT p FROM ProjectType p WHERE p.createdTs = :createdTs")})
public class ProjectType implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projecttypeename")
    private String projecttypeename;
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
    @OneToMany(mappedBy = "projecttyperefid")
    private List<Project> projectList;

    public ProjectType() {
    }

    public ProjectType(Integer id) {
        this.id = id;
    }

    public ProjectType(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getProjecttypeename() {
        return projecttypeename;
    }

    public void setProjecttypeename(String projecttypeename) {
        this.projecttypeename = projecttypeename;
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
    public List<Project> getProjectList() {
        return projectList;
    }

    public void setProjectList(List<Project> projectList) {
        this.projectList = projectList;
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
        if (!(object instanceof ProjectType)) {
            return false;
        }
        ProjectType other = (ProjectType) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.ProjectType[ id=" + id + " ]";
    }
    
}
