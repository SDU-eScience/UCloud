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
@Table(name = "r_rule_exec")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleExec.findAll", query = "SELECT r FROM RRuleExec r")
    , @NamedQuery(name = "RRuleExec.findByRuleExecId", query = "SELECT r FROM RRuleExec r WHERE r.ruleExecId = :ruleExecId")
    , @NamedQuery(name = "RRuleExec.findByRuleName", query = "SELECT r FROM RRuleExec r WHERE r.ruleName = :ruleName")
    , @NamedQuery(name = "RRuleExec.findByReiFilePath", query = "SELECT r FROM RRuleExec r WHERE r.reiFilePath = :reiFilePath")
    , @NamedQuery(name = "RRuleExec.findByUserName", query = "SELECT r FROM RRuleExec r WHERE r.userName = :userName")
    , @NamedQuery(name = "RRuleExec.findByExeAddress", query = "SELECT r FROM RRuleExec r WHERE r.exeAddress = :exeAddress")
    , @NamedQuery(name = "RRuleExec.findByExeTime", query = "SELECT r FROM RRuleExec r WHERE r.exeTime = :exeTime")
    , @NamedQuery(name = "RRuleExec.findByExeFrequency", query = "SELECT r FROM RRuleExec r WHERE r.exeFrequency = :exeFrequency")
    , @NamedQuery(name = "RRuleExec.findByPriority", query = "SELECT r FROM RRuleExec r WHERE r.priority = :priority")
    , @NamedQuery(name = "RRuleExec.findByEstimatedExeTime", query = "SELECT r FROM RRuleExec r WHERE r.estimatedExeTime = :estimatedExeTime")
    , @NamedQuery(name = "RRuleExec.findByNotificationAddr", query = "SELECT r FROM RRuleExec r WHERE r.notificationAddr = :notificationAddr")
    , @NamedQuery(name = "RRuleExec.findByLastExeTime", query = "SELECT r FROM RRuleExec r WHERE r.lastExeTime = :lastExeTime")
    , @NamedQuery(name = "RRuleExec.findByExeStatus", query = "SELECT r FROM RRuleExec r WHERE r.exeStatus = :exeStatus")
    , @NamedQuery(name = "RRuleExec.findByCreateTs", query = "SELECT r FROM RRuleExec r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleExec.findByModifyTs", query = "SELECT r FROM RRuleExec r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleExec.findById", query = "SELECT r FROM RRuleExec r WHERE r.id = :id")})
public class RRuleExec implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "rule_exec_id")
    private long ruleExecId;
    @Basic(optional = false)
    @Column(name = "rule_name")
    private String ruleName;
    @Column(name = "rei_file_path")
    private String reiFilePath;
    @Column(name = "user_name")
    private String userName;
    @Column(name = "exe_address")
    private String exeAddress;
    @Column(name = "exe_time")
    private String exeTime;
    @Column(name = "exe_frequency")
    private String exeFrequency;
    @Column(name = "priority")
    private String priority;
    @Column(name = "estimated_exe_time")
    private String estimatedExeTime;
    @Column(name = "notification_addr")
    private String notificationAddr;
    @Column(name = "last_exe_time")
    private String lastExeTime;
    @Column(name = "exe_status")
    private String exeStatus;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RRuleExec() {
    }

    public RRuleExec(Integer id) {
        this.id = id;
    }

    public RRuleExec(Integer id, long ruleExecId, String ruleName) {
        this.id = id;
        this.ruleExecId = ruleExecId;
        this.ruleName = ruleName;
    }

    public long getRuleExecId() {
        return ruleExecId;
    }

    public void setRuleExecId(long ruleExecId) {
        this.ruleExecId = ruleExecId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getReiFilePath() {
        return reiFilePath;
    }

    public void setReiFilePath(String reiFilePath) {
        this.reiFilePath = reiFilePath;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getExeAddress() {
        return exeAddress;
    }

    public void setExeAddress(String exeAddress) {
        this.exeAddress = exeAddress;
    }

    public String getExeTime() {
        return exeTime;
    }

    public void setExeTime(String exeTime) {
        this.exeTime = exeTime;
    }

    public String getExeFrequency() {
        return exeFrequency;
    }

    public void setExeFrequency(String exeFrequency) {
        this.exeFrequency = exeFrequency;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getEstimatedExeTime() {
        return estimatedExeTime;
    }

    public void setEstimatedExeTime(String estimatedExeTime) {
        this.estimatedExeTime = estimatedExeTime;
    }

    public String getNotificationAddr() {
        return notificationAddr;
    }

    public void setNotificationAddr(String notificationAddr) {
        this.notificationAddr = notificationAddr;
    }

    public String getLastExeTime() {
        return lastExeTime;
    }

    public void setLastExeTime(String lastExeTime) {
        this.lastExeTime = lastExeTime;
    }

    public String getExeStatus() {
        return exeStatus;
    }

    public void setExeStatus(String exeStatus) {
        this.exeStatus = exeStatus;
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
        if (!(object instanceof RRuleExec)) {
            return false;
        }
        RRuleExec other = (RRuleExec) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleExec[ id=" + id + " ]";
    }
    
}
