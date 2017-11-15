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
@Table(name = "irodsaccesstype")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Irodsaccesstype.findAll", query = "SELECT i FROM Irodsaccesstype i")
        , @NamedQuery(name = "Irodsaccesstype.findById", query = "SELECT i FROM Irodsaccesstype i WHERE i.id = :id")
        , @NamedQuery(name = "Irodsaccesstype.findByIrodsaccesstypetext", query = "SELECT i FROM Irodsaccesstype i WHERE i.irodsaccesstypetext = :irodsaccesstypetext")
        , @NamedQuery(name = "Irodsaccesstype.findByIrodsaccesstypeidmap", query = "SELECT i FROM Irodsaccesstype i WHERE i.irodsaccesstypeidmap = :irodsaccesstypeidmap")
        , @NamedQuery(name = "Irodsaccesstype.findByActive", query = "SELECT i FROM Irodsaccesstype i WHERE i.active = :active")
        , @NamedQuery(name = "Irodsaccesstype.findByLastmodified", query = "SELECT i FROM Irodsaccesstype i WHERE i.lastmodified = :lastmodified")})
public class Irodsaccesstype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "irodsaccesstypetext")
    private String irodsaccesstypetext;
    @Column(name = "irodsaccesstypeidmap")
    private Integer irodsaccesstypeidmap;
    @Column(name = "active")
    private Integer active;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;

    public Irodsaccesstype() {
    }

    public Irodsaccesstype(Integer id) {
        this.id = id;
    }

    public Irodsaccesstype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIrodsaccesstypetext() {
        return irodsaccesstypetext;
    }

    public void setIrodsaccesstypetext(String irodsaccesstypetext) {
        this.irodsaccesstypetext = irodsaccesstypetext;
    }

    public Integer getIrodsaccesstypeidmap() {
        return irodsaccesstypeidmap;
    }

    public void setIrodsaccesstypeidmap(Integer irodsaccesstypeidmap) {
        this.irodsaccesstypeidmap = irodsaccesstypeidmap;
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
        if (!(object instanceof Irodsaccesstype)) {
            return false;
        }
        Irodsaccesstype other = (Irodsaccesstype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Irodsaccesstype[ id=" + id + " ]";
    }

}
