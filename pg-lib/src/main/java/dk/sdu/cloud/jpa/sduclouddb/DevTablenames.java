/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "dev_tablenames")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DevTablenames.findAll", query = "SELECT d FROM DevTablenames d")
    , @NamedQuery(name = "DevTablenames.findByTableName", query = "SELECT d FROM DevTablenames d WHERE d.tableName = :tableName")})
public class DevTablenames implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "table_name")
    private String tableName;

    public DevTablenames() {
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
}
