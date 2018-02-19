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
@Table(name = "r_user_auth")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RUserAuth.findAll", query = "SELECT r FROM RUserAuth r")
    , @NamedQuery(name = "RUserAuth.findByUserId", query = "SELECT r FROM RUserAuth r WHERE r.userId = :userId")
    , @NamedQuery(name = "RUserAuth.findByUserAuthName", query = "SELECT r FROM RUserAuth r WHERE r.userAuthName = :userAuthName")
    , @NamedQuery(name = "RUserAuth.findByCreateTs", query = "SELECT r FROM RUserAuth r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RUserAuth.findById", query = "SELECT r FROM RUserAuth r WHERE r.id = :id")})
public class RUserAuth implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Column(name = "user_auth_name")
    private String userAuthName;
    @Column(name = "create_ts")
    private String createTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RUserAuth() {
    }

    public RUserAuth(Integer id) {
        this.id = id;
    }

    public RUserAuth(Integer id, long userId) {
        this.id = id;
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUserAuthName() {
        return userAuthName;
    }

    public void setUserAuthName(String userAuthName) {
        this.userAuthName = userAuthName;
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
        if (!(object instanceof RUserAuth)) {
            return false;
        }
        RUserAuth other = (RUserAuth) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RUserAuth[ id=" + id + " ]";
    }
    
}
