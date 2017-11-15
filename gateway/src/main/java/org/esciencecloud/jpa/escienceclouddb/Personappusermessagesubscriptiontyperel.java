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
@Table(name = "personappusermessagesubscriptiontyperel")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Personappusermessagesubscriptiontyperel.findAll", query = "SELECT p FROM Personappusermessagesubscriptiontyperel p")
        , @NamedQuery(name = "Personappusermessagesubscriptiontyperel.findById", query = "SELECT p FROM Personappusermessagesubscriptiontyperel p WHERE p.id = :id")
        , @NamedQuery(name = "Personappusermessagesubscriptiontyperel.findByActive", query = "SELECT p FROM Personappusermessagesubscriptiontyperel p WHERE p.active = :active")
        , @NamedQuery(name = "Personappusermessagesubscriptiontyperel.findByLastmodified", query = "SELECT p FROM Personappusermessagesubscriptiontyperel p WHERE p.lastmodified = :lastmodified")})
public class Personappusermessagesubscriptiontyperel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "active")
    private Integer active;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @JoinColumn(name = "appusermessagesubscriptiontyperefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Appusermessagesubscriptiontype appusermessagesubscriptiontyperefid;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Person personrefid;

    public Personappusermessagesubscriptiontyperel() {
    }

    public Personappusermessagesubscriptiontyperel(Integer id) {
        this.id = id;
    }

    public Personappusermessagesubscriptiontyperel(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
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

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Appusermessagesubscriptiontype getAppusermessagesubscriptiontyperefid() {
        return appusermessagesubscriptiontyperefid;
    }

    public void setAppusermessagesubscriptiontyperefid(Appusermessagesubscriptiontype appusermessagesubscriptiontyperefid) {
        this.appusermessagesubscriptiontyperefid = appusermessagesubscriptiontyperefid;
    }

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
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
        if (!(object instanceof Personappusermessagesubscriptiontyperel)) {
            return false;
        }
        Personappusermessagesubscriptiontyperel other = (Personappusermessagesubscriptiontyperel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Personappusermessagesubscriptiontyperel[ id=" + id + " ]";
    }

}
