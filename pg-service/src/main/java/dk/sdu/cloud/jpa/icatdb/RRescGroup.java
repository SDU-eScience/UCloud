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
@Table(name = "r_resc_group")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRescGroup.findAll", query = "SELECT r FROM RRescGroup r")
    , @NamedQuery(name = "RRescGroup.findByRescGroupId", query = "SELECT r FROM RRescGroup r WHERE r.rescGroupId = :rescGroupId")
    , @NamedQuery(name = "RRescGroup.findByRescGroupName", query = "SELECT r FROM RRescGroup r WHERE r.rescGroupName = :rescGroupName")
    , @NamedQuery(name = "RRescGroup.findByRescId", query = "SELECT r FROM RRescGroup r WHERE r.rescId = :rescId")
    , @NamedQuery(name = "RRescGroup.findByCreateTs", query = "SELECT r FROM RRescGroup r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRescGroup.findByModifyTs", query = "SELECT r FROM RRescGroup r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRescGroup.findById", query = "SELECT r FROM RRescGroup r WHERE r.id = :id")})
public class RRescGroup implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "resc_group_id")
    private long rescGroupId;
    @Basic(optional = false)
    @Column(name = "resc_group_name")
    private String rescGroupName;
    @Basic(optional = false)
    @Column(name = "resc_id")
    private long rescId;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RRescGroup() {
    }

    public RRescGroup(Integer id) {
        this.id = id;
    }

    public RRescGroup(Integer id, long rescGroupId, String rescGroupName, long rescId) {
        this.id = id;
        this.rescGroupId = rescGroupId;
        this.rescGroupName = rescGroupName;
        this.rescId = rescId;
    }

    public long getRescGroupId() {
        return rescGroupId;
    }

    public void setRescGroupId(long rescGroupId) {
        this.rescGroupId = rescGroupId;
    }

    public String getRescGroupName() {
        return rescGroupName;
    }

    public void setRescGroupName(String rescGroupName) {
        this.rescGroupName = rescGroupName;
    }

    public long getRescId() {
        return rescId;
    }

    public void setRescId(long rescId) {
        this.rescId = rescId;
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
        if (!(object instanceof RRescGroup)) {
            return false;
        }
        RRescGroup other = (RRescGroup) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRescGroup[ id=" + id + " ]";
    }
    
}
