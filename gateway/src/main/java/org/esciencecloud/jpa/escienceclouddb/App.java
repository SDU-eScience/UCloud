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
import java.util.Collection;
import java.util.Date;

/**
 * @author bjhj
 */
@Entity
@Table(name = "app")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "App.findAll", query = "SELECT a FROM App a")
        , @NamedQuery(name = "App.findById", query = "SELECT a FROM App a WHERE a.id = :id")
        , @NamedQuery(name = "App.findByApptext", query = "SELECT a FROM App a WHERE a.apptext = :apptext")
        , @NamedQuery(name = "App.findByAppdescriptiontext", query = "SELECT a FROM App a WHERE a.appdescriptiontext = :appdescriptiontext")
        , @NamedQuery(name = "App.findByActive", query = "SELECT a FROM App a WHERE a.active = :active")
        , @NamedQuery(name = "App.findByLastmodified", query = "SELECT a FROM App a WHERE a.lastmodified = :lastmodified")})
public class App implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "apptext")
    private String apptext;
    @Column(name = "appdescriptiontext")
    private String appdescriptiontext;
    @Column(name = "active")
    private Integer active;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "apprefid")
    private Collection<Appappsourcelanguagerel> appappsourcelanguagerelCollection;

    public App() {
    }

    public App(Integer id) {
        this.id = id;
    }

    public App(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getApptext() {
        return apptext;
    }

    public void setApptext(String apptext) {
        this.apptext = apptext;
    }

    public String getAppdescriptiontext() {
        return appdescriptiontext;
    }

    public void setAppdescriptiontext(String appdescriptiontext) {
        this.appdescriptiontext = appdescriptiontext;
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

    @XmlTransient
    public Collection<Appappsourcelanguagerel> getAppappsourcelanguagerelCollection() {
        return appappsourcelanguagerelCollection;
    }

    public void setAppappsourcelanguagerelCollection(Collection<Appappsourcelanguagerel> appappsourcelanguagerelCollection) {
        this.appappsourcelanguagerelCollection = appappsourcelanguagerelCollection;
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
        if (!(object instanceof App)) {
            return false;
        }
        App other = (App) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.App[ id=" + id + " ]";
    }

}
