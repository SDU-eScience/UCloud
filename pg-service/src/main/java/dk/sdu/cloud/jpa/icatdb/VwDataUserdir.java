/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "vw_data_userdir")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "VwDataUserdir.findAll", query = "SELECT v FROM VwDataUserdir v")
    , @NamedQuery(name = "VwDataUserdir.findByCollName", query = "SELECT v FROM VwDataUserdir v WHERE v.collName = :collName")
    , @NamedQuery(name = "VwDataUserdir.findByUserName", query = "SELECT v FROM VwDataUserdir v WHERE v.userName = :userName")})
public class VwDataUserdir implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "coll_name")
    private String collName;
    @Column(name = "user_name")
    private String userName;

    public VwDataUserdir() {
    }

    public String getCollName() {
        return collName;
    }

    public void setCollName(String collName) {
        this.collName = collName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
    
}
