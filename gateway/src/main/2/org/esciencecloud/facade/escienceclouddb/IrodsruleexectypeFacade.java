package org.esciencecloud.facade.escienceclouddb;

import org.esciencecloud.jpa.escienceclouddb.Irodsruleexectype;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class IrodsruleexectypeFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IrodsruleexectypeFacade.class);

    public boolean createIrodsruleexectype(Irodsruleexectype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateIrodsruleexectype(Irodsruleexectype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteIrodsruleexectypeById(Irodsruleexectype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public List<Irodsruleexectype> listAllIrodsruleexectype() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Irodsruleexectype> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Irodsruleexectype.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityList;
    }
}