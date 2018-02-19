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
@Table(name = "projectpersonrel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Projectpersonrel.findAll", query = "SELECT p FROM Projectpersonrel p")
    , @NamedQuery(name = "Projectpersonrel.findById", query = "SELECT p FROM Projectpersonrel p WHERE p.id = :id")
    , @NamedQuery(name = "Projectpersonrel.findByActive", query = "SELECT p FROM Projectpersonrel p WHERE p.active = :active")
    , @NamedQuery(name = "Projectpersonrel.findByMarkedfordelete", query = "SELECT p FROM Projectpersonrel p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Projectpersonrel.findByModifiedTs", query = "SELECT p FROM Projectpersonrel p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Projectpersonrel.findByCreatedTs", query = "SELECT p FROM Projectpersonrel p WHERE p.createdTs = :createdTs")})
public class Projectpersonrel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
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
    @ManyToOne(optional = false)
    private Person personrefid;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Project projectrefid;
    @JoinColumn(name = "projectrolerefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Projectrole projectrolerefid;

    public Projectpersonrel() {
    }

    public Projectpersonrel(Integer id) {
        this.id = id;
    }

    public Projectpersonrel(Integer id, Date modifiedTs, Date createdTs) {
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

    public Projectrole getProjectrolerefid() {
        return projectrolerefid;
    }

    public void setProjectrolerefid(Projectrole projectrolerefid) {
        this.projectrolerefid = projectrolerefid;
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
        if (!(object instanceof Projectpersonrel)) {
            return false;
        }
        Projectpersonrel other = (Projectpersonrel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "Projectpersonrel{" +
                "id=" + id +
                ", active=" + active +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", personrefid=" + personrefid +
                ", projectrefid=" + projectrefid +
                ", projectrolerefid=" + projectrolerefid +
                '}';
    }
}
