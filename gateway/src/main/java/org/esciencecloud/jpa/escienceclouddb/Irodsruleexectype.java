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
@Table(name = "irodsruleexectype")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Irodsruleexectype.findAll", query = "SELECT i FROM Irodsruleexectype i")
    , @NamedQuery(name = "Irodsruleexectype.findById", query = "SELECT i FROM Irodsruleexectype i WHERE i.id = :id")
    , @NamedQuery(name = "Irodsruleexectype.findByIrodsruleexectypetext", query = "SELECT i FROM Irodsruleexectype i WHERE i.irodsruleexectypetext = :irodsruleexectypetext")
    , @NamedQuery(name = "Irodsruleexectype.findByIrodsruleexectypeidmap", query = "SELECT i FROM Irodsruleexectype i WHERE i.irodsruleexectypeidmap = :irodsruleexectypeidmap")
    , @NamedQuery(name = "Irodsruleexectype.findByActive", query = "SELECT i FROM Irodsruleexectype i WHERE i.active = :active")
    , @NamedQuery(name = "Irodsruleexectype.findByLastmodified", query = "SELECT i FROM Irodsruleexectype i WHERE i.lastmodified = :lastmodified")})
public class Irodsruleexectype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "irodsruleexectypetext")
    private String irodsruleexectypetext;
    @Column(name = "irodsruleexectypeidmap")
    private Integer irodsruleexectypeidmap;
    @Column(name = "active")
    private Integer active;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;

    public Irodsruleexectype() {
    }

    public Irodsruleexectype(Integer id) {
        this.id = id;
    }

    public Irodsruleexectype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIrodsruleexectypetext() {
        return irodsruleexectypetext;
    }

    public void setIrodsruleexectypetext(String irodsruleexectypetext) {
        this.irodsruleexectypetext = irodsruleexectypetext;
    }

    public Integer getIrodsruleexectypeidmap() {
        return irodsruleexectypeidmap;
    }

    public void setIrodsruleexectypeidmap(Integer irodsruleexectypeidmap) {
        this.irodsruleexectypeidmap = irodsruleexectypeidmap;
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Irodsruleexectype)) {
            return false;
        }
        Irodsruleexectype other = (Irodsruleexectype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Irodsruleexectype[ id=" + id + " ]";
    }
    
}
