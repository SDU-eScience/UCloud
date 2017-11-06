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
@Table(name = "projectpersonrel")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectpersonrel.findAll", query = "SELECT p FROM Projectpersonrel p")
        , @NamedQuery(name = "Projectpersonrel.findById", query = "SELECT p FROM Projectpersonrel p WHERE p.id = :id")
        , @NamedQuery(name = "Projectpersonrel.findByLastmodified", query = "SELECT p FROM Projectpersonrel p WHERE p.lastmodified = :lastmodified")
        , @NamedQuery(name = "Projectpersonrel.findByProjectpersonrelactive", query = "SELECT p FROM Projectpersonrel p WHERE p.projectpersonrelactive = :projectpersonrelactive")})
public class Projectpersonrel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "projectpersonrelactive")
    private Integer projectpersonrelactive;
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

    public Projectpersonrel(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Integer getProjectpersonrelactive() {
        return projectpersonrelactive;
    }

    public void setProjectpersonrelactive(Integer projectpersonrelactive) {
        this.projectpersonrelactive = projectpersonrelactive;
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

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectpersonrel[ id=" + id + " ]";
    }

}
