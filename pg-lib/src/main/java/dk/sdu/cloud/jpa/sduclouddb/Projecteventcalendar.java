/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
@Table(name = "projecteventcalendar")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Projecteventcalendar.findAll", query = "SELECT p FROM Projecteventcalendar p")
    , @NamedQuery(name = "Projecteventcalendar.findById", query = "SELECT p FROM Projecteventcalendar p WHERE p.id = :id")
    , @NamedQuery(name = "Projecteventcalendar.findByEventname", query = "SELECT p FROM Projecteventcalendar p WHERE p.eventname = :eventname")
    , @NamedQuery(name = "Projecteventcalendar.findByEventstart", query = "SELECT p FROM Projecteventcalendar p WHERE p.eventstart = :eventstart")
    , @NamedQuery(name = "Projecteventcalendar.findByEventend", query = "SELECT p FROM Projecteventcalendar p WHERE p.eventend = :eventend")
    , @NamedQuery(name = "Projecteventcalendar.findByActive", query = "SELECT p FROM Projecteventcalendar p WHERE p.active = :active")
    , @NamedQuery(name = "Projecteventcalendar.findByMarkedfordelete", query = "SELECT p FROM Projecteventcalendar p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Projecteventcalendar.findByModifiedTs", query = "SELECT p FROM Projecteventcalendar p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Projecteventcalendar.findByCreatedTs", query = "SELECT p FROM Projecteventcalendar p WHERE p.createdTs = :createdTs")})
public class Projecteventcalendar implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "eventname")
    private String eventname;
    @Column(name = "eventstart")
    @Temporal(TemporalType.TIMESTAMP)
    private Date eventstart;
    @Column(name = "eventend")
    @Temporal(TemporalType.TIMESTAMP)
    private Date eventend;
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
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;

    public Projecteventcalendar() {
    }

    public Projecteventcalendar(Integer id) {
        this.id = id;
    }

    public Projecteventcalendar(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getEventname() {
        return eventname;
    }

    public void setEventname(String eventname) {
        this.eventname = eventname;
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

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
    }

    public Project getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Project projectrefid) {
        this.projectrefid = projectrefid;
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

    @java.lang.Override
    public java.lang.String toString() {
        return "Projecteventcalendar{" +
                "id=" + id +
                ", eventname='" + eventname + '\'' +
                ", eventstart=" + eventstart +
                ", eventend=" + eventend +
                ", active=" + active +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", personrefid=" + personrefid +
                ", projectrefid=" + projectrefid +
                '}';
    }
}
