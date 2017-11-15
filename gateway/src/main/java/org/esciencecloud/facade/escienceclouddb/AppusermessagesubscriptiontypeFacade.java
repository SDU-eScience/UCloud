package org.esciencecloud.facade.escienceclouddb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esciencecloud.jpa.escienceclouddb.Appusermessagesubscriptiontype;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class AppusermessagesubscriptiontypeFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AppusermessagesubscriptiontypeFacade.class);

    public boolean createAppusermessagesubscriptiontype(Appusermessagesubscriptiontype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateAppusermessagesubscriptiontype(Appusermessagesubscriptiontype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteAppusermessagesubscriptiontypeById(int id) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(em.find(Appusermessagesubscriptiontype.class, id));
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public String listAllActiveAppusermessagesubscriptiontype() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Appusermessagesubscriptiontype> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Appusermessagesubscriptiontype.findByActive");
        nq1.setParameter("active", 1);
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String listAllAppusermessagesubscriptiontype() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Appusermessagesubscriptiontype> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Appusermessagesubscriptiontype.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String entityToJson(Appusermessagesubscriptiontype entity) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entity);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    public String entityListToJson(List<Appusermessagesubscriptiontype> entityList) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entityList);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }
}