package dk.sdu.cloud.jpa.utils;

import dk.sdu.cloud.jpa.sduclouddb.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.*;


public class JpaHelpers {


    public List<DataobjectDirectory> getJpaDataobjectDirectoryList() {

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
        EntityManager em = emf.createEntityManager();
        List<DataobjectDirectory> dataobjectcollectionList = new ArrayList<>();

        Query nq1 = em.createNamedQuery("DataobjectDirectory.findAll");
        //nq1.setParameter("rescId", 10014);

        dataobjectcollectionList = nq1.getResultList();

        System.err.println("dataobjectcollectionList:" + dataobjectcollectionList.size());

        em.close();
        emf.close();
        return dataobjectcollectionList;
    }

    public List<Project> getJpaProjectList() {

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
        EntityManager em = emf.createEntityManager();
        List<Project> projectJPAList = new ArrayList<>();

        Query nq1 = em.createNamedQuery("Project.findAll");
        //nq1.setParameter("rescId", 10014);

        projectJPAList = nq1.getResultList();

        System.err.println("projectJPAList:" + projectJPAList.size());

        em.close();
        emf.close();
        return projectJPAList;
    }


    public List<Dataobject> getJpaDataobjectList() {

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
        EntityManager em = emf.createEntityManager();
        List<Dataobject> dataobjectList = new ArrayList<>();

        Query nq1 = em.createNamedQuery("Dataobject.findAll");
        //nq1.setParameter("rescId", 10014);

        dataobjectList = nq1.getResultList();

        System.err.println("dataobjectList:" + dataobjectList.size());

        em.close();
        emf.close();
        return dataobjectList;
    }

    public List<Person> getJpaPersonList() {

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
        EntityManager em = emf.createEntityManager();
        List<Person> jpaPersonList = new ArrayList<>();

        Query nq1 = em.createNamedQuery("Person.findAll");
        //nq1.setParameter("rescId", 10014);

        jpaPersonList = nq1.getResultList();

        System.err.println("personJPAList:" + jpaPersonList.size());

        em.close();
        emf.close();
        return jpaPersonList;
    }

//    public List<Dataobject> getRandonDataObjectsForProjects() {
//        List<Dataobject> jpaDataobjectList = new ArrayList<>();
//
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//
//        while (jpaDataobjectList.size() < 40001) {
//
//            try {
//                Query nq1 = em.createNamedQuery("Dataobject.findByCounter");
//                nq1.setParameter("counter", randInt(1, 28770000));
//                List<Dataobject> dataobjectResultList = nq1.getResultList();
//                if (!dataobjectResultList.isEmpty()) {
//
//                    if (!jpaDataobjectList.contains(dataobjectResultList.get(0))) {
//                        jpaDataobjectList.add(dataobjectResultList.get(0));
//                        System.err.println("jpaDataobjectList: " + jpaDataobjectList.size() + " counter: " + dataobjectResultList.get(0).getCounter() + "classification: " + dataobjectResultList.get(0).getDataobjectclassificationrefid().getDataobjectclassificationname());
//                    }
//                }
//            } catch (Exception e) {
//
//            }
//        }
//
//
//        em.close();
//        emf.close();
//
//        return jpaDataobjectList;
//    }


//    public static int randInt(int min, int max) {
//
//        // Usually this can be a field rather than a method variable
//        Random rand = new Random();
//
//        // nextInt is normally exclusive of the top value,
//        // so add 1 to make it inclusive
//        int randomNum = rand.nextInt((max - min) + 1) + min;
//
//        return randomNum;
//    }
//
//    public void setCollectionTestData() {
//        List<Dataobject> dataobjectList = new ArrayList<>();
//
//        dataobjectList.addAll(this.getRandonDataObjectsForProjects());
//
//        int counter = 1;
////dataobjectcollectionrefid, personrefid
//
//        for (Dataobject dataobject : dataobjectList)
//
//        {
//            if (counter < 1001) {
//
//                addDataobjectCollectionrel(dataobject, 1);
//
//
//            }
//
//            if (counter >= 1001 && counter < 2001) {
//
//                addDataobjectCollectionrel(dataobject,2 );
//
//            }
//            if (counter >= 2001 && counter < 3001) {
//
//                addDataobjectCollectionrel(dataobject,3);
//
//            }
//            if (counter >= 3001 && counter < 4001) {
//                addDataobjectCollectionrel(dataobject,4);
//
//            }
//            if (counter >= 4001 && counter < 5001) {
//                addDataobjectCollectionrel(dataobject,5);
//
//            }
//            if (counter >= 5001 && counter < 6001) {
//                addDataobjectCollectionrel(dataobject,6);
//
//            }
//            if (counter >= 6001 && counter < 7001) {
//                addDataobjectCollectionrel(dataobject,7);
//
//            }
//            if (counter >= 7001 && counter < 8001) {
//                addDataobjectCollectionrel(dataobject,8);
//
//            }
//            if (counter >= 8001 && counter < 9001) {
//                addDataobjectCollectionrel(dataobject,9);
//
//            }
//            if (counter >= 9001 && counter < 10001) {
//                addDataobjectCollectionrel(dataobject,10);
//
//            }
//            if (counter >= 10001 && counter < 11001) {
//                addDataobjectCollectionrel(dataobject,11);
//
//            }
//            if (counter >= 11001 && counter < 12001) {
//                addDataobjectCollectionrel(dataobject,12);
//
//            }
//            if (counter >= 12001 && counter < 13001) {
//                addDataobjectCollectionrel(dataobject,13);
//
//            }
//            if (counter >= 13001 && counter < 14001) {
//                addDataobjectCollectionrel(dataobject,14);
//
//            }
//            if (counter >= 14001 && counter < 15001) {
//                addDataobjectCollectionrel(dataobject,15);
//
//            }
//            if (counter >= 15001 && counter < 16001) {
//                addDataobjectCollectionrel(dataobject,16);
//
//            }
//            if (counter >= 16001 && counter < 17001) {
//                addDataobjectCollectionrel(dataobject,17);
//
//            }
//            if (counter >= 17001 && counter < 18001) {
//                addDataobjectCollectionrel(dataobject,18);
//
//            }
//            if (counter >= 18001 && counter < 19001) {
//                addDataobjectCollectionrel(dataobject,19);
//
//            }
//            if (counter >= 19001 && counter < 20001) {
//                addDataobjectCollectionrel(dataobject,20);
//
//            }
//            if (counter >= 20001 && counter < 21001) {
//                addDataobjectCollectionrel(dataobject,21);
//
//            }
//            if (counter >= 21001 && counter < 22001) {
//                addDataobjectCollectionrel(dataobject,22);
//
//            }
//            if (counter >= 22001 && counter < 23001) {
//                addDataobjectCollectionrel(dataobject,23);
//
//            }
//            if (counter >= 23001 && counter < 24001) {
//                addDataobjectCollectionrel(dataobject,24);
//
//            }
//            if (counter >= 24001 && counter < 25001) {
//                addDataobjectCollectionrel(dataobject,25);
//
//            }
//            if (counter >= 25001 && counter < 26001) {
//                addDataobjectCollectionrel(dataobject,26);
//
//            }
//            if (counter >= 26001 && counter < 27001) {
//                addDataobjectCollectionrel(dataobject,27);
//
//            }
//            if (counter >= 27001 && counter < 28001) {
//                addDataobjectCollectionrel(dataobject,28);
//
//            }
//            if (counter >= 28001 && counter < 29001) {
//                addDataobjectCollectionrel(dataobject,29);
//
//            }
//            if (counter >= 29001 && counter < 30001) {
//                addDataobjectCollectionrel(dataobject,30);
//
//            }
//            if (counter >= 30001 && counter < 31001) {
//                addDataobjectCollectionrel(dataobject,31);
//
//            }
//            if (counter >= 31001 && counter < 32001) {
//                addDataobjectCollectionrel(dataobject,32);
//
//            }
//            if (counter >= 32001 && counter < 33001) {
//                addDataobjectCollectionrel(dataobject,33);
//
//            }
//            if (counter >= 33001 && counter < 34001) {
//                addDataobjectCollectionrel(dataobject,34);
//
//            }
//            if (counter >= 34001 && counter < 35001) {
//                addDataobjectCollectionrel(dataobject,35);
//
//            }
//            if (counter >= 35001 && counter < 36001) {
//                addDataobjectCollectionrel(dataobject,36);
//
//            }
//            if (counter >= 36001 && counter < 37001) {
//                addDataobjectCollectionrel(dataobject,37);
//
//            }
//            if (counter >= 37001 && counter < 38001) {
//                addDataobjectCollectionrel(dataobject,38);
//
//            }
//            if (counter >= 38001 && counter < 39001) {
//                addDataobjectCollectionrel(dataobject,39);
//
//            }
//            if (counter >= 39001 && counter < 40001) {
//                addDataobjectCollectionrel(dataobject,40);
//
//            }
//            counter++;
//        }
//
//
//    }
//
//    public int getDummuProjectByPersonrefid(int personrefid) {
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//
//        Person person = em.find(Person.class, personrefid);
//
//        int rc = 0;
//
//        for (ProjectPersonRelation projectPersonRelation : person.getProjectpersonrelList()) {
//            if (projectPersonRelation.getProjectrefid().getProjecttyperefid().getId() == 4) {
//                rc = projectPersonRelation.getProjectrefid().getId();
//                break;
//            }
//        }
//
//        em.close();
//        emf.close();
//
//        return rc;
//
//    }

//    public void addDataobjectCollectionrel(Dataobject dataobjectrefid,int dataobjectcollectionrefid) {
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
//        EntityManager em = emf.createEntityManager();
//        em.getTransaction().begin();
//        Dataobjectcollectionrel dataobjectcollectionrel = new Dataobjectcollectionrel();
//        dataobjectcollectionrel.setCreatedTs(new java.sql.Date(System.currentTimeMillis()));
//        dataobjectcollectionrel.setModifiedTs(dataobjectcollectionrel.getCreatedTs());
//        dataobjectcollectionrel.setDataobjectcollectionrefid(em.find(Dataobjectcollection.class,dataobjectcollectionrefid));
//        dataobjectcollectionrel.setDataobjectrefid(dataobjectrefid);
//        dataobjectcollectionrel.setMarkedfordelete(0);
//        em.persist(dataobjectcollectionrel);
//        em.getTransaction().commit();
//
//
//    }

}
