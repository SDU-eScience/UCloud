/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.math.BigInteger;
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
@Table(name = "vw_project_org")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "VwProjectOrg.findAll", query = "SELECT v FROM VwProjectOrg v")
    , @NamedQuery(name = "VwProjectOrg.findByRecid", query = "SELECT v FROM VwProjectOrg v WHERE v.recid = :recid")
    , @NamedQuery(name = "VwProjectOrg.findByProjectrefid", query = "SELECT v FROM VwProjectOrg v WHERE v.projectrefid = :projectrefid")
    , @NamedQuery(name = "VwProjectOrg.findByProjectname", query = "SELECT v FROM VwProjectOrg v WHERE v.projectname = :projectname")
    , @NamedQuery(name = "VwProjectOrg.findByProjectstart", query = "SELECT v FROM VwProjectOrg v WHERE v.projectstart = :projectstart")
    , @NamedQuery(name = "VwProjectOrg.findByProjectend", query = "SELECT v FROM VwProjectOrg v WHERE v.projectend = :projectend")
    , @NamedQuery(name = "VwProjectOrg.findByActive", query = "SELECT v FROM VwProjectOrg v WHERE v.active = :active")
    , @NamedQuery(name = "VwProjectOrg.findByPersonrefid", query = "SELECT v FROM VwProjectOrg v WHERE v.personrefid = :personrefid")
    , @NamedQuery(name = "VwProjectOrg.findByPersonfirstname", query = "SELECT v FROM VwProjectOrg v WHERE v.personfirstname = :personfirstname")
    , @NamedQuery(name = "VwProjectOrg.findByPersonmiddlename", query = "SELECT v FROM VwProjectOrg v WHERE v.personmiddlename = :personmiddlename")
    , @NamedQuery(name = "VwProjectOrg.findByPersonlastname", query = "SELECT v FROM VwProjectOrg v WHERE v.personlastname = :personlastname")
    , @NamedQuery(name = "VwProjectOrg.findByProjectrolerefid", query = "SELECT v FROM VwProjectOrg v WHERE v.projectrolerefid = :projectrolerefid")
    , @NamedQuery(name = "VwProjectOrg.findByProjectroletext", query = "SELECT v FROM VwProjectOrg v WHERE v.projectroletext = :projectroletext")
    , @NamedQuery(name = "VwProjectOrg.findByOrgrefid", query = "SELECT v FROM VwProjectOrg v WHERE v.orgrefid = :orgrefid")
    , @NamedQuery(name = "VwProjectOrg.findByOrgfullname", query = "SELECT v FROM VwProjectOrg v WHERE v.orgfullname = :orgfullname")
    , @NamedQuery(name = "VwProjectOrg.findByOrgshortname", query = "SELECT v FROM VwProjectOrg v WHERE v.orgshortname = :orgshortname")})
public class VwProjectOrg implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "recid")
    private BigInteger recid;
    @Column(name = "projectrefid")
    private Integer projectrefid;
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
    @Column(name = "personrefid")
    private Integer personrefid;
    @Column(name = "personfirstname")
    private String personfirstname;
    @Column(name = "personmiddlename")
    private String personmiddlename;
    @Column(name = "personlastname")
    private String personlastname;
    @Column(name = "projectrolerefid")
    private Integer projectrolerefid;
    @Column(name = "projectroletext")
    private String projectroletext;
    @Column(name = "orgrefid")
    private Integer orgrefid;
    @Column(name = "orgfullname")
    private String orgfullname;
    @Column(name = "orgshortname")
    private String orgshortname;

    public VwProjectOrg() {
    }

    public BigInteger getRecid() {
        return recid;
    }

    public void setRecid(BigInteger recid) {
        this.recid = recid;
    }

    public Integer getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Integer projectrefid) {
        this.projectrefid = projectrefid;
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

    public Integer getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Integer personrefid) {
        this.personrefid = personrefid;
    }

    public String getPersonfirstname() {
        return personfirstname;
    }

    public void setPersonfirstname(String personfirstname) {
        this.personfirstname = personfirstname;
    }

    public String getPersonmiddlename() {
        return personmiddlename;
    }

    public void setPersonmiddlename(String personmiddlename) {
        this.personmiddlename = personmiddlename;
    }

    public String getPersonlastname() {
        return personlastname;
    }

    public void setPersonlastname(String personlastname) {
        this.personlastname = personlastname;
    }

    public Integer getProjectrolerefid() {
        return projectrolerefid;
    }

    public void setProjectrolerefid(Integer projectrolerefid) {
        this.projectrolerefid = projectrolerefid;
    }

    public String getProjectroletext() {
        return projectroletext;
    }

    public void setProjectroletext(String projectroletext) {
        this.projectroletext = projectroletext;
    }

    public Integer getOrgrefid() {
        return orgrefid;
    }

    public void setOrgrefid(Integer orgrefid) {
        this.orgrefid = orgrefid;
    }

    public String getOrgfullname() {
        return orgfullname;
    }

    public void setOrgfullname(String orgfullname) {
        this.orgfullname = orgfullname;
    }

    public String getOrgshortname() {
        return orgshortname;
    }

    public void setOrgshortname(String orgshortname) {
        this.orgshortname = orgshortname;
    }
    
}
