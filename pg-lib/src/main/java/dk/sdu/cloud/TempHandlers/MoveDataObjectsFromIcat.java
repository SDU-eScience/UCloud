package dk.sdu.cloud.TempHandlers;

import dk.sdu.cloud.jpa.icatdb.RDataMain;
import dk.sdu.cloud.jpa.sduclouddb.Dataobject;
import dk.sdu.cloud.jpa.sduclouddb.Dataobjectclassification;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.List;

public class MoveDataObjectsFromIcat {
    public static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("ICATJpaPU");
        EntityManager em = emf.createEntityManager();
        EntityManagerFactory emf1 = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
        EntityManager em1 = emf1.createEntityManager();

        Query nq1 = em.createNamedQuery("RDataMain.findByNotRescId");
        nq1.setParameter("rescId", 10014);


        List<RDataMain> rDataMainList = nq1.getResultList();

        System.err.println("rDataMainList " + rDataMainList.size());



        Integer i=5000;
        while (i<15000)
        {
            em1.getTransaction().begin();
            for (RDataMain rDataMain : rDataMainList) {
                Dataobject dataobject = new Dataobject();
                dataobject.setCephid(rDataMain.getDataPath()+"_"+i.toString());
                dataobject.setDataobjectclassificationrefid(em1.find(Dataobjectclassification.class, 1));
                dataobject.setCreatedTs(new java.sql.Date(System.currentTimeMillis()));
                dataobject.setModifiedTs(dataobject.getCreatedTs());
                dataobject.setDataobjectname(rDataMain.getDataName());
                dataobject.setDataobjectsize((int) rDataMain.getDataSize());
                dataobject.setDataobjectchecksum(rDataMain.getDataChecksum());
                em1.persist(dataobject);
            }
            em1.getTransaction().commit();
            i++;
        }

        em.close();
        emf.close();
        em1.close();
        emf1.close();
    }
}
