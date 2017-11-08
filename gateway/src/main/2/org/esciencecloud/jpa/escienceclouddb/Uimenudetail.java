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
@Table(name = "uimenudetail")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Uimenudetail.findAll", query = "SELECT u FROM Uimenudetail u")
        , @NamedQuery(name = "Uimenudetail.findById", query = "SELECT u FROM Uimenudetail u WHERE u.id = :id")
        , @NamedQuery(name = "Uimenudetail.findByUimenudetailtext", query = "SELECT u FROM Uimenudetail u WHERE u.uimenudetailtext = :uimenudetailtext")
        , @NamedQuery(name = "Uimenudetail.findByUimenudetailhref", query = "SELECT u FROM Uimenudetail u WHERE u.uimenudetailhref = :uimenudetailhref")
        , @NamedQuery(name = "Uimenudetail.findByActive", query = "SELECT u FROM Uimenudetail u WHERE u.active = :active")
        , @NamedQuery(name = "Uimenudetail.findByMarkedfordelete", query = "SELECT u FROM Uimenudetail u WHERE u.markedfordelete = :markedfordelete")
        , @NamedQuery(name = "Uimenudetail.findByLastmodified", query = "SELECT u FROM Uimenudetail u WHERE u.lastmodified = :lastmodified")})
public class Uimenudetail implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "uimenudetailtext")
    private String uimenudetailtext;
    @Column(name = "uimenudetailhref")
    private String uimenudetailhref;
    @Column(name = "active")
    private Integer active;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @JoinColumn(name = "uimenurefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Uimenu uimenurefid;

    public Uimenudetail() {
    }

    public Uimenudetail(Integer id) {
        this.id = id;
    }

    public Uimenudetail(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUimenudetailtext() {
        return uimenudetailtext;
    }

    public void setUimenudetailtext(String uimenudetailtext) {
        this.uimenudetailtext = uimenudetailtext;
    }

    public String getUimenudetailhref() {
        return uimenudetailhref;
    }

    public void setUimenudetailhref(String uimenudetailhref) {
        this.uimenudetailhref = uimenudetailhref;
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

    public Uimenu getUimenurefid() {
        return uimenurefid;
    }

    public void setUimenurefid(Uimenu uimenurefid) {
        this.uimenurefid = uimenurefid;
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
        if (!(object instanceof Uimenudetail)) {
            return false;
        }
        Uimenudetail other = (Uimenudetail) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Uimenudetail[ id=" + id + " ]";
    }

}
