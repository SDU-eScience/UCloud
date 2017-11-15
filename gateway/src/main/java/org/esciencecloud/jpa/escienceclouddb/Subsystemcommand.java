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
@Table(name = "subsystemcommand")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Subsystemcommand.findAll", query = "SELECT s FROM Subsystemcommand s")
        , @NamedQuery(name = "Subsystemcommand.findById", query = "SELECT s FROM Subsystemcommand s WHERE s.id = :id")
        , @NamedQuery(name = "Subsystemcommand.findBySubsystemcommandtext", query = "SELECT s FROM Subsystemcommand s WHERE s.subsystemcommandtext = :subsystemcommandtext")
        , @NamedQuery(name = "Subsystemcommand.findByLastmodified", query = "SELECT s FROM Subsystemcommand s WHERE s.lastmodified = :lastmodified")
        , @NamedQuery(name = "Subsystemcommand.findByImplemented", query = "SELECT s FROM Subsystemcommand s WHERE s.implemented = :implemented")
        , @NamedQuery(name = "Subsystemcommand.findByKafkatopicname", query = "SELECT s FROM Subsystemcommand s WHERE s.kafkatopicname = :kafkatopicname")})
public class Subsystemcommand implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "subsystemcommandtext")
    private String subsystemcommandtext;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Basic(optional = false)
    @Column(name = "implemented")
    private boolean implemented;
    @Column(name = "kafkatopicname")
    private String kafkatopicname;
    @OneToMany(mappedBy = "subsystemcommandrefid")
    private Collection<Subsystemcommandqueue> subsystemcommandqueueCollection;
    @JoinColumn(name = "subsystemrefid", referencedColumnName = "id")
    @ManyToOne
    private Subsystem subsystemrefid;
    @JoinColumn(name = "subsystemcommandcategoryrefid", referencedColumnName = "id")
    @ManyToOne
    private Subsystemcommandcategory subsystemcommandcategoryrefid;

    public Subsystemcommand() {
    }

    public Subsystemcommand(Integer id) {
        this.id = id;
    }

    public Subsystemcommand(Integer id, Date lastmodified, boolean implemented) {
        this.id = id;
        this.lastmodified = lastmodified;
        this.implemented = implemented;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSubsystemcommandtext() {
        return subsystemcommandtext;
    }

    public void setSubsystemcommandtext(String subsystemcommandtext) {
        this.subsystemcommandtext = subsystemcommandtext;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public boolean getImplemented() {
        return implemented;
    }

    public void setImplemented(boolean implemented) {
        this.implemented = implemented;
    }

    public String getKafkatopicname() {
        return kafkatopicname;
    }

    public void setKafkatopicname(String kafkatopicname) {
        this.kafkatopicname = kafkatopicname;
    }

    @XmlTransient
    public Collection<Subsystemcommandqueue> getSubsystemcommandqueueCollection() {
        return subsystemcommandqueueCollection;
    }

    public void setSubsystemcommandqueueCollection(Collection<Subsystemcommandqueue> subsystemcommandqueueCollection) {
        this.subsystemcommandqueueCollection = subsystemcommandqueueCollection;
    }

    public Subsystem getSubsystemrefid() {
        return subsystemrefid;
    }

    public void setSubsystemrefid(Subsystem subsystemrefid) {
        this.subsystemrefid = subsystemrefid;
    }

    public Subsystemcommandcategory getSubsystemcommandcategoryrefid() {
        return subsystemcommandcategoryrefid;
    }

    public void setSubsystemcommandcategoryrefid(Subsystemcommandcategory subsystemcommandcategoryrefid) {
        this.subsystemcommandcategoryrefid = subsystemcommandcategoryrefid;
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
        if (!(object instanceof Subsystemcommand)) {
            return false;
        }
        Subsystemcommand other = (Subsystemcommand) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Subsystemcommand[ id=" + id + " ]";
    }

}
