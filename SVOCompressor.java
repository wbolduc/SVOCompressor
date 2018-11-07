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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author wbolduc
 */
public class SVOCompressor {
    public static String inputFile;
    public static String outputFile;
    public static boolean collapseNegations;
    
    
    public static void main(String[] args) throws IOException {
        readArgs(args);
        
        System.out.println("Loading " + inputFile);
        ArrayList<SVO> svos = loadAllSVOsFromCSV(inputFile);

        svos.forEach(svo -> {
            int neg = 0;
            if(svo.isSubNeg())
                neg += 1;
            if(svo.isVerbNeg())
                neg += 1;
            if(svo.isObjNeg())
                neg += 1;
            if (neg == 3)
                System.out.println(svo);
        });
        
        if(collapseNegations == true)
        {
            System.out.println("Collapsing negatives");
            
            svos.replaceAll(svo -> svo.collapseNegatives());
        }
        
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
        System.out.println("Storing SVO groups to " + outputFile);
        
        CSVPrinter printer = new CSVPrinter(new BufferedWriter(new FileWriter(outputFile)),
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
    
        private static void readArgs(String[] args)
    {
        
        Options options = new Options();
        options.addOption("h", "help", false, "Displays help messege");
        options.addOption("i", "inputFile",true,"The input csv to be compressed");
        options.addOption("o", "outputFile", true, "The output csv");
        options.addOption("c", "collapseNegations", false, "include this flag to collapse double and triple negatives");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            Logger.getLogger(SVOCompressor.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        if(cmd.hasOption("h"))
        {
            (new HelpFormatter()).printHelp("SVOCompressor", "This tool groups all SVOs that share the same Subject, Verb, and Objects (Both positive and negative) and collapses them down to a single representative SVO with some aggregated sentiment", options, "", true);
            System.exit(0);
        }

        if(cmd.hasOption('c'))
            collapseNegations = true;
        else
            collapseNegations = false;
        
        if(cmd.hasOption("i"))
        {
            inputFile = FilenameUtils.normalize(cmd.getOptionValue("i"));
            if (inputFile == null)
            {
                System.out.println("Not a viable input file path");
                System.exit(0);
            }

            if(FilenameUtils.isExtension(inputFile, "csv") == false)
            {
                System.out.println("Input file must be cvs");
                System.exit(0);
            }
        }
        else
        {
            System.out.println("Need an input file");
            System.exit(0);
        }

        if(cmd.hasOption("o"))
        {
            outputFile = FilenameUtils.normalize(cmd.getOptionValue("o"));
            if(outputFile == null)
            {
                System.out.println("Not a viable output file path");
                System.exit(0);
            }
            if(!FilenameUtils.getExtension(outputFile).equals("csv"))
            {
                System.out.println("Output must be csv");
                System.exit(0);
            }
        }
        else
        {
            outputFile =    FilenameUtils.getFullPath(inputFile) + 
                            FilenameUtils.getBaseName(inputFile) + 
                            "-";
            if(collapseNegations == true)
                outputFile += "neg_";
            outputFile += "CompressedSVOs.csv";

        }
    }
}
