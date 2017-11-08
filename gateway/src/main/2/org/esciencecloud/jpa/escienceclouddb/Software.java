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
@Table(name = "software")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Software.findAll", query = "SELECT s FROM Software s")
        , @NamedQuery(name = "Software.findById", query = "SELECT s FROM Software s WHERE s.id = :id")
        , @NamedQuery(name = "Software.findByDownloadurl", query = "SELECT s FROM Software s WHERE s.downloadurl = :downloadurl")
        , @NamedQuery(name = "Software.findBySoftwaretext", query = "SELECT s FROM Software s WHERE s.softwaretext = :softwaretext")
        , @NamedQuery(name = "Software.findByVersion", query = "SELECT s FROM Software s WHERE s.version = :version")
        , @NamedQuery(name = "Software.findByRpms", query = "SELECT s FROM Software s WHERE s.rpms = :rpms")
        , @NamedQuery(name = "Software.findByYums", query = "SELECT s FROM Software s WHERE s.yums = :yums")
        , @NamedQuery(name = "Software.findByPorts", query = "SELECT s FROM Software s WHERE s.ports = :ports")
        , @NamedQuery(name = "Software.findByLastmodified", query = "SELECT s FROM Software s WHERE s.lastmodified = :lastmodified")})
public class Software implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "downloadurl")
    private String downloadurl;
    @Column(name = "softwaretext")
    private String softwaretext;
    @Column(name = "version")
    private String version;
    @Column(name = "rpms")
    private String rpms;
    @Column(name = "yums")
    private String yums;
    @Column(name = "ports")
    private String ports;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
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

    public Software(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
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

    public String getSoftwaretext() {
        return softwaretext;
    }

    public void setSoftwaretext(String softwaretext) {
        this.softwaretext = softwaretext;
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

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
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
        return "org.escience.jpa.escienceclouddb.Software[ id=" + id + " ]";
    }

}
