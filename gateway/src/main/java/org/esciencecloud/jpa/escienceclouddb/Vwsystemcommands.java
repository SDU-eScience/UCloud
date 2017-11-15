/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * @author bjhj
 */
@Entity
@Table(name = "vwsystemcommands")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Vwsystemcommands.findAll", query = "SELECT v FROM Vwsystemcommands v")
        , @NamedQuery(name = "Vwsystemcommands.findByRecid", query = "SELECT v FROM Vwsystemcommands v WHERE v.recid = :recid")
        , @NamedQuery(name = "Vwsystemcommands.findBySubsystemid", query = "SELECT v FROM Vwsystemcommands v WHERE v.subsystemid = :subsystemid")
        , @NamedQuery(name = "Vwsystemcommands.findBySubsystemtext", query = "SELECT v FROM Vwsystemcommands v WHERE v.subsystemtext = :subsystemtext")
        , @NamedQuery(name = "Vwsystemcommands.findBySubsystemcommandcategoryid", query = "SELECT v FROM Vwsystemcommands v WHERE v.subsystemcommandcategoryid = :subsystemcommandcategoryid")
        , @NamedQuery(name = "Vwsystemcommands.findBySubsystemcommandcategorytext", query = "SELECT v FROM Vwsystemcommands v WHERE v.subsystemcommandcategorytext = :subsystemcommandcategorytext")
        , @NamedQuery(name = "Vwsystemcommands.findBySubsystemcommandid", query = "SELECT v FROM Vwsystemcommands v WHERE v.subsystemcommandid = :subsystemcommandid")
        , @NamedQuery(name = "Vwsystemcommands.findBySubsystemcommandtext", query = "SELECT v FROM Vwsystemcommands v WHERE v.subsystemcommandtext = :subsystemcommandtext")
        , @NamedQuery(name = "Vwsystemcommands.findByKafkatopicname", query = "SELECT v FROM Vwsystemcommands v WHERE v.kafkatopicname = :kafkatopicname")
        , @NamedQuery(name = "Vwsystemcommands.findBySessionid", query = "SELECT v FROM Vwsystemcommands v WHERE v.sessionid = :sessionid")})
public class Vwsystemcommands implements Serializable {

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
    @Column(name = "sessionid")
    private String sessionid;

    public Vwsystemcommands() {
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

    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

}
