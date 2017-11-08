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
@Table(name = "subsystemcommandcategory")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Subsystemcommandcategory.findAll", query = "SELECT s FROM Subsystemcommandcategory s")
        , @NamedQuery(name = "Subsystemcommandcategory.findById", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.id = :id")
        , @NamedQuery(name = "Subsystemcommandcategory.findBySubsystemcommandcategorytext", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.subsystemcommandcategorytext = :subsystemcommandcategorytext")
        , @NamedQuery(name = "Subsystemcommandcategory.findByLastmodified", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.lastmodified = :lastmodified")})
public class Subsystemcommandcategory implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "subsystemcommandcategorytext")
    private String subsystemcommandcategorytext;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "subsystemcommandcategoryrefid")
    private List<Subsystemcommand> subsystemcommandList;

    public Subsystemcommandcategory() {
    }

    public Subsystemcommandcategory(Integer id) {
        this.id = id;
    }

    public Subsystemcommandcategory(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSubsystemcommandcategorytext() {
        return subsystemcommandcategorytext;
    }

    public void setSubsystemcommandcategorytext(String subsystemcommandcategorytext) {
        this.subsystemcommandcategorytext = subsystemcommandcategorytext;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    @XmlTransient
    public List<Subsystemcommand> getSubsystemcommandList() {
        return subsystemcommandList;
    }

    public void setSubsystemcommandList(List<Subsystemcommand> subsystemcommandList) {
        this.subsystemcommandList = subsystemcommandList;
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
        if (!(object instanceof Subsystemcommandcategory)) {
            return false;
        }
        Subsystemcommandcategory other = (Subsystemcommandcategory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Subsystemcommandcategory[ id=" + id + " ]";
    }

}
