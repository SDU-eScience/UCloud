/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;

/**
 * @author bjhj
 */
@Entity
@Table(name = "projecteventcalendar")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projecteventcalendar.findAll", query = "SELECT p FROM Projecteventcalendar p")
        , @NamedQuery(name = "Projecteventcalendar.findById", query = "SELECT p FROM Projecteventcalendar p WHERE p.id = :id")
        , @NamedQuery(name = "Projecteventcalendar.findByEventtext", query = "SELECT p FROM Projecteventcalendar p WHERE p.eventtext = :eventtext")
        , @NamedQuery(name = "Projecteventcalendar.findByProjectrefid", query = "SELECT p FROM Projecteventcalendar p WHERE p.projectrefid = :projectrefid")
        , @NamedQuery(name = "Projecteventcalendar.findByPersonrefid", query = "SELECT p FROM Projecteventcalendar p WHERE p.personrefid = :personrefid")
        , @NamedQuery(name = "Projecteventcalendar.findByEventstart", query = "SELECT p FROM Projecteventcalendar p WHERE p.eventstart = :eventstart")
        , @NamedQuery(name = "Projecteventcalendar.findByEventend", query = "SELECT p FROM Projecteventcalendar p WHERE p.eventend = :eventend")
        , @NamedQuery(name = "Projecteventcalendar.findByLastmodified", query = "SELECT p FROM Projecteventcalendar p WHERE p.lastmodified = :lastmodified")
        , @NamedQuery(name = "Projecteventcalendar.findByProjecteventcalendaractive", query = "SELECT p FROM Projecteventcalendar p WHERE p.projecteventcalendaractive = :projecteventcalendaractive")})
public class Projecteventcalendar implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "eventtext")
    private String eventtext;
    @Column(name = "projectrefid")
    private Integer projectrefid;
    @Column(name = "personrefid")
    private Integer personrefid;
    @Column(name = "eventstart")
    @Temporal(TemporalType.TIMESTAMP)
    private Date eventstart;
    @Column(name = "eventend")
    @Temporal(TemporalType.TIMESTAMP)
    private Date eventend;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "projecteventcalendaractive")
    private Integer projecteventcalendaractive;

    public Projecteventcalendar() {
    }

    public Projecteventcalendar(Integer id) {
        this.id = id;
    }

    public Projecteventcalendar(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEventtext() {
        return eventtext;
    }

    public void setEventtext(String eventtext) {
        this.eventtext = eventtext;
    }

    public Integer getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Integer projectrefid) {
        this.projectrefid = projectrefid;
    }

    public Integer getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Integer personrefid) {
        this.personrefid = personrefid;
    }

    public Date getEventstart() {
        return eventstart;
    }

    public void setEventstart(Date eventstart) {
        this.eventstart = eventstart;
    }

    public Date getEventend() {
        return eventend;
    }

    public void setEventend(Date eventend) {
        this.eventend = eventend;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Integer getProjecteventcalendaractive() {
        return projecteventcalendaractive;
    }

    public void setProjecteventcalendaractive(Integer projecteventcalendaractive) {
        this.projecteventcalendaractive = projecteventcalendaractive;
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
        if (!(object instanceof Projecteventcalendar)) {
            return false;
        }
        Projecteventcalendar other = (Projecteventcalendar) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projecteventcalendar[ id=" + id + " ]";
    }

}
