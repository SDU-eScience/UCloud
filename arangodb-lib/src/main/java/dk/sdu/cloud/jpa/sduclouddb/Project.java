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
@Table(name = "project")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Project.findAll", query = "SELECT p FROM Project p")
    , @NamedQuery(name = "Project.findById", query = "SELECT p FROM Project p WHERE p.id = :id")
    , @NamedQuery(name = "Project.findByProjectname", query = "SELECT p FROM Project p WHERE p.projectname = :projectname")
    , @NamedQuery(name = "Project.findByProjectstart", query = "SELECT p FROM Project p WHERE p.projectstart = :projectstart")
    , @NamedQuery(name = "Project.findByProjectend", query = "SELECT p FROM Project p WHERE p.projectend = :projectend")
    , @NamedQuery(name = "Project.findByActive", query = "SELECT p FROM Project p WHERE p.active = :active")
    , @NamedQuery(name = "Project.findByProjectshortname", query = "SELECT p FROM Project p WHERE p.projectshortname = :projectshortname")
    , @NamedQuery(name = "Project.findByMarkedfordelete", query = "SELECT p FROM Project p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Project.findByModifiedTs", query = "SELECT p FROM Project p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Project.findByCreatedTs", query = "SELECT p FROM Project p WHERE p.createdTs = :createdTs")
    , @NamedQuery(name = "Project.findByVisible", query = "SELECT p FROM Project p WHERE p.visible = :visible")})
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectname")
    private String projectname;
    @Column(name = "projectstart")
    @Temporal(TemporalType.TIMESTAMP)
    private Date projectstart;
    @Column(name = "projectend")
    @Temporal(TemporalType.TIMESTAMP)
    private Date projectend;
    @Column(name = "active")
    private Integer active;
    @Column(name = "projectshortname")
    private String projectshortname;
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
    @Column(name = "visible")
    private Integer visible;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrefid")
    private List<ProjectPersonRelation> projectPersonRelationList;
    @JoinColumn(name = "projecttyperefid", referencedColumnName = "id")
    @ManyToOne
    private ProjectType projecttyperefid;
    @OneToMany(mappedBy = "projectrefid")
    private List<DataobjectDirectory> dataobjectDirectoryList;
    @OneToMany(mappedBy = "projectrefid")
    private List<ProjectPublicationRelation> projectPublicationRelationList;
    @OneToMany(mappedBy = "projectrefid")
    private List<ProjectEventCalendar> projectEventCalendarList;
    @OneToMany(mappedBy = "projectrefid")
    private List<ProjectProjectresearchtypeRelation> projectProjectresearchtypeRelationList;
    @OneToMany(mappedBy = "projectrefid")
    private List<ProjectProjectdocumentRelation> projectProjectdocumentRelationList;

    public Project() {
    }

    public Project(Integer id) {
        this.id = id;
    }

    public Project(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getProjectname() {
        return projectname;
    }

    public void setProjectname(String projectname) {
        this.projectname = projectname;
    }

    public Date getProjectstart() {
        return projectstart;
    }

    public void setProjectstart(Date projectstart) {
        this.projectstart = projectstart;
    }

    public Date getProjectend() {
        return projectend;
    }

    public void setProjectend(Date projectend) {
        this.projectend = projectend;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public String getProjectshortname() {
        return projectshortname;
    }

    public void setProjectshortname(String projectshortname) {
        this.projectshortname = projectshortname;
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

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }

    @XmlTransient
    public List<ProjectPersonRelation> getProjectPersonRelationList() {
        return projectPersonRelationList;
    }

    public void setProjectPersonRelationList(List<ProjectPersonRelation> projectPersonRelationList) {
        this.projectPersonRelationList = projectPersonRelationList;
    }

    public ProjectType getProjecttyperefid() {
        return projecttyperefid;
    }

    public void setProjecttyperefid(ProjectType projecttyperefid) {
        this.projecttyperefid = projecttyperefid;
    }

    @XmlTransient
    public List<DataobjectDirectory> getDataobjectDirectoryList() {
        return dataobjectDirectoryList;
    }

    public void setDataobjectDirectoryList(List<DataobjectDirectory> dataobjectDirectoryList) {
        this.dataobjectDirectoryList = dataobjectDirectoryList;
    }

    @XmlTransient
    public List<ProjectPublicationRelation> getProjectPublicationRelationList() {
        return projectPublicationRelationList;
    }

    public void setProjectPublicationRelationList(List<ProjectPublicationRelation> projectPublicationRelationList) {
        this.projectPublicationRelationList = projectPublicationRelationList;
    }

    @XmlTransient
    public List<ProjectEventCalendar> getProjectEventCalendarList() {
        return projectEventCalendarList;
    }

    public void setProjectEventCalendarList(List<ProjectEventCalendar> projectEventCalendarList) {
        this.projectEventCalendarList = projectEventCalendarList;
    }

    @XmlTransient
    public List<ProjectProjectresearchtypeRelation> getProjectProjectresearchtypeRelationList() {
        return projectProjectresearchtypeRelationList;
    }

    public void setProjectProjectresearchtypeRelationList(List<ProjectProjectresearchtypeRelation> projectProjectresearchtypeRelationList) {
        this.projectProjectresearchtypeRelationList = projectProjectresearchtypeRelationList;
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
        if (!(object instanceof Project)) {
            return false;
        }
        Project other = (Project) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Project[ id=" + id + " ]";
    }
    
}
