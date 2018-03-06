/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "vw_person_active_projects")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "VwPersonActiveProjects.findAll", query = "SELECT v FROM VwPersonActiveProjects v")
    , @NamedQuery(name = "VwPersonActiveProjects.findByPersonrefid", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.personrefid = :personrefid")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectrefid", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectrefid = :projectrefid")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectrolerefid", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectrolerefid = :projectrolerefid")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectrolename", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectrolename = :projectrolename")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectname", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectname = :projectname")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectshortname", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectshortname = :projectshortname")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectstart", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectstart = :projectstart")
    , @NamedQuery(name = "VwPersonActiveProjects.findByProjectend", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.projectend = :projectend")
    , @NamedQuery(name = "VwPersonActiveProjects.findByActive", query = "SELECT v FROM VwPersonActiveProjects v WHERE v.active = :active")})
public class VwPersonActiveProjects implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "personrefid")
    private Integer personrefid;
    @Column(name = "projectrefid")
    private Integer projectrefid;
    @Column(name = "projectrolerefid")
    private Integer projectrolerefid;
    @Column(name = "projectrolename")
    private String projectrolename;
    @Column(name = "projectname")
    private String projectname;
    @Column(name = "projectshortname")
    private String projectshortname;
    @Column(name = "projectstart")
    @Temporal(TemporalType.TIMESTAMP)
    private Date projectstart;
    @Column(name = "projectend")
    @Temporal(TemporalType.TIMESTAMP)
    private Date projectend;
    @Column(name = "active")
    private Integer active;

    public VwPersonActiveProjects() {
    }

    public Integer getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Integer personrefid) {
        this.personrefid = personrefid;
    }

    public Integer getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Integer projectrefid) {
        this.projectrefid = projectrefid;
    }

    public Integer getProjectrolerefid() {
        return projectrolerefid;
    }

    public void setProjectrolerefid(Integer projectrolerefid) {
        this.projectrolerefid = projectrolerefid;
    }

    public String getProjectrolename() {
        return projectrolename;
    }

    public void setProjectrolename(String projectrolename) {
        this.projectrolename = projectrolename;
    }

    public String getProjectname() {
        return projectname;
    }

    public void setProjectname(String projectname) {
        this.projectname = projectname;
    }

    public String getProjectshortname() {
        return projectshortname;
    }

    public void setProjectshortname(String projectshortname) {
        this.projectshortname = projectshortname;
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
    
}
