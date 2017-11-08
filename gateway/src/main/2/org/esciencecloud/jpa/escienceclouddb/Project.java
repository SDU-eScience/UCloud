/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
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
        , @NamedQuery(name = "Project.findByLastmodified", query = "SELECT p FROM Project p WHERE p.lastmodified = :lastmodified")
        , @NamedQuery(name = "Project.findByIrodsgroupidmap", query = "SELECT p FROM Project p WHERE p.irodsgroupidmap = :irodsgroupidmap")
        , @NamedQuery(name = "Project.findByProjectactive", query = "SELECT p FROM Project p WHERE p.projectactive = :projectactive")
        , @NamedQuery(name = "Project.findByProjectshortname", query = "SELECT p FROM Project p WHERE p.projectshortname = :projectshortname")
        , @NamedQuery(name = "Project.findByIrodsgroupadmin", query = "SELECT p FROM Project p WHERE p.irodsgroupadmin = :irodsgroupadmin")})
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
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "irodsgroupidmap")
    private Integer irodsgroupidmap;
    @Column(name = "projectactive")
    private Integer projectactive;
    @Column(name = "projectshortname")
    private String projectshortname;
    @Column(name = "irodsgroupadmin")
    private String irodsgroupadmin;
    @OneToMany(mappedBy = "projectrefid")
    private List<Projectprojectresearchtyperel> projectprojectresearchtyperelList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrefid")
    private List<Projectpersonrel> projectpersonrelList;
    @OneToMany(mappedBy = "projectrefid")
    private List<Projectprojectdocumentrel> projectprojectdocumentrelList;

    public Project() {
    }

    public Project(Integer id) {
        this.id = id;
    }

    public Project(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
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

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Integer getIrodsgroupidmap() {
        return irodsgroupidmap;
    }

    public void setIrodsgroupidmap(Integer irodsgroupidmap) {
        this.irodsgroupidmap = irodsgroupidmap;
    }

    public Integer getProjectactive() {
        return projectactive;
    }

    public void setProjectactive(Integer projectactive) {
        this.projectactive = projectactive;
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
    public List<Projectprojectdocumentrel> getProjectprojectdocumentrelList() {
        return projectprojectdocumentrelList;
    }

    public void setProjectprojectdocumentrelList(List<Projectprojectdocumentrel> projectprojectdocumentrelList) {
        this.projectprojectdocumentrelList = projectprojectdocumentrelList;
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
        return "org.escience.jpa.escienceclouddb.Project[ id=" + id + " ]";
    }

}
