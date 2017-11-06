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
@Table(name = "irodsauditpep")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Irodsauditpep.findAll", query = "SELECT i FROM Irodsauditpep i")
        , @NamedQuery(name = "Irodsauditpep.findById", query = "SELECT i FROM Irodsauditpep i WHERE i.id = :id")
        , @NamedQuery(name = "Irodsauditpep.findByType", query = "SELECT i FROM Irodsauditpep i WHERE i.type = :type")
        , @NamedQuery(name = "Irodsauditpep.findByParm", query = "SELECT i FROM Irodsauditpep i WHERE i.parm = :parm")
        , @NamedQuery(name = "Irodsauditpep.findByLastmodified", query = "SELECT i FROM Irodsauditpep i WHERE i.lastmodified = :lastmodified")
        , @NamedQuery(name = "Irodsauditpep.findByPhase", query = "SELECT i FROM Irodsauditpep i WHERE i.phase = :phase")
        , @NamedQuery(name = "Irodsauditpep.findByActive", query = "SELECT i FROM Irodsauditpep i WHERE i.active = :active")})
public class Irodsauditpep implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "type")
    private String type;
    @Column(name = "parm")
    private String parm;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "phase")
    private String phase;
    @Column(name = "active")
    private Integer active;

    public Irodsauditpep() {
    }

    public Irodsauditpep(Integer id) {
        this.id = id;
    }

    public Irodsauditpep(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getParm() {
        return parm;
    }

    public void setParm(String parm) {
        this.parm = parm;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
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
        if (!(object instanceof Irodsauditpep)) {
            return false;
        }
        Irodsauditpep other = (Irodsauditpep) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Irodsauditpep[ id=" + id + " ]";
    }

}
