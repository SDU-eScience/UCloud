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
@Table(name = "r_user_password")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RUserPassword.findAll", query = "SELECT r FROM RUserPassword r")
    , @NamedQuery(name = "RUserPassword.findByUserId", query = "SELECT r FROM RUserPassword r WHERE r.userId = :userId")
    , @NamedQuery(name = "RUserPassword.findByRcatPassword", query = "SELECT r FROM RUserPassword r WHERE r.rcatPassword = :rcatPassword")
    , @NamedQuery(name = "RUserPassword.findByPassExpiryTs", query = "SELECT r FROM RUserPassword r WHERE r.passExpiryTs = :passExpiryTs")
    , @NamedQuery(name = "RUserPassword.findByCreateTs", query = "SELECT r FROM RUserPassword r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RUserPassword.findByModifyTs", query = "SELECT r FROM RUserPassword r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RUserPassword.findById", query = "SELECT r FROM RUserPassword r WHERE r.id = :id")})
public class RUserPassword implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Basic(optional = false)
    @Column(name = "rcat_password")
    private String rcatPassword;
    @Basic(optional = false)
    @Column(name = "pass_expiry_ts")
    private String passExpiryTs;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RUserPassword() {
    }

    public RUserPassword(Integer id) {
        this.id = id;
    }

    public RUserPassword(Integer id, long userId, String rcatPassword, String passExpiryTs) {
        this.id = id;
        this.userId = userId;
        this.rcatPassword = rcatPassword;
        this.passExpiryTs = passExpiryTs;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getRcatPassword() {
        return rcatPassword;
    }

    public void setRcatPassword(String rcatPassword) {
        this.rcatPassword = rcatPassword;
    }

    public String getPassExpiryTs() {
        return passExpiryTs;
    }

    public void setPassExpiryTs(String passExpiryTs) {
        this.passExpiryTs = passExpiryTs;
    }

    public String getCreateTs() {
        return createTs;
    }

    public void setCreateTs(String createTs) {
        this.createTs = createTs;
    }

    public String getModifyTs() {
        return modifyTs;
    }

    public void setModifyTs(String modifyTs) {
        this.modifyTs = modifyTs;
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
        if (!(object instanceof RUserPassword)) {
            return false;
        }
        RUserPassword other = (RUserPassword) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RUserPassword[ id=" + id + " ]";
    }
    
}
