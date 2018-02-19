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
@Table(name = "r_server_load_digest")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RServerLoadDigest.findAll", query = "SELECT r FROM RServerLoadDigest r")
    , @NamedQuery(name = "RServerLoadDigest.findByRescName", query = "SELECT r FROM RServerLoadDigest r WHERE r.rescName = :rescName")
    , @NamedQuery(name = "RServerLoadDigest.findByLoadFactor", query = "SELECT r FROM RServerLoadDigest r WHERE r.loadFactor = :loadFactor")
    , @NamedQuery(name = "RServerLoadDigest.findByCreateTs", query = "SELECT r FROM RServerLoadDigest r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RServerLoadDigest.findById", query = "SELECT r FROM RServerLoadDigest r WHERE r.id = :id")})
public class RServerLoadDigest implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "resc_name")
    private String rescName;
    @Column(name = "load_factor")
    private Integer loadFactor;
    @Column(name = "create_ts")
    private String createTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RServerLoadDigest() {
    }

    public RServerLoadDigest(Integer id) {
        this.id = id;
    }

    public RServerLoadDigest(Integer id, String rescName) {
        this.id = id;
        this.rescName = rescName;
    }

    public String getRescName() {
        return rescName;
    }

    public void setRescName(String rescName) {
        this.rescName = rescName;
    }

    public Integer getLoadFactor() {
        return loadFactor;
    }

    public void setLoadFactor(Integer loadFactor) {
        this.loadFactor = loadFactor;
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
        if (!(object instanceof RServerLoadDigest)) {
            return false;
        }
        RServerLoadDigest other = (RServerLoadDigest) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RServerLoadDigest[ id=" + id + " ]";
    }
    
}
