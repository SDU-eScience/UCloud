package org.esciencecloud.facade.escienceclouddb;

import org.esciencecloud.jpa.escienceclouddb.Personappusermessagesubscriptiontyperel;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class PersonappusermessagesubscriptiontyperelFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PersonappusermessagesubscriptiontyperelFacade.class);

    public boolean createPersonappusermessagesubscriptiontyperel(Personappusermessagesubscriptiontyperel entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updatePersonappusermessagesubscriptiontyperel(Personappusermessagesubscriptiontyperel entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deletePersonappusermessagesubscriptiontyperelById(Personappusermessagesubscriptiontyperel entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public List<Personappusermessagesubscriptiontyperel> listAllPersonappusermessagesubscriptiontyperel() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Personappusermessagesubscriptiontyperel> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Personappusermessagesubscriptiontyperel.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityList;
    }
}