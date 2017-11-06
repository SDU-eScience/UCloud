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
@Table(name = "subsystemcommandqueue")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Subsystemcommandqueue.findAll", query = "SELECT s FROM Subsystemcommandqueue s")
        , @NamedQuery(name = "Subsystemcommandqueue.findById", query = "SELECT s FROM Subsystemcommandqueue s WHERE s.id = :id")
        , @NamedQuery(name = "Subsystemcommandqueue.findByPayload", query = "SELECT s FROM Subsystemcommandqueue s WHERE s.payload = :payload")
        , @NamedQuery(name = "Subsystemcommandqueue.findByLastmodified", query = "SELECT s FROM Subsystemcommandqueue s WHERE s.lastmodified = :lastmodified")})
public class Subsystemcommandqueue implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "payload")
    private String payload;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @JoinColumn(name = "personsessionhistoryrefid", referencedColumnName = "id")
    @ManyToOne
    private Personsessionhistory personsessionhistoryrefid;
    @JoinColumn(name = "subsystemcommandrefid", referencedColumnName = "id")
    @ManyToOne
    private Subsystemcommand subsystemcommandrefid;
    @JoinColumn(name = "subsystemcommandstatusrefid", referencedColumnName = "id")
    @ManyToOne
    private Subsystemcommandstatus subsystemcommandstatusrefid;

    public Subsystemcommandqueue() {
    }

    public Subsystemcommandqueue(Integer id) {
        this.id = id;
    }

    public Subsystemcommandqueue(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public Personsessionhistory getPersonsessionhistoryrefid() {
        return personsessionhistoryrefid;
    }

    public void setPersonsessionhistoryrefid(Personsessionhistory personsessionhistoryrefid) {
        this.personsessionhistoryrefid = personsessionhistoryrefid;
    }

    public Subsystemcommand getSubsystemcommandrefid() {
        return subsystemcommandrefid;
    }

    public void setSubsystemcommandrefid(Subsystemcommand subsystemcommandrefid) {
        this.subsystemcommandrefid = subsystemcommandrefid;
    }

    public Subsystemcommandstatus getSubsystemcommandstatusrefid() {
        return subsystemcommandstatusrefid;
    }

    public void setSubsystemcommandstatusrefid(Subsystemcommandstatus subsystemcommandstatusrefid) {
        this.subsystemcommandstatusrefid = subsystemcommandstatusrefid;
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
        if (!(object instanceof Subsystemcommandqueue)) {
            return false;
        }
        Subsystemcommandqueue other = (Subsystemcommandqueue) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Subsystemcommandqueue[ id=" + id + " ]";
    }

}
