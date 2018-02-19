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
@Table(name = "r_specific_query")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RSpecificQuery.findAll", query = "SELECT r FROM RSpecificQuery r")
    , @NamedQuery(name = "RSpecificQuery.findByAlias", query = "SELECT r FROM RSpecificQuery r WHERE r.alias = :alias")
    , @NamedQuery(name = "RSpecificQuery.findBySqlstr", query = "SELECT r FROM RSpecificQuery r WHERE r.sqlstr = :sqlstr")
    , @NamedQuery(name = "RSpecificQuery.findByCreateTs", query = "SELECT r FROM RSpecificQuery r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RSpecificQuery.findById", query = "SELECT r FROM RSpecificQuery r WHERE r.id = :id")})
public class RSpecificQuery implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "alias")
    private String alias;
    @Column(name = "sqlstr")
    private String sqlstr;
    @Column(name = "create_ts")
    private String createTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RSpecificQuery() {
    }

    public RSpecificQuery(Integer id) {
        this.id = id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getSqlstr() {
        return sqlstr;
    }

    public void setSqlstr(String sqlstr) {
        this.sqlstr = sqlstr;
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
        if (!(object instanceof RSpecificQuery)) {
            return false;
        }
        RSpecificQuery other = (RSpecificQuery) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RSpecificQuery[ id=" + id + " ]";
    }
    
}
