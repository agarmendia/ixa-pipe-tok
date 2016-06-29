package eus.ixa.ixa.pipe.tok;

import ixa.kaflib.KAFDocument;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

public class IXAdaemon {


  public static KAFDocument inputKafDocument(BufferedReader kafReader) throws IOException {
    KAFDocument kaf = null;   
    try {
      File temp = File.createTempFile("tempo", ".tmp");
      temp.deleteOnExit();
      BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
      String line;

      while ((line = kafReader.readLine()) != null)  {
        //if (line == null) {
        //System.err.println("KASKAZO!!!");
        //return null;
        //}
        if (line.equals("[IXAdaemon]EOF")) {
          System.err.println("[IXAdaemon]EOF jaso da");
          //bw.close();
          break;
        }
        bw.write(line);
        bw.write("\n");
      }
      bw.close();
      kaf = KAFDocument.createFromFile(temp);
      temp.delete();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return kaf;
  }

  public static void sendINIT(){
    System.err.println("[IXAdaemon]INIT");
  }

  public static void sendRUN(){
    System.err.println("[IXAdaemon]RUN");
  }

  public static void sendEOD(){
    System.err.println("[IXAdaemon]EOD");
  }
}