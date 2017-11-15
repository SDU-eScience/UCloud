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
@Table(name = "publication")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Publication.findAll", query = "SELECT p FROM Publication p")
        , @NamedQuery(name = "Publication.findById", query = "SELECT p FROM Publication p WHERE p.id = :id")
        , @NamedQuery(name = "Publication.findByPublicationtitle", query = "SELECT p FROM Publication p WHERE p.publicationtitle = :publicationtitle")
        , @NamedQuery(name = "Publication.findByPublicationextlink", query = "SELECT p FROM Publication p WHERE p.publicationextlink = :publicationextlink")
        , @NamedQuery(name = "Publication.findByPublicationdate", query = "SELECT p FROM Publication p WHERE p.publicationdate = :publicationdate")
        , @NamedQuery(name = "Publication.findByLastmodified", query = "SELECT p FROM Publication p WHERE p.lastmodified = :lastmodified")
        , @NamedQuery(name = "Publication.findByActive", query = "SELECT p FROM Publication p WHERE p.active = :active")})
public class Publication implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "publicationtitle")
    private String publicationtitle;
    @Column(name = "publicationextlink")
    private String publicationextlink;
    @Column(name = "publicationdate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date publicationdate;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "active")
    private Integer active;

    public Publication() {
    }

    public Publication(Integer id) {
        this.id = id;
    }

    public Publication(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPublicationtitle() {
        return publicationtitle;
    }

    public void setPublicationtitle(String publicationtitle) {
        this.publicationtitle = publicationtitle;
    }

    public String getPublicationextlink() {
        return publicationextlink;
    }

    public void setPublicationextlink(String publicationextlink) {
        this.publicationextlink = publicationextlink;
    }

    public Date getPublicationdate() {
        return publicationdate;
    }

    public void setPublicationdate(Date publicationdate) {
        this.publicationdate = publicationdate;
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
        if (!(object instanceof Publication)) {
            return false;
        }
        Publication other = (Publication) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Publication[ id=" + id + " ]";
    }

}
