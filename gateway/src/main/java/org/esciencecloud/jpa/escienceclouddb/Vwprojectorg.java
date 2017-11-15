/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;

/**
 * @author bjhj
 */
@Entity
@Table(name = "vwprojectorg")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Vwprojectorg.findAll", query = "SELECT v FROM Vwprojectorg v")
        , @NamedQuery(name = "Vwprojectorg.findByRecid", query = "SELECT v FROM Vwprojectorg v WHERE v.recid = :recid")
        , @NamedQuery(name = "Vwprojectorg.findByProjectrefid", query = "SELECT v FROM Vwprojectorg v WHERE v.projectrefid = :projectrefid")
        , @NamedQuery(name = "Vwprojectorg.findByProjectname", query = "SELECT v FROM Vwprojectorg v WHERE v.projectname = :projectname")
        , @NamedQuery(name = "Vwprojectorg.findByProjectstart", query = "SELECT v FROM Vwprojectorg v WHERE v.projectstart = :projectstart")
        , @NamedQuery(name = "Vwprojectorg.findByProjectend", query = "SELECT v FROM Vwprojectorg v WHERE v.projectend = :projectend")
        , @NamedQuery(name = "Vwprojectorg.findByActive", query = "SELECT v FROM Vwprojectorg v WHERE v.active = :active")
        , @NamedQuery(name = "Vwprojectorg.findByPersonrefid", query = "SELECT v FROM Vwprojectorg v WHERE v.personrefid = :personrefid")
        , @NamedQuery(name = "Vwprojectorg.findByPersonfirstname", query = "SELECT v FROM Vwprojectorg v WHERE v.personfirstname = :personfirstname")
        , @NamedQuery(name = "Vwprojectorg.findByPersonmiddlename", query = "SELECT v FROM Vwprojectorg v WHERE v.personmiddlename = :personmiddlename")
        , @NamedQuery(name = "Vwprojectorg.findByPersonlastname", query = "SELECT v FROM Vwprojectorg v WHERE v.personlastname = :personlastname")
        , @NamedQuery(name = "Vwprojectorg.findByProjectrolerefid", query = "SELECT v FROM Vwprojectorg v WHERE v.projectrolerefid = :projectrolerefid")
        , @NamedQuery(name = "Vwprojectorg.findByProjectroletext", query = "SELECT v FROM Vwprojectorg v WHERE v.projectroletext = :projectroletext")})
public class Vwprojectorg implements Serializable {

    private static final long serialVersionUID = 1L;
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

    public Vwprojectorg() {
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

}
