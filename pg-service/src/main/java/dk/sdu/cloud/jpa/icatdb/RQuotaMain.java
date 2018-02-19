/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import java.math.BigInteger;
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
@Table(name = "r_quota_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RQuotaMain.findAll", query = "SELECT r FROM RQuotaMain r")
    , @NamedQuery(name = "RQuotaMain.findByUserId", query = "SELECT r FROM RQuotaMain r WHERE r.userId = :userId")
    , @NamedQuery(name = "RQuotaMain.findByRescId", query = "SELECT r FROM RQuotaMain r WHERE r.rescId = :rescId")
    , @NamedQuery(name = "RQuotaMain.findByQuotaLimit", query = "SELECT r FROM RQuotaMain r WHERE r.quotaLimit = :quotaLimit")
    , @NamedQuery(name = "RQuotaMain.findByQuotaOver", query = "SELECT r FROM RQuotaMain r WHERE r.quotaOver = :quotaOver")
    , @NamedQuery(name = "RQuotaMain.findByModifyTs", query = "SELECT r FROM RQuotaMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RQuotaMain.findById", query = "SELECT r FROM RQuotaMain r WHERE r.id = :id")})
public class RQuotaMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "user_id")
    private BigInteger userId;
    @Column(name = "resc_id")
    private BigInteger rescId;
    @Column(name = "quota_limit")
    private BigInteger quotaLimit;
    @Column(name = "quota_over")
    private BigInteger quotaOver;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RQuotaMain() {
    }

    public RQuotaMain(Integer id) {
        this.id = id;
    }

    public BigInteger getUserId() {
        return userId;
    }

    public void setUserId(BigInteger userId) {
        this.userId = userId;
    }

    public BigInteger getRescId() {
        return rescId;
    }

    public void setRescId(BigInteger rescId) {
        this.rescId = rescId;
    }

    public BigInteger getQuotaLimit() {
        return quotaLimit;
    }

    public void setQuotaLimit(BigInteger quotaLimit) {
        this.quotaLimit = quotaLimit;
    }

    public BigInteger getQuotaOver() {
        return quotaOver;
    }

    public void setQuotaOver(BigInteger quotaOver) {
        this.quotaOver = quotaOver;
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
        if (!(object instanceof RQuotaMain)) {
            return false;
        }
        RQuotaMain other = (RQuotaMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RQuotaMain[ id=" + id + " ]";
    }
    
}
