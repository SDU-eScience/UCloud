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
@Table(name = "subsystemcommand")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Subsystemcommand.findAll", query = "SELECT s FROM Subsystemcommand s")
    , @NamedQuery(name = "Subsystemcommand.findById", query = "SELECT s FROM Subsystemcommand s WHERE s.id = :id")
    , @NamedQuery(name = "Subsystemcommand.findByPayloadmodel", query = "SELECT s FROM Subsystemcommand s WHERE s.payloadmodel = :payloadmodel")
    , @NamedQuery(name = "Subsystemcommand.findByImplemented", query = "SELECT s FROM Subsystemcommand s WHERE s.implemented = :implemented")
    , @NamedQuery(name = "Subsystemcommand.findByKafkatopicname", query = "SELECT s FROM Subsystemcommand s WHERE s.kafkatopicname = :kafkatopicname")
    , @NamedQuery(name = "Subsystemcommand.findByMarkedfordelete", query = "SELECT s FROM Subsystemcommand s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Subsystemcommand.findByModifiedTs", query = "SELECT s FROM Subsystemcommand s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Subsystemcommand.findByCreatedTs", query = "SELECT s FROM Subsystemcommand s WHERE s.createdTs = :createdTs")})
public class Subsystemcommand implements Serializable {

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
    @OneToMany(mappedBy = "subsystemcommandrefid")
    private List<Subsystemcommandqueue> subsystemcommandqueueList;
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

    public Subsystemcommand(Integer id, boolean implemented, Date modifiedTs, Date createdTs) {
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

    @XmlTransient
    public List<Subsystemcommandqueue> getSubsystemcommandqueueList() {
        return subsystemcommandqueueList;
    }

    public void setSubsystemcommandqueueList(List<Subsystemcommandqueue> subsystemcommandqueueList) {
        this.subsystemcommandqueueList = subsystemcommandqueueList;
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

    @java.lang.Override
    public java.lang.String toString() {
        return "Subsystemcommand{" +
                "id=" + id +
                ", payloadmodel='" + payloadmodel + '\'' +
                ", implemented=" + implemented +
                ", kafkatopicname='" + kafkatopicname + '\'' +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", subsystemcommandqueueList=" + subsystemcommandqueueList +
                ", subsystemrefid=" + subsystemrefid +
                ", subsystemcommandcategoryrefid=" + subsystemcommandcategoryrefid +
                '}';
    }
}
