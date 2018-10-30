/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package svocompressor;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;
import multicorenlp.BWord;
import multicorenlp.SVO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author wbolduc
 */
public class SVOCompressor {

    public static void main(String[] args) throws IOException {
        
        //reading arguments
        if(args.length == 0)
        {
            System.out.println("No input file given");
            System.exit(0);
        }
        
        if(args[0].equals("-h"))
        {
            System.out.println("This tool groups all SVOs that share the same Subject, Verb, and Objects (Both positive and negative) and collapses them down to a single representative SVO with some aggregated sentiment");
            System.exit(0);
        }
        
        String inFile = args[0];
        inFile = FilenameUtils.normalize(inFile);
        if(inFile == null)
        {
            System.out.println("Not a valid file path");
            System.exit(0);
        }
        if(FilenameUtils.getExtension(inFile).equals("csv") != true)
        {
            System.out.println("Input file must be csv");
            System.exit(0);
        }
        
        String pathNoExtension = FilenameUtils.getFullPath(inFile) + FilenameUtils.getBaseName(inFile);
        
        
        System.out.println("Loading " + inFile);
        ArrayList<SVO> svos = loadAllSVOsFromCSV(inFile);

        System.out.println("Compressing...");
        //group svos, <some representative svo, list of SVOS that match>
        HashMap<SVO, ArrayList<SVO>> svoGroups = new HashMap<>();
        svos.forEach(svo -> {
            ArrayList<SVO> group = svoGroups.get(svo);
            if(group == null)
                svoGroups.put(svo, new ArrayList<>(Arrays.asList(svo)));
            else
                group.add(svo);
        });
        
        //compress groups to a single sentiment value
        //each resulting entry is of the form Pair<Representative SVO, <some data (in this case, sentiment and count)>>
        ArrayList<Pair<SVO, Pair<Double, Integer>>> svoSents = new ArrayList<>();
        svoGroups.entrySet().forEach(group -> svoSents.add(new Pair(group.getKey(), compressGroup(group.getValue())))); //to change the compression scheme, change the function (defined below
        
        //sorting by count
        System.out.println("Sorting by count...");
        Collections.sort(svoSents, new Comparator(){
            @Override
            public int compare(Object o1, Object o2) {
                return ((Pair<SVO, Pair<Double, Integer>>)o2).getValue().getValue() - ((Pair<SVO, Pair<Double, Integer>>)o1).getValue().getValue();
            }
        });
        
        
        //writing compressed SVO file
        System.out.println("Storing SVO groups");
        CSVPrinter printer = new CSVPrinter(new BufferedWriter(new FileWriter(pathNoExtension + "-compressedSVOs.csv")),
                                    CSVFormat.DEFAULT.withHeader(   "subject",
                                                                    "subjectNegated",
                                                                    "verb",
                                                                    "verbNegated",
                                                                    "object",
                                                                    "objectNegated",
                                                                    "sentiment",
                                                                    "count"));
        svoSents.forEach(tuple -> {
            try {
                SVO svo = tuple.getKey();
                BWord subject = svo.getSubject();
                BWord verb = svo.getVerb();
                BWord object = svo.getObject();
                printer.printRecord(subject.word,
                                    subject.negated,
                                    verb.word,
                                    verb.negated,
                                    object.word,
                                    object.negated,
                                    tuple.getValue().getKey(),      //sentiment for this group
                                    tuple.getValue().getValue());   //count of this group
            } catch (IOException ex) {
                Logger.getLogger(SVOCompressor.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        printer.close();
        
        System.out.println("Done");
    }
    
    public static Pair<Double, Integer> compressGroup(ArrayList<SVO> group)
    {
        //FILL THIS WITH THE REQUIRED SENTIMENT AND WEIGHTING
        double sum = 0;
        for(SVO svo : group)
        {
            sum += svo.getSentiment();
        }
        return new Pair<Double,Integer>(sum/group.size(), group.size());    //currently just averaged 
    }
    
    public static ArrayList<SVO> loadAllSVOsFromCSV(String fileName) throws FileNotFoundException, IOException
    {
        ArrayList<SVO> svos = new ArrayList<>();
        
        Reader csvData = new FileReader(fileName);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(csvData);
        
        for(CSVRecord rec : records)
        {
            svos.add(new SVO(   rec.get("subject"),
                                Boolean.parseBoolean(rec.get("subjectNegated")),
                                rec.get("verb"),
                                Boolean.parseBoolean(rec.get("verbNegated")),
                                rec.get("object"),
                                Boolean.parseBoolean(rec.get("objectNegated")),
                                Double.parseDouble(rec.get("sentimentClass"))));
        }     
        return svos;
    }    
}
