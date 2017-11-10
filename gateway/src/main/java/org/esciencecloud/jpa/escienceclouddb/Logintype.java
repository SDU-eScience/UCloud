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
@Table(name = "logintype")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Logintype.findAll", query = "SELECT l FROM Logintype l")
    , @NamedQuery(name = "Logintype.findById", query = "SELECT l FROM Logintype l WHERE l.id = :id")
    , @NamedQuery(name = "Logintype.findByLogintypetext", query = "SELECT l FROM Logintype l WHERE l.logintypetext = :logintypetext")
    , @NamedQuery(name = "Logintype.findByLastmodified", query = "SELECT l FROM Logintype l WHERE l.lastmodified = :lastmodified")
    , @NamedQuery(name = "Logintype.findByActive", query = "SELECT l FROM Logintype l WHERE l.active = :active")})
public class Logintype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "logintypetext")
    private String logintypetext;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "active")
    private Integer active;

    public Logintype() {
    }

    public Logintype(Integer id) {
        this.id = id;
    }

    public Logintype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLogintypetext() {
        return logintypetext;
    }

    public void setLogintypetext(String logintypetext) {
        this.logintypetext = logintypetext;
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
        if (!(object instanceof Logintype)) {
            return false;
        }
        Logintype other = (Logintype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Logintype[ id=" + id + " ]";
    }
    
}
