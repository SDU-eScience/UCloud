/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

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
@Table(name = "subsystem_command_queue")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "SubsystemCommandQueue.findAll", query = "SELECT s FROM SubsystemCommandQueue s")
    , @NamedQuery(name = "SubsystemCommandQueue.findById", query = "SELECT s FROM SubsystemCommandQueue s WHERE s.id = :id")
    , @NamedQuery(name = "SubsystemCommandQueue.findByPayload", query = "SELECT s FROM SubsystemCommandQueue s WHERE s.payload = :payload")
    , @NamedQuery(name = "SubsystemCommandQueue.findByPersonjwthistoryrefid", query = "SELECT s FROM SubsystemCommandQueue s WHERE s.personjwthistoryrefid = :personjwthistoryrefid")
    , @NamedQuery(name = "SubsystemCommandQueue.findByMarkedfordelete", query = "SELECT s FROM SubsystemCommandQueue s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "SubsystemCommandQueue.findByModifiedTs", query = "SELECT s FROM SubsystemCommandQueue s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "SubsystemCommandQueue.findByCreatedTs", query = "SELECT s FROM SubsystemCommandQueue s WHERE s.createdTs = :createdTs")})
public class SubsystemCommandQueue implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "payload")
    private String payload;
    @Column(name = "personjwthistoryrefid")
    private Integer personjwthistoryrefid;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @JoinColumn(name = "subsystemcommandrefid", referencedColumnName = "id")
    @ManyToOne
    private SubsystemCommand subsystemcommandrefid;
    @JoinColumn(name = "subsystemcommandstatusrefid", referencedColumnName = "id")
    @ManyToOne
    private SubsystemCommandStatus subsystemcommandstatusrefid;

    public SubsystemCommandQueue() {
    }

    public SubsystemCommandQueue(Integer id) {
        this.id = id;
    }

    public SubsystemCommandQueue(Integer id, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
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

    public Integer getPersonjwthistoryrefid() {
        return personjwthistoryrefid;
    }

    public void setPersonjwthistoryrefid(Integer personjwthistoryrefid) {
        this.personjwthistoryrefid = personjwthistoryrefid;
    }

    public Integer getMarkedfordelete() {
        return markedfordelete;
    }

    public void setMarkedfordelete(Integer markedfordelete) {
        this.markedfordelete = markedfordelete;
    }

    public Date getModifiedTs() {
        return modifiedTs;
    }

    public void setModifiedTs(Date modifiedTs) {
        this.modifiedTs = modifiedTs;
    }

    public Date getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(Date createdTs) {
        this.createdTs = createdTs;
    }

    public SubsystemCommand getSubsystemcommandrefid() {
        return subsystemcommandrefid;
    }

    public void setSubsystemcommandrefid(SubsystemCommand subsystemcommandrefid) {
        this.subsystemcommandrefid = subsystemcommandrefid;
    }

    public SubsystemCommandStatus getSubsystemcommandstatusrefid() {
        return subsystemcommandstatusrefid;
    }

    public void setSubsystemcommandstatusrefid(SubsystemCommandStatus subsystemcommandstatusrefid) {
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
        if (!(object instanceof SubsystemCommandQueue)) {
            return false;
        }
        SubsystemCommandQueue other = (SubsystemCommandQueue) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.SubsystemCommandQueue[ id=" + id + " ]";
    }
    
}
