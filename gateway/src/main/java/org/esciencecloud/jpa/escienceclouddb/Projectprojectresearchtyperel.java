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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
@Table(name = "projectprojectresearchtyperel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Projectprojectresearchtyperel.findAll", query = "SELECT p FROM Projectprojectresearchtyperel p")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findById", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.id = :id")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findByActive", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.active = :active")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findByLastmodified", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.lastmodified = :lastmodified")})
public class Projectprojectresearchtyperel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "active")
    private Integer active;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;
    @JoinColumn(name = "projectresearchtyperefid", referencedColumnName = "id")
    @ManyToOne
    private Projectresearchtype projectresearchtyperefid;

    public Projectprojectresearchtyperel() {
    }

    public Projectprojectresearchtyperel(Integer id) {
        this.id = id;
    }

    public Projectprojectresearchtyperel(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Project getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Project projectrefid) {
        this.projectrefid = projectrefid;
    }

    public Projectresearchtype getProjectresearchtyperefid() {
        return projectresearchtyperefid;
    }

    public void setProjectresearchtyperefid(Projectresearchtype projectresearchtyperefid) {
        this.projectresearchtyperefid = projectresearchtyperefid;
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
        if (!(object instanceof Projectprojectresearchtyperel)) {
            return false;
        }
        Projectprojectresearchtyperel other = (Projectprojectresearchtyperel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectprojectresearchtyperel[ id=" + id + " ]";
    }
    
}
