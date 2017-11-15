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
@Table(name = "projectresearchtype")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectresearchtype.findAll", query = "SELECT p FROM Projectresearchtype p")
        , @NamedQuery(name = "Projectresearchtype.findById", query = "SELECT p FROM Projectresearchtype p WHERE p.id = :id")
        , @NamedQuery(name = "Projectresearchtype.findByProjectresearchtypetext", query = "SELECT p FROM Projectresearchtype p WHERE p.projectresearchtypetext = :projectresearchtypetext")
        , @NamedQuery(name = "Projectresearchtype.findByActive", query = "SELECT p FROM Projectresearchtype p WHERE p.active = :active")
        , @NamedQuery(name = "Projectresearchtype.findByLastmodified", query = "SELECT p FROM Projectresearchtype p WHERE p.lastmodified = :lastmodified")})
public class Projectresearchtype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectresearchtypetext")
    private String projectresearchtypetext;
    @Column(name = "active")
    private Integer active;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "projectresearchtyperefid")
    private Collection<Projectprojectresearchtyperel> projectprojectresearchtyperelCollection;

    public Projectresearchtype() {
    }

    public Projectresearchtype(Integer id) {
        this.id = id;
    }

    public Projectresearchtype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getProjectresearchtypetext() {
        return projectresearchtypetext;
    }

    public void setProjectresearchtypetext(String projectresearchtypetext) {
        this.projectresearchtypetext = projectresearchtypetext;
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
    public Collection<Projectprojectresearchtyperel> getProjectprojectresearchtyperelCollection() {
        return projectprojectresearchtyperelCollection;
    }

    public void setProjectprojectresearchtyperelCollection(Collection<Projectprojectresearchtyperel> projectprojectresearchtyperelCollection) {
        this.projectprojectresearchtyperelCollection = projectprojectresearchtyperelCollection;
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
        if (!(object instanceof Projectresearchtype)) {
            return false;
        }
        Projectresearchtype other = (Projectresearchtype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectresearchtype[ id=" + id + " ]";
    }

}
