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
@Table(name = "systemrole")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Systemrole.findAll", query = "SELECT s FROM Systemrole s")
    , @NamedQuery(name = "Systemrole.findById", query = "SELECT s FROM Systemrole s WHERE s.id = :id")
    , @NamedQuery(name = "Systemrole.findBySystemroletext", query = "SELECT s FROM Systemrole s WHERE s.systemroletext = :systemroletext")
    , @NamedQuery(name = "Systemrole.findByLastmodified", query = "SELECT s FROM Systemrole s WHERE s.lastmodified = :lastmodified")
    , @NamedQuery(name = "Systemrole.findByLandingpage", query = "SELECT s FROM Systemrole s WHERE s.landingpage = :landingpage")
    , @NamedQuery(name = "Systemrole.findByActive", query = "SELECT s FROM Systemrole s WHERE s.active = :active")})
public class Systemrole implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "systemroletext")
    private String systemroletext;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "landingpage")
    private String landingpage;
    @Column(name = "active")
    private Integer active;

    public Systemrole() {
    }

    public Systemrole(Integer id) {
        this.id = id;
    }

    public Systemrole(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSystemroletext() {
        return systemroletext;
    }

    public void setSystemroletext(String systemroletext) {
        this.systemroletext = systemroletext;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public String getLandingpage() {
        return landingpage;
    }

    public void setLandingpage(String landingpage) {
        this.landingpage = landingpage;
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
        if (!(object instanceof Systemrole)) {
            return false;
        }
        Systemrole other = (Systemrole) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Systemrole[ id=" + id + " ]";
    }
    
}
