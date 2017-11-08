package org.esciencecloud.facade.escienceclouddb;

import org.esciencecloud.jpa.escienceclouddb.Publication;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class PublicationFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PublicationFacade.class);

    public boolean createPublication(Publication entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updatePublication(Publication entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deletePublicationById(Publication entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public List<Publication> listAllPublication() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Publication> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Publication.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityList;
    }
}