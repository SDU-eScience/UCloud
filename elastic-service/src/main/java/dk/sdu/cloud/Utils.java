package dk.sdu.cloud;



import dk.sdu.model.CephFileObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public List<CephFileObject> readCephFileList() throws IOException {


        String csvFile = "elastic-service/CephFsInputFile/all.csv";
        String line = "";
        String cvsSplitBy = ";";
        List<CephFileObject> cephFileObjectList = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        int lineCount = 0;
        while ((line = br.readLine()) != null) {

            if (lineCount > 0) {
                String[]   cephFileObjectRec = line.split(cvsSplitBy);

                if ((cephFileObjectRec[0].trim().length() > 0) && (cephFileObjectRec[2].trim().length() > 0)) {
                    CephFileObject cephFileObject = new CephFileObject();



                }
            }
            lineCount++;


        }

        return cephFileObjectList;
    }
}