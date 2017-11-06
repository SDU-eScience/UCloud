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
@Table(name = "uimenutype")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Uimenutype.findAll", query = "SELECT u FROM Uimenutype u")
        , @NamedQuery(name = "Uimenutype.findById", query = "SELECT u FROM Uimenutype u WHERE u.id = :id")
        , @NamedQuery(name = "Uimenutype.findByUimenutypetext", query = "SELECT u FROM Uimenutype u WHERE u.uimenutypetext = :uimenutypetext")
        , @NamedQuery(name = "Uimenutype.findByActive", query = "SELECT u FROM Uimenutype u WHERE u.active = :active")
        , @NamedQuery(name = "Uimenutype.findByMarkedfordelete", query = "SELECT u FROM Uimenutype u WHERE u.markedfordelete = :markedfordelete")
        , @NamedQuery(name = "Uimenutype.findByLastmodified", query = "SELECT u FROM Uimenutype u WHERE u.lastmodified = :lastmodified")})
public class Uimenutype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "uimenutypetext")
    private String uimenutypetext;
    @Column(name = "active")
    private Integer active;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @JoinColumn(name = "projectrolerefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Projectrole projectrolerefid;

    public Uimenutype() {
    }

    public Uimenutype(Integer id) {
        this.id = id;
    }

    public Uimenutype(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUimenutypetext() {
        return uimenutypetext;
    }

    public void setUimenutypetext(String uimenutypetext) {
        this.uimenutypetext = uimenutypetext;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public Integer getMarkedfordelete() {
        return markedfordelete;
    }

    public void setMarkedfordelete(Integer markedfordelete) {
        this.markedfordelete = markedfordelete;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Projectrole getProjectrolerefid() {
        return projectrolerefid;
    }

    public void setProjectrolerefid(Projectrole projectrolerefid) {
        this.projectrolerefid = projectrolerefid;
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
        if (!(object instanceof Uimenutype)) {
            return false;
        }
        Uimenutype other = (Uimenutype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Uimenutype[ id=" + id + " ]";
    }

}
