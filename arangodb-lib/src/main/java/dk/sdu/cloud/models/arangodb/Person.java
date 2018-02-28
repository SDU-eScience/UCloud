/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.models.arangodb;

import com.arangodb.entity.DocumentField;

import java.time.LocalDateTime;

/**
 *
 * @author bjhj
 */

public class Person  {

    @DocumentField(DocumentField.Type.KEY)
    private  String key;
    @DocumentField(DocumentField.Type.ID)
    private int id;
    private Integer person_id;
    private String person_title;
    private String person_first_name;
    private String person_middle_name;
    private String person_lastname;
    private String person_phoneno;
    private String person_username;
    private String orcid;
    private String person_fullname;
    private java.time.LocalDateTime  modified_datetime;
    private java.time.LocalDateTime  created_datetime;

    public Person() {
        super();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getPerson_id() {
        return person_id;
    }

    public void setPerson_id(Integer person_id) {
        this.person_id = person_id;
    }

    public String getPerson_title() {
        return person_title;
    }

    public void setPerson_title(String person_title) {
        this.person_title = person_title;
    }

    public String getPerson_first_name() {
        return person_first_name;
    }

    public void setPerson_first_name(String person_first_name) {
        this.person_first_name = person_first_name;
    }

    public String getPerson_middle_name() {
        return person_middle_name;
    }

    public void setPerson_middle_name(String person_middle_name) {
        this.person_middle_name = person_middle_name;
    }

    public String getPerson_lastname() {
        return person_lastname;
    }

    public void setPerson_lastname(String person_lastname) {
        this.person_lastname = person_lastname;
    }

    public String getPerson_phoneno() {
        return person_phoneno;
    }

    public void setPerson_phoneno(String person_phoneno) {
        this.person_phoneno = person_phoneno;
    }

    public String getPerson_username() {
        return person_username;
    }

    public void setPerson_username(String person_username) {
        this.person_username = person_username;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public String getPerson_fullname() {
        return person_fullname;
    }

    public void setPerson_fullname(String person_fullname) {
        this.person_fullname = person_fullname;
    }

    public LocalDateTime getModified_datetime() {
        return modified_datetime;
    }

    public void setModified_datetime(LocalDateTime modified_datetime) {
        this.modified_datetime = modified_datetime;
    }

    public LocalDateTime getCreated_datetime() {
        return created_datetime;
    }

    public void setCreated_datetime(LocalDateTime created_datetime) {
        this.created_datetime = created_datetime;
    }
}
