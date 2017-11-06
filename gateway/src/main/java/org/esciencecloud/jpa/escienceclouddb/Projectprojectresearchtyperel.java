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
@Table(name = "projectprojectresearchtyperel")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectprojectresearchtyperel.findAll", query = "SELECT p FROM Projectprojectresearchtyperel p")
        , @NamedQuery(name = "Projectprojectresearchtyperel.findById", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.id = :id")
        , @NamedQuery(name = "Projectprojectresearchtyperel.findByProjectprojectresearchtyperelactive", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.projectprojectresearchtyperelactive = :projectprojectresearchtyperelactive")
        , @NamedQuery(name = "Projectprojectresearchtyperel.findByLastmodified", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.lastmodified = :lastmodified")})
public class Projectprojectresearchtyperel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectprojectresearchtyperelactive")
    private Integer projectprojectresearchtyperelactive;
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

    public Integer getProjectprojectresearchtyperelactive() {
        return projectprojectresearchtyperelactive;
    }

    public void setProjectprojectresearchtyperelactive(Integer projectprojectresearchtyperelactive) {
        this.projectprojectresearchtyperelactive = projectprojectresearchtyperelactive;
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
