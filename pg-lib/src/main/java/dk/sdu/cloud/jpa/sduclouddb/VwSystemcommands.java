/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.math.BigInteger;
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
@Table(name = "vw_systemcommands")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "VwSystemcommands.findAll", query = "SELECT v FROM VwSystemcommands v")
    , @NamedQuery(name = "VwSystemcommands.findByRecid", query = "SELECT v FROM VwSystemcommands v WHERE v.recid = :recid")
    , @NamedQuery(name = "VwSystemcommands.findBySubsystemid", query = "SELECT v FROM VwSystemcommands v WHERE v.subsystemid = :subsystemid")
    , @NamedQuery(name = "VwSystemcommands.findBySubsystemtext", query = "SELECT v FROM VwSystemcommands v WHERE v.subsystemtext = :subsystemtext")
    , @NamedQuery(name = "VwSystemcommands.findBySubsystemcommandcategoryid", query = "SELECT v FROM VwSystemcommands v WHERE v.subsystemcommandcategoryid = :subsystemcommandcategoryid")
    , @NamedQuery(name = "VwSystemcommands.findBySubsystemcommandcategorytext", query = "SELECT v FROM VwSystemcommands v WHERE v.subsystemcommandcategorytext = :subsystemcommandcategorytext")
    , @NamedQuery(name = "VwSystemcommands.findBySubsystemcommandid", query = "SELECT v FROM VwSystemcommands v WHERE v.subsystemcommandid = :subsystemcommandid")
    , @NamedQuery(name = "VwSystemcommands.findBySubsystemcommandtext", query = "SELECT v FROM VwSystemcommands v WHERE v.subsystemcommandtext = :subsystemcommandtext")
    , @NamedQuery(name = "VwSystemcommands.findByKafkatopicname", query = "SELECT v FROM VwSystemcommands v WHERE v.kafkatopicname = :kafkatopicname")
    , @NamedQuery(name = "VwSystemcommands.findByJwt", query = "SELECT v FROM VwSystemcommands v WHERE v.jwt = :jwt")})
public class VwSystemcommands implements Serializable {
    @Id
    private static final long serialVersionUID = 1L;
    @Column(name = "recid")
    private BigInteger recid;
    @Column(name = "subsystemid")
    private Integer subsystemid;
    @Column(name = "subsystemtext")
    private String subsystemtext;
    @Column(name = "subsystemcommandcategoryid")
    private Integer subsystemcommandcategoryid;
    @Column(name = "subsystemcommandcategorytext")
    private String subsystemcommandcategorytext;
    @Column(name = "subsystemcommandid")
    private Integer subsystemcommandid;
    @Column(name = "subsystemcommandtext")
    private String subsystemcommandtext;
    @Column(name = "kafkatopicname")
    private String kafkatopicname;
    @Column(name = "jwt")
    private String jwt;

    public VwSystemcommands() {
    }

    public BigInteger getRecid() {
        return recid;
    }

    public void setRecid(BigInteger recid) {
        this.recid = recid;
    }

    public Integer getSubsystemid() {
        return subsystemid;
    }

    public void setSubsystemid(Integer subsystemid) {
        this.subsystemid = subsystemid;
    }

    public String getSubsystemtext() {
        return subsystemtext;
    }

    public void setSubsystemtext(String subsystemtext) {
        this.subsystemtext = subsystemtext;
    }

    public Integer getSubsystemcommandcategoryid() {
        return subsystemcommandcategoryid;
    }

    public void setSubsystemcommandcategoryid(Integer subsystemcommandcategoryid) {
        this.subsystemcommandcategoryid = subsystemcommandcategoryid;
    }

    public String getSubsystemcommandcategorytext() {
        return subsystemcommandcategorytext;
    }

    public void setSubsystemcommandcategorytext(String subsystemcommandcategorytext) {
        this.subsystemcommandcategorytext = subsystemcommandcategorytext;
    }

    public Integer getSubsystemcommandid() {
        return subsystemcommandid;
    }

    public void setSubsystemcommandid(Integer subsystemcommandid) {
        this.subsystemcommandid = subsystemcommandid;
    }

    public String getSubsystemcommandtext() {
        return subsystemcommandtext;
    }

    public void setSubsystemcommandtext(String subsystemcommandtext) {
        this.subsystemcommandtext = subsystemcommandtext;
    }

    public String getKafkatopicname() {
        return kafkatopicname;
    }

    public void setKafkatopicname(String kafkatopicname) {
        this.kafkatopicname = kafkatopicname;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }
    
}
