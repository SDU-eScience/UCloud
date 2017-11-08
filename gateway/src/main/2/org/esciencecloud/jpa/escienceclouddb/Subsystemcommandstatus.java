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
@Table(name = "subsystemcommandstatus")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Subsystemcommandstatus.findAll", query = "SELECT s FROM Subsystemcommandstatus s")
        , @NamedQuery(name = "Subsystemcommandstatus.findById", query = "SELECT s FROM Subsystemcommandstatus s WHERE s.id = :id")
        , @NamedQuery(name = "Subsystemcommandstatus.findBySubsystemcommandstatustext", query = "SELECT s FROM Subsystemcommandstatus s WHERE s.subsystemcommandstatustext = :subsystemcommandstatustext")
        , @NamedQuery(name = "Subsystemcommandstatus.findByLastmodified", query = "SELECT s FROM Subsystemcommandstatus s WHERE s.lastmodified = :lastmodified")})
public class Subsystemcommandstatus implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "subsystemcommandstatustext")
    private String subsystemcommandstatustext;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "subsystemcommandstatusrefid")
    private List<Subsystemcommandqueue> subsystemcommandqueueList;

    public Subsystemcommandstatus() {
    }

    public Subsystemcommandstatus(Integer id) {
        this.id = id;
    }

    public Subsystemcommandstatus(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSubsystemcommandstatustext() {
        return subsystemcommandstatustext;
    }

    public void setSubsystemcommandstatustext(String subsystemcommandstatustext) {
        this.subsystemcommandstatustext = subsystemcommandstatustext;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    @XmlTransient
    public List<Subsystemcommandqueue> getSubsystemcommandqueueList() {
        return subsystemcommandqueueList;
    }

    public void setSubsystemcommandqueueList(List<Subsystemcommandqueue> subsystemcommandqueueList) {
        this.subsystemcommandqueueList = subsystemcommandqueueList;
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
        if (!(object instanceof Subsystemcommandstatus)) {
            return false;
        }
        Subsystemcommandstatus other = (Subsystemcommandstatus) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Subsystemcommandstatus[ id=" + id + " ]";
    }

}
