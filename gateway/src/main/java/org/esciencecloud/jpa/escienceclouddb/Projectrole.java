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
@Table(name = "projectrole")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectrole.findAll", query = "SELECT p FROM Projectrole p")
        , @NamedQuery(name = "Projectrole.findById", query = "SELECT p FROM Projectrole p WHERE p.id = :id")
        , @NamedQuery(name = "Projectrole.findByProjectroletext", query = "SELECT p FROM Projectrole p WHERE p.projectroletext = :projectroletext")
        , @NamedQuery(name = "Projectrole.findByLastmodified", query = "SELECT p FROM Projectrole p WHERE p.lastmodified = :lastmodified")
        , @NamedQuery(name = "Projectrole.findByActive", query = "SELECT p FROM Projectrole p WHERE p.active = :active")
        , @NamedQuery(name = "Projectrole.findByIrodsrolemap", query = "SELECT p FROM Projectrole p WHERE p.irodsrolemap = :irodsrolemap")})
public class Projectrole implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectroletext")
    private String projectroletext;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "active")
    private Integer active;
    @Column(name = "irodsrolemap")
    private String irodsrolemap;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrolerefid")
    private Collection<Uimenu> uimenuCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrolerefid")
    private Collection<Uimenutype> uimenutypeCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "projectrolerefid")
    private Collection<Projectpersonrel> projectpersonrelCollection;

    public Projectrole() {
    }

    public Projectrole(Integer id) {
        this.id = id;
    }

    public Projectrole(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getProjectroletext() {
        return projectroletext;
    }

    public void setProjectroletext(String projectroletext) {
        this.projectroletext = projectroletext;
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

    public String getIrodsrolemap() {
        return irodsrolemap;
    }

    public void setIrodsrolemap(String irodsrolemap) {
        this.irodsrolemap = irodsrolemap;
    }

    @XmlTransient
    public Collection<Uimenu> getUimenuCollection() {
        return uimenuCollection;
    }

    public void setUimenuCollection(Collection<Uimenu> uimenuCollection) {
        this.uimenuCollection = uimenuCollection;
    }

    @XmlTransient
    public Collection<Uimenutype> getUimenutypeCollection() {
        return uimenutypeCollection;
    }

    public void setUimenutypeCollection(Collection<Uimenutype> uimenutypeCollection) {
        this.uimenutypeCollection = uimenutypeCollection;
    }

    @XmlTransient
    public Collection<Projectpersonrel> getProjectpersonrelCollection() {
        return projectpersonrelCollection;
    }

    public void setProjectpersonrelCollection(Collection<Projectpersonrel> projectpersonrelCollection) {
        this.projectpersonrelCollection = projectpersonrelCollection;
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
        if (!(object instanceof Projectrole)) {
            return false;
        }
        Projectrole other = (Projectrole) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectrole[ id=" + id + " ]";
    }

}
