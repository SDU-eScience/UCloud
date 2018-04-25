package dk.sdu.cloud.jpa.utils;


import dk.sdu.cloud.models.inbound.PersonJson;


import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class DaoUtils {

    public String getInitUiResponseByEmail(String email) {


//        ObjectMapper mapper = new ObjectMapper();
        String initUiResponseString = null;
//        Person person = new Person();
//        person.getDataobjectcollectionList();
//
//
//        InitUi initUi = new InitUi();
//        initUi.setFullName("Jonas Malte Hinchely");
//        //initUi.setDataobjectcollectionList(dataObjectCollectionOutboundList);
//        //initUi.setProjectList(projectOutboundList);
//        initUi.setDiskSpaceMb(100);
//        initUi.setNoMessages(10);
//        initUi.setNoProjects(6);
//        initUi.setNoSystemroles(1);
//        initUi.setNoDataObjectCollections(7);
//
//        InitUiResponse initUiResponse = new InitUiResponse();
//        initUiResponse.setRc("success");
//        initUiResponse.setInitUi(initUi);
//
//        try {
//            initUiResponseString = mapper.writeValueAsString(initUiResponse);
//            System.err.println(initUiResponseString);
//        } catch (IOException e) {
//            initUiResponse.setRc("error");
//            e.printStackTrace();
//        }

        return initUiResponseString;
    }

    public String admin_create_new_Person()
    {
        return null;
    }

    public String admin_create_new_Project()
    {
        return null;
    }

    public String admin_modify_Person()
    {
        return null;
    }

    public String admin_modify_Project()
    {
        return null;
    }


//    public boolean createNewPerson(PersonJson personJson)
//    {
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//
//
//        if (!checkPersonExists(personJson.toPersonObj(personJson)))
//        {
//
//        }
//
//        em.close();
//        emf.close();
//        return true;
//    }

//    public boolean checkEmailRegistered(String email)
//    {
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//
//       List<Email> emailList = new ArrayList<>();
//
//       boolean rc = false;
//
//        Query nq1 = em.createNamedQuery("Email.findByEmail");
//        nq1.setParameter("email", email);
//
//        emailList = nq1.getResultList();
//
//        if (!emailList.isEmpty())
//        {
//            rc = true;
//
//        }
//
//        em.close();
//        emf.close();
//        return rc;
//    }


//    public boolean checkPersonExists(Person person)
//    {
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//
//        List<Person> personList = new ArrayList<>();
//
//        boolean rc = false;
//
//        Query nq1 = em.createNamedQuery("Person.checkExist");
//        nq1.setParameter("personfirstnames", person.getPersonfirstnames().toUpperCase());
//        nq1.setParameter("personlastname", person.getPersonlastname().toUpperCase());
//
//
//        personList = nq1.getResultList();
//
//        if (!personList.isEmpty())
//        {
//            rc=true;
//        }
//
//        em.close();
//        emf.close();
//        return rc;
//    }

//    public boolean createPersonInit(PersonJson personJson)
//    {
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//        Person person = personJson.toPersonObj(personJson);
//
//        person.setRecordState(1);
//        person.setActive(0);
//        person.setCreatedTs(new java.sql.Date(System.currentTimeMillis()));
//        person.setMarkedfordelete(0);
//        person.setModifiedTs(person.getCreatedTs());
//
//        try {
//
//            em.getTransaction().begin();
//            em.persist(person);
//            em.getTransaction().commit();
//
//        }
//
//        catch (Exception e)
//        {}
//
//        finally {
//            em.close();
//            emf.close();
//        }
//    return true;
//    }
}
