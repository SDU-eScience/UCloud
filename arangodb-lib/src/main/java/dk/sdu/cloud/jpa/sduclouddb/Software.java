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
@Table(name = "software")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Software.findAll", query = "SELECT s FROM Software s")
    , @NamedQuery(name = "Software.findById", query = "SELECT s FROM Software s WHERE s.id = :id")
    , @NamedQuery(name = "Software.findByDownloadurl", query = "SELECT s FROM Software s WHERE s.downloadurl = :downloadurl")
    , @NamedQuery(name = "Software.findBySoftwarename", query = "SELECT s FROM Software s WHERE s.softwarename = :softwarename")
    , @NamedQuery(name = "Software.findByVersion", query = "SELECT s FROM Software s WHERE s.version = :version")
    , @NamedQuery(name = "Software.findByRpms", query = "SELECT s FROM Software s WHERE s.rpms = :rpms")
    , @NamedQuery(name = "Software.findByYums", query = "SELECT s FROM Software s WHERE s.yums = :yums")
    , @NamedQuery(name = "Software.findByPorts", query = "SELECT s FROM Software s WHERE s.ports = :ports")
    , @NamedQuery(name = "Software.findByMarkedfordelete", query = "SELECT s FROM Software s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Software.findByModifiedTs", query = "SELECT s FROM Software s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Software.findByCreatedTs", query = "SELECT s FROM Software s WHERE s.createdTs = :createdTs")})
public class Software implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "downloadurl")
    private String downloadurl;
    @Column(name = "softwarename")
    private String softwarename;
    @Column(name = "version")
    private String version;
    @Column(name = "rpms")
    private String rpms;
    @Column(name = "yums")
    private String yums;
    @Column(name = "ports")
    private String ports;
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
    @JoinColumn(name = "devstagerefid", referencedColumnName = "id")
    @ManyToOne
    private Devstage devstagerefid;
    @JoinColumn(name = "serverrefid", referencedColumnName = "id")
    @ManyToOne
    private Server serverrefid;

    public Software() {
    }

    public Software(Integer id) {
        this.id = id;
    }

    public Software(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDownloadurl() {
        return downloadurl;
    }

    public void setDownloadurl(String downloadurl) {
        this.downloadurl = downloadurl;
    }

    public String getSoftwarename() {
        return softwarename;
    }

    public void setSoftwarename(String softwarename) {
        this.softwarename = softwarename;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRpms() {
        return rpms;
    }

    public void setRpms(String rpms) {
        this.rpms = rpms;
    }

    public String getYums() {
        return yums;
    }

    public void setYums(String yums) {
        this.yums = yums;
    }

    public String getPorts() {
        return ports;
    }

    public void setPorts(String ports) {
        this.ports = ports;
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

    public Devstage getDevstagerefid() {
        return devstagerefid;
    }

    public void setDevstagerefid(Devstage devstagerefid) {
        this.devstagerefid = devstagerefid;
    }

    public Server getServerrefid() {
        return serverrefid;
    }

    public void setServerrefid(Server serverrefid) {
        this.serverrefid = serverrefid;
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
        if (!(object instanceof Software)) {
            return false;
        }
        Software other = (Software) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Software[ id=" + id + " ]";
    }
    
}
