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
@Table(name = "login_type")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "LoginType.findAll", query = "SELECT l FROM LoginType l")
    , @NamedQuery(name = "LoginType.findById", query = "SELECT l FROM LoginType l WHERE l.id = :id")
    , @NamedQuery(name = "LoginType.findByLogintypename", query = "SELECT l FROM LoginType l WHERE l.logintypename = :logintypename")
    , @NamedQuery(name = "LoginType.findByActive", query = "SELECT l FROM LoginType l WHERE l.active = :active")
    , @NamedQuery(name = "LoginType.findByMarkedfordelete", query = "SELECT l FROM LoginType l WHERE l.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "LoginType.findByModifiedTs", query = "SELECT l FROM LoginType l WHERE l.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "LoginType.findByCreatedTs", query = "SELECT l FROM LoginType l WHERE l.createdTs = :createdTs")})
public class LoginType implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "logintypename")
    private String logintypename;
    @Column(name = "active")
    private Integer active;
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

    public LoginType() {
    }

    public LoginType(Integer id) {
        this.id = id;
    }

    public LoginType(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getLogintypename() {
        return logintypename;
    }

    public void setLogintypename(String logintypename) {
        this.logintypename = logintypename;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof LoginType)) {
            return false;
        }
        LoginType other = (LoginType) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.LoginType[ id=" + id + " ]";
    }
    
}
