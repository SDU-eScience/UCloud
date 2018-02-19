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
@Table(name = "r_ticket_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RTicketMain.findAll", query = "SELECT r FROM RTicketMain r")
    , @NamedQuery(name = "RTicketMain.findByTicketId", query = "SELECT r FROM RTicketMain r WHERE r.ticketId = :ticketId")
    , @NamedQuery(name = "RTicketMain.findByTicketString", query = "SELECT r FROM RTicketMain r WHERE r.ticketString = :ticketString")
    , @NamedQuery(name = "RTicketMain.findByTicketType", query = "SELECT r FROM RTicketMain r WHERE r.ticketType = :ticketType")
    , @NamedQuery(name = "RTicketMain.findByUserId", query = "SELECT r FROM RTicketMain r WHERE r.userId = :userId")
    , @NamedQuery(name = "RTicketMain.findByObjectId", query = "SELECT r FROM RTicketMain r WHERE r.objectId = :objectId")
    , @NamedQuery(name = "RTicketMain.findByObjectType", query = "SELECT r FROM RTicketMain r WHERE r.objectType = :objectType")
    , @NamedQuery(name = "RTicketMain.findByUsesLimit", query = "SELECT r FROM RTicketMain r WHERE r.usesLimit = :usesLimit")
    , @NamedQuery(name = "RTicketMain.findByUsesCount", query = "SELECT r FROM RTicketMain r WHERE r.usesCount = :usesCount")
    , @NamedQuery(name = "RTicketMain.findByWriteFileLimit", query = "SELECT r FROM RTicketMain r WHERE r.writeFileLimit = :writeFileLimit")
    , @NamedQuery(name = "RTicketMain.findByWriteFileCount", query = "SELECT r FROM RTicketMain r WHERE r.writeFileCount = :writeFileCount")
    , @NamedQuery(name = "RTicketMain.findByWriteByteLimit", query = "SELECT r FROM RTicketMain r WHERE r.writeByteLimit = :writeByteLimit")
    , @NamedQuery(name = "RTicketMain.findByWriteByteCount", query = "SELECT r FROM RTicketMain r WHERE r.writeByteCount = :writeByteCount")
    , @NamedQuery(name = "RTicketMain.findByTicketExpiryTs", query = "SELECT r FROM RTicketMain r WHERE r.ticketExpiryTs = :ticketExpiryTs")
    , @NamedQuery(name = "RTicketMain.findByRestrictions", query = "SELECT r FROM RTicketMain r WHERE r.restrictions = :restrictions")
    , @NamedQuery(name = "RTicketMain.findByCreateTs", query = "SELECT r FROM RTicketMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RTicketMain.findByModifyTs", query = "SELECT r FROM RTicketMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RTicketMain.findById", query = "SELECT r FROM RTicketMain r WHERE r.id = :id")})
public class RTicketMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "ticket_id")
    private long ticketId;
    @Column(name = "ticket_string")
    private String ticketString;
    @Column(name = "ticket_type")
    private String ticketType;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Basic(optional = false)
    @Column(name = "object_id")
    private long objectId;
    @Column(name = "object_type")
    private String objectType;
    @Column(name = "uses_limit")
    private Integer usesLimit;
    @Column(name = "uses_count")
    private Integer usesCount;
    @Column(name = "write_file_limit")
    private Integer writeFileLimit;
    @Column(name = "write_file_count")
    private Integer writeFileCount;
    @Column(name = "write_byte_limit")
    private Integer writeByteLimit;
    @Column(name = "write_byte_count")
    private Integer writeByteCount;
    @Column(name = "ticket_expiry_ts")
    private String ticketExpiryTs;
    @Column(name = "restrictions")
    private String restrictions;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RTicketMain() {
    }

    public RTicketMain(Integer id) {
        this.id = id;
    }

    public RTicketMain(Integer id, long ticketId, long userId, long objectId) {
        this.id = id;
        this.ticketId = ticketId;
        this.userId = userId;
        this.objectId = objectId;
    }

    public long getTicketId() {
        return ticketId;
    }

    public void setTicketId(long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketString() {
        return ticketString;
    }

    public void setTicketString(String ticketString) {
        this.ticketString = ticketString;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Integer getUsesLimit() {
        return usesLimit;
    }

    public void setUsesLimit(Integer usesLimit) {
        this.usesLimit = usesLimit;
    }

    public Integer getUsesCount() {
        return usesCount;
    }

    public void setUsesCount(Integer usesCount) {
        this.usesCount = usesCount;
    }

    public Integer getWriteFileLimit() {
        return writeFileLimit;
    }

    public void setWriteFileLimit(Integer writeFileLimit) {
        this.writeFileLimit = writeFileLimit;
    }

    public Integer getWriteFileCount() {
        return writeFileCount;
    }

    public void setWriteFileCount(Integer writeFileCount) {
        this.writeFileCount = writeFileCount;
    }

    public Integer getWriteByteLimit() {
        return writeByteLimit;
    }

    public void setWriteByteLimit(Integer writeByteLimit) {
        this.writeByteLimit = writeByteLimit;
    }

    public Integer getWriteByteCount() {
        return writeByteCount;
    }

    public void setWriteByteCount(Integer writeByteCount) {
        this.writeByteCount = writeByteCount;
    }

    public String getTicketExpiryTs() {
        return ticketExpiryTs;
    }

    public void setTicketExpiryTs(String ticketExpiryTs) {
        this.ticketExpiryTs = ticketExpiryTs;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(String restrictions) {
        this.restrictions = restrictions;
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
        if (!(object instanceof RTicketMain)) {
            return false;
        }
        RTicketMain other = (RTicketMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RTicketMain[ id=" + id + " ]";
    }
    
}
