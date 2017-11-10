/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
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
@Table(name = "systemrolepersonrel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Systemrolepersonrel.findAll", query = "SELECT s FROM Systemrolepersonrel s")
    , @NamedQuery(name = "Systemrolepersonrel.findById", query = "SELECT s FROM Systemrolepersonrel s WHERE s.id = :id")
    , @NamedQuery(name = "Systemrolepersonrel.findBySystemrolerefid", query = "SELECT s FROM Systemrolepersonrel s WHERE s.systemrolerefid = :systemrolerefid")
    , @NamedQuery(name = "Systemrolepersonrel.findByPersonrefid", query = "SELECT s FROM Systemrolepersonrel s WHERE s.personrefid = :personrefid")
    , @NamedQuery(name = "Systemrolepersonrel.findByLastmodified", query = "SELECT s FROM Systemrolepersonrel s WHERE s.lastmodified = :lastmodified")
    , @NamedQuery(name = "Systemrolepersonrel.findByActive", query = "SELECT s FROM Systemrolepersonrel s WHERE s.active = :active")})
public class Systemrolepersonrel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Basic(optional = false)
    @Column(name = "systemrolerefid")
    private int systemrolerefid;
    @Basic(optional = false)
    @Column(name = "personrefid")
    private int personrefid;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "active")
    private Integer active;

    public Systemrolepersonrel() {
    }

    public Systemrolepersonrel(Integer id) {
        this.id = id;
    }

    public Systemrolepersonrel(Integer id, int systemrolerefid, int personrefid, Date lastmodified) {
        this.id = id;
        this.systemrolerefid = systemrolerefid;
        this.personrefid = personrefid;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getSystemrolerefid() {
        return systemrolerefid;
    }

    public void setSystemrolerefid(int systemrolerefid) {
        this.systemrolerefid = systemrolerefid;
    }

    public int getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(int personrefid) {
        this.personrefid = personrefid;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
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
        if (!(object instanceof Systemrolepersonrel)) {
            return false;
        }
        Systemrolepersonrel other = (Systemrolepersonrel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Systemrolepersonrel[ id=" + id + " ]";
    }
    
}
