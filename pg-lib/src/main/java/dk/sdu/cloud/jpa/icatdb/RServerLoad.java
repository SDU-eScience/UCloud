/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "r_server_load")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RServerLoad.findAll", query = "SELECT r FROM RServerLoad r")
    , @NamedQuery(name = "RServerLoad.findByHostName", query = "SELECT r FROM RServerLoad r WHERE r.hostName = :hostName")
    , @NamedQuery(name = "RServerLoad.findByRescName", query = "SELECT r FROM RServerLoad r WHERE r.rescName = :rescName")
    , @NamedQuery(name = "RServerLoad.findByCpuUsed", query = "SELECT r FROM RServerLoad r WHERE r.cpuUsed = :cpuUsed")
    , @NamedQuery(name = "RServerLoad.findByMemUsed", query = "SELECT r FROM RServerLoad r WHERE r.memUsed = :memUsed")
    , @NamedQuery(name = "RServerLoad.findBySwapUsed", query = "SELECT r FROM RServerLoad r WHERE r.swapUsed = :swapUsed")
    , @NamedQuery(name = "RServerLoad.findByRunqLoad", query = "SELECT r FROM RServerLoad r WHERE r.runqLoad = :runqLoad")
    , @NamedQuery(name = "RServerLoad.findByDiskSpace", query = "SELECT r FROM RServerLoad r WHERE r.diskSpace = :diskSpace")
    , @NamedQuery(name = "RServerLoad.findByNetInput", query = "SELECT r FROM RServerLoad r WHERE r.netInput = :netInput")
    , @NamedQuery(name = "RServerLoad.findByNetOutput", query = "SELECT r FROM RServerLoad r WHERE r.netOutput = :netOutput")
    , @NamedQuery(name = "RServerLoad.findByCreateTs", query = "SELECT r FROM RServerLoad r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RServerLoad.findById", query = "SELECT r FROM RServerLoad r WHERE r.id = :id")})
public class RServerLoad implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "host_name")
    private String hostName;
    @Basic(optional = false)
    @Column(name = "resc_name")
    private String rescName;
    @Column(name = "cpu_used")
    private Integer cpuUsed;
    @Column(name = "mem_used")
    private Integer memUsed;
    @Column(name = "swap_used")
    private Integer swapUsed;
    @Column(name = "runq_load")
    private Integer runqLoad;
    @Column(name = "disk_space")
    private Integer diskSpace;
    @Column(name = "net_input")
    private Integer netInput;
    @Column(name = "net_output")
    private Integer netOutput;
    @Column(name = "create_ts")
    private String createTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RServerLoad() {
    }

    public RServerLoad(Integer id) {
        this.id = id;
    }

    public RServerLoad(Integer id, String hostName, String rescName) {
        this.id = id;
        this.hostName = hostName;
        this.rescName = rescName;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getRescName() {
        return rescName;
    }

    public void setRescName(String rescName) {
        this.rescName = rescName;
    }

    public Integer getCpuUsed() {
        return cpuUsed;
    }

    public void setCpuUsed(Integer cpuUsed) {
        this.cpuUsed = cpuUsed;
    }

    public Integer getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(Integer memUsed) {
        this.memUsed = memUsed;
    }

    public Integer getSwapUsed() {
        return swapUsed;
    }

    public void setSwapUsed(Integer swapUsed) {
        this.swapUsed = swapUsed;
    }

    public Integer getRunqLoad() {
        return runqLoad;
    }

    public void setRunqLoad(Integer runqLoad) {
        this.runqLoad = runqLoad;
    }

    public Integer getDiskSpace() {
        return diskSpace;
    }

    public void setDiskSpace(Integer diskSpace) {
        this.diskSpace = diskSpace;
    }

    public Integer getNetInput() {
        return netInput;
    }

    public void setNetInput(Integer netInput) {
        this.netInput = netInput;
    }

    public Integer getNetOutput() {
        return netOutput;
    }

    public void setNetOutput(Integer netOutput) {
        this.netOutput = netOutput;
    }

    public String getCreateTs() {
        return createTs;
    }

    public void setCreateTs(String createTs) {
        this.createTs = createTs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
        if (!(object instanceof RServerLoad)) {
            return false;
        }
        RServerLoad other = (RServerLoad) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RServerLoad[ id=" + id + " ]";
    }
    
}
