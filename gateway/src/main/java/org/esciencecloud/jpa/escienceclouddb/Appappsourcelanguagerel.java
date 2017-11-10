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
@Table(name = "appappsourcelanguagerel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Appappsourcelanguagerel.findAll", query = "SELECT a FROM Appappsourcelanguagerel a")
    , @NamedQuery(name = "Appappsourcelanguagerel.findById", query = "SELECT a FROM Appappsourcelanguagerel a WHERE a.id = :id")
    , @NamedQuery(name = "Appappsourcelanguagerel.findByActive", query = "SELECT a FROM Appappsourcelanguagerel a WHERE a.active = :active")
    , @NamedQuery(name = "Appappsourcelanguagerel.findByLastmodified", query = "SELECT a FROM Appappsourcelanguagerel a WHERE a.lastmodified = :lastmodified")})
public class Appappsourcelanguagerel implements Serializable {

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
    @JoinColumn(name = "apprefid", referencedColumnName = "id")
    @ManyToOne
    private App apprefid;
    @JoinColumn(name = "appsourcelanguagerefid", referencedColumnName = "id")
    @ManyToOne
    private Appsourcelanguage appsourcelanguagerefid;

    public Appappsourcelanguagerel() {
    }

    public Appappsourcelanguagerel(Integer id) {
        this.id = id;
    }

    public Appappsourcelanguagerel(Integer id, Date lastmodified) {
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

    public App getApprefid() {
        return apprefid;
    }

    public void setApprefid(App apprefid) {
        this.apprefid = apprefid;
    }

    public Appsourcelanguage getAppsourcelanguagerefid() {
        return appsourcelanguagerefid;
    }

    public void setAppsourcelanguagerefid(Appsourcelanguage appsourcelanguagerefid) {
        this.appsourcelanguagerefid = appsourcelanguagerefid;
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
        if (!(object instanceof Appappsourcelanguagerel)) {
            return false;
        }
        Appappsourcelanguagerel other = (Appappsourcelanguagerel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Appappsourcelanguagerel[ id=" + id + " ]";
    }
    
}
