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
@Table(name = "projectpublicationrel")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectpublicationrel.findAll", query = "SELECT p FROM Projectpublicationrel p")
        , @NamedQuery(name = "Projectpublicationrel.findById", query = "SELECT p FROM Projectpublicationrel p WHERE p.id = :id")
        , @NamedQuery(name = "Projectpublicationrel.findByProjectrefid", query = "SELECT p FROM Projectpublicationrel p WHERE p.projectrefid = :projectrefid")
        , @NamedQuery(name = "Projectpublicationrel.findByPublicationrefid", query = "SELECT p FROM Projectpublicationrel p WHERE p.publicationrefid = :publicationrefid")
        , @NamedQuery(name = "Projectpublicationrel.findByLastmodified", query = "SELECT p FROM Projectpublicationrel p WHERE p.lastmodified = :lastmodified")
        , @NamedQuery(name = "Projectpublicationrel.findByProjectpublicationrelactive", query = "SELECT p FROM Projectpublicationrel p WHERE p.projectpublicationrelactive = :projectpublicationrelactive")})
public class Projectpublicationrel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectrefid")
    private Integer projectrefid;
    @Column(name = "publicationrefid")
    private Integer publicationrefid;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "projectpublicationrelactive")
    private Integer projectpublicationrelactive;

    public Projectpublicationrel() {
    }

    public Projectpublicationrel(Integer id) {
        this.id = id;
    }

    public Projectpublicationrel(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Integer projectrefid) {
        this.projectrefid = projectrefid;
    }

    public Integer getPublicationrefid() {
        return publicationrefid;
    }

    public void setPublicationrefid(Integer publicationrefid) {
        this.publicationrefid = publicationrefid;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Integer getProjectpublicationrelactive() {
        return projectpublicationrelactive;
    }

    public void setProjectpublicationrelactive(Integer projectpublicationrelactive) {
        this.projectpublicationrelactive = projectpublicationrelactive;
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
        if (!(object instanceof Projectpublicationrel)) {
            return false;
        }
        Projectpublicationrel other = (Projectpublicationrel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectpublicationrel[ id=" + id + " ]";
    }

}
