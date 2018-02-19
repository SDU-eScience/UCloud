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
@Table(name = "r_grid_configuration")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RGridConfiguration.findAll", query = "SELECT r FROM RGridConfiguration r")
    , @NamedQuery(name = "RGridConfiguration.findByNamespace", query = "SELECT r FROM RGridConfiguration r WHERE r.namespace = :namespace")
    , @NamedQuery(name = "RGridConfiguration.findByOptionName", query = "SELECT r FROM RGridConfiguration r WHERE r.optionName = :optionName")
    , @NamedQuery(name = "RGridConfiguration.findByOptionValue", query = "SELECT r FROM RGridConfiguration r WHERE r.optionValue = :optionValue")
    , @NamedQuery(name = "RGridConfiguration.findById", query = "SELECT r FROM RGridConfiguration r WHERE r.id = :id")})
public class RGridConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "namespace")
    private String namespace;
    @Column(name = "option_name")
    private String optionName;
    @Column(name = "option_value")
    private String optionValue;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RGridConfiguration() {
    }

    public RGridConfiguration(Integer id) {
        this.id = id;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getOptionName() {
        return optionName;
    }

    public void setOptionName(String optionName) {
        this.optionName = optionName;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
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
        if (!(object instanceof RGridConfiguration)) {
            return false;
        }
        RGridConfiguration other = (RGridConfiguration) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RGridConfiguration[ id=" + id + " ]";
    }
    
}
