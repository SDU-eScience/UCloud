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
@Table(name = "irodsresourcetype")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Irodsresourcetype.findAll", query = "SELECT i FROM Irodsresourcetype i")
        , @NamedQuery(name = "Irodsresourcetype.findById", query = "SELECT i FROM Irodsresourcetype i WHERE i.id = :id")
        , @NamedQuery(name = "Irodsresourcetype.findByIrodsresourcetypetext", query = "SELECT i FROM Irodsresourcetype i WHERE i.irodsresourcetypetext = :irodsresourcetypetext")
        , @NamedQuery(name = "Irodsresourcetype.findByIrodsresourcetypeidmap", query = "SELECT i FROM Irodsresourcetype i WHERE i.irodsresourcetypeidmap = :irodsresourcetypeidmap")
        , @NamedQuery(name = "Irodsresourcetype.findByIrodsresourcetypeactive", query = "SELECT i FROM Irodsresourcetype i WHERE i.irodsresourcetypeactive = :irodsresourcetypeactive")
        , @NamedQuery(name = "Irodsresourcetype.findByLastmodified", query = "SELECT i FROM Irodsresourcetype i WHERE i.lastmodified = :lastmodified")})
public class Irodsresourcetype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "irodsresourcetypetext")
    private String irodsresourcetypetext;
    @Column(name = "irodsresourcetypeidmap")
    private Integer irodsresourcetypeidmap;
    @Column(name = "irodsresourcetypeactive")
    private Integer irodsresourcetypeactive;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;

    public Irodsresourcetype() {
    }

    public Irodsresourcetype(Integer id) {
        this.id = id;
    }

    public Irodsresourcetype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIrodsresourcetypetext() {
        return irodsresourcetypetext;
    }

    public void setIrodsresourcetypetext(String irodsresourcetypetext) {
        this.irodsresourcetypetext = irodsresourcetypetext;
    }

    public Integer getIrodsresourcetypeidmap() {
        return irodsresourcetypeidmap;
    }

    public void setIrodsresourcetypeidmap(Integer irodsresourcetypeidmap) {
        this.irodsresourcetypeidmap = irodsresourcetypeidmap;
    }

    public Integer getIrodsresourcetypeactive() {
        return irodsresourcetypeactive;
    }

    public void setIrodsresourcetypeactive(Integer irodsresourcetypeactive) {
        this.irodsresourcetypeactive = irodsresourcetypeactive;
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
        if (!(object instanceof Irodsresourcetype)) {
            return false;
        }
        Irodsresourcetype other = (Irodsresourcetype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Irodsresourcetype[ id=" + id + " ]";
    }

}
