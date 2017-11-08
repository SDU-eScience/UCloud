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
@Table(name = "uimenu")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Uimenu.findAll", query = "SELECT u FROM Uimenu u")
        , @NamedQuery(name = "Uimenu.findById", query = "SELECT u FROM Uimenu u WHERE u.id = :id")
        , @NamedQuery(name = "Uimenu.findByUimenutext", query = "SELECT u FROM Uimenu u WHERE u.uimenutext = :uimenutext")
        , @NamedQuery(name = "Uimenu.findByActive", query = "SELECT u FROM Uimenu u WHERE u.active = :active")
        , @NamedQuery(name = "Uimenu.findByMarkedfordelete", query = "SELECT u FROM Uimenu u WHERE u.markedfordelete = :markedfordelete")
        , @NamedQuery(name = "Uimenu.findByLastmodified", query = "SELECT u FROM Uimenu u WHERE u.lastmodified = :lastmodified")})
public class Uimenu implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "uimenutext")
    private String uimenutext;
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
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "uimenurefid")
    private List<Uimenudetail> uimenudetailList;

    public Uimenu() {
    }

    public Uimenu(Integer id) {
        this.id = id;
    }

    public Uimenu(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUimenutext() {
        return uimenutext;
    }

    public void setUimenutext(String uimenutext) {
        this.uimenutext = uimenutext;
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

    @XmlTransient
    public List<Uimenudetail> getUimenudetailList() {
        return uimenudetailList;
    }

    public void setUimenudetailList(List<Uimenudetail> uimenudetailList) {
        this.uimenudetailList = uimenudetailList;
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
        if (!(object instanceof Uimenu)) {
            return false;
        }
        Uimenu other = (Uimenu) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Uimenu[ id=" + id + " ]";
    }

}
