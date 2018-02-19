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
    , @NamedQuery(name = "Project.findByIrodsgroupidmap", query = "SELECT p FROM Project p WHERE p.irodsgroupidmap = :irodsgroupidmap")
    , @NamedQuery(name = "Project.findByActive", query = "SELECT p FROM Project p WHERE p.active = :active")
    , @NamedQuery(name = "Project.findByProjectshortname", query = "SELECT p FROM Project p WHERE p.projectshortname = :projectshortname")
    , @NamedQuery(name = "Project.findByIrodsgroupadmin", query = "SELECT p FROM Project p WHERE p.irodsgroupadmin = :irodsgroupadmin")
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
    @Column(name = "irodsgroupidmap")
    private Integer irodsgroupidmap;
    @Column(name = "active")
    private Integer active;
    @Column(name = "projectshortname")
    private String projectshortname;
    @Column(name = "irodsgroupadmin")
    private String irodsgroupadmin;
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
    @OneToMany(mappedBy = "projectrefid")
    private List<Dataobjectsharerel> dataobjectsharerelList;
    @JoinColumn(name = "projecttyperefid", referencedColumnName = "id")
    @ManyToOne
    private Projecttype projecttyperefid;
    @OneToMany(mappedBy = "projectrefid")
    private List<Dataobjectcollection> dataobjectcollectionList;
    @OneToMany(mappedBy = "projectrefid")
    private List<Projectprojectresearchtyperel> projectprojectresearchtyperelList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrefid")
    private List<Projectpersonrel> projectpersonrelList;
    @OneToMany(mappedBy = "projectrefid")
    private List<Projecteventcalendar> projecteventcalendarList;
    @OneToMany(mappedBy = "projectrefid")
    private List<Projectprojectdocumentrel> projectprojectdocumentrelList;
    @OneToMany(mappedBy = "projectrefid")
    private List<Projectpublicationrel> projectpublicationrelList;

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

    public Integer getIrodsgroupidmap() {
        return irodsgroupidmap;
    }

    public void setIrodsgroupidmap(Integer irodsgroupidmap) {
        this.irodsgroupidmap = irodsgroupidmap;
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

    public String getIrodsgroupadmin() {
        return irodsgroupadmin;
    }

    public void setIrodsgroupadmin(String irodsgroupadmin) {
        this.irodsgroupadmin = irodsgroupadmin;
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
    public List<Dataobjectsharerel> getDataobjectsharerelList() {
        return dataobjectsharerelList;
    }

    public void setDataobjectsharerelList(List<Dataobjectsharerel> dataobjectsharerelList) {
        this.dataobjectsharerelList = dataobjectsharerelList;
    }

    public Projecttype getProjecttyperefid() {
        return projecttyperefid;
    }

    public void setProjecttyperefid(Projecttype projecttyperefid) {
        this.projecttyperefid = projecttyperefid;
    }

    @XmlTransient
    public List<Dataobjectcollection> getDataobjectcollectionList() {
        return dataobjectcollectionList;
    }

    public void setDataobjectcollectionList(List<Dataobjectcollection> dataobjectcollectionList) {
        this.dataobjectcollectionList = dataobjectcollectionList;
    }

    @XmlTransient
    public List<Projectprojectresearchtyperel> getProjectprojectresearchtyperelList() {
        return projectprojectresearchtyperelList;
    }

    public void setProjectprojectresearchtyperelList(List<Projectprojectresearchtyperel> projectprojectresearchtyperelList) {
        this.projectprojectresearchtyperelList = projectprojectresearchtyperelList;
    }

    @XmlTransient
    public List<Projectpersonrel> getProjectpersonrelList() {
        return projectpersonrelList;
    }

    public void setProjectpersonrelList(List<Projectpersonrel> projectpersonrelList) {
        this.projectpersonrelList = projectpersonrelList;
    }

    @XmlTransient
    public List<Projecteventcalendar> getProjecteventcalendarList() {
        return projecteventcalendarList;
    }

    public void setProjecteventcalendarList(List<Projecteventcalendar> projecteventcalendarList) {
        this.projecteventcalendarList = projecteventcalendarList;
    }

    @XmlTransient
    public List<Projectprojectdocumentrel> getProjectprojectdocumentrelList() {
        return projectprojectdocumentrelList;
    }

    public void setProjectprojectdocumentrelList(List<Projectprojectdocumentrel> projectprojectdocumentrelList) {
        this.projectprojectdocumentrelList = projectprojectdocumentrelList;
    }

    @XmlTransient
    public List<Projectpublicationrel> getProjectpublicationrelList() {
        return projectpublicationrelList;
    }

    public void setProjectpublicationrelList(List<Projectpublicationrel> projectpublicationrelList) {
        this.projectpublicationrelList = projectpublicationrelList;
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

    @java.lang.Override
    public java.lang.String toString() {
        return "Project{" +
                "id=" + id +
                ", projectname='" + projectname + '\'' +
                ", projectstart=" + projectstart +
                ", projectend=" + projectend +
                ", irodsgroupidmap=" + irodsgroupidmap +
                ", active=" + active +
                ", projectshortname='" + projectshortname + '\'' +
                ", irodsgroupadmin='" + irodsgroupadmin + '\'' +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", visible=" + visible +
                ", dataobjectsharerelList=" + dataobjectsharerelList +
                ", projecttyperefid=" + projecttyperefid +
                ", dataobjectcollectionList=" + dataobjectcollectionList +
                ", projectprojectresearchtyperelList=" + projectprojectresearchtyperelList +
                ", projectpersonrelList=" + projectpersonrelList +
                ", projecteventcalendarList=" + projecteventcalendarList +
                ", projectprojectdocumentrelList=" + projectprojectdocumentrelList +
                ", projectpublicationrelList=" + projectpublicationrelList +
                '}';
    }
}
