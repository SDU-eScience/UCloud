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
@Table(name = "subsystem")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Subsystem.findAll", query = "SELECT s FROM Subsystem s")
    , @NamedQuery(name = "Subsystem.findById", query = "SELECT s FROM Subsystem s WHERE s.id = :id")
    , @NamedQuery(name = "Subsystem.findBySubsystemname", query = "SELECT s FROM Subsystem s WHERE s.subsystemname = :subsystemname")
    , @NamedQuery(name = "Subsystem.findByHealth", query = "SELECT s FROM Subsystem s WHERE s.health = :health")
    , @NamedQuery(name = "Subsystem.findByIpDev", query = "SELECT s FROM Subsystem s WHERE s.ipDev = :ipDev")
    , @NamedQuery(name = "Subsystem.findByPortDev", query = "SELECT s FROM Subsystem s WHERE s.portDev = :portDev")
    , @NamedQuery(name = "Subsystem.findByIpTest", query = "SELECT s FROM Subsystem s WHERE s.ipTest = :ipTest")
    , @NamedQuery(name = "Subsystem.findByPortTest", query = "SELECT s FROM Subsystem s WHERE s.portTest = :portTest")
    , @NamedQuery(name = "Subsystem.findByIpProd", query = "SELECT s FROM Subsystem s WHERE s.ipProd = :ipProd")
    , @NamedQuery(name = "Subsystem.findByPortProd", query = "SELECT s FROM Subsystem s WHERE s.portProd = :portProd")
    , @NamedQuery(name = "Subsystem.findByMarkedfordelete", query = "SELECT s FROM Subsystem s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Subsystem.findByModifiedTs", query = "SELECT s FROM Subsystem s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Subsystem.findByCreatedTs", query = "SELECT s FROM Subsystem s WHERE s.createdTs = :createdTs")})
public class Subsystem implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "subsystemname")
    private String subsystemname;
    @Column(name = "health")
    private Integer health;
    @Column(name = "ip_dev")
    private String ipDev;
    @Column(name = "port_dev")
    private String portDev;
    @Column(name = "ip_test")
    private String ipTest;
    @Column(name = "port_test")
    private String portTest;
    @Column(name = "ip_prod")
    private String ipProd;
    @Column(name = "port_prod")
    private String portProd;
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
    @OneToMany(mappedBy = "subsystemrefid")
    private List<SubsystemCommand> subsystemCommandList;

    public Subsystem() {
    }

    public Subsystem(Integer id) {
        this.id = id;
    }

    public Subsystem(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getSubsystemname() {
        return subsystemname;
    }

    public void setSubsystemname(String subsystemname) {
        this.subsystemname = subsystemname;
    }

    public Integer getHealth() {
        return health;
    }

    public void setHealth(Integer health) {
        this.health = health;
    }

    public String getIpDev() {
        return ipDev;
    }

    public void setIpDev(String ipDev) {
        this.ipDev = ipDev;
    }

    public String getPortDev() {
        return portDev;
    }

    public void setPortDev(String portDev) {
        this.portDev = portDev;
    }

    public String getIpTest() {
        return ipTest;
    }

    public void setIpTest(String ipTest) {
        this.ipTest = ipTest;
    }

    public String getPortTest() {
        return portTest;
    }

    public void setPortTest(String portTest) {
        this.portTest = portTest;
    }

    public String getIpProd() {
        return ipProd;
    }

    public void setIpProd(String ipProd) {
        this.ipProd = ipProd;
    }

    public String getPortProd() {
        return portProd;
    }

    public void setPortProd(String portProd) {
        this.portProd = portProd;
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
    public List<SubsystemCommand> getSubsystemCommandList() {
        return subsystemCommandList;
    }

    public void setSubsystemCommandList(List<SubsystemCommand> subsystemCommandList) {
        this.subsystemCommandList = subsystemCommandList;
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
        if (!(object instanceof Subsystem)) {
            return false;
        }
        Subsystem other = (Subsystem) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Subsystem[ id=" + id + " ]";
    }
    
}
