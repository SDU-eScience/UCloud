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
@Table(name = "subsystem")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Subsystem.findAll", query = "SELECT s FROM Subsystem s")
        , @NamedQuery(name = "Subsystem.findById", query = "SELECT s FROM Subsystem s WHERE s.id = :id")
        , @NamedQuery(name = "Subsystem.findBySubsystemtext", query = "SELECT s FROM Subsystem s WHERE s.subsystemtext = :subsystemtext")
        , @NamedQuery(name = "Subsystem.findByHealth", query = "SELECT s FROM Subsystem s WHERE s.health = :health")
        , @NamedQuery(name = "Subsystem.findByIpDev", query = "SELECT s FROM Subsystem s WHERE s.ipDev = :ipDev")
        , @NamedQuery(name = "Subsystem.findByPortDev", query = "SELECT s FROM Subsystem s WHERE s.portDev = :portDev")
        , @NamedQuery(name = "Subsystem.findByIpTest", query = "SELECT s FROM Subsystem s WHERE s.ipTest = :ipTest")
        , @NamedQuery(name = "Subsystem.findByPortTest", query = "SELECT s FROM Subsystem s WHERE s.portTest = :portTest")
        , @NamedQuery(name = "Subsystem.findByIpProd", query = "SELECT s FROM Subsystem s WHERE s.ipProd = :ipProd")
        , @NamedQuery(name = "Subsystem.findByPortProd", query = "SELECT s FROM Subsystem s WHERE s.portProd = :portProd")
        , @NamedQuery(name = "Subsystem.findByLastmodified", query = "SELECT s FROM Subsystem s WHERE s.lastmodified = :lastmodified")})
public class Subsystem implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "subsystemtext")
    private String subsystemtext;
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
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "subsystemrefid")
    private Collection<Subsystemcommand> subsystemcommandCollection;

    public Subsystem() {
    }

    public Subsystem(Integer id) {
        this.id = id;
    }

    public Subsystem(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSubsystemtext() {
        return subsystemtext;
    }

    public void setSubsystemtext(String subsystemtext) {
        this.subsystemtext = subsystemtext;
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

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    @XmlTransient
    public Collection<Subsystemcommand> getSubsystemcommandCollection() {
        return subsystemcommandCollection;
    }

    public void setSubsystemcommandCollection(Collection<Subsystemcommand> subsystemcommandCollection) {
        this.subsystemcommandCollection = subsystemcommandCollection;
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
        return "org.escience.jpa.escienceclouddb.Subsystem[ id=" + id + " ]";
    }

}
