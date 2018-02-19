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
@Table(name = "r_rule_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleMain.findAll", query = "SELECT r FROM RRuleMain r")
    , @NamedQuery(name = "RRuleMain.findByRuleId", query = "SELECT r FROM RRuleMain r WHERE r.ruleId = :ruleId")
    , @NamedQuery(name = "RRuleMain.findByRuleVersion", query = "SELECT r FROM RRuleMain r WHERE r.ruleVersion = :ruleVersion")
    , @NamedQuery(name = "RRuleMain.findByRuleBaseName", query = "SELECT r FROM RRuleMain r WHERE r.ruleBaseName = :ruleBaseName")
    , @NamedQuery(name = "RRuleMain.findByRuleName", query = "SELECT r FROM RRuleMain r WHERE r.ruleName = :ruleName")
    , @NamedQuery(name = "RRuleMain.findByRuleEvent", query = "SELECT r FROM RRuleMain r WHERE r.ruleEvent = :ruleEvent")
    , @NamedQuery(name = "RRuleMain.findByRuleCondition", query = "SELECT r FROM RRuleMain r WHERE r.ruleCondition = :ruleCondition")
    , @NamedQuery(name = "RRuleMain.findByRuleBody", query = "SELECT r FROM RRuleMain r WHERE r.ruleBody = :ruleBody")
    , @NamedQuery(name = "RRuleMain.findByRuleRecovery", query = "SELECT r FROM RRuleMain r WHERE r.ruleRecovery = :ruleRecovery")
    , @NamedQuery(name = "RRuleMain.findByRuleStatus", query = "SELECT r FROM RRuleMain r WHERE r.ruleStatus = :ruleStatus")
    , @NamedQuery(name = "RRuleMain.findByRuleOwnerName", query = "SELECT r FROM RRuleMain r WHERE r.ruleOwnerName = :ruleOwnerName")
    , @NamedQuery(name = "RRuleMain.findByRuleOwnerZone", query = "SELECT r FROM RRuleMain r WHERE r.ruleOwnerZone = :ruleOwnerZone")
    , @NamedQuery(name = "RRuleMain.findByRuleDescr1", query = "SELECT r FROM RRuleMain r WHERE r.ruleDescr1 = :ruleDescr1")
    , @NamedQuery(name = "RRuleMain.findByRuleDescr2", query = "SELECT r FROM RRuleMain r WHERE r.ruleDescr2 = :ruleDescr2")
    , @NamedQuery(name = "RRuleMain.findByInputParams", query = "SELECT r FROM RRuleMain r WHERE r.inputParams = :inputParams")
    , @NamedQuery(name = "RRuleMain.findByOutputParams", query = "SELECT r FROM RRuleMain r WHERE r.outputParams = :outputParams")
    , @NamedQuery(name = "RRuleMain.findByDollarVars", query = "SELECT r FROM RRuleMain r WHERE r.dollarVars = :dollarVars")
    , @NamedQuery(name = "RRuleMain.findByIcatElements", query = "SELECT r FROM RRuleMain r WHERE r.icatElements = :icatElements")
    , @NamedQuery(name = "RRuleMain.findBySideeffects", query = "SELECT r FROM RRuleMain r WHERE r.sideeffects = :sideeffects")
    , @NamedQuery(name = "RRuleMain.findByRComment", query = "SELECT r FROM RRuleMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRuleMain.findByCreateTs", query = "SELECT r FROM RRuleMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleMain.findByModifyTs", query = "SELECT r FROM RRuleMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleMain.findById", query = "SELECT r FROM RRuleMain r WHERE r.id = :id")})
public class RRuleMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "rule_id")
    private long ruleId;
    @Column(name = "rule_version")
    private String ruleVersion;
    @Basic(optional = false)
    @Column(name = "rule_base_name")
    private String ruleBaseName;
    @Basic(optional = false)
    @Column(name = "rule_name")
    private String ruleName;
    @Basic(optional = false)
    @Column(name = "rule_event")
    private String ruleEvent;
    @Column(name = "rule_condition")
    private String ruleCondition;
    @Basic(optional = false)
    @Column(name = "rule_body")
    private String ruleBody;
    @Basic(optional = false)
    @Column(name = "rule_recovery")
    private String ruleRecovery;
    @Column(name = "rule_status")
    private BigInteger ruleStatus;
    @Basic(optional = false)
    @Column(name = "rule_owner_name")
    private String ruleOwnerName;
    @Basic(optional = false)
    @Column(name = "rule_owner_zone")
    private String ruleOwnerZone;
    @Column(name = "rule_descr_1")
    private String ruleDescr1;
    @Column(name = "rule_descr_2")
    private String ruleDescr2;
    @Column(name = "input_params")
    private String inputParams;
    @Column(name = "output_params")
    private String outputParams;
    @Column(name = "dollar_vars")
    private String dollarVars;
    @Column(name = "icat_elements")
    private String icatElements;
    @Column(name = "sideeffects")
    private String sideeffects;
    @Column(name = "r_comment")
    private String rComment;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RRuleMain() {
    }

    public RRuleMain(Integer id) {
        this.id = id;
    }

    public RRuleMain(Integer id, long ruleId, String ruleBaseName, String ruleName, String ruleEvent, String ruleBody, String ruleRecovery, String ruleOwnerName, String ruleOwnerZone) {
        this.id = id;
        this.ruleId = ruleId;
        this.ruleBaseName = ruleBaseName;
        this.ruleName = ruleName;
        this.ruleEvent = ruleEvent;
        this.ruleBody = ruleBody;
        this.ruleRecovery = ruleRecovery;
        this.ruleOwnerName = ruleOwnerName;
        this.ruleOwnerZone = ruleOwnerZone;
    }

    public long getRuleId() {
        return ruleId;
    }

    public void setRuleId(long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getRuleBaseName() {
        return ruleBaseName;
    }

    public void setRuleBaseName(String ruleBaseName) {
        this.ruleBaseName = ruleBaseName;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleEvent() {
        return ruleEvent;
    }

    public void setRuleEvent(String ruleEvent) {
        this.ruleEvent = ruleEvent;
    }

    public String getRuleCondition() {
        return ruleCondition;
    }

    public void setRuleCondition(String ruleCondition) {
        this.ruleCondition = ruleCondition;
    }

    public String getRuleBody() {
        return ruleBody;
    }

    public void setRuleBody(String ruleBody) {
        this.ruleBody = ruleBody;
    }

    public String getRuleRecovery() {
        return ruleRecovery;
    }

    public void setRuleRecovery(String ruleRecovery) {
        this.ruleRecovery = ruleRecovery;
    }

    public BigInteger getRuleStatus() {
        return ruleStatus;
    }

    public void setRuleStatus(BigInteger ruleStatus) {
        this.ruleStatus = ruleStatus;
    }

    public String getRuleOwnerName() {
        return ruleOwnerName;
    }

    public void setRuleOwnerName(String ruleOwnerName) {
        this.ruleOwnerName = ruleOwnerName;
    }

    public String getRuleOwnerZone() {
        return ruleOwnerZone;
    }

    public void setRuleOwnerZone(String ruleOwnerZone) {
        this.ruleOwnerZone = ruleOwnerZone;
    }

    public String getRuleDescr1() {
        return ruleDescr1;
    }

    public void setRuleDescr1(String ruleDescr1) {
        this.ruleDescr1 = ruleDescr1;
    }

    public String getRuleDescr2() {
        return ruleDescr2;
    }

    public void setRuleDescr2(String ruleDescr2) {
        this.ruleDescr2 = ruleDescr2;
    }

    public String getInputParams() {
        return inputParams;
    }

    public void setInputParams(String inputParams) {
        this.inputParams = inputParams;
    }

    public String getOutputParams() {
        return outputParams;
    }

    public void setOutputParams(String outputParams) {
        this.outputParams = outputParams;
    }

    public String getDollarVars() {
        return dollarVars;
    }

    public void setDollarVars(String dollarVars) {
        this.dollarVars = dollarVars;
    }

    public String getIcatElements() {
        return icatElements;
    }

    public void setIcatElements(String icatElements) {
        this.icatElements = icatElements;
    }

    public String getSideeffects() {
        return sideeffects;
    }

    public void setSideeffects(String sideeffects) {
        this.sideeffects = sideeffects;
    }

    public String getRComment() {
        return rComment;
    }

    public void setRComment(String rComment) {
        this.rComment = rComment;
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
        if (!(object instanceof RRuleMain)) {
            return false;
        }
        RRuleMain other = (RRuleMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleMain[ id=" + id + " ]";
    }
    
}
