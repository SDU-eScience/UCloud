/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
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
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "subsystem_command")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "SubsystemCommand.findAll", query = "SELECT s FROM SubsystemCommand s")
    , @NamedQuery(name = "SubsystemCommand.findById", query = "SELECT s FROM SubsystemCommand s WHERE s.id = :id")
    , @NamedQuery(name = "SubsystemCommand.findByPayloadmodel", query = "SELECT s FROM SubsystemCommand s WHERE s.payloadmodel = :payloadmodel")
    , @NamedQuery(name = "SubsystemCommand.findByImplemented", query = "SELECT s FROM SubsystemCommand s WHERE s.implemented = :implemented")
    , @NamedQuery(name = "SubsystemCommand.findByKafkatopicname", query = "SELECT s FROM SubsystemCommand s WHERE s.kafkatopicname = :kafkatopicname")
    , @NamedQuery(name = "SubsystemCommand.findByMarkedfordelete", query = "SELECT s FROM SubsystemCommand s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "SubsystemCommand.findByModifiedTs", query = "SELECT s FROM SubsystemCommand s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "SubsystemCommand.findByCreatedTs", query = "SELECT s FROM SubsystemCommand s WHERE s.createdTs = :createdTs")
    , @NamedQuery(name = "SubsystemCommand.findByDaoutil", query = "SELECT s FROM SubsystemCommand s WHERE s.daoutil = :daoutil")})
public class SubsystemCommand implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "payloadmodel")
    private String payloadmodel;
    @Basic(optional = false)
    @Column(name = "implemented")
    private boolean implemented;
    @Column(name = "kafkatopicname")
    private String kafkatopicname;
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
    @Column(name = "daoutil")
    private String daoutil;
    @JoinColumn(name = "subsystemrefid", referencedColumnName = "id")
    @ManyToOne
    private Subsystem subsystemrefid;
    @JoinColumn(name = "subsystemcommandcategoryrefid", referencedColumnName = "id")
    @ManyToOne
    private SubsystemCommandCategory subsystemcommandcategoryrefid;
    @OneToMany(mappedBy = "subsystemcommandrefid")
    private List<SubsystemCommandQueue> subsystemCommandQueueList;

    public SubsystemCommand() {
    }

    public SubsystemCommand(Integer id) {
        this.id = id;
    }

    public SubsystemCommand(Integer id, boolean implemented, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.implemented = implemented;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPayloadmodel() {
        return payloadmodel;
    }

    public void setPayloadmodel(String payloadmodel) {
        this.payloadmodel = payloadmodel;
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

    public String getDaoutil() {
        return daoutil;
    }

    public void setDaoutil(String daoutil) {
        this.daoutil = daoutil;
    }

    public Subsystem getSubsystemrefid() {
        return subsystemrefid;
    }

    public void setSubsystemrefid(Subsystem subsystemrefid) {
        this.subsystemrefid = subsystemrefid;
    }

    public SubsystemCommandCategory getSubsystemcommandcategoryrefid() {
        return subsystemcommandcategoryrefid;
    }

    public void setSubsystemcommandcategoryrefid(SubsystemCommandCategory subsystemcommandcategoryrefid) {
        this.subsystemcommandcategoryrefid = subsystemcommandcategoryrefid;
    }

    @XmlTransient
    public List<SubsystemCommandQueue> getSubsystemCommandQueueList() {
        return subsystemCommandQueueList;
    }

    public void setSubsystemCommandQueueList(List<SubsystemCommandQueue> subsystemCommandQueueList) {
        this.subsystemCommandQueueList = subsystemCommandQueueList;
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
        if (!(object instanceof SubsystemCommand)) {
            return false;
        }
        SubsystemCommand other = (SubsystemCommand) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.SubsystemCommand[ id=" + id + " ]";
    }
    
}
