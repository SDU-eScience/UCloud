package dk.sdu.cloud.TempHandlers;


import dk.sdu.cloud.jpa.utils.DaoUtils;

public class Test {
    public static void main(String[] args) {

        DaoUtils daoUtils = new DaoUtils();

        daoUtils.checkEmailRegistered("bjhj@imada.sdu.dk");



    }
}