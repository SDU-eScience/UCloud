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
@Table(name = "projectprojectdocumentrel")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectprojectdocumentrel.findAll", query = "SELECT p FROM Projectprojectdocumentrel p")
        , @NamedQuery(name = "Projectprojectdocumentrel.findById", query = "SELECT p FROM Projectprojectdocumentrel p WHERE p.id = :id")
        , @NamedQuery(name = "Projectprojectdocumentrel.findByProjectprojectdocumentrelactive", query = "SELECT p FROM Projectprojectdocumentrel p WHERE p.projectprojectdocumentrelactive = :projectprojectdocumentrelactive")
        , @NamedQuery(name = "Projectprojectdocumentrel.findByLastmodified", query = "SELECT p FROM Projectprojectdocumentrel p WHERE p.lastmodified = :lastmodified")})
public class Projectprojectdocumentrel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectprojectdocumentrelactive")
    private Integer projectprojectdocumentrelactive;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;
    @JoinColumn(name = "projectdocumentrefid", referencedColumnName = "id")
    @ManyToOne
    private Projectdocument projectdocumentrefid;

    public Projectprojectdocumentrel() {
    }

    public Projectprojectdocumentrel(Integer id) {
        this.id = id;
    }

    public Projectprojectdocumentrel(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProjectprojectdocumentrelactive() {
        return projectprojectdocumentrelactive;
    }

    public void setProjectprojectdocumentrelactive(Integer projectprojectdocumentrelactive) {
        this.projectprojectdocumentrelactive = projectprojectdocumentrelactive;
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

    public Projectdocument getProjectdocumentrefid() {
        return projectdocumentrefid;
    }

    public void setProjectdocumentrefid(Projectdocument projectdocumentrefid) {
        this.projectdocumentrefid = projectdocumentrefid;
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
        if (!(object instanceof Projectprojectdocumentrel)) {
            return false;
        }
        Projectprojectdocumentrel other = (Projectprojectdocumentrel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectprojectdocumentrel[ id=" + id + " ]";
    }

}
