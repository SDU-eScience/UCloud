/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author bjhj
 */
@Entity
@Table(name = "appusermessagesubscriptiontype")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Appusermessagesubscriptiontype.findAll", query = "SELECT a FROM Appusermessagesubscriptiontype a")
        , @NamedQuery(name = "Appusermessagesubscriptiontype.findById", query = "SELECT a FROM Appusermessagesubscriptiontype a WHERE a.id = :id")
        , @NamedQuery(name = "Appusermessagesubscriptiontype.findByAppusermessagesubscriptiontypetext", query = "SELECT a FROM Appusermessagesubscriptiontype a WHERE a.appusermessagesubscriptiontypetext = :appusermessagesubscriptiontypetext")
        , @NamedQuery(name = "Appusermessagesubscriptiontype.findByAppusermessagesubscriptiontypeactive", query = "SELECT a FROM Appusermessagesubscriptiontype a WHERE a.appusermessagesubscriptiontypeactive = :appusermessagesubscriptiontypeactive")
        , @NamedQuery(name = "Appusermessagesubscriptiontype.findByLastmodified", query = "SELECT a FROM Appusermessagesubscriptiontype a WHERE a.lastmodified = :lastmodified")})
public class Appusermessagesubscriptiontype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "appusermessagesubscriptiontypetext")
    private String appusermessagesubscriptiontypetext;
    @Column(name = "appusermessagesubscriptiontypeactive")
    private Integer appusermessagesubscriptiontypeactive;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "appusermessagesubscriptiontyperefid")
    private List<Personappusermessagesubscriptiontyperel> personappusermessagesubscriptiontyperelList;

    public Appusermessagesubscriptiontype() {
    }

    public Appusermessagesubscriptiontype(Integer id) {
        this.id = id;
    }

    public Appusermessagesubscriptiontype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAppusermessagesubscriptiontypetext() {
        return appusermessagesubscriptiontypetext;
    }

    public void setAppusermessagesubscriptiontypetext(String appusermessagesubscriptiontypetext) {
        this.appusermessagesubscriptiontypetext = appusermessagesubscriptiontypetext;
    }

    public Integer getAppusermessagesubscriptiontypeactive() {
        return appusermessagesubscriptiontypeactive;
    }

    public void setAppusermessagesubscriptiontypeactive(Integer appusermessagesubscriptiontypeactive) {
        this.appusermessagesubscriptiontypeactive = appusermessagesubscriptiontypeactive;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    @XmlTransient
    public List<Personappusermessagesubscriptiontyperel> getPersonappusermessagesubscriptiontyperelList() {
        return personappusermessagesubscriptiontyperelList;
    }

    public void setPersonappusermessagesubscriptiontyperelList(List<Personappusermessagesubscriptiontyperel> personappusermessagesubscriptiontyperelList) {
        this.personappusermessagesubscriptiontyperelList = personappusermessagesubscriptiontyperelList;
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
        if (!(object instanceof Appusermessagesubscriptiontype)) {
            return false;
        }
        Appusermessagesubscriptiontype other = (Appusermessagesubscriptiontype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Appusermessagesubscriptiontype[ id=" + id + " ]";
    }

}
